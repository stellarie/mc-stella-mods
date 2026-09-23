package com.stella.mobs;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.config.GearConfig;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.RollSpec;
import com.stella.mobs.gear.GearTier;
import com.stella.mobs.scaling.SpawnScaling;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;

/**
 * The boss foundation: the config surface B01 to B08 reads, the durable boss flag with its codec
 * stability, and the area level the ten level bonus is measured against.
 */
public class BossGameTest {
	private static final String TEST = "bossFoundation";
	private static final double EPSILON = 1.0E-9;
	private static final String BOSS_CONFIG = """
		{
			"entities": {},
			"rolls": {
				"boss_chance": 0.01,
				"boss_health_multiplier": 3.0,
				"boss_level_bonus": 0.5,
				"entities": { "minecraft:skeleton": { "boss_chance": 1.0 } }
			}
		}""";

	@GameTest(maxTicks = 200)
	public void bossChanceIsOffUnlessAConfigAsksForIt(final GameTestHelper helper) {
		GearConfig config = GearConfigParser.parse(BOSS_CONFIG);
		double rigDefault = RollSpec.DEFAULT.bossChance();
		double configured = config.rollFor(Identifier.parse("minecraft:zombie")).bossChance();
		double overridden = config.rollFor(Identifier.parse("minecraft:skeleton")).bossChance();
		double healthMultiplier = config.rollFor(Identifier.parse("minecraft:zombie")).bossHealthMultiplier();
		double levelBonus = config.rollFor(Identifier.parse("minecraft:zombie")).bossLevelBonus();

		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test={} RESULT rigDefault={} configured={} overridden={} healthMultiplier={} levelBonus={}",
			TEST, rigDefault, configured, overridden, healthMultiplier, levelBonus
		);

		helper.assertValueEqual(Double.valueOf(rigDefault), Double.valueOf(0.0), "the code default keeps every rig free of bosses");
		helper.assertValueEqual(Double.valueOf(configured), Double.valueOf(0.01), "the configured boss chance");
		helper.assertValueEqual(Double.valueOf(overridden), Double.valueOf(1.0), "the per-type boss chance override");
		helper.assertValueEqual(Double.valueOf(healthMultiplier), Double.valueOf(3.0), "the configured boss health multiplier");
		helper.assertValueEqual(Double.valueOf(levelBonus), Double.valueOf(0.5), "the configured boss level bonus");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void anOlderSaveStillDecodesAndTheBossFlagSurvives(final GameTestHelper helper) {
		// A save written before the boss field existed carries none of the keys, which is an empty object.
		// NbtOps.empty() is the end tag rather than an empty map, so this half runs on the JSON ops.
		EliteState legacy = EliteState.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).resultOrPartial().orElseThrow();
		EliteState marked = new EliteState(
			false, true, Optional.of(GearTier.NETHERITE), 3, true, false, false, false, false, java.util.List.of("hardened")
		);
		var encoded = EliteState.CODEC.encodeStart(JsonOps.INSTANCE, marked).resultOrPartial().orElseThrow();
		EliteState decoded = EliteState.CODEC.parse(JsonOps.INSTANCE, encoded).resultOrPartial().orElseThrow();

		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test={} RESULT legacyBoss={} legacyElite={} decodedBoss={} decodedElite={} decodedTier={} decodedPearls={}",
			TEST, legacy.boss(), legacy.elite(), decoded.boss(), decoded.elite(), decoded.eliteTier().orElse(null), decoded.pearlsLeft()
		);

		helper.assertFalse(legacy.boss(), "an older save decoded as a boss");
		helper.assertFalse(legacy.elite(), "an older save invented an elite");
		helper.assertTrue(decoded.boss(), "the boss flag did not survive a round trip");
		helper.assertTrue(decoded.elite(), "the elite flag did not survive a round trip");
		helper.assertValueEqual(decoded.eliteTier().orElse(null), GearTier.NETHERITE, "the tier after a round trip");
		helper.assertValueEqual(Integer.valueOf(decoded.pearlsLeft()), Integer.valueOf(3), "the pearl budget after a round trip");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void theLevelStepMatchesR12sOwnDistanceWeight(final GameTestHelper helper) {
		Arena.buildFloor(helper);
		helper.setTime(Arena.NIGHT_TIME);
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, 2, 3, 2).spawn();

		double tenLevels = SpawnScaling.LEVEL_HEALTH_BONUS * SpawnScaling.LEVEL_DISTANCE_POINTS;
		int level = SpawnScaling.areaLevel(mob);

		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test={} RESULT tenLevelsOfHealth={} r12HealthWeight={} areaLevel={} depthPoints={}",
			TEST, tenLevels, SpawnScaling.HEALTH_DISTANCE_WEIGHT, level, SpawnScaling.LEVEL_DEPTH_POINTS
		);

		helper.assertTrue(
			Math.abs(tenLevels - SpawnScaling.HEALTH_DISTANCE_WEIGHT) < EPSILON,
			"ten levels are not R12's distance weight: " + tenLevels
		);
		helper.assertTrue(
			level >= 0 && level <= SpawnScaling.LEVEL_DISTANCE_POINTS + SpawnScaling.LEVEL_DEPTH_POINTS,
			"the area level left its range: " + level
		);
		helper.succeed();
	}
}
