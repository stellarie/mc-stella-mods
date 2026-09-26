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

	public static double levelFraction(final int level, final PowerConfig config) {
		int span = config.levelCap() - 1;
		if (span <= 0) {
			return 0.0;
		}

		double climbed = Math.max(0, Math.min(config.levelCap(), level) - 1);
		return climbed / span;
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

	public static int threat(final int scaledDistance, final int y, final PowerConfig config) {
		PowerConfig.MobLevel mob = config.mobLevel();
		double distanceSteps = Math.floor(scaledDistance / mob.blocksPerLevel());
		int fromDistance = (int) Math.max(0.0, Math.min(mob.cap(), distanceSteps));
		double depthSteps = Math.floor((mob.depthReferenceY() - y) / mob.depthBonusBlocks());
		int fromDepth = (int) Math.max(0.0, Math.min(mob.depthBonusCap(), depthSteps));
		return Math.min(mob.levelCap(), fromDistance + fromDepth);
	}

	/** Raw threat scaled from 0..threat cap onto 0..player level cap, for the band comparison only. */
	public static int normalizedThreat(final int rawThreat, final PowerConfig config) {
		int threatCap = config.mobLevel().levelCap();
		if (threatCap <= 0) {
			return 0;
		}

		int clamped = Math.max(0, Math.min(threatCap, rawThreat));
		return (int) Math.round(clamped * (double) config.levelCap() / threatCap);
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
