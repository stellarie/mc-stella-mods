package com.stella.equips.power;


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
