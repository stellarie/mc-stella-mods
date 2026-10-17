package com.stella.end;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The dragon's four wyverns: phantoms at three times the size, each ridden by a bowman in netherite. A wyvern fights
 * only once its rider is dead, and until then it cruises low over the deck so the bow reaches it. A dead one comes
 * back later, while the dragon lives and rooftop spawns are allowed.
 */
final class Wyverns {
	static final String TAG = "mod-stella-end.wyvern";
	private static final double RING = 12.0;
	/** The wyvern's anchor stands this far over the spot it spawns at: the game sets it at spawn, and the circling follows it. */
	private static final int ANCHOR_LIFT = 5;
	/** Its circling flies from 8 below its anchor to 1 above, so the anchor stands this high over the deck. */
	private static final double ANCHOR_HEIGHT = 15.0;
	private static final double CRUISE_HEIGHT = 11.0;
	/** A wyvern with a live rider stays this close to the middle of the deck, which is a disc of 23. */
	private static final double HOLD_REACH = 20.0;
	private static final int HUNT_PERIOD = 20;
	/** The anchor of a wyvern's circling stands this far from the middle, toward the middle of its own sector. */
	private static final double SECTOR_REACH = 6.0;
	/** A freed wyvern flies at its prey this fast, and gives up a swoop that has not reached them in this many ticks. */
	private static final double SWOOP_SPEED = 1.0;
	private static final int SWOOP_LIMIT = 100;
	/** A swoop touches when the wyvern's box, grown by this much, meets the player's. */
	private static final double TOUCH = 0.2;
	/** Pushing one pair apart can close another gap, so the pairs are checked again up to this many times a tick. */
	private static final int SPACING_PASSES = 16;

	/** Identities of side mobs that left the world for good, as opposed to leaving it with their chunk. */
	private static final Set<UUID> GONE = new HashSet<>();

	private Wyverns() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity.entityTags().contains(EndDragons.SIDE_TAG) && entity.getRemovalReason() != null && entity.getRemovalReason().shouldDestroy()) {
				GONE.add(entity.getUUID());
			}
		});
	}

	static void reset() {
		GONE.clear();
	}

	/** A delay drawn evenly from {@code min} to {@code max} ticks, both ends included. */
	static int draw(final RandomSource random, final int min, final int max) {
		return min + random.nextInt(max - min + 1);
	}

	static void tick(final ServerLevel end, final EnderDragon dragon, final DragonState state, final long now) {
		boolean barred = Rooftop.spawnsBlocked(end);
		for (int index = 0; index < state.slots.length; index++) {
			DragonState.Slot slot = state.slots[index];
			if (slot.wyvern != null) {
				Entity wyvern = end.getEntity(slot.wyvern);
				if (lost(wyvern, slot.wyvern)) {
					slot.respawnAt = now + DragonRules.wyvernRespawn(state.buffs);
					BehaviorLog.info("[END] wyvern lost slot={} backAt={}", index, slot.respawnAt);
					slot.wyvern = null;
					slot.rider = null;
					forget(slot);
				} else if (wyvern instanceof Phantom phantom) {
					Entity rider = slot.rider == null ? null : end.getEntity(slot.rider);
					boolean riderLives = slot.rider != null && !lost(rider, slot.rider);
					if (riderLives) {
						hold(phantom);
					}
					if (riderLives && phantom.getTarget() != null) {
						phantom.setTarget(null);
					} else if (!riderLives && phantom.getTarget() == null && now % HUNT_PERIOD == 0) {
						// Its own scan looks only 16 blocks aside.
						// A freed wyvern hunts the whole roof.
						phantom.setTarget(prey(end, phantom));
					}
					if (!riderLives) {
						freed(end, state, slot, index, phantom, now);
					}
				}
			}
			boolean due = slot.respawnAt == EndState.NEVER || now >= slot.respawnAt;
			if (slot.wyvern == null && due && !barred) {
				pair(end, dragon, state, slot, index);
			}
		}
		space(end, state);
	}

	private static void forget(final DragonState.Slot slot) {
		slot.freedAt = EndState.NEVER;
		slot.nextBreathAt = EndState.NEVER;
		slot.nextSwoopAt = EndState.NEVER;
		slot.swoop = null;
	}

	/** A wyvern whose rider has died: now and then it breathes dragon breath at a player and swoops at one. */
	private static void freed(final ServerLevel end, final DragonState state, final DragonState.Slot slot, final int index, final Phantom phantom, final long now) {
		if (slot.freedAt == EndState.NEVER) {
			slot.freedAt = now;
			slot.nextBreathAt = now + draw(end.getRandom(), DragonRules.WYVERN_BREATH_MIN, DragonRules.WYVERN_BREATH_MAX);
			slot.nextSwoopAt = now + draw(end.getRandom(), DragonRules.WYVERN_SWOOP_MIN, DragonRules.WYVERN_SWOOP_MAX);
			BehaviorLog.info("[END] wyvern freed slot={} breathAt={} swoopAt={}", index, slot.nextBreathAt, slot.nextSwoopAt);
		}
		if (slot.swoop != null) {
			swoop(end, slot, phantom, now);
			return;
		}
		if (now >= slot.nextSwoopAt) {
			ServerPlayer prey = prey(end, phantom);
			if (prey == null) {
				slot.nextSwoopAt = now + draw(end.getRandom(), DragonRules.WYVERN_SWOOP_MIN, DragonRules.WYVERN_SWOOP_MAX);
			} else {
				slot.swoop = new DragonState.WyvernSwoop(index, now, prey.getUUID());
				state.wyvernSwoops.add(slot.swoop);
				BehaviorLog.info("[END] wyvern swoop begins slot={} at={} target={}", index, now, prey.getName().getString());
			}
		}
		if (slot.swoop == null && now >= slot.nextBreathAt) {
			breathe(end, state, slot, index, phantom, now);
		}
	}

	private static void swoop(final ServerLevel end, final DragonState.Slot slot, final Phantom phantom, final long now) {
		DragonState.WyvernSwoop swoop = slot.swoop;
		ServerPlayer prey = end.getEntity(swoop.target) instanceof ServerPlayer player && player.isAlive() && Rooftop.players(end).contains(player) ? player : null;
		boolean touched = prey != null && phantom.getBoundingBox().inflate(TOUCH).intersects(prey.getBoundingBox());
		if (touched) {
			float before = prey.getHealth();
			phantom.doHurtTarget(end, prey);
			swoop.dealt = before - prey.getHealth();
			swoop.hitAt = now;
			// Away and up, so it does not hang on the player.
			phantom.setDeltaMovement(0.0, 0.6, 0.0);
		}
		if (touched || prey == null || now - swoop.startedAt > SWOOP_LIMIT) {
			swoop.endedAt = now;
			slot.swoop = null;
			slot.nextSwoopAt = now + draw(end.getRandom(), DragonRules.WYVERN_SWOOP_MIN, DragonRules.WYVERN_SWOOP_MAX);
			BehaviorLog.info("[END] wyvern swoop ends at={} hit={} dealt={} targetOnRoof={}", now, touched, swoop.dealt, prey != null);
			return;
		}
		Vec3 to = prey.position().add(0.0, prey.getBbHeight() * 0.5, 0.0).subtract(phantom.position().add(0.0, phantom.getBbHeight() * 0.5, 0.0));
		Vec3 step = to.scale(Math.min(SWOOP_SPEED, to.length()) / Math.max(to.length(), 1.0E-6));
		phantom.setPos(phantom.position().add(step));
		phantom.setDeltaMovement(step);
		phantom.setYRot((float) (Mth.atan2(to.z, to.x) * (180.0F / Math.PI)) - 90.0F);
	}

	private static void breathe(final ServerLevel end, final DragonState state, final DragonState.Slot slot, final int index, final Phantom phantom, final long now) {
		slot.nextBreathAt = now + draw(end.getRandom(), DragonRules.WYVERN_BREATH_MIN, DragonRules.WYVERN_BREATH_MAX);
		ServerPlayer prey = prey(end, phantom);
		if (prey == null) {
			return;
		}
		double x = prey.getX() + end.getRandom().triangle(0.0, DragonRules.WYVERN_BREATH_SCATTER);
		double z = prey.getZ() + end.getRandom().triangle(0.0, DragonRules.WYVERN_BREATH_SCATTER);
		AreaEffectCloud cloud = new AreaEffectCloud(end, x, prey.getY(), z);
		cloud.setOwner(phantom);
		cloud.setCustomParticle(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F));
		cloud.setRadius(DragonRules.CLOUD_RADIUS);
		DragonBreath.limit(cloud);
		cloud.addEffect(new MobEffectInstance(MobEffects.INSTANT_DAMAGE, 1, 0));
		if (end.addFreshEntity(cloud)) {
			state.breaths.add(new DragonState.Breath(index, now, cloud.getUUID(), prey.getUUID()));
			BehaviorLog.info("[END] wyvern breathes slot={} at={} target={}", index, now, prey.getName().getString());
		}
	}

	/**
	 * Pushes wyverns apart until every two stand {@link DragonRules#WYVERN_SPACING} blocks from each other along the ground.
	 * A wyvern that is swooping keeps its course and the other one gives way.
	 */
	private static void space(final ServerLevel end, final DragonState state) {
		List<Phantom> found = new ArrayList<>();
		List<Boolean> swooping = new ArrayList<>();
		for (DragonState.Slot slot : state.slots) {
			if (slot.wyvern != null && end.getEntity(slot.wyvern) instanceof Phantom phantom && phantom.isAlive()) {
				found.add(phantom);
				swooping.add(slot.swoop != null);
			}
		}
		for (int pass = 0; pass < SPACING_PASSES; pass++) {
			boolean moved = false;
			for (int a = 0; a < found.size(); a++) {
				for (int b = a + 1; b < found.size(); b++) {
					moved |= pushApart(found.get(a), found.get(b), swooping.get(a), swooping.get(b), a);
				}
			}
			if (!moved) {
				return;
			}
		}
	}

	private static boolean pushApart(final Phantom one, final Phantom other, final boolean oneSwoops, final boolean otherSwoops, final int seed) {
		double dx = other.getX() - one.getX();
		double dz = other.getZ() - one.getZ();
		double gap = Math.hypot(dx, dz);
		if (gap >= DragonRules.WYVERN_SPACING) {
			return false;
		}
		// Two on one spot have no side to leave by, so the pair's index picks one.
		double ux = gap < 1.0E-6 ? Math.cos(seed) : dx / gap;
		double uz = gap < 1.0E-6 ? Math.sin(seed) : dz / gap;
		double deficit = DragonRules.WYVERN_SPACING - gap + 1.0E-3;
		double oneShare = oneSwoops && !otherSwoops ? 0.0 : otherSwoops && !oneSwoops ? 1.0 : 0.5;
		one.setPos(one.getX() - ux * deficit * oneShare, one.getY(), one.getZ() - uz * deficit * oneShare);
		other.setPos(other.getX() + ux * deficit * (1.0 - oneShare), other.getY(), other.getZ() + uz * deficit * (1.0 - oneShare));
		return true;
	}

	/**
	 * Keeps a wyvern with a live rider between the heights the bow reaches and over the deck. The dragon's wings throw
	 * back what comes near them, and the wyvern is put back at once.
	 */
	private static void hold(final Phantom phantom) {
		double y = phantom.getY();
		double held = Mth.clamp(y, Rooftop.DECK_Y + DragonRules.WYVERN_BAND_LOW, Rooftop.DECK_Y + DragonRules.WYVERN_BAND_HIGH);
		double reach = Math.hypot(phantom.getX(), phantom.getZ());
		double pull = reach > HOLD_REACH ? HOLD_REACH / reach : 1.0;
		if (held != y || pull < 1.0) {
			phantom.setPos(phantom.getX() * pull, held, phantom.getZ() * pull);
			Vec3 motion = phantom.getDeltaMovement();
			phantom.setDeltaMovement(pull < 1.0 ? 0.0 : motion.x, 0.0, pull < 1.0 ? 0.0 : motion.z);
		}
	}

	private static @Nullable ServerPlayer prey(final ServerLevel end, final Phantom phantom) {
		ServerPlayer best = null;
		double bestDistance = Double.MAX_VALUE;
		for (ServerPlayer player : Rooftop.players(end)) {
			double distance = player.distanceToSqr(phantom);
			if (distance < bestDistance) {
				best = player;
				bestDistance = distance;
			}
		}
		return best;
	}

	/** Whether a recorded mob is dead or gone for good; one that merely sits in an unloaded chunk is not lost. */
	private static boolean lost(final @Nullable Entity entity, final UUID id) {
		return entity == null ? GONE.contains(id) : !entity.isAlive();
	}

	private static void pair(final ServerLevel end, final EnderDragon dragon, final DragonState state, final DragonState.Slot slot, final int index) {
		// Each place owns a quarter of the roof's circle, and its wyvern circles round a point in the middle of it.
		double angle = Math.PI * 2.0 * (index + 0.5) / state.slots.length;
		Vec3 hover = Rooftop.hoverPoint();
		Vec3 at = new Vec3(hover.x + Math.cos(angle) * RING, Rooftop.DECK_Y + CRUISE_HEIGHT, hover.z + Math.sin(angle) * RING);
		Phantom wyvern = EntityTypes.PHANTOM.create(end, EntitySpawnReason.EVENT);
		Skeleton rider = EntityTypes.SKELETON.create(end, EntitySpawnReason.EVENT);
		if (wyvern == null || rider == null) {
			return;
		}
		wyvern.addTag(TAG);
		wyvern.getAttribute(Attributes.SCALE).setBaseValue(DragonRules.WYVERN_SCALE);
		wyvern.refreshDimensions();
		// The game takes the anchor from where the wyvern stands as it spawns.
		// It circles round the middle of its sector, not round its own start.
		own(wyvern, new Vec3(hover.x + Math.cos(angle) * SECTOR_REACH, Rooftop.DECK_Y + ANCHOR_HEIGHT - ANCHOR_LIFT, hover.z + Math.sin(angle) * SECTOR_REACH), EndLevels.WYVERN);
		wyvern.finalizeSpawn(end, end.getCurrentDifficultyAt(wyvern.blockPosition()), EntitySpawnReason.EVENT, null);
		wyvern.snapTo(at.x, at.y, at.z, wyvern.getYRot(), 0.0F);
		kit(rider);
		own(rider, at, EndLevels.WYVERN);
		if (!end.addFreshEntity(wyvern)) {
			return;
		}
		if (!end.addFreshEntity(rider)) {
			wyvern.discard();
			return;
		}
		rider.startRiding(wyvern, true, false);
		slot.wyvern = wyvern.getUUID();
		slot.rider = rider.getUUID();
		slot.respawnAt = EndState.NEVER;
		forget(slot);
		GONE.remove(wyvern.getUUID());
		BehaviorLog.info("[END] wyvern pair slot={} at={} riding={}", index, at, rider.isPassenger());
	}

	/** What every one of the dragon's own shares: the claim on its level, the side mark, a place, and a life that outlasts despawn. */
	static void own(final Mob mob, final Vec3 at, final int level) {
		LevelBridge.claim(mob);
		mob.addTag(EndDragons.SIDE_TAG);
		mob.snapTo(at.x, at.y, at.z, mob.getRandom().nextFloat() * 360.0F, 0.0F);
		mob.setPersistenceRequired();
		LevelBridge.apply(mob, level);
	}

	private static void kit(final Skeleton rider) {
		rider.getAttribute(Attributes.SCALE).setBaseValue(DragonRules.RIDER_SCALE);
		rider.refreshDimensions();
		rider.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
		rider.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
		rider.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
		rider.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
		rider.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			rider.setDropChance(slot, 0.0F);
		}
	}
}
