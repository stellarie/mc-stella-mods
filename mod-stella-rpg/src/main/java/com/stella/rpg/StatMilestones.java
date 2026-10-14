package com.stella.rpg;

/**
 * Every {@link #STEP} points in a stat add {@link #BONUS_PER_STEP} to the benefits that stat already gives. The
 * factor multiplies the benefit, so it never adds a new kind of effect.
 */
public final class StatMilestones {
	public static final int STEP = 3;
	public static final double BONUS_PER_STEP = 0.02;

	private static volatile double bonusPerStep = BONUS_PER_STEP;

	private StatMilestones() {
	}

	/** A test sets the bonus so suites that pin exact stat numbers can run without milestones. Null restores 2%. */
	public static void setBonusPerStep(final Double bonus) {
		bonusPerStep = bonus == null ? BONUS_PER_STEP : bonus;
	}

	/** How many milestones a stat value has reached. */
	public static int reached(final int stat) {
		return Math.max(0, stat) / STEP;
	}

	/** The multiplier on a stat's benefits: 1.0 below the first milestone. */
	public static double factor(final int stat) {
		return 1.0 + bonusPerStep * reached(stat);
	}
}
