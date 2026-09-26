package com.stella.equips;

import com.stella.equips.power.PowerAttributes;
import com.stella.equips.power.PowerConfig;
import com.stella.equips.power.PowerConfigParser;
import com.stella.equips.power.PowerConfigStore;
import com.stella.equips.power.PowerCurve;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public class PowerCurveGameTest {
	private static final int DISTANCE_CAP_BLOCKS = 9600;
	private static final int MOB_OVERALL_CAP = 36;
	private static final int MOB_LEVEL_CAP = 32;
	private static final int MOB_LEVEL_AT_DEPTH_CAP = 36;
	private static final double TOLERANCE = 1.0E-9;

	@GameTest
	public void theHealthCurveIsFlatPerLevelToTheEngineCeiling(final GameTestHelper helper) {
		PowerConfigStore.load();
		PowerConfig config = PowerConfigStore.get();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PacketRecorder.attach(player);
		Map<Integer, Double> measured = new LinkedHashMap<>();
		for (int level : List.of(2, 10, 50, 100)) {
			PowerAttributes.apply(player, level, config);
			measured.put(level, (double) player.getMaxHealth());
		}

		PowerAttributes.apply(player, 300, config);
		double ceiling = player.getMaxHealth();
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=healthCurve RESULT level2={} level10={} level50={} level100={} level300={} "
				+ "engineCeiling=1024 rule=20+4*(level-1)",
			measured.get(2), measured.get(10), measured.get(50), measured.get(100), ceiling
		);
		helper.assertTrue(Math.abs(measured.get(2) - 24.0) < 0.001, "level 2 read " + measured.get(2));
		helper.assertTrue(Math.abs(measured.get(10) - 56.0) < 0.001, "level 10 read " + measured.get(10));
		helper.assertTrue(Math.abs(measured.get(50) - 216.0) < 0.001, "level 50 read " + measured.get(50));
		helper.assertTrue(Math.abs(measured.get(100) - 416.0) < 0.001, "level 100 read " + measured.get(100));
		helper.assertTrue(
			Math.abs(ceiling - 1024.0) < 0.001,
			"level 300 read " + ceiling + " instead of the engine ceiling of 1024"
		);
		helper.succeed();
	}

	@GameTest
	public void theBundledDocumentRaisesNoFalseAlarm(final GameTestHelper helper) {
		PowerConfigStore.bundled();
		List<String> ignored = PowerConfigParser.ignoredKeys();
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=bundledKeys RESULT ignoredCount={} keys={}", ignored.size(), ignored
		);
		helper.assertTrue(
			ignored.isEmpty(),
			"the mod warns about the file it wrote itself: " + ignored
		);
		helper.succeed();
	}

	@GameTest
	public void aStaleSchemaIsReportedWithItsIgnoredKeys(final GameTestHelper helper) {
		String stale = """
			{
				"version": 1,
				"level_cap": 50,
				"distance": { "max_health_per_point": 0.5 },
				"combat": { "attack_damage_max": 8.0, "attack": { "damage_max": 40.0 } },
				"attributes": { "max_health_at_cap": 1004.0 },
				"mob_level": { "dimension_scale": { "minecraft:the_nether": 8.0 } }
			}
			""";
		PowerConfig parsed = PowerConfigParser.parse(stale, PowerFixtures.acceptanceConfig());
		List<String> ignored = PowerConfigParser.ignoredKeys();
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=staleSchema RESULT ignoredCount={} keys={} healthPerLevel={} levelCap={}",
			ignored.size(), ignored, parsed.attributes().healthPerLevel(), parsed.levelCap()
		);
		helper.assertTrue(
			ignored.contains("distance.max_health_per_point"),
			"the stale travel-point key was not reported: " + ignored
		);
		helper.assertTrue(
			ignored.contains("combat.attack_damage_max"),
			"the stale combat key was not reported: " + ignored
		);
		helper.assertTrue(
			ignored.contains("attributes.max_health_at_cap"),
			"the retired health key was not reported: " + ignored
		);
		helper.assertTrue(
			ignored.contains("combat.attack.damage_max"),
			"a twice nested stale key was not reported: " + ignored
		);
		helper.assertTrue(
			!ignored.contains("mob_level.dimension_scale.minecraft:the_nether"),
			"a known dimension scale id was reported as stale: " + ignored
		);
		helper.assertTrue(
			ignored.equals(List.of(
				"distance.max_health_per_point",
				"combat.attack_damage_max",
				"combat.attack.damage_max",
				"attributes.max_health_at_cap"
			)),
			"the stale document reported " + ignored + " instead of exactly its four stale keys"
		);
		helper.assertTrue(
			Math.abs(parsed.attributes().healthPerLevel() - 4.0) < 1.0E-9,
			"the bundled health per level lost to a stale file: " + parsed.attributes().healthPerLevel()
		);
		helper.succeed();
	}

	@GameTest
	public void threatNormalizesOntoThePlayerLevelScale(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		int threatCap = config.mobLevel().levelCap();
		int levelCap = config.levelCap();
		int atZero = PowerCurve.normalizedThreat(0, config);
		int atHalf = PowerCurve.normalizedThreat(threatCap / 2, config);
		int atCap = PowerCurve.normalizedThreat(threatCap, config);
		int aboveCap = PowerCurve.normalizedThreat(threatCap * 2, config);
		int belowZero = PowerCurve.normalizedThreat(-5, config);
		int drops = 0;
		for (int raw = 1; raw <= threatCap; raw++) {
			drops += PowerCurve.normalizedThreat(raw, config) < PowerCurve.normalizedThreat(raw - 1, config) ? 1 : 0;
		}

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=threatNormalized RESULT at0={} atHalf={} atCap={} aboveCap={} belowZero={} drops={}",
			atZero, atHalf, atCap, aboveCap, belowZero, drops
		);
		helper.assertTrue(
			atZero == 0 && atHalf == Math.round(threatCap / 2 * (double) levelCap / threatCap) && atCap == levelCap
				&& aboveCap == levelCap && belowZero == 0 && drops == 0,
			"raw threat 0, half, cap, double and -5 normalized to " + atZero + ", " + atHalf + ", " + atCap + ", "
				+ aboveCap + ", " + belowZero + " with " + drops + " drops"
		);
		helper.succeed();
	}

	@GameTest
	public void aZeroThreatCapNormalizesEveryThreatToZero(final GameTestHelper helper) {
		PowerConfig base = PowerFixtures.acceptanceConfig();
		PowerConfig.MobLevel mob = base.mobLevel();
		PowerConfig config = PowerFixtures.withMobLevel(new PowerConfig.MobLevel(
			mob.blocksPerLevel(), mob.cap(), mob.depthBonusCap(), mob.depthBonusBlocks(), mob.depthReferenceY(),
			0, mob.otherDimensionScale(), mob.dimensionScale()
		));
		int nonZero = 0;
		for (int raw = 0; raw <= MOB_OVERALL_CAP; raw++) {
			nonZero += PowerCurve.normalizedThreat(raw, config) != 0 ? 1 : 0;
		}

		StellaEquips.LOGGER.info("[BEHAVIOR] test=threatZeroCap RESULT nonZero={}", nonZero);
		helper.assertTrue(nonZero == 0, nonZero + " raw threats normalized above zero with a threat cap of 0");
		helper.succeed();
	}

	@GameTest
	public void theEveryAttributeValueIsTheValueAtTheCap(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		double atCap = PowerCurve.levelFraction(config.levelCap(), config);
		double atOne = PowerCurve.levelFraction(1, config);
		double atTwentyFive = PowerCurve.levelFraction(25, config);
		double expectedHalfway = 24.0 / (double) (config.levelCap() - 1);
		helper.assertTrue(atCap == 1.0, "the fraction at the cap was " + atCap);
		helper.assertTrue(atOne == 0.0, "the fraction at level 1 was " + atOne);
		helper.assertTrue(
			Math.abs(atTwentyFive - expectedHalfway) < TOLERANCE,
			"the fraction at level 25 was " + atTwentyFive + " instead of " + expectedHalfway
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=levelFraction RESULT atLevel1={} atLevel25={} atCap={}",
			atOne, atTwentyFive, atCap
		);
		helper.succeed();
	}

	@GameTest
	public void mobLevelAtTheDistanceCapIsThirtyTwo(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		int level = mobLevelAt(DISTANCE_CAP_BLOCKS, 0, PowerFixtures.OVERWORLD, config);
		helper.assertTrue(level == MOB_LEVEL_CAP, "the mob level at (9600, 0, 0) was " + level + " instead of 32");
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=mobLevelDistanceCap RESULT x=9600 y=0 mobLevel={}", level
		);
		helper.succeed();
	}

	@GameTest
	public void mobLevelAtTheDepthCapIsThirtySix(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		int level = mobLevelAt(DISTANCE_CAP_BLOCKS, -64, PowerFixtures.OVERWORLD, config);
		helper.assertTrue(
			level == MOB_LEVEL_AT_DEPTH_CAP, "the mob level at (9600, -64, 0) was " + level + " instead of 36"
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=mobLevelOverallCap RESULT x=9600 y=-64 mobLevel={}", level
		);
		helper.succeed();
	}

	@GameTest
	public void netherDistanceScaleMatchesTheOverworld(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		double netherScaled = PowerCurve.scaledDistance(1200.0, 0.0, 0.0, 0.0, PowerFixtures.NETHER, config);
		int nether = mobLevelAt(1200, 0, PowerFixtures.NETHER, config);
		int overworld = mobLevelAt(DISTANCE_CAP_BLOCKS, 0, PowerFixtures.OVERWORLD, config);
		helper.assertTrue(netherScaled == 9600.0, "1200 nether blocks scaled to " + netherScaled);
		helper.assertTrue(nether == overworld, "the nether mob read " + nether + " and the overworld " + overworld);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=netherScale RESULT netherBlocks=1200 scaledDistance={} netherLevel={} overworldLevel={}",
			netherScaled, nether, overworld
		);
		helper.succeed();
	}

	@GameTest
	public void mobLevelNearSpawnAtSeaLevelIsZero(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		int level = mobLevelAt(100, 63, PowerFixtures.OVERWORLD, config);
		helper.assertTrue(level == 0, "the mob level at (100, 63, 100) was " + level + " instead of 0");
		StellaEquips.LOGGER.info("[BEHAVIOR] test=mobLevelNearSpawn RESULT x=100 y=63 mobLevel={}", level);
		helper.succeed();
	}

	@GameTest
	public void mobLevelStaysInsideItsLimitsForTheWholeTable(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		int lowest = Integer.MAX_VALUE;
		int highest = Integer.MIN_VALUE;
		int samples = 0;
		for (int distance = -600; distance <= 40000; distance += 100) {
			for (int y = 320; y >= -128; y -= 8) {
				int level = PowerCurve.threat(distance, y, config);
				lowest = Math.min(lowest, level);
				highest = Math.max(highest, level);
				samples++;
			}
		}

		helper.assertTrue(lowest == 0, "the lowest mob level in the table was " + lowest);
		helper.assertTrue(highest == MOB_OVERALL_CAP, "the highest mob level in the table was " + highest);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=mobLevelLimits RESULT samples={} lowest={} highest={}", samples, lowest, highest
		);
		helper.succeed();
	}

	@GameTest
	public void bandColorsMatchTheDecisionRecord(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		PowerCurve.Band atSix = PowerCurve.band(6, config);
		PowerCurve.Band atTen = PowerCurve.band(10, config);
		PowerCurve.Band atTwenty = PowerCurve.band(20, config);
		PowerCurve.Band atFive = PowerCurve.band(5, config);
		PowerCurve.Band atMinusFive = PowerCurve.band(-5, config);
		PowerCurve.Band atMinusSix = PowerCurve.band(-6, config);
		PowerCurve.Band atMinusTen = PowerCurve.band(-10, config);
		helper.assertTrue(atSix == PowerCurve.Band.ORANGE, "gap 6 read " + atSix);
		helper.assertTrue(atTen == PowerCurve.Band.YELLOW, "gap 10 read " + atTen);
		helper.assertTrue(atTwenty == PowerCurve.Band.RED, "gap 20 read " + atTwenty);
		helper.assertTrue(atFive == PowerCurve.Band.GREEN, "gap 5 read " + atFive);
		helper.assertTrue(atMinusFive == PowerCurve.Band.GREEN, "gap -5 read " + atMinusFive);
		helper.assertTrue(atMinusSix == PowerCurve.Band.WHITE, "gap -6 read " + atMinusSix);
		helper.assertTrue(atMinusTen == PowerCurve.Band.WHITE, "gap -10 read " + atMinusTen);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=bandColors RESULT gap6={} gap10={} gap20={} gap5={} gap-5={} gap-6={} gap-10={}",
			atSix, atTen, atTwenty, atFive, atMinusFive, atMinusSix, atMinusTen
		);
		helper.succeed();
	}

	@GameTest
	public void bandIsDefinedForEveryGapBetweenMinusFortyAndForty(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		java.util.EnumSet<PowerCurve.Band> seen = java.util.EnumSet.noneOf(PowerCurve.Band.class);
		int undefined = 0;
		for (int gap = -40; gap <= 40; gap++) {
			PowerCurve.Band band = PowerCurve.band(gap, config);
			if (band == null) {
				undefined++;
			} else {
				seen.add(band);
			}
		}

		helper.assertTrue(undefined == 0, undefined + " gaps had no band");
		helper.assertTrue(seen.size() == 5, "only " + seen.size() + " of the 5 bands appear");
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=bandComplete RESULT gaps=81 undefined={} bandsSeen={}", undefined, seen.size()
		);
		helper.succeed();
	}

	private static int mobLevelAt(final int blocks, final int y, final Identifier dimension, final PowerConfig config) {
		double scaled = PowerCurve.scaledDistance(blocks, 0.0, 0.0, 0.0, dimension, config);
		return PowerCurve.threat((int) Math.floor(scaled), y, config);
	}
}
