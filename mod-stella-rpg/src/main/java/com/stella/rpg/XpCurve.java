package com.stella.rpg;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The XP a player needs for the next level. Up to {@link #VANILLA_CAP} it is the vanilla value. Above it, the
 * value grows by {@code growth} per level, compounding, and multiplies by {@code step} at each milestone level.
 * It replaces the vanilla curve on both sides, so the client XP bar matches the server with the default config.
 */
public record XpCurve(double growth, double step) {
	public static final String KEY = "xp_curve";
	public static final XpCurve DEFAULT = new XpCurve(0.07, 2.0);
	public static final int VANILLA_CAP = 30;
	public static final int[] MILESTONES = {50, 100, 150};

	public static int vanilla(final int level) {
		if (level >= 31) {
			return 9 * level - 158;
		}
		return level >= 16 ? 5 * level - 38 : 2 * level + 7;
	}

	public int need(final int level) {
		int clamped = Math.max(0, level);
		if (clamped <= VANILLA_CAP) {
			return vanilla(clamped);
		}
		double value = vanilla(VANILLA_CAP) * Math.pow(1.0 + this.growth, clamped - VANILLA_CAP);
		for (int milestone : MILESTONES) {
			if (clamped >= milestone) {
				value *= this.step;
			}
		}
		return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, Math.round(value)));
	}

	public static int needFor(final int level) {
		return RpgConfigStore.current().xpCurve().need(level);
	}

	Map<String, Object> toJson() {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("growth", this.growth);
		out.put("step", this.step);
		return out;
	}
}
