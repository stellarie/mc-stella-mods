package com.stella.equips;

import com.stella.equips.power.PowerConfig;
import com.stella.equips.power.PowerCurve;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public class PowerCurveGameTest {
	private static final int DISTANCE_CAP_BLOCKS = 10000;
	private static final int BEYOND_DISTANCE_CAP_BLOCKS = 12000;
	private static final int MOB_OVERALL_CAP = 36;
	private static final int MONOTONIC_SAMPLE_POINTS = 10000;
	private static final double TOLERANCE = 1.0E-9;

	@GameTest
	public void levelRisesMonotonicallyOverTheFirstTenThousandPoints(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		int previous = PowerCurve.levelFor(0L, config);
		int drops = 0;
		for (long points = 1L; points <= MONOTONIC_SAMPLE_POINTS; points++) {
			int current = PowerCurve.levelFor(points, config);
			if (current < previous) {
				drops++;
			}
			previous = current;
		}
		int atTravelCeiling = PowerCurve.levelFor(
			PowerFixtures.DISTANCE_CAP_POINTS + PowerFixtures.DEPTH_CAP_POINTS, config
		);
		helper.assertTrue(drops == 0, "the level function dropped " + drops + " times over 0 to 10000 points");
		helper.assertTrue(
			previous == config.levelCap(),
			"10000 points reached level " + previous + " instead of the cap " + config.levelCap()
		);
		helper.assertTrue(
			atTravelCeiling == 35,
			"the distance and depth ceiling of 2008 points reached level " + atTravelCeiling + " instead of 35"
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=levelMonotonic RESULT samples={} drops={} levelAt10000Points={} levelAtTravelCeiling={}",
			MONOTONIC_SAMPLE_POINTS, drops, previous, atTravelCeiling
		);
		helper.succeed();
	}

	@GameTest
	public void levelOneCostsNothingAndTheFirstStepCostsNine(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		long first = PowerCurve.pointsForLevel(1, config);
		long second = PowerCurve.pointsForLevel(2, config);
		int levelAtZeroPoints = PowerCurve.levelFor(0L, config);
		helper.assertTrue(first == 0L, "level 1 cost " + first + " points instead of 0");
		helper.assertTrue(second == 9L, "level 2 cost " + second + " points instead of base 6 plus 3");
		helper.assertTrue(levelAtZeroPoints == 1, "zero points reached level " + levelAtZeroPoints + " instead of 1");
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=levelOneFree RESULT pointsForLevel1={} pointsForLevel2={} levelAtZeroPoints={}",
			first, second, levelAtZeroPoints
		);
		helper.succeed();
	}

	@GameTest
	public void levelFiftyCostsThreeThousandNineHundredSixtyNine(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		long total = PowerCurve.pointsForLevel(50, config);
		helper.assertTrue(total == PowerFixtures.LEVEL_50_POINTS, "level 50 cost " + total + " points instead of 3969");
		helper.assertTrue(
			PowerCurve.levelFor(PowerFixtures.LEVEL_50_POINTS - 1L, config) == 49,
			"3968 points reached level " + PowerCurve.levelFor(PowerFixtures.LEVEL_50_POINTS - 1L, config)
		);
		helper.assertTrue(
			PowerCurve.levelFor(PowerFixtures.LEVEL_50_POINTS, config) == 50,
			"3969 points reached level " + PowerCurve.levelFor(PowerFixtures.LEVEL_50_POINTS, config)
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=levelFiftyCost RESULT pointsForLevel50={} levelAt3968={} levelAt3969={}",
			total,
			PowerCurve.levelFor(PowerFixtures.LEVEL_50_POINTS - 1L, config),
			PowerCurve.levelFor(PowerFixtures.LEVEL_50_POINTS, config)
		);
		helper.succeed();
	}

	@GameTest
	public void mobLevelAtTheDistanceCapIsThirtyTwo(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		int level = mobLevelAt(9600.0, 0.0, PowerFixtures.OVERWORLD, config);
		helper.assertTrue(level == 32, "the mob level at (9600, 0, 0) was " + level + " instead of 32");
		StellaEquips.LOGGER.info("[BEHAVIOR] test=mobLevelDistanceCap RESULT x=9600 y=0 mobLevel={}", level);
		helper.succeed();
	}

	@GameTest
	public void mobLevelAtTheDepthCapIsThirtySix(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		int level = mobLevelAt(9600.0, -64.0, PowerFixtures.OVERWORLD, config);
		helper.assertTrue(level == MOB_OVERALL_CAP, "the mob level at (9600, -64, 0) was " + level + " instead of 36");
		StellaEquips.LOGGER.info("[BEHAVIOR] test=mobLevelOverallCap RESULT x=9600 y=-64 mobLevel={}", level);
		helper.succeed();
	}

	@GameTest
	public void netherDistanceScaleMatchesTheOverworld(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		double netherScaled = PowerCurve.scaledDistance(1200.0, 0.0, 0.0, 0.0, PowerFixtures.NETHER, config);
		int nether = mobLevelAt(1200.0, 0.0, PowerFixtures.NETHER, config);
		int overworld = mobLevelAt(9600.0, 0.0, PowerFixtures.OVERWORLD, config);
		helper.assertTrue(
			netherScaled == 9600.0,
			"1200 nether blocks scaled to " + netherScaled + " instead of 9600"
		);
		helper.assertTrue(
			nether == overworld,
			"a nether mob at (1200, 0, 0) read level " + nether + " but an overworld mob at (9600, 0, 0) read " + overworld
		);
		helper.assertTrue(nether == 32, "the nether mob read level " + nether + " instead of 32");
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=netherScale RESULT netherBlocks=1200 scaledDistance={} netherLevel={} overworldLevel={}",
			netherScaled, nether, overworld
		);
		helper.succeed();
	}

	@GameTest
	public void distancePointsAtTenThousandBlocksAreTwoThousand(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		long points = PowerCurve.distancePoints(DISTANCE_CAP_BLOCKS, config);
		helper.assertTrue(points == 2000L, "10000 blocks gave " + points + " distance points instead of 2000");
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=distancePointsAtCap RESULT blocks={} points={} cap={}",
			DISTANCE_CAP_BLOCKS, points, config.distance().maxPoints()
		);
		helper.succeed();
	}

	@GameTest
	public void distancePointCapBindsBeyondTenThousandBlocks(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		long raw = (long) (BEYOND_DISTANCE_CAP_BLOCKS / config.distance().blocksPerPoint());
		long points = PowerCurve.distancePoints(BEYOND_DISTANCE_CAP_BLOCKS, config);
		helper.assertTrue(
			raw == 2400L,
			"the raw division at 12000 blocks was " + raw + ": the test cannot show the cap binding"
		);
		helper.assertTrue(points == 2000L, "12000 blocks gave " + points + " distance points instead of the 2000 cap");
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=distanceCapBinds RESULT blocks={} rawDivision={} cappedPoints={}",
			BEYOND_DISTANCE_CAP_BLOCKS, raw, points
		);
		helper.succeed();
	}

	@GameTest
	public void mobLevelNearSpawnAtSeaLevelIsZero(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		int level = mobLevelAt(100.0, 63.0, PowerFixtures.OVERWORLD, config);
		int firstStep = PowerCurve.mobLevel(300, 63, config);
		helper.assertTrue(level == 0, "the mob level at (100, 63, 100) was " + level + " instead of 0");
		helper.assertTrue(
			firstStep == 1,
			"the mob level at 300 scaled blocks and y 63 was " + firstStep + " instead of 1"
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=mobLevelNearSpawn RESULT x=100 y=63 mobLevel={} at300Blocks={}", level, firstStep
		);
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
		helper.assertTrue(
			lowest == 0,
			"the lowest mob level in the table was " + lowest + " instead of 0"
		);
		helper.assertTrue(
			highest == MOB_OVERALL_CAP,
			"the highest mob level in the table was " + highest + " instead of " + MOB_OVERALL_CAP
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=mobLevelLimits RESULT samples={} lowest={} highest={}",
			samples, lowest, highest
		);
		helper.succeed();
	}

	@GameTest
	public void weakPlayerAgainstAStrongMobReachesTheTopMultiplier(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		double multiplier = PowerCurve.xpMultiplier(MOB_OVERALL_CAP, 1, config);
		helper.assertTrue(
			multiplier == 4.0,
			"a level 1 player against a level 36 mob earned the multiplier " + multiplier + " instead of 4.0"
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=xpMultiplierTop RESULT mobLevel={} playerLevel=1 multiplier={}",
			MOB_OVERALL_CAP, multiplier
		);
		helper.succeed();
	}

	@GameTest
	public void strongPlayerAgainstAWeakMobReachesTheBottomMultiplier(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		double multiplier = PowerCurve.xpMultiplier(0, 50, config);
		helper.assertTrue(
			multiplier == 0.05,
			"a level 50 player against a level 0 mob earned the multiplier " + multiplier + " instead of 0.05"
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=xpMultiplierBottom RESULT mobLevel=0 playerLevel=50 multiplier={}", multiplier
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
		PowerCurve.Band atMinusNine = PowerCurve.band(-9, config);
		PowerCurve.Band atMinusTen = PowerCurve.band(-10, config);
		helper.assertTrue(atSix == PowerCurve.Band.ORANGE, "gap 6 read " + atSix + " instead of ORANGE");
		helper.assertTrue(atTen == PowerCurve.Band.YELLOW, "gap 10 read " + atTen + " instead of YELLOW");
		helper.assertTrue(atTwenty == PowerCurve.Band.RED, "gap 20 read " + atTwenty + " instead of RED");
		helper.assertTrue(atFive == PowerCurve.Band.GREEN, "gap 5 read " + atFive + " instead of GREEN");
		helper.assertTrue(atMinusFive == PowerCurve.Band.GREEN, "gap -5 read " + atMinusFive + " instead of GREEN");
		helper.assertTrue(
			atMinusSix == PowerCurve.Band.WHITE, "gap -6 read " + atMinusSix + " instead of WHITE"
		);
		helper.assertTrue(
			atMinusNine == PowerCurve.Band.WHITE, "gap -9 read " + atMinusNine + " instead of WHITE"
		);
		helper.assertTrue(atMinusTen == PowerCurve.Band.WHITE, "gap -10 read " + atMinusTen + " instead of WHITE");
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=bandColors RESULT gap6={} gap10={} gap20={} gap5={} gap-5={} gap-6={} gap-9={} gap-10={}",
			atSix, atTen, atTwenty, atFive, atMinusFive, atMinusSix, atMinusNine, atMinusTen
		);
		helper.succeed();
	}

	@GameTest
	public void theOutputBonusesRunFromZeroToTheirMaxima(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		double damageAtOne = PowerCurve.attackDamageBonus(1, config);
		double damageAtCap = PowerCurve.attackDamageBonus(config.levelCap(), config);
		double damageAboveCap = PowerCurve.attackDamageBonus(config.levelCap() + 10, config);
		double speedAtOne = PowerCurve.attackSpeedBonus(1, config);
		double speedAtCap = PowerCurve.attackSpeedBonus(config.levelCap(), config);
		helper.assertTrue(damageAtOne == 0.0, "a level 1 player earned " + damageAtOne + " attack damage");
		helper.assertTrue(speedAtOne == 0.0, "a level 1 player earned " + speedAtOne + " attack speed");
		helper.assertTrue(
			Math.abs(damageAtCap - PowerFixtures.ATTACK_DAMAGE_MAX) < TOLERANCE,
			"at level " + config.levelCap() + " the attack damage bonus was " + damageAtCap
		);
		helper.assertTrue(
			Math.abs(speedAtCap - PowerFixtures.ATTACK_SPEED_MAX) < TOLERANCE,
			"at level " + config.levelCap() + " the attack speed bonus was " + speedAtCap
		);
		helper.assertTrue(
			Math.abs(damageAboveCap - damageAtCap) < TOLERANCE,
			"level " + (config.levelCap() + 10) + " earned " + damageAboveCap + " attack damage past the cap"
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=outputBonuses RESULT damageAtLevel1={} damageAtCap={} damageAboveCap={} speedAtLevel1={} speedAtCap={}",
			damageAtOne, damageAtCap, damageAboveCap, speedAtOne, speedAtCap
		);
		helper.succeed();
	}

	@GameTest
	public void bandIsDefinedForEveryGapBetweenMinusFortyAndForty(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		java.util.EnumSet<PowerCurve.Band> seen = java.util.EnumSet.noneOf(PowerCurve.Band.class);
		int undefined = 0;
		int firstUndefined = 0;
		for (int gap = -40; gap <= 40; gap++) {
			PowerCurve.Band band = PowerCurve.band(gap, config);
			if (band == null) {
				undefined++;
				if (undefined == 1) {
					firstUndefined = gap;
				}
			} else {
				seen.add(band);
			}
		}
		helper.assertTrue(
			undefined == 0,
			undefined + " gaps had no band, the first at gap " + firstUndefined
		);
		helper.assertTrue(
			seen.size() == 5,
			"only " + seen.size() + " of the 5 bands appear between gap -40 and 40: " + seen
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=bandComplete RESULT gaps=81 undefined={} bandsSeen={} gapMinusSix={} gapMinusNine={}",
			undefined, seen.size(), PowerCurve.band(-6, config), PowerCurve.band(-9, config)
		);
		helper.succeed();
	}

	@GameTest
	public void everyGapBelowGreenIsWhite(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		int wrong = 0;
		int firstWrong = 0;
		PowerCurve.Band firstBand = PowerCurve.Band.WHITE;

		for (int gap = -5; gap >= -40; gap--) {
			PowerCurve.Band band = PowerCurve.band(gap, config);
			boolean whiteExpected = gap <= -6;

			if (whiteExpected && band != PowerCurve.Band.WHITE && wrong == 0) {
				wrong++;
				firstWrong = gap;
				firstBand = band;
			}
		}

		helper.assertTrue(
			wrong == 0,
			"gap " + firstWrong + " read " + firstBand + " instead of WHITE"
		);
		helper.assertTrue(
			PowerCurve.band(-5, config) == PowerCurve.Band.GREEN,
			"gap -5 read " + PowerCurve.band(-5, config) + " instead of GREEN"
		);
		helper.assertTrue(
			PowerCurve.band(-6, config) == PowerCurve.Band.WHITE,
			"gap -6 read " + PowerCurve.band(-6, config) + " instead of WHITE"
		);
		helper.assertTrue(
			PowerCurve.band(-9, config) == PowerCurve.Band.WHITE,
			"gap -9 read " + PowerCurve.band(-9, config) + " instead of WHITE"
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=bandBelowGreen RESULT gapMinusFive={} gapMinusSix={} gapMinusNine={} gapMinusForty={} wrong={}",
			PowerCurve.band(-5, config), PowerCurve.band(-6, config),
			PowerCurve.band(-9, config), PowerCurve.band(-40, config), wrong
		);
		helper.succeed();
	}

	private static int mobLevelAt(final double x, final double y, final net.minecraft.resources.Identifier dimension, final PowerConfig config) {
		double scaled = PowerCurve.scaledDistance(x, 0.0, 0.0, 0.0, dimension, config);
		return PowerCurve.mobLevel((int) Math.floor(scaled), (int) Math.floor(y), config);
	}
}
