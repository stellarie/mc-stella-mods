package com.stella.equips.power;

import net.minecraft.resources.Identifier;

public final class PowerCurve {
	public enum Band {
		RED,
		YELLOW,
		ORANGE,
		GREEN,
		WHITE
	}

	private PowerCurve() {
	}

	public static long pointsForLevel(final int level, final PowerConfig config) {
		if (level <= 1) {
			return 0L;
		}

		long steps = Math.min(level, config.levelCap()) - 1L;
		double base = config.curve().base();
		double growth = config.curve().growth();
		if (growth == 1.0) {
			return (long) Math.floor(base * steps);
		}

		return (long) Math.floor(base * (Math.pow(growth, steps) - 1.0) / (growth - 1.0));
	}

	public static int levelFor(final long points, final PowerConfig config) {
		int level = 1;
		while (level < config.levelCap() && points >= pointsForLevel(level + 1, config)) {
			level++;
		}

		return level;
	}

	public static double levelFraction(final int level, final PowerConfig config) {
		int span = config.levelCap() - 1;
		if (span <= 0) {
			return 0.0;
		}

		double climbed = Math.max(0, Math.min(config.levelCap(), level) - 1);
		return climbed / span;
	}

	public static int awardFor(final int mobLevel, final PowerConfig config) {
		double award = Math.ceil(mobLevel * config.award().divisor());
		return (int) Math.max(config.award().minimum(), award);
	}

	public static double scaledDistance(
		final double x,
		final double z,
		final double spawnX,
		final double spawnZ,
		final Identifier dimension,
		final PowerConfig config
	) {
		return Math.hypot(x - spawnX, z - spawnZ) * config.mobLevel().scaleFor(dimension);
	}

	public static int mobLevel(final int scaledDistance, final int y, final PowerConfig config) {
		PowerConfig.MobLevel mob = config.mobLevel();
		double distanceSteps = Math.floor(scaledDistance / mob.blocksPerLevel());
		int fromDistance = (int) Math.max(0.0, Math.min(mob.cap(), distanceSteps));
		double depthSteps = Math.floor((mob.depthReferenceY() - y) / mob.depthBonusBlocks());
		int fromDepth = (int) Math.max(0.0, Math.min(mob.depthBonusCap(), depthSteps));
		return Math.min(mob.levelCap(), fromDistance + fromDepth);
	}

	public static Band band(final int gap, final PowerConfig config) {
		PowerConfig.Bands bands = config.bands();
		if (gap >= bands.redAbove()) {
			return Band.RED;
		}

		if (gap >= bands.yellowAbove()) {
			return Band.YELLOW;
		}

		if (gap >= bands.orangeAbove()) {
			return Band.ORANGE;
		}

		if (gap >= -bands.greenWithin()) {
			return Band.GREEN;
		}

		return Band.WHITE;
	}
}
