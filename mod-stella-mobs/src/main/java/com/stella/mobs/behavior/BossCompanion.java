package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.gear.GearRoller;
import com.stella.mobs.gear.GearTier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * B05: exactly one elite companion of the boss's own kind. It is queued at spawn and added on the level
 * tick boundary like {@link EscortSquad}, but a boss takes no six mob escort, so the count is one and the
 * companion is marked elite rather than left ordinary.
 */
public final class BossCompanion {
	public static final int COUNT = 1;
	public static final float DROP_CHANCE = 0.02F;
	/** The companion's tier: the same netherite grade as the boss, so it is never the weaker of the two. */
	public static final GearTier TIER = GearTier.NETHERITE;
	private static final double SCATTER = 3.0;
	private static final int FOLLOW_PRIORITY = 5;
	private static final double FOLLOW_SPEED = 1.0;
	private static final float FOLLOW_STOP_DISTANCE = 6.0F;
	private static final float FOLLOW_AREA_SIZE = 16.0F;
	private static final AttachmentType<List<Pending>> QUEUE = AttachmentRegistry.createDefaulted(
		Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "boss_companion_queue"), ArrayList::new
	);

	private BossCompanion() {
	}

	private record Pending(Mob boss) {
	}

	public static void register() {
		StellaMobs.LOGGER.debug("Registered the boss companion queue attachment {}", QUEUE.identifier());
	}

	public static void queue(final Mob boss) {
		if (boss.level() instanceof ServerLevel level) {
			((AttachmentTarget) level).getAttachedOrCreate(QUEUE).add(new Pending(boss));
		}
	}

	/** Adds every companion queued on the previous tick, once the level's own entity pass has finished. */
	public static void drain(final ServerLevel level) {
		List<Pending> pending = ((AttachmentTarget) level).getAttached(QUEUE);
		if (pending == null || pending.isEmpty()) {
			return;
		}

		List<Pending> batch = List.copyOf(pending);
		pending.clear();

		for (Pending item : batch) {
			Mob boss = item.boss();
			if (!boss.isAlive() || boss.isRemoved()) {
				continue;
			}

			// B05: exactly one companion. A second queue entry for the same boss is dropped here.
			ReactiveState state = ReactiveState.get(boss);
			if (state.isCompanionSpawned()) {
				continue;
			}

			state.setCompanionSpawned(true);
			spawnOne(level, boss);		}
	}

	@SuppressWarnings("unchecked")
	private static void spawnOne(final ServerLevel level, final Mob boss) {
		EntityType<? extends Mob> type = (EntityType<? extends Mob>) boss.getType();
		Mob companion = type.create(level, EntitySpawnReason.EVENT);
		if (companion == null) {
			StellaMobs.LOGGER.info("[BEHAVIOR] test=bossCompanion FAILED reason=create-null type={}", type);
			return;
		}

		RandomSource random = companion.getRandom();
		companion.snapTo(
			boss.getX() + (random.nextDouble() * 2.0 - 1.0) * SCATTER,
			boss.getY(),
			boss.getZ() + (random.nextDouble() * 2.0 - 1.0) * SCATTER,
			random.nextFloat() * 360.0F,
			0.0F
		);
		companion.finalizeSpawn(
			level, level.getCurrentDifficultyAt(companion.blockPosition()), EntitySpawnReason.EVENT, null
		);
		// Both flags must exist before the add: ENTITY_LOAD fires inside addFreshEntity, and the
		// summoned flag is what stops the companion from taking a boss section of its own.
		EliteState.setSummoned(companion, true);
		ReactiveState.get(companion).setGearApplied(true);
		Elite.mark(companion, TIER, Elite.HEALTH_MULTIPLIER);

		Registry<Enchantment> registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		GearRoller.rollSet(companion, TIER, DROP_CHANCE, random, registry);

		companion.getGoalSelector().removeAllGoals(goal -> goal instanceof WaterAvoidingRandomStrollGoal);
		companion.getGoalSelector().addGoal(
			FOLLOW_PRIORITY, new FollowAllyGoal(companion, boss, FOLLOW_SPEED, FOLLOW_STOP_DISTANCE, FOLLOW_AREA_SIZE)
		);
		level.addFreshEntity(companion);
		ReactiveState.get(boss).setCompanionId(companion.getUUID());

		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=bossCompanion t={} COMPANION boss={} type={} elite={} tier={} health={} bossFlag={} summoned={}",
			level.getGameTime(),
			BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()),
			BuiltInRegistries.ENTITY_TYPE.getKey(companion.getType()),
			Elite.isElite(companion),
			Elite.kitTier(companion),
			companion.getMaxHealth(),
			EliteState.isBoss(companion),
			EliteState.isSummoned(companion)
		);
	}

	/** The companions of one boss: the one it was linked to at spawn, if it is still alive. */
	public static List<Mob> companionsOf(final ServerLevel level, final Mob boss) {
		UUID recorded = ReactiveState.get(boss).companionId();
		if (recorded == null) {
			return List.of();
		}

		Entity entity = level.getEntity(recorded);
		return entity instanceof Mob companion ? List.of(companion) : List.of();
	}
}
