package com.stella.end;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
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
				}
			}
			boolean due = slot.respawnAt == EndState.NEVER || now >= slot.respawnAt;
			if (slot.wyvern == null && due && !barred) {
				pair(end, dragon, state, slot, index);
			}
		}
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
		double angle = Math.PI * 2.0 * index / state.slots.length;
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
		// It circles round the middle of the roof, not round its own start.
		own(wyvern, new Vec3(hover.x, Rooftop.DECK_Y + ANCHOR_HEIGHT - ANCHOR_LIFT, hover.z), EndLevels.WYVERN);
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
