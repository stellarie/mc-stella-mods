package com.stella.mobs.config;

/** The numbers behind a mob's level, health, damage, armor piercing, and kill experience. */
public record ScalingConfig(
	int floorRadius,
	int floorOffset,
	double depthBlocksPerLevel,
	double heightBlocksPerLevel,
	double healthPerLevel,
	double damageGrowth,
	int tailStart,
	double tailMax,
	double tailMidpoint,
	double tailWidth,
	double tailEnd,
	double tailFlatPerLevel,
	double piercePerLevel,
	double pierceCap,
	double xpLevelDivisor
) {
	public static final ScalingConfig DEFAULTS = new ScalingConfig(
		128, 2, 6.4, 80.0, 0.20, 1.06, 80, 19.0, 500.0, 150.0, 1000.0, 0.02, 0.015, 0.5, 10.0
	);
}
