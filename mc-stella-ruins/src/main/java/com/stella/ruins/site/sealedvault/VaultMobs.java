package com.stella.ruins.site.sealedvault;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.MobAbility;
import com.stella.ruins.world.RuinCoreBlockEntity;
import com.stella.ruins.world.RuinData;
import com.stella.ruins.world.RuinFrame;
import com.stella.ruins.world.RuinMobKind;
import com.stella.ruins.world.RuinMobs;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The three themed mobs of the vault, and the loot tables the vault owns. The Vault Warden is the center of the
 * site: it hunts the nearest player through the whole maze, heals while nobody is near, and lets a player feel it
 * coming through the walls.
 */
public final class VaultMobs {
	public static final ResourceKey<LootTable> VAULT_LOOT = table("chests/sealed_vault");
	public static final ResourceKey<LootTable> WRAITH_DROPS = table("entities/void_wraith");
	public static final ResourceKey<LootTable> GUARDIAN_DROPS = table("entities/vault_guardian");
	public static final ResourceKey<LootTable> WARDEN_DROPS = table("entities/vault_warden");

	/** The Darkness a wraith's hit gives, in ticks. */
	public static final int WRAITH_DARKNESS_TICKS = 60;
	/** The Warden heals this much each second while no player is within {@link #REGEN_QUIET_RANGE}. */
	public static final float WARDEN_REGEN_PER_SECOND = 2.0F;
	public static final double REGEN_QUIET_RANGE = 16.0;
	public static final double HEARTBEAT_RANGE = 16.0;
	public static final int HEARTBEAT_PERIOD = 40;
	public static final double DARKNESS_RANGE = 8.0;
	public static final int DARKNESS_TICKS = 40;
	/** The Warden path search must cover the whole maze, so its follow range is wide. */
	public static final double WARDEN_FOLLOW_RANGE = 128.0;

	/** Plays the heartbeat to one player. Tests replace it with a counter. */
	private static BiConsumer<ServerPlayer, Double> heartbeat = VaultMobs::playHeartbeat;

	public static RuinMobKind VOID_WRAITH;
	public static RuinMobKind VAULT_GUARDIAN;
	public static RuinMobKind VAULT_WARDEN;

	private VaultMobs() {
	}

	private static ResourceKey<LootTable> table(final String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(StellaRuins.MOD_ID, path));
	}

	public static void register() {
		VOID_WRAITH = RuinMobKind.builder("void_wraith")
			.base(EntityTypes.VEX)
			.health(3.0)
			.ability(new MobAbility() {
				@Override
				public void tick(final Mob mob, final ServerLevel level) {
					keepInside(mob, level);
				}

				@Override
				public void onHurtTarget(final Mob mob, final LivingEntity target) {
					target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, WRAITH_DARKNESS_TICKS, 0));
				}
			})
			.drops(WRAITH_DROPS)
			.register();
		VAULT_GUARDIAN = RuinMobKind.builder("vault_guardian")
			.base(EntityTypes.HUSK)
			.scale(1.2F)
			.health(3.0)
			.gear(EquipmentSlot.HEAD, () -> new ItemStack(Items.NETHERITE_HELMET))
			.drops(GUARDIAN_DROPS)
			.register();
		VAULT_WARDEN = RuinMobKind.builder("vault_warden")
			.base(EntityTypes.WITHER_SKELETON)
			.scale(1.8F)
			.health(10.0)
			.gear(EquipmentSlot.MAINHAND, () -> new ItemStack(Items.NETHERITE_SWORD))
			.ability(new MobAbility() {
				@Override
				public void tick(final Mob mob, final ServerLevel level) {
					hunt(mob, level);
				}
			})
			.drops(WARDEN_DROPS)
			.boss(true)
			.register();
	}

	/** Replaces the heartbeat sink, or restores the real one with null. */
	public static void setHeartbeat(final @Nullable BiConsumer<ServerPlayer, Double> sink) {
		heartbeat = sink == null ? VaultMobs::playHeartbeat : sink;
	}

	// ---- bounds ----

	/** The box of the vault a roster mob belongs to, or null for a mob the data does not know. */
	public static @Nullable BoundingBox boundsOf(final Mob mob, final ServerLevel level) {
		BlockPos core = RuinData.get(level).coreOf(mob.getUUID());
		if (core == null || !(level.getBlockEntity(core) instanceof RuinCoreBlockEntity block)) {
			return null;
		}
		RuinFrame frame = block.frame();
		return frame == null ? null : frame.box();
	}

	/** Moves a mob that stands outside its vault back to the nearest block inside. Returns true when it moved. */
	public static boolean keepInside(final Mob mob, final ServerLevel level) {
		BoundingBox box = boundsOf(mob, level);
		if (box == null || box.isInside(mob.blockPosition())) {
			return false;
		}
		double x = clamp(mob.getX(), box.minX() + 0.5, box.maxX() + 0.5);
		double y = clamp(mob.getY(), box.minY(), box.maxY());
		double z = clamp(mob.getZ(), box.minZ() + 0.5, box.maxZ() + 0.5);
		mob.snapTo(x, y, z, mob.getYRot(), mob.getXRot());
		mob.getNavigation().stop();
		mob.setDeltaMovement(Vec3.ZERO);
		StellaRuins.LOGGER.debug("[RUINS] vault mob {} pulled back inside to {}", mob.getUUID(), mob.blockPosition().toShortString());
		return true;
	}

	private static double clamp(final double value, final double min, final double max) {
		return Math.max(min, Math.min(max, value));
	}

	// ---- the Warden ----

	/** One ability tick of the Warden: stay inside, track, heal, and be felt. */
	static void hunt(final Mob warden, final ServerLevel level) {
		keepInside(warden, level);
		AttributeInstance follow = warden.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow != null && follow.getBaseValue() < WARDEN_FOLLOW_RANGE) {
			follow.setBaseValue(WARDEN_FOLLOW_RANGE);
		}
		BoundingBox box = boundsOf(warden, level);
		ServerPlayer nearest = nearestPlayer(level, box, warden.position());
		if (nearest != null) {
			if (warden.getTarget() != nearest) {
				warden.setTarget(nearest);
			}
			if (!warden.isNoAi()) {
				walkToward(warden, level, nearest);
			}
		}
		if (!anyPlayerWithin(level, warden.position(), REGEN_QUIET_RANGE)) {
			warden.heal(WARDEN_REGEN_PER_SECOND * RuinMobs.ABILITY_TICKS / 20.0F);
		}
		feel(warden, level);
		level.sendParticles(ParticleTypes.SOUL, warden.getX(), warden.getY() + warden.getBbHeight() * 0.5, warden.getZ(), 2, 0.5, warden.getBbHeight() * 0.35, 0.5, 0.01);
	}

	/** Walks the next leg of the route through the maze, or straight at the player when no leg is needed. */
	private static void walkToward(final Mob warden, final ServerLevel level, final ServerPlayer target) {
		BlockPos core = RuinData.get(level).coreOf(warden.getUUID());
		RuinFrame frame = core != null && level.getBlockEntity(core) instanceof RuinCoreBlockEntity block ? block.frame() : null;
		Vec3 leg = frame == null ? null : VaultGuide.waypoint(frame, warden.position(), target.position());
		if (leg == null || !warden.getNavigation().moveTo(leg.x, leg.y, leg.z, 1.0)) {
			warden.getNavigation().moveTo(target, 1.0);
		}
	}

	/** The living player closest to a spot, inside the box when one is given, with or without a line of sight. */
	public static @Nullable ServerPlayer nearestPlayer(final ServerLevel level, final @Nullable BoundingBox box, final Vec3 from) {
		ServerPlayer best = null;
		double bestDistance = Double.MAX_VALUE;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !player.isAlive() || (box != null && !box.isInside(player.blockPosition()))) {
				continue;
			}
			double distance = player.position().distanceToSqr(from);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = player;
			}
		}
		return best;
	}

	private static boolean anyPlayerWithin(final ServerLevel level, final Vec3 from, final double range) {
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.isAlive() && player.position().distanceToSqr(from) <= range * range) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Within 16 blocks the player hears a heartbeat every 40 ticks, closer and louder as the Warden nears. Within 8
	 * blocks the player has Darkness. Walls do not matter.
	 */
	static void feel(final Mob warden, final ServerLevel level) {
		boolean beat = warden.tickCount % HEARTBEAT_PERIOD < RuinMobs.ABILITY_TICKS;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			double distance = player.position().distanceTo(warden.position());
			if (distance <= HEARTBEAT_RANGE && beat) {
				heartbeat.accept(player, distance);
			}
			if (distance <= DARKNESS_RANGE) {
				player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARKNESS_TICKS, 0, true, false));
			}
		}
	}

	private static void playHeartbeat(final ServerPlayer player, final Double distance) {
		float closeness = (float) (1.0 - distance / HEARTBEAT_RANGE);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.0F + closeness * 2.0F, 0.8F + closeness * 0.5F);
	}
}
