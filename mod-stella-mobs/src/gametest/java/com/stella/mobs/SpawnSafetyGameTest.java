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
	public void gateAnswersForEveryWorldSpawnInsideTheRadius(final GameTestHelper helper) {
		double r = SpawnSafety.SHIPPED_RADIUS;
		// Each case flips exactly one input away from a suppressed roll, so every input must be able to lift the gate.
		boolean[] actual = {
			SpawnSafety.suppresses(Level.OVERWORLD, EntitySpawnReason.NATURAL, r - 0.1, 0.0, 0.0, 0.0, r),
			SpawnSafety.suppresses(Level.OVERWORLD, EntitySpawnReason.NATURAL, r, 0.0, 0.0, 0.0, r),
			SpawnSafety.suppresses(Level.OVERWORLD, EntitySpawnReason.NATURAL, r + 0.1, 0.0, 0.0, 0.0, r),
			SpawnSafety.suppresses(Level.OVERWORLD, EntitySpawnReason.NATURAL, r * 0.75, r * 0.75, 0.0, 0.0, r),
			SpawnSafety.suppresses(Level.OVERWORLD, EntitySpawnReason.NATURAL, r + 5.0, 0.0, 10.0, 0.0, r),
			SpawnSafety.suppresses(Level.NETHER, EntitySpawnReason.NATURAL, 0.0, 0.0, 0.0, 0.0, r),
			SpawnSafety.suppresses(Level.END, EntitySpawnReason.NATURAL, 0.0, 0.0, 0.0, 0.0, r),
			SpawnSafety.suppresses(Level.OVERWORLD, EntitySpawnReason.SPAWNER, 0.0, 0.0, 0.0, 0.0, r),
			SpawnSafety.suppresses(Level.OVERWORLD, EntitySpawnReason.STRUCTURE, 0.0, 0.0, 0.0, 0.0, r),
			SpawnSafety.suppresses(Level.OVERWORLD, EntitySpawnReason.CHUNK_GENERATION, 0.0, 0.0, 0.0, 0.0, r),
			SpawnSafety.suppresses(Level.OVERWORLD, null, 0.0, 0.0, 0.0, 0.0, r),
			SpawnSafety.suppresses(Level.OVERWORLD, EntitySpawnReason.PATROL, 0.0, 0.0, 0.0, 0.0, r),
			SpawnSafety.suppresses(Level.OVERWORLD, EntitySpawnReason.SPAWN_ITEM_USE, 0.0, 0.0, 0.0, 0.0, r),
			SpawnSafety.suppresses(Level.OVERWORLD, EntitySpawnReason.COMMAND, 0.0, 0.0, 0.0, 0.0, r),
			SpawnSafety.suppresses(Level.OVERWORLD, EntitySpawnReason.NATURAL, 0.0, 0.0, 0.0, 0.0, 0.0)
		};
		// The world's own spawns are all gated; an egg or a command, a player's own choice, is not.
		boolean[] expected = { true, true, false, false, true, false, false, true, true, true, false, true, false, false, false };
		int wrong = 0;
		StringBuilder cases = new StringBuilder();

		for (int i = 0; i < expected.length; i++) {
			cases.append(i).append('=').append(actual[i]).append(' ');
			if (actual[i] != expected[i]) {
				wrong++;
			}
		}

		result("gateAnswersForEveryWorldSpawnInsideTheRadius", "wrong=" + wrong + " " + cases);
		helper.assertValueEqual(Integer.valueOf(wrong), Integer.valueOf(0), "gate answers that disagree with the rule");
		helper.succeed();
	}

	@GameTest
	public void naturalZombieInsideTheRadiusNeverRollsElite(final GameTestHelper helper) {
		double distance = spawnDistance(helper);
		Mob zombie = spawnWith(helper, EntityTypes.ZOMBIE, eliteConfig(distance + MARGIN));
		boolean elite = removed(zombie, Elite.isElite(zombie));
		result("naturalZombieInsideTheRadiusNeverRollsElite", "distance=" + fmt(distance) + " elite=" + elite);
		helper.assertFalse(elite, "a natural zombie inside the radius rolled elite at a chance of 1.0");
		helper.succeed();
	}

	@GameTest
	public void naturalZombieJustOutsideTheRadiusStillRollsElite(final GameTestHelper helper) {
		double distance = spawnDistance(helper);
		helper.assertTrue(distance > MARGIN, "the arena sits on 0,0, so no radius can leave it outside");
		Mob zombie = spawnWith(helper, EntityTypes.ZOMBIE, eliteConfig(distance - MARGIN));
		boolean elite = removed(zombie, Elite.isElite(zombie));
		result("naturalZombieJustOutsideTheRadiusStillRollsElite", "distance=" + fmt(distance) + " elite=" + elite);
		helper.assertTrue(elite, "a natural zombie outside the radius did not roll elite at a chance of 1.0");
		helper.succeed();
	}

	@GameTest
	public void naturalSkeletonInsideTheRadiusNeverRollsBoss(final GameTestHelper helper) {
		double distance = spawnDistance(helper);
		Mob skeleton = spawnWith(helper, EntityTypes.SKELETON, bossConfig(distance + MARGIN));
		boolean boss = removed(skeleton, EliteState.isBoss(skeleton));
		result("naturalSkeletonInsideTheRadiusNeverRollsBoss", "distance=" + fmt(distance) + " boss=" + boss);
		helper.assertFalse(boss, "a natural skeleton inside the radius rolled boss at a chance of 1.0");
		helper.succeed();
	}

	@GameTest
	public void forcedElderGuardianStaysEliteInsideTheRadius(final GameTestHelper helper) {
		double distance = spawnDistance(helper);
		Mob guardian = spawnWith(helper, EntityTypes.ELDER_GUARDIAN, rolls("\"elite_chance\": 0.0, \"boss_chance\": 0.0", distance + MARGIN));
		boolean elite = removed(guardian, Elite.isElite(guardian));
		result("forcedElderGuardianStaysEliteInsideTheRadius", "distance=" + fmt(distance) + " elite=" + elite);
		helper.assertTrue(elite, "the gate took the forced elder guardian's elite mark");
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
			// The helper's spawn reaches the load hook as STRUCTURE, so the mob is created and added the way a natural spawn is.
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

	private static double spawnDistance(final GameTestHelper helper) {
		Vec3 at = helper.absoluteVec(new Vec3(SPAWN_X + 0.5, SPAWN_Y, SPAWN_Z + 0.5));
		return Math.hypot(at.x - SpawnSafety.ORIGIN_X, at.z - SpawnSafety.ORIGIN_Z);
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
