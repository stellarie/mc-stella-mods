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

		PowerAttributes.apply(player, 500, config);
		double ceiling = player.getMaxHealth();
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=healthCurve RESULT level2={} level10={} level50={} level100={} level300={} "
				+ "engineCeiling=1024 rule=20+2.5*(level-1)",
			measured.get(2), measured.get(10), measured.get(50), measured.get(100), ceiling
		);
		helper.assertTrue(Math.abs(measured.get(2) - 22.5) < 0.001, "level 2 read " + measured.get(2));
		helper.assertTrue(Math.abs(measured.get(10) - 42.5) < 0.001, "level 10 read " + measured.get(10));
		helper.assertTrue(Math.abs(measured.get(50) - 142.5) < 0.001, "level 50 read " + measured.get(50));
		helper.assertTrue(Math.abs(measured.get(100) - 267.5) < 0.001, "level 100 read " + measured.get(100));
		helper.assertTrue(
			Math.abs(ceiling - 1024.0) < 0.001,
			"level 500 read " + ceiling + " instead of the engine ceiling of 1024"
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
			ignored.equals(List.of(
				"distance.max_health_per_point",
				"combat.attack_damage_max",
				"combat.attack.damage_max",
				"attributes.max_health_at_cap",
				"mob_level.dimension_scale.minecraft:the_nether"
			)),
			"the stale document reported " + ignored + " instead of exactly its five stale keys"
		);
		helper.assertTrue(
			Math.abs(parsed.attributes().healthPerLevel() - 4.0) < 1.0E-9,
			"the bundled health per level lost to a stale file: " + parsed.attributes().healthPerLevel()
		);
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
}
