package com.stella.equips;

import com.stella.equips.power.PowerConfig;
import com.stella.equips.power.PowerCurve;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

public class PowerCurveGameTest {
	private static final int DISTANCE_CAP_BLOCKS = 9600;
	private static final int MOB_OVERALL_CAP = 36;
	private static final int MOB_LEVEL_CAP = 32;
	private static final int MOB_LEVEL_AT_DEPTH_CAP = 36;
	private static final double TOLERANCE = 1.0E-9;
	private static final double STEP_TOLERANCE = 1.0;
	private static final double RATIO_TOLERANCE = 0.2;
	private static final long SMALLEST_STEP_FOR_THE_RATIO_CHECK = 8L;

	@GameTest
	public void levelFiftyCostsSixThousandTwoHundredSeventyFive(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		long total = PowerCurve.pointsForLevel(config.levelCap(), config);
		helper.assertTrue(
			total == PowerFixtures.LEVEL_50_POINTS,
			"level 50 cost " + total + " points instead of " + PowerFixtures.LEVEL_50_POINTS
		);
		helper.assertTrue(PowerCurve.pointsForLevel(1, config) == 0L, "level 1 cost more than nothing");
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=levelFiftyTotal RESULT pointsForLevel50={} pointsForLevel1={}",
			total, PowerCurve.pointsForLevel(1, config)
		);
		helper.succeed();
	}

	@GameTest
	public void theLastStepCostsEightHundredNineteenAndIsThirteenPercent(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		long at49 = PowerCurve.pointsForLevel(49, config);
		long at50 = PowerCurve.pointsForLevel(50, config);
		long step = at50 - at49;
		double share = (double) step / (double) at50;
		helper.assertTrue(
			step == PowerFixtures.STEP_49_TO_50_POINTS,
			"the step from 49 to 50 cost " + step + " points instead of " + PowerFixtures.STEP_49_TO_50_POINTS
		);
		helper.assertTrue(
			Math.abs(share - PowerFixtures.LEVEL_50_SHARE) < 0.01,
			"the last step was " + share + " of the lifetime total instead of about 0.13"
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=lastStep RESULT at49={} at50={} step={} share={}",
			at49, at50, step, share
		);
		helper.succeed();
	}

	@GameTest
	public void theCurveIsExponentialWithinRounding(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		double worstExactDeviation = 0.0;
		double worstRatioDeviation = 0.0;
		int levels = 0;
		long previousStep = 0L;
		for (int level = 2; level <= config.levelCap(); level++) {
			long step = PowerCurve.pointsForLevel(level, config) - PowerCurve.pointsForLevel(level - 1, config);
			double exact = config.curve().base() * Math.pow(config.curve().growth(), level - 2);
			worstExactDeviation = Math.max(worstExactDeviation, Math.abs(step - exact));
			if (previousStep >= SMALLEST_STEP_FOR_THE_RATIO_CHECK) {
				double ratio = (double) step / (double) previousStep;
				worstRatioDeviation = Math.max(
					worstRatioDeviation, Math.abs(ratio - config.curve().growth()) / config.curve().growth()
				);
			}

			previousStep = step;
			levels++;
		}

		helper.assertTrue(
			worstExactDeviation < STEP_TOLERANCE,
			"a step was off the exact exponential value by " + worstExactDeviation
		);
		helper.assertTrue(
			worstRatioDeviation < RATIO_TOLERANCE,
			"a step ratio was off the growth of " + config.curve().growth() + " by " + worstRatioDeviation
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=curveExponential RESULT levels={} growth={} worstExactDeviation={} worstRatioDeviation={}",
			levels, config.curve().growth(), worstExactDeviation, worstRatioDeviation
		);
		helper.succeed();
	}

	@GameTest
	public void theLevelRisesMonotonicallyAndReachesFiftyAtTheStatedTotal(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		int previous = PowerCurve.levelFor(0L, config);
		int drops = 0;
		for (long points = 1L; points <= PowerFixtures.LEVEL_50_POINTS; points++) {
			int current = PowerCurve.levelFor(points, config);
			if (current < previous) {
				drops++;
			}

			previous = current;
		}

		int atOneBelow = PowerCurve.levelFor(PowerFixtures.LEVEL_50_POINTS - 1L, config);
		int atTotal = PowerCurve.levelFor(PowerFixtures.LEVEL_50_POINTS, config);
		helper.assertTrue(drops == 0, "the level function dropped " + drops + " times over the whole curve");
		helper.assertTrue(
			atOneBelow == 49,
			"one point below the total read level " + atOneBelow + " instead of 49"
		);
		helper.assertTrue(
			atTotal == config.levelCap(),
			"the stated total read level " + atTotal + " instead of " + config.levelCap()
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=curveMonotonic RESULT samples={} drops={} levelAtTotalMinusOne={} levelAtTotal={}",
			PowerFixtures.LEVEL_50_POINTS, drops, atOneBelow, atTotal
		);
		helper.succeed();
	}

	@GameTest
	public void theAwardReadsTheMobLevel(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		int atZero = PowerCurve.awardFor(0, config);
		int atThirtyTwo = PowerCurve.awardFor(32, config);
		int atFifty = PowerCurve.awardFor(50, config);
		int atMobCap = PowerCurve.awardFor(MOB_OVERALL_CAP, config);
		helper.assertTrue(atZero == 1, "a level 0 mob paid " + atZero + " instead of 1");
		helper.assertTrue(atThirtyTwo == 16, "a level 32 mob paid " + atThirtyTwo + " instead of 16");
		helper.assertTrue(atFifty == 25, "a level 50 mob paid " + atFifty + " instead of 25");
		helper.assertTrue(atMobCap == 18, "a level 36 mob paid " + atMobCap + " instead of 18");
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=awardFormula RESULT divisor={} minimum={} at0={} at32={} at36={} at50={}",
			config.award().divisor(), config.award().minimum(), atZero, atThirtyTwo, atMobCap, atFifty
		);
		helper.succeed();
	}

	@GameTest
	public void aDeepKillPaysMoreThanAShallowOne(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		int shallow = PowerCurve.awardFor(PowerCurve.mobLevel(0, 63, config), config);
		int deep = PowerCurve.awardFor(PowerCurve.mobLevel(DISTANCE_CAP_BLOCKS, -64, config), config);
		helper.assertTrue(
			deep > shallow,
			"a deep mob paid " + deep + " and a shallow mob paid " + shallow
		);
		helper.assertTrue(
			deep == 18 && shallow == 1,
			"the deep award was " + deep + " and the shallow award was " + shallow
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=deepKillPays RESULT shallowMobLevel={} shallowAward={} deepMobLevel={} deepAward={}",
			PowerCurve.mobLevel(0, 63, config), shallow,
			PowerCurve.mobLevel(DISTANCE_CAP_BLOCKS, -64, config), deep
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
				int level = PowerCurve.mobLevel(distance, y, config);
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
		return PowerCurve.mobLevel((int) Math.floor(scaled), y, config);
	}
}
