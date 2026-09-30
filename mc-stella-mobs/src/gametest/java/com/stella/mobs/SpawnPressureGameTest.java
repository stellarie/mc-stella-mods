package com.stella.mobs;

import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.behavior.SpawnPressure;
import com.stella.mobs.config.GearConfig;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.gear.GearTier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.phys.Vec3;

/**
 * Natural hostile spawns are halved within 16 blocks of an elite and 32 blocks of a boss. The watch list holds the
 * elites and bosses, so a spawn attempt reads a handful of entries and never sweeps the level.
 */
public class SpawnPressureGameTest {
	private static final double ELITE_RADIUS = 16.0;
	private static final double BOSS_RADIUS = 32.0;
	private static final double FACTOR = 0.5;
	private static final double EPSILON = 1.0e-9;
	private static final String QUIET_CONFIG = "{ \"entities\": {}, \"rolls\": { \"roll_chance\": 0.0, \"elite_chance\": 0.0, "
		+ "\"elite_quiet_radius\": 16.0, \"boss_quiet_radius\": 32.0, \"quiet_factor\": 0.5 } }";

	@GameTest
	public void eliteQuietsSpawnsWithinSixteenBlocks(final GameTestHelper helper) {
		withConfig(QUIET_CONFIG, () -> {
			Mob elite = mark(helper, false, 3.0, 3.0);
			double[] actual = {
				factorAtOffset(helper, elite, 0.0),
				factorAtOffset(helper, elite, ELITE_RADIUS - 0.1),
				factorAtOffset(helper, elite, ELITE_RADIUS),
				factorAtOffset(helper, elite, ELITE_RADIUS + 0.1),
				factorAtOffset(helper, elite, BOSS_RADIUS - 1.0)
			};
			double[] expected = {FACTOR, FACTOR, FACTOR, 1.0, 1.0};
			elite.discard();
			check(helper, "eliteQuietsSpawnsWithinSixteenBlocks", actual, expected);
		});
	}

	@GameTest
	public void bossQuietsSpawnsWithinThirtyTwoBlocks(final GameTestHelper helper) {
		withConfig(QUIET_CONFIG, () -> {
			Mob boss = mark(helper, true, 3.0, 3.0);
			double[] actual = {
				factorAtOffset(helper, boss, 0.0),
				factorAtOffset(helper, boss, ELITE_RADIUS + 5.0),
				factorAtOffset(helper, boss, BOSS_RADIUS),
				factorAtOffset(helper, boss, BOSS_RADIUS + 0.1)
			};
			double[] expected = {FACTOR, FACTOR, FACTOR, 1.0};
			boss.discard();
			check(helper, "bossQuietsSpawnsWithinThirtyTwoBlocks", actual, expected);
		});
	}

	@GameTest
	public void overlappingElitesAndBossesHalveOnlyOnce(final GameTestHelper helper) {
		withConfig(QUIET_CONFIG, () -> {
			Mob first = mark(helper, false, 3.0, 3.0);
			Mob second = mark(helper, false, 4.0, 3.0);
			Mob boss = mark(helper, true, 5.0, 3.0);
			double[] actual = {factorAtOffset(helper, first, 0.0)};
			double[] expected = {FACTOR};
			first.discard();
			second.discard();
			boss.discard();
			check(helper, "overlappingElitesAndBossesHalveOnlyOnce", actual, expected);
		});
	}

	@GameTest
	public void aDeadOrRemovedEliteStopsQuietingSpawns(final GameTestHelper helper) {
		withConfig(QUIET_CONFIG, () -> {
			Mob elite = mark(helper, false, 3.0, 3.0);
			double alive = factorAtOffset(helper, elite, 0.0);
			Vec3 where = elite.position();
			elite.discard();
			double gone = SpawnPressure.factorAt(helper.getLevel(), where.x, where.y, where.z);
			check(helper, "aDeadOrRemovedEliteStopsQuietingSpawns", new double[] {alive, gone}, new double[] {FACTOR, 1.0});
		});
	}

	@GameTest
	public void noQuietingWhenNothingIsNearOrTheRulesAreOff(final GameTestHelper helper) {
		double[] measured = new double[2];
		withConfig(QUIET_CONFIG, () -> {
			// Nothing is watched within reach of a spot five hundred blocks off.
			Vec3 at = helper.absoluteVec(new Vec3(3.5, 3.0, 3.5));
			measured[0] = SpawnPressure.factorAt(helper.getLevel(), at.x + 500.0, at.y, at.z + 500.0);
		});
		withConfig("{ \"entities\": {}, \"rolls\": { \"roll_chance\": 0.0 } }", () -> {
			Mob elite = mark(helper, false, 3.0, 3.0);
			measured[1] = factorAtOffset(helper, elite, 0.0);
			elite.discard();
		});
		check(helper, "noQuietingWhenNothingIsNearOrTheRulesAreOff", measured, new double[] {1.0, 1.0});
	}

	@GameTest
	public void skipRateIsHalfOverManyAttempts(final GameTestHelper helper) {
		withConfig(QUIET_CONFIG, () -> {
			Mob elite = mark(helper, false, 3.0, 3.0);
			BlockPos near = elite.blockPosition();
			BlockPos far = near.offset(200, 0, 0);
			RandomSource random = RandomSource.create(20_260_929L);
			int attempts = 20_000;
			int skippedNear = 0;
			int skippedFar = 0;
			int skippedCreature = 0;
			for (int attempt = 0; attempt < attempts; attempt++) {
				skippedNear += SpawnPressure.skips(helper.getLevel(), MobCategory.MONSTER, near, random) ? 1 : 0;
				skippedFar += SpawnPressure.skips(helper.getLevel(), MobCategory.MONSTER, far, random) ? 1 : 0;
				skippedCreature += SpawnPressure.skips(helper.getLevel(), MobCategory.CREATURE, near, random) ? 1 : 0;
			}
			elite.discard();
			double p = 1.0 - FACTOR;
			double tolerance = 5.0 * Math.sqrt(attempts * p * (1.0 - p));
			boolean valid = Math.abs(skippedNear - attempts * p) < tolerance && skippedFar == 0 && skippedCreature == 0;
			result("skipRateIsHalfOverManyAttempts", "attempts=" + attempts + " skippedNear=" + skippedNear
				+ " skippedFar=" + skippedFar + " skippedCreature=" + skippedCreature + " tolerance=" + tolerance + " valid=" + valid);
			helper.assertTrue(valid, "the skip rate near an elite is not one half, or it reached a far or friendly spawn");
		});
		helper.succeed();
	}

	@GameTest
	public void vanillaNaturalSpawnerCallsTheHook(final GameTestHelper helper) {
		withConfig(QUIET_CONFIG, () -> {
			ServerLevel level = helper.getLevel();
			Mob elite = mark(helper, false, 3.0, 3.0);
			BlockPos near = elite.blockPosition();
			BlockPos far = near.offset(200, 0, 0);
			int attempts = 2_000;
			SpawnPressure.resetCounters();
			// A predicate that refuses every mob, so no spawn happens: the hook still sees each attempt.
			for (int attempt = 0; attempt < attempts; attempt++) {
				NaturalSpawner.spawnCategoryForPosition(
					MobCategory.MONSTER, level, level.getChunkAt(near), near, (type, world, pos, chunk) -> false, (mob, chunk) -> { }
				);
			}
			long nearAttempts = SpawnPressure.attempts();
			long nearSkipped = SpawnPressure.skipped();
			SpawnPressure.resetCounters();
			for (int attempt = 0; attempt < attempts; attempt++) {
				NaturalSpawner.spawnCategoryForPosition(
					MobCategory.MONSTER, level, level.getChunkAt(far), far, (type, world, pos, chunk) -> false, (mob, chunk) -> { }
				);
			}
			long farAttempts = SpawnPressure.attempts();
			long farSkipped = SpawnPressure.skipped();
			elite.discard();
			double p = 1.0 - FACTOR;
			double tolerance = 5.0 * Math.sqrt(attempts * p * (1.0 - p));
			boolean valid = nearAttempts == attempts && farAttempts == attempts && farSkipped == 0
				&& Math.abs(nearSkipped - attempts * p) < tolerance;
			result("vanillaNaturalSpawnerCallsTheHook", "attempts=" + attempts + " nearAttempts=" + nearAttempts
				+ " nearSkipped=" + nearSkipped + " farAttempts=" + farAttempts + " farSkipped=" + farSkipped
				+ " tolerance=" + tolerance + " valid=" + valid);
			helper.assertTrue(valid, "vanilla's natural spawner did not reach the hook, or the hook skipped the wrong share");
		});
		helper.succeed();
	}

	private static Mob mark(final GameTestHelper helper, final boolean boss, final double x, final double z) {
		Mob mob = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		Vec3 at = helper.absoluteVec(new Vec3(x + 0.5, 3.0, z + 0.5));
		mob.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		EliteState.setSummoned(mob, true);
		com.stella.mobs.behavior.ReactiveState.get(mob).setGearApplied(true);
		EliteState.markElite(mob, GearTier.DIAMOND);
		if (boss) {
			EliteState.setBoss(mob, true);
		}
		mob.setPersistenceRequired();
		helper.getLevel().addFreshEntity(mob);
		return mob;
	}

	private static double factorAtOffset(final GameTestHelper helper, final Mob center, final double offset) {
		Vec3 at = center.position();
		return SpawnPressure.factorAt(helper.getLevel(), at.x + offset, at.y, at.z);
	}

	private static void check(final GameTestHelper helper, final String test, final double[] actual, final double[] expected) {
		int wrong = 0;
		StringBuilder cases = new StringBuilder();
		for (int index = 0; index < expected.length; index++) {
			cases.append(index).append('=').append(actual[index]).append(' ');
			wrong += Math.abs(actual[index] - expected[index]) < EPSILON ? 0 : 1;
		}
		result(test, "wrong=" + wrong + " " + cases);
		helper.assertValueEqual(Integer.valueOf(wrong), Integer.valueOf(0), "quiet factors that disagree with the rule");
		helper.succeed();
	}

	private static void withConfig(final String json, final Runnable body) {
		GearConfig previous = GearConfigStore.get();
		try {
			GearConfigStore.set(GearConfigParser.parse(json));
			body.run();
		} finally {
			GearConfigStore.set(previous);
		}
	}

	private static void result(final String test, final String detail) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, detail);
	}
}
