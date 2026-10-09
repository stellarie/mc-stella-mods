package com.stella.mobs;

import com.stella.mobs.behavior.WitherEncounter;
import com.stella.mobs.behavior.WitherPursueGoal;
import com.stella.mobs.behavior.WitherState;
import com.stella.mobs.config.WitherConfig;
import com.stella.mobs.config.WitherConfigStore;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * An encounter Wither keeps the vanilla charge-up, then flies low at the nearest player, breaks no blocks with its
 * body, and takes arrows at any health. A Wither with no state stays vanilla.
 */
public final class WitherMovementGameTest {
	private static final int CHARGE_UP = 220;
	private static final double EPSILON = 0.05;
	private static final int PURSUIT_TICKS = 200;
	private static final double STOP_SLACK = 3.0;

	private static WitherBoss spawn(final GameTestHelper helper, final WideArena arena, final double x, final double y, final double z,
		final int chargeTicks, final boolean encounter) {
		WitherBoss wither = EntityTypes.WITHER.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		Vec3 at = arena.at(x, y, z);
		wither.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		wither.setPersistenceRequired();
		wither.setInvulnerableTicks(chargeTicks);
		if (!encounter) {
			wither.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		}
		helper.getLevel().addFreshEntity(wither);
		arena.track(wither);
		return wither;
	}

	private static boolean pursuing(final WitherBoss wither) {
		return wither.getGoalSelector().getAvailableGoals().stream()
			.anyMatch(wrapped -> wrapped.getGoal() instanceof WitherPursueGoal && wrapped.isRunning());
	}

	@GameTest(maxTicks = CHARGE_UP + 80, padding = WideArena.PADDING)
	public void chargeUpUntouched(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		ServerPlayer player = arena.survivalPlayer(28, 44);
		WitherBoss wither = spawn(helper, arena, 28, WideArena.SPAWN_Y + 8, 28, CHARGE_UP, true);
		Vec3 start = wither.position();
		int[] chargeTicks = new int[1];
		int[] pursuedDuringCharge = new int[1];
		double[] drift = new double[1];
		boolean[] activatedEarly = new boolean[1];
		int[] activatedAt = new int[1];
		helper.onEachTick(() -> {
			WideArena.keepValid(player);
			if (wither.getInvulnerableTicks() > 0) {
				chargeTicks[0]++;
				if (pursuing(wither)) {
					pursuedDuringCharge[0]++;
				}
				if (WitherEncounter.isActive(wither)) {
					activatedEarly[0] = true;
				}
				drift[0] = Math.max(drift[0], Math.hypot(wither.getX() - start.x, wither.getZ() - start.z));
			} else if (activatedAt[0] == 0 && WitherEncounter.isActive(wither)) {
				activatedAt[0] = chargeTicks[0];
			}
		});
		helper.runAfterDelay(CHARGE_UP + 40, () -> {
			WitherState state = WitherState.find(wither);
			StellaMobs.LOGGER.info("[BEHAVIOR] test=witherChargeUp RESULT chargeTicks={} pursuedDuringCharge={} drift={} activatedEarly={} activatedAfter={} anchorY={}",
				chargeTicks[0], pursuedDuringCharge[0], drift[0], activatedEarly[0], activatedAt[0], state == null ? -1 : state.anchorY());
			helper.assertTrue(chargeTicks[0] >= CHARGE_UP - 20, "the charge-up ran only " + chargeTicks[0] + " ticks");
			helper.assertTrue(pursuedDuringCharge[0] == 0, "the pursuit ran during the charge-up");
			helper.assertTrue(!activatedEarly[0], "the Wither activated before the charge-up ended");
			helper.assertTrue(drift[0] < 0.5, "the Wither moved " + drift[0] + " blocks sideways during the charge-up");
			helper.assertTrue(wither.getInvulnerableTicks() == 0 && WitherEncounter.isActive(wither), "the Wither never activated");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = PURSUIT_TICKS + 60, padding = WideArena.PADDING)
	public void ceilingHolds(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		int pillarHeight = 6;
		for (int y = 1; y <= pillarHeight; y++) {
			helper.setBlock(28, y, 28, Blocks.STONE);
		}
		ServerPlayer player = arena.survivalPlayer(28.5, 28.5);
		Vec3 top = arena.at(28.5, pillarHeight + 1.0, 28.5);
		player.teleportTo(top.x, top.y, top.z);
		WitherBoss wither = spawn(helper, arena, 28, WideArena.SPAWN_Y, 44, 0, true);
		double groundTop = arena.at(0, 1.0, 0).y;
		double ceiling = groundTop + WitherConfigStore.get().get(WitherConfig.MOVE_CEILING);
		double[] maxY = {wither.getY()};
		double[] nearest = {Double.MAX_VALUE};
		helper.onEachTick(() -> {
			player.teleportTo(top.x, top.y, top.z);
			WideArena.keepValid(player);
			maxY[0] = Math.max(maxY[0], wither.getY());
			nearest[0] = Math.min(nearest[0], Math.hypot(wither.getX() - top.x, wither.getZ() - top.z));
		});
		helper.runAfterDelay(PURSUIT_TICKS, () -> {
			StellaMobs.LOGGER.info("[BEHAVIOR] test=witherCeiling RESULT maxY={} ceiling={} groundTop={} targetY={} nearest={} active={}",
				maxY[0], ceiling, groundTop, top.y, nearest[0], WitherEncounter.isActive(wither));
			double stop = WitherConfigStore.get().get(WitherConfig.MOVE_STOP_DISTANCE);
			helper.assertTrue(WitherEncounter.isActive(wither), "the Wither never activated");
			helper.assertTrue(nearest[0] <= stop + 1.0, "the Wither never closed on the player: " + nearest[0]);
			helper.assertTrue(maxY[0] <= ceiling + EPSILON, "the Wither flew to " + maxY[0] + ", past its ceiling " + ceiling);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = PURSUIT_TICKS + 60, padding = WideArena.PADDING)
	public void pursuesNearest(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		ServerPlayer near = arena.survivalPlayer(28.5, 48.5);
		ServerPlayer far = arena.survivalPlayer(28.5, 3.5);
		WitherBoss wither = spawn(helper, arena, 28, WideArena.SPAWN_Y, 28, 0, true);
		Vec3 nearAt = near.position();
		double[] closest = {Double.MAX_VALUE};
		int[] farTargeted = new int[1];
		helper.onEachTick(() -> {
			WideArena.keepValid(near);
			WideArena.keepValid(far);
			near.teleportTo(nearAt.x, nearAt.y, nearAt.z);
			closest[0] = Math.min(closest[0], Math.hypot(wither.getX() - near.getX(), wither.getZ() - near.getZ()));
			if (wither.getTarget() == far) {
				farTargeted[0]++;
			}
		});
		helper.runAfterDelay(PURSUIT_TICKS, () -> {
			double settled = Math.hypot(wither.getX() - near.getX(), wither.getZ() - near.getZ());
			StellaMobs.LOGGER.info("[BEHAVIOR] test=witherPursuit RESULT target={} closest={} settled={} farTargetedTicks={}",
				wither.getTarget() == near ? "near" : String.valueOf(wither.getTarget()), closest[0], settled, farTargeted[0]);
			double stop = WitherConfigStore.get().get(WitherConfig.MOVE_STOP_DISTANCE);
			helper.assertTrue(wither.getTarget() == near, "the Wither does not target the nearest player: " + wither.getTarget());
			helper.assertTrue(settled <= stop + 1.0 && settled >= stop - STOP_SLACK, "the Wither settled " + settled + " blocks from the nearest player, stop distance " + stop);
			helper.assertTrue(farTargeted[0] == 0, "the Wither targeted the far player for " + farTargeted[0] + " ticks");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 160, padding = WideArena.PADDING)
	public void bodyBreaksNoBlocks(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		int low = 24;
		int high = 32;
		int baseY = 4;
		int topY = 14;
		// Obsidian outlasts a skull's blast, so only the body's own block breaking can remove it.
		for (int x = low; x <= high; x++) {
			for (int y = baseY; y <= topY; y++) {
				for (int z = low; z <= high; z++) {
					helper.setBlock(x, y, z, Blocks.OBSIDIAN);
				}
			}
		}
		WitherBoss wither = spawn(helper, arena, 28.5, 8, 28.5, 0, true);
		int[] before = new int[1];
		int[] after = new int[1];
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(WitherEncounter.isActive(wither), "the Wither never activated");
			before[0] = obsidian(helper, low, high, baseY, topY);
			wither.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 1.0F);
		});
		helper.runAfterDelay(120, () -> {
			after[0] = obsidian(helper, low, high, baseY, topY);
			StellaMobs.LOGGER.info("[BEHAVIOR] test=witherBodyBlocks RESULT before={} after={}", before[0], after[0]);
			helper.assertTrue(before[0] > 0, "the obsidian block was not built");
			helper.assertTrue(after[0] == before[0], "the Wither body broke " + (before[0] - after[0]) + " blocks");
			helper.succeed();
		});
	}

	private static int obsidian(final GameTestHelper helper, final int low, final int high, final int baseY, final int topY) {
		int count = 0;
		for (int x = low; x <= high; x++) {
			for (int y = baseY; y <= topY; y++) {
				for (int z = low; z <= high; z++) {
					if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.OBSIDIAN)) {
						count++;
					}
				}
			}
		}
		return count;
	}

	@GameTest(maxTicks = 60, padding = WideArena.PADDING)
	public void arrowsHurtAHalfHealthWither(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		ServerPlayer archer = arena.survivalPlayer(28.5, 44.5);
		WitherBoss encounter = spawn(helper, arena, 10, WideArena.SPAWN_Y + 6, 10, 0, true);
		WitherBoss legacy = spawn(helper, arena, 46, WideArena.SPAWN_Y + 6, 10, 0, false);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			for (WitherBoss wither : new WitherBoss[] {encounter, legacy}) {
				wither.setHealth(wither.getMaxHealth() / 2.0F - 1.0F);
				wither.setInvulnerableTime(0);
			}
			helper.assertTrue(encounter.isPowered() && legacy.isPowered(), "the test Withers are not at half health");
			float encounterBefore = encounter.getHealth();
			float legacyBefore = legacy.getHealth();
			Arrow arrowA = new Arrow(level, archer, new ItemStack(Items.ARROW), null);
			Arrow arrowB = new Arrow(level, archer, new ItemStack(Items.ARROW), null);
			DamageSource sourceA = level.damageSources().arrow(arrowA, archer);
			DamageSource sourceB = level.damageSources().arrow(arrowB, archer);
			boolean encounterHurt = encounter.hurtServer(level, sourceA, 10.0F);
			boolean legacyHurt = legacy.hurtServer(level, sourceB, 10.0F);
			StellaMobs.LOGGER.info("[BEHAVIOR] test=witherArrows RESULT encounterHurt={} encounterLost={} legacyHurt={} legacyLost={}",
				encounterHurt, encounterBefore - encounter.getHealth(), legacyHurt, legacyBefore - legacy.getHealth());
			helper.assertTrue(legacyBefore - legacy.getHealth() <= 0.0F && !legacyHurt, "the control Wither no longer ignores arrows at half health");
			helper.assertTrue(encounterHurt && encounter.getHealth() < encounterBefore, "an arrow did not hurt the encounter Wither at half health");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40, padding = WideArena.PADDING)
	public void aLegacyWitherKeepsItsVanillaGoals(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		WitherBoss encounter = spawn(helper, arena, 10, WideArena.SPAWN_Y + 6, 10, 0, true);
		WitherBoss legacy = spawn(helper, arena, 46, WideArena.SPAWN_Y + 6, 10, 0, false);
		helper.runAfterDelay(5, () -> {
			boolean encounterPursues = hasGoal(encounter, WitherPursueGoal.class);
			boolean encounterRanged = hasGoal(encounter, RangedAttackGoal.class);
			boolean legacyPursues = hasGoal(legacy, WitherPursueGoal.class);
			boolean legacyRanged = hasGoal(legacy, RangedAttackGoal.class);
			StellaMobs.LOGGER.info("[BEHAVIOR] test=witherLegacy RESULT encounterPursues={} encounterRanged={} legacyPursues={} legacyRanged={}",
				encounterPursues, encounterRanged, legacyPursues, legacyRanged);
			helper.assertTrue(encounterPursues && !encounterRanged, "the encounter Wither did not swap its goals");
			helper.assertTrue(!legacyPursues && legacyRanged, "the legacy Wither lost its vanilla goals");
			helper.succeed();
		});
	}

	private static boolean hasGoal(final WitherBoss wither, final Class<?> type) {
		return wither.getGoalSelector().getAvailableGoals().stream().anyMatch(wrapped -> type.isInstance(wrapped.getGoal()));
	}
}
