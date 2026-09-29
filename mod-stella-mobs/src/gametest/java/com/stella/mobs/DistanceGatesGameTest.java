package com.stella.mobs;

import com.stella.mobs.behavior.DistanceGates;
import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.behavior.SpawnSafety;
import com.stella.mobs.config.GearConfig;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.config.WorldRules;
import java.util.Locale;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

/**
 * Elites and bosses have a first distance from 0,0, the elite chance grows with distance, and the spawn safety
 * radius is centred on 0,0. Every gate follows the same rule as the spawn safety gate: it binds only the mobs the
 * world spawns by itself, only in the Overworld, and never the forced elder guardian.
 */
public class DistanceGatesGameTest {
	private static final int SPAWN_X = 3;
	private static final int SPAWN_Y = 3;
	private static final int SPAWN_Z = 3;
	private static final double MARGIN = 2.0;
	private static final double EPSILON = 1.0e-9;
	private static final double ELITE_MIN = 500.0;
	private static final double BOSS_MIN = 2000.0;
	private static final WorldRules SHIPPED = GearConfigStore.defaults().world();

	@GameTest
	public void eliteGateClosesInsideTheMinimumDistance(final GameTestHelper helper) {
		double r = ELITE_MIN;
		boolean[] actual = {
			DistanceGates.eliteAllowed(Level.OVERWORLD, EntitySpawnReason.NATURAL, r - 0.1, 0.0, r),
			DistanceGates.eliteAllowed(Level.OVERWORLD, EntitySpawnReason.NATURAL, r, 0.0, r),
			DistanceGates.eliteAllowed(Level.OVERWORLD, EntitySpawnReason.NATURAL, 0.0, -r, r),
			DistanceGates.eliteAllowed(Level.OVERWORLD, EntitySpawnReason.NATURAL, 300.0, 300.0, r),
			DistanceGates.eliteAllowed(Level.OVERWORLD, EntitySpawnReason.NATURAL, 360.0, -360.0, r),
			DistanceGates.eliteAllowed(Level.OVERWORLD, EntitySpawnReason.SPAWNER, 0.0, 0.0, r),
			DistanceGates.eliteAllowed(Level.OVERWORLD, EntitySpawnReason.STRUCTURE, 0.0, 0.0, r),
			DistanceGates.eliteAllowed(Level.OVERWORLD, EntitySpawnReason.COMMAND, 0.0, 0.0, r),
			DistanceGates.eliteAllowed(Level.OVERWORLD, EntitySpawnReason.SPAWN_ITEM_USE, 0.0, 0.0, r),
			DistanceGates.eliteAllowed(Level.OVERWORLD, null, 0.0, 0.0, r),
			DistanceGates.eliteAllowed(Level.NETHER, EntitySpawnReason.NATURAL, 0.0, 0.0, r),
			DistanceGates.eliteAllowed(Level.END, EntitySpawnReason.NATURAL, 0.0, 0.0, r),
			DistanceGates.eliteAllowed(Level.OVERWORLD, EntitySpawnReason.NATURAL, 0.0, 0.0, 0.0)
		};
		// The world's own spawns are held back until the minimum; an egg or command, a player's choice, is not.
		boolean[] expected = {false, true, true, false, true, false, false, true, true, true, true, true, true};
		checkCases(helper, "eliteGateClosesInsideTheMinimumDistance", actual, expected);
	}

	@GameTest
	public void bossGateClosesInsideTheMinimumDistance(final GameTestHelper helper) {
		double r = BOSS_MIN;
		boolean[] actual = {
			DistanceGates.bossAllowed(Level.OVERWORLD, EntitySpawnReason.NATURAL, r - 0.1, 0.0, r),
			DistanceGates.bossAllowed(Level.OVERWORLD, EntitySpawnReason.NATURAL, r, 0.0, r),
			DistanceGates.bossAllowed(Level.OVERWORLD, EntitySpawnReason.NATURAL, 0.0, -r, r),
			DistanceGates.bossAllowed(Level.OVERWORLD, EntitySpawnReason.NATURAL, 1400.0, 1400.0, r),
			DistanceGates.bossAllowed(Level.OVERWORLD, EntitySpawnReason.NATURAL, 1500.0, -1500.0, r),
			DistanceGates.bossAllowed(Level.OVERWORLD, EntitySpawnReason.SPAWNER, 0.0, 0.0, r),
			DistanceGates.bossAllowed(Level.OVERWORLD, EntitySpawnReason.COMMAND, 0.0, 0.0, r),
			DistanceGates.bossAllowed(Level.OVERWORLD, null, 0.0, 0.0, r),
			DistanceGates.bossAllowed(Level.NETHER, EntitySpawnReason.NATURAL, 0.0, 0.0, r),
			DistanceGates.bossAllowed(Level.END, EntitySpawnReason.NATURAL, 0.0, 0.0, r),
			DistanceGates.bossAllowed(Level.OVERWORLD, EntitySpawnReason.NATURAL, 0.0, 0.0, 0.0)
		};
		boolean[] expected = {false, true, true, false, true, false, true, true, true, true, true};
		checkCases(helper, "bossGateClosesInsideTheMinimumDistance", actual, expected);
	}

	@GameTest
	public void eliteChanceGrowsPerHundredBlocksAndCaps(final GameTestHelper helper) {
		WorldRules rules = SHIPPED;
		double base = 0.05;
		double per100 = rules.eliteBonusPer100();
		double cap = rules.eliteBonusCap();
		double[] actual = {
			DistanceGates.eliteChance(base, Level.OVERWORLD, 0.0, 0.0, rules),
			DistanceGates.eliteChance(base, Level.OVERWORLD, 100.0, 0.0, rules),
			DistanceGates.eliteChance(base, Level.OVERWORLD, 0.0, -5000.0, rules),
			DistanceGates.eliteChance(base, Level.OVERWORLD, 1_000_000.0, 0.0, rules),
			DistanceGates.eliteChance(base, Level.NETHER, 5000.0, 0.0, rules),
			DistanceGates.eliteChance(base, Level.END, 5000.0, 0.0, rules),
			DistanceGates.eliteChance(0.0, Level.OVERWORLD, 5000.0, 0.0, rules),
			DistanceGates.eliteChance(0.95, Level.OVERWORLD, 1_000_000.0, 0.0, rules),
			DistanceGates.eliteChance(base, Level.OVERWORLD, 5000.0, 0.0, WorldRules.OFF)
		};
		double[] expected = {
			base, base + per100, base + 50 * per100, base + cap, base, base, 0.0, 1.0, base
		};
		int wrong = 0;
		StringBuilder cases = new StringBuilder();
		for (int index = 0; index < expected.length; index++) {
			cases.append(index).append('=').append(actual[index]).append(' ');
			wrong += Math.abs(actual[index] - expected[index]) < EPSILON ? 0 : 1;
		}
		result("eliteChanceGrowsPerHundredBlocksAndCaps", "wrong=" + wrong + " per100=" + per100 + " cap=" + cap + " " + cases);
		helper.assertValueEqual(Integer.valueOf(wrong), Integer.valueOf(0), "elite chances that disagree with the rule");
		helper.succeed();
	}

	@GameTest
	public void naturalZombieBeforeTheEliteDistanceNeverRollsElite(final GameTestHelper helper) {
		double distance = originDistance(helper);
		Mob zombie = spawnWith(helper, EntityTypes.ZOMBIE, eliteConfig(distance + MARGIN));
		boolean elite = removed(zombie, Elite.isElite(zombie));
		result("naturalZombieBeforeTheEliteDistanceNeverRollsElite", "distance=" + fmt(distance) + " elite=" + elite);
		helper.assertFalse(elite, "a natural zombie before the elite distance rolled elite at a chance of 1.0");
		helper.succeed();
	}

	@GameTest
	public void naturalZombiePastTheEliteDistanceStillRollsElite(final GameTestHelper helper) {
		double distance = originDistance(helper);
		helper.assertTrue(distance > MARGIN, "the arena sits on 0,0, so no minimum can leave it past the gate");
		Mob zombie = spawnWith(helper, EntityTypes.ZOMBIE, eliteConfig(distance - MARGIN));
		boolean elite = removed(zombie, Elite.isElite(zombie));
		result("naturalZombiePastTheEliteDistanceStillRollsElite", "distance=" + fmt(distance) + " elite=" + elite);
		helper.assertTrue(elite, "a natural zombie past the elite distance did not roll elite at a chance of 1.0");
		helper.succeed();
	}

	@GameTest
	public void naturalSkeletonBeforeTheBossDistanceNeverRollsBoss(final GameTestHelper helper) {
		double distance = originDistance(helper);
		Mob skeleton = spawnWith(helper, EntityTypes.SKELETON, bossConfig(distance + MARGIN));
		boolean boss = removed(skeleton, EliteState.isBoss(skeleton));
		result("naturalSkeletonBeforeTheBossDistanceNeverRollsBoss", "distance=" + fmt(distance) + " boss=" + boss);
		helper.assertFalse(boss, "a natural skeleton before the boss distance rolled boss at a chance of 1.0");
		helper.succeed();
	}

	@GameTest
	public void naturalSkeletonPastTheBossDistanceStillRollsBoss(final GameTestHelper helper) {
		double distance = originDistance(helper);
		helper.assertTrue(distance > MARGIN, "the arena sits on 0,0, so no minimum can leave it past the gate");
		Mob skeleton = spawnWith(helper, EntityTypes.SKELETON, bossConfig(distance - MARGIN));
		boolean boss = removed(skeleton, EliteState.isBoss(skeleton));
		result("naturalSkeletonPastTheBossDistanceStillRollsBoss", "distance=" + fmt(distance) + " boss=" + boss);
		helper.assertTrue(boss, "a natural skeleton past the boss distance did not roll boss at a chance of 1.0");
		helper.succeed();
	}

	@GameTest
	public void forcedElderGuardianStaysEliteBeforeTheEliteDistance(final GameTestHelper helper) {
		double distance = originDistance(helper);
		Mob guardian = spawnWith(helper, EntityTypes.ELDER_GUARDIAN,
			rolls("\"elite_chance\": 0.0, \"boss_chance\": 0.0, \"elite_min_distance\": " + fmt(distance + MARGIN)));
		boolean elite = removed(guardian, Elite.isElite(guardian));
		result("forcedElderGuardianStaysEliteBeforeTheEliteDistance", "distance=" + fmt(distance) + " elite=" + elite);
		helper.assertTrue(elite, "the elite distance took the forced elder guardian's elite mark");
		helper.succeed();
	}

	@GameTest
	public void spawnSafetyIsCentredOnTheOriginNotTheWorldSpawn(final GameTestHelper helper) {
		double distance = originDistance(helper);
		var level = helper.getLevel();
		LevelData.RespawnData previous = level.getRespawnData();
		boolean elite;
		try {
			// The world spawn is put far from the arena: only a gate centred on 0,0 can still cover it.
			level.setRespawnData(LevelData.RespawnData.of(level.dimension(), new BlockPos(9_000, 64, -9_000), 0.0F, 0.0F));
			Mob zombie = spawnWith(helper, EntityTypes.ZOMBIE,
				rolls("\"elite_chance\": 1.0, \"boss_chance\": 0.0, \"spawn_safety_radius\": " + fmt(distance + MARGIN)));
			elite = removed(zombie, Elite.isElite(zombie));
		} finally {
			level.setRespawnData(previous);
		}
		result("spawnSafetyIsCentredOnTheOriginNotTheWorldSpawn", "distance=" + fmt(distance) + " elite=" + elite);
		helper.assertFalse(elite, "the spawn safety radius followed the world spawn instead of 0,0");
		helper.succeed();
	}

	@GameTest
	public void shippedSafetyRadiusIs240AroundTheOrigin(final GameTestHelper helper) {
		boolean valid = Math.abs(SpawnSafety.SHIPPED_RADIUS - 240.0) < EPSILON
			&& SpawnSafety.suppresses(Level.OVERWORLD, EntitySpawnReason.NATURAL, 239.9, 0.0, 0.0, 0.0, SpawnSafety.SHIPPED_RADIUS)
			&& !SpawnSafety.suppresses(Level.OVERWORLD, EntitySpawnReason.NATURAL, 240.1, 0.0, 0.0, 0.0, SpawnSafety.SHIPPED_RADIUS);
		result("shippedSafetyRadiusIs240AroundTheOrigin", "radius=" + SpawnSafety.SHIPPED_RADIUS + " valid=" + valid);
		helper.assertTrue(valid, "the shipped spawn safety radius is not 240 blocks");
		helper.succeed();
	}

	private static void checkCases(final GameTestHelper helper, final String test, final boolean[] actual, final boolean[] expected) {
		int wrong = 0;
		StringBuilder cases = new StringBuilder();
		for (int index = 0; index < expected.length; index++) {
			cases.append(index).append('=').append(actual[index]).append(' ');
			if (actual[index] != expected[index]) {
				wrong++;
			}
		}
		result(test, "wrong=" + wrong + " " + cases);
		helper.assertValueEqual(Integer.valueOf(wrong), Integer.valueOf(0), "gate answers that disagree with the rule");
		helper.succeed();
	}

	private static Mob spawnWith(final GameTestHelper helper, final EntityType<? extends Mob> type, final String config) {
		GearConfig previous = GearConfigStore.get();
		helper.setTime(Arena.NIGHT_TIME);
		Arena.buildFloor(helper);

		try {
			GearConfigStore.set(GearConfigParser.parse(config));
			Mob mob = type.create(helper.getLevel(), EntitySpawnReason.NATURAL);
			Vec3 at = helper.absoluteVec(new Vec3(SPAWN_X + 0.5, SPAWN_Y, SPAWN_Z + 0.5));
			mob.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
			mob.setPersistenceRequired();
			helper.getLevel().addFreshEntity(mob);
			return mob;
		} finally {
			GearConfigStore.set(previous);
		}
	}

	/** Removes the subject once it is read, so a live elite and its queued escorts never wander into a neighbouring arena. */
	private static boolean removed(final Mob mob, final boolean observed) {
		mob.discard();
		return observed;
	}

	private static double originDistance(final GameTestHelper helper) {
		Vec3 at = helper.absoluteVec(new Vec3(SPAWN_X + 0.5, SPAWN_Y, SPAWN_Z + 0.5));
		return Math.hypot(at.x, at.z);
	}

	private static String eliteConfig(final double minimum) {
		return rolls("\"elite_chance\": 1.0, \"boss_chance\": 0.0, \"elite_min_distance\": " + fmt(minimum));
	}

	private static String bossConfig(final double minimum) {
		return rolls("\"elite_chance\": 0.0, \"boss_chance\": 1.0, \"boss_min_distance\": " + fmt(minimum));
	}

	private static String rolls(final String body) {
		return "{ \"version\": 1, \"entities\": {}, \"rolls\": { \"roll_chance\": 0.0, " + body + " } }";
	}

	private static String fmt(final double value) {
		return String.format(Locale.ROOT, "%.3f", Math.max(0.0, value));
	}

	private static void result(final String test, final String detail) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, detail);
	}
}
