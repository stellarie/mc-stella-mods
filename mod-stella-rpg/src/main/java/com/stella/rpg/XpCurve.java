package com.stella.rpg;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The XP a player needs for the next level: {@code base + factor * level ^ exponent}, rounded. It replaces the
 * vanilla curve on both sides, so the client XP bar matches the server with the default config.
 */
public record XpCurve(double base, double factor, double exponent) {
	public static final String KEY = "xp_curve";
	public static final XpCurve DEFAULT = new XpCurve(10.0, 2.5, 1.5);

	public int need(final int level) {
		double value = this.base + this.factor * Math.pow(Math.max(0, level), this.exponent);
		return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, Math.round(value)));
	}

	public static int needFor(final int level) {
		return RpgConfigStore.current().xpCurve().need(level);
	}

	Map<String, Object> toJson() {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("base", this.base);
		out.put("factor", this.factor);
		out.put("exponent", this.exponent);
		return out;
	}
}
