package com.stella.mobs;

import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.behavior.SpawnSafety;
import com.stella.mobs.config.GearConfig;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** M3: no natural elite or boss roll within the configured radius of the shared spawn, and nothing else gated. */
public class SpawnSafetyGameTest {
	private static final int SPAWN_X = 3;
	private static final int SPAWN_Y = 3;
	private static final int SPAWN_Z = 3;
	private static final double MARGIN = 2.0;
	private static final double EPSILON = 1.0e-9;
	private static final String OLDER_CONFIG = """
		{
			"version": 1,
			"entities": {},
			"rolls": { "roll_chance": 0.3, "boss_chance": 0.01 }
		}""";

	@GameTest
	public void gateAnswersOnlyForNaturalOverworldSpawnsInsideTheRadius(final GameTestHelper helper) {
		double r = SpawnSafety.SHIPPED_RADIUS;
		// Each case flips exactly one input away from a suppressed roll, so every input must be able to lift the gate.
		boolean[] actual = {
			SpawnSafety.suppresses(Level.OVERWORLD, EntitySpawnReason.NATURAL, r - 0.1, 0.0, 0.0, 0.0, r),
			SpawnSafety.suppresses(Level.OVERWORLD, EntitySpawnReason.NATURAL, r, 0.0, 0.0, 0.0, r),
			SpawnSafety.suppresses(Level.OVERWORLD, EntitySpawnReason.NATURAL, r + 0.1, 0.0, 0.0, 0.0, r),
			SpawnSafety.suppresses(Level.OVERWORLD, EntitySpawnReason.NATURAL, r * 0.6, r * 0.6, 0.0, 0.0, r),
			SpawnSafety.suppresses(Level.OVERWORLD, EntitySpawnReason.NATURAL, r + 5.0, 0.0, 10.0, 0.0, r),
			SpawnSafety.suppresses(Level.NETHER, EntitySpawnReason.NATURAL, 0.0, 0.0, 0.0, 0.0, r),
			SpawnSafety.suppresses(Level.END, EntitySpawnReason.NATURAL, 0.0, 0.0, 0.0, 0.0, r),
			SpawnSafety.suppresses(Level.OVERWORLD, EntitySpawnReason.SPAWNER, 0.0, 0.0, 0.0, 0.0, r),
			SpawnSafety.suppresses(Level.OVERWORLD, EntitySpawnReason.STRUCTURE, 0.0, 0.0, 0.0, 0.0, r),
			SpawnSafety.suppresses(Level.OVERWORLD, EntitySpawnReason.CHUNK_GENERATION, 0.0, 0.0, 0.0, 0.0, r),
			SpawnSafety.suppresses(Level.OVERWORLD, null, 0.0, 0.0, 0.0, 0.0, r),
			SpawnSafety.suppresses(Level.OVERWORLD, EntitySpawnReason.NATURAL, 0.0, 0.0, 0.0, 0.0, 0.0)
		};
		boolean[] expected = { true, true, false, false, true, false, false, false, false, false, false, false };
		int wrong = 0;
		StringBuilder cases = new StringBuilder();

		for (int i = 0; i < expected.length; i++) {
			cases.append(i).append('=').append(actual[i]).append(' ');
			if (actual[i] != expected[i]) {
				wrong++;
			}
		}

		result("gateAnswersOnlyForNaturalOverworldSpawnsInsideTheRadius", "wrong=" + wrong + " " + cases);
		helper.assertValueEqual(Integer.valueOf(wrong), Integer.valueOf(0), "gate answers that disagree with the rule");
		helper.succeed();
	}

	@GameTest
	public void naturalZombieInsideTheRadiusNeverRollsElite(final GameTestHelper helper) {
		double distance = spawnDistance(helper);
		Mob zombie = spawnWith(helper, EntityTypes.ZOMBIE, eliteConfig(distance + MARGIN));
		result("naturalZombieInsideTheRadiusNeverRollsElite", "distance=" + fmt(distance) + " elite=" + Elite.isElite(zombie));
		helper.assertFalse(Elite.isElite(zombie), "a natural zombie inside the radius rolled elite at a chance of 1.0");
		helper.succeed();
	}

	@GameTest
	public void naturalZombieJustOutsideTheRadiusStillRollsElite(final GameTestHelper helper) {
		double distance = spawnDistance(helper);
		helper.assertTrue(distance > MARGIN, "the arena sits on the shared spawn, so no radius can leave it outside");
		Mob zombie = spawnWith(helper, EntityTypes.ZOMBIE, eliteConfig(distance - MARGIN));
		result("naturalZombieJustOutsideTheRadiusStillRollsElite", "distance=" + fmt(distance) + " elite=" + Elite.isElite(zombie));
		helper.assertTrue(Elite.isElite(zombie), "a natural zombie outside the radius did not roll elite at a chance of 1.0");
		helper.succeed();
	}

	@GameTest
	public void naturalSkeletonInsideTheRadiusNeverRollsBoss(final GameTestHelper helper) {
		double distance = spawnDistance(helper);
		Mob skeleton = spawnWith(helper, EntityTypes.SKELETON, bossConfig(distance + MARGIN));
		result("naturalSkeletonInsideTheRadiusNeverRollsBoss", "distance=" + fmt(distance) + " boss=" + EliteState.isBoss(skeleton));
		helper.assertFalse(EliteState.isBoss(skeleton), "a natural skeleton inside the radius rolled boss at a chance of 1.0");
		helper.succeed();
	}

	@GameTest
	public void forcedElderGuardianStaysEliteInsideTheRadius(final GameTestHelper helper) {
		double distance = spawnDistance(helper);
		Mob guardian = spawnWith(helper, EntityTypes.ELDER_GUARDIAN, rolls("\"elite_chance\": 0.0, \"boss_chance\": 0.0", distance + MARGIN));
		result("forcedElderGuardianStaysEliteInsideTheRadius", "distance=" + fmt(distance) + " elite=" + Elite.isElite(guardian));
		helper.assertTrue(Elite.isElite(guardian), "the gate took the forced elder guardian's elite mark");
		helper.succeed();
	}

	@GameTest
	public void anOlderConfigGainsTheShippedSpawnSafetyRadius(final GameTestHelper helper) {
		GearConfig previous = GearConfigStore.get();

		try {
			Path older = helper.getLevel().getServer().getServerDirectory().resolve("config-migration").resolve("spawn-safety.json");
			Files.createDirectories(older.getParent());
			Files.writeString(older, OLDER_CONFIG, StandardCharsets.UTF_8);
			double parsed = GearConfigParser.parse(OLDER_CONFIG).spawnSafetyRadius();
			double migrated = GearConfigStore.loadFrom(older).spawnSafetyRadius();
			result("anOlderConfigGainsTheShippedSpawnSafetyRadius", "parsed=" + parsed + " migrated=" + migrated);
			helper.assertTrue(
				Math.abs(migrated - SpawnSafety.SHIPPED_RADIUS) < EPSILON && Math.abs(parsed - migrated) > EPSILON,
				"an older file reads " + migrated + " after migration and " + parsed + " before it"
			);
		} catch (IOException e) {
			helper.fail("could not write the older config: " + e.getMessage());
		} finally {
			GearConfigStore.set(previous);
		}

		helper.succeed();
	}

	private static Mob spawnWith(final GameTestHelper helper, final EntityType<? extends Mob> type, final String config) {
		GearConfig previous = GearConfigStore.get();
		helper.setTime(Arena.NIGHT_TIME);
		Arena.buildFloor(helper);

		try {
			GearConfigStore.set(GearConfigParser.parse(config));
			Mob mob = helper.spawnMob(type, SPAWN_X, SPAWN_Y, SPAWN_Z).spawnReason(EntitySpawnReason.NATURAL).spawn();
			mob.setPersistenceRequired();
			return mob;
		} finally {
			GearConfigStore.set(previous);
		}
	}

	private static double spawnDistance(final GameTestHelper helper) {
		Vec3 at = helper.absoluteVec(new Vec3(SPAWN_X + 0.5, SPAWN_Y, SPAWN_Z + 0.5));
		BlockPos spawn = helper.getLevel().getRespawnData().pos();
		return Math.hypot(at.x - spawn.getX(), at.z - spawn.getZ());
	}

	private static String eliteConfig(final double radius) {
		return rolls("\"elite_chance\": 1.0, \"boss_chance\": 0.0", radius);
	}

	private static String bossConfig(final double radius) {
		return rolls("\"elite_chance\": 0.0, \"boss_chance\": 1.0", radius);
	}

	private static String rolls(final String chances, final double radius) {
		return "{ \"version\": 1, \"entities\": {}, \"rolls\": { \"roll_chance\": 0.0, " + chances
			+ ", \"spawn_safety_radius\": " + String.format(Locale.ROOT, "%.3f", Math.max(0.0, radius)) + " } }";
	}

	private static String fmt(final double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	private static void result(final String test, final String detail) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, detail);
	}
}
