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
		return steps * config.pointsPerLevel().base()
			+ config.pointsPerLevel().perLevel() * (steps * (steps + 1L) / 2L);
	}

	public static int levelFor(final long points, final PowerConfig config) {
		int level = 1;
		while (level < config.levelCap() && points >= pointsForLevel(level + 1, config)) {
			level++;
		}

		return level;
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

	public static long distancePoints(final double scaledDistance, final PowerConfig config) {
		if (scaledDistance <= 0.0) {
			return 0L;
		}

		long raw = (long) Math.floor(scaledDistance / config.distance().blocksPerPoint());
		return Math.min(raw, config.distance().maxPoints());
	}

	public static long depthPoints(final double y, final PowerConfig config) {
		double below = config.depth().minY() - y;
		if (below <= 0.0) {
			return 0L;
		}

		long raw = (long) Math.floor(below / config.depth().blocksPerPoint());
		return Math.min(raw, config.depth().maxPoints());
	}

	public static int mobLevel(final int scaledDistance, final int y, final PowerConfig config) {
		PowerConfig.MobLevel mob = config.mobLevel();
		double distanceSteps = Math.floor(scaledDistance / mob.blocksPerLevel());
		int fromDistance = (int) Math.max(0.0, Math.min(mob.cap(), distanceSteps));
		double depthSteps = Math.floor((mob.depthReferenceY() - y) / mob.depthBonusBlocks());
		int fromDepth = (int) Math.max(0.0, Math.min(mob.depthBonusCap(), depthSteps));
		return Math.min(mob.levelCap(), fromDistance + fromDepth);
	}

	public static double xpMultiplier(final int mobLevel, final int playerLevel, final PowerConfig config) {
		PowerConfig.XpMultiplier multiplier = config.xpMultiplier();
		double value = multiplier.evenFight() + multiplier.perLevelGap() * (mobLevel - playerLevel);
		return Math.max(multiplier.min(), Math.min(multiplier.max(), value));
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

		if (gap > bands.whiteBelow()) {
			return Band.ORANGE;
		}

		return Band.WHITE;
	}
}
