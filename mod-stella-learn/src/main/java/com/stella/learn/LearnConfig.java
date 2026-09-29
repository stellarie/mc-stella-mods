package com.stella.learn;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** The tunable numbers, each a starting value. A bad entry logs and keeps its default. */
public record LearnConfig(
	double calloutRadius, int maxResponders, int calloutCooldownTicks, long profileHalfLifeTicks, double rushBandGain, double rushSaturation
) {
	public static final double DEFAULT_CALLOUT_RADIUS = 24.0;
	public static final int DEFAULT_MAX_RESPONDERS = 4;
	public static final int DEFAULT_CALLOUT_COOLDOWN_TICKS = 100;
	public static final long DEFAULT_PROFILE_HALF_LIFE_TICKS = 6000L;
	/** Blocks an archer's near band grows by against a player whose rush count has reached the saturation. */
	public static final double DEFAULT_RUSH_BAND_GAIN = 6.0;
	/** Melee hits, decayed, at which a player counts as a full rusher. */
	public static final double DEFAULT_RUSH_SATURATION = 8.0;
	static final String CALLOUT_RADIUS = "callout_radius";
	static final String MAX_RESPONDERS = "max_responders";
	static final String CALLOUT_COOLDOWN_TICKS = "callout_cooldown_ticks";
	static final String PROFILE_HALF_LIFE_TICKS = "profile_half_life_ticks";
	static final String RUSH_BAND_GAIN = "rush_band_gain";
	static final String RUSH_SATURATION = "rush_saturation";

	public static LearnConfig defaults() {
		return new LearnConfig(
			DEFAULT_CALLOUT_RADIUS, DEFAULT_MAX_RESPONDERS, DEFAULT_CALLOUT_COOLDOWN_TICKS, DEFAULT_PROFILE_HALF_LIFE_TICKS,
			DEFAULT_RUSH_BAND_GAIN, DEFAULT_RUSH_SATURATION
		);
	}

	/** An exact entry wins over its default; a missing or bad entry keeps the default and a broken file gives them all. */
	public static LearnConfig parse(final String json) {
		JsonObject root;
		try {
			JsonElement parsed = JsonParser.parseString(json);
			if (!parsed.isJsonObject()) {
				StellaLearn.LOGGER.warn("Learning config is not an object; using the defaults");
				return defaults();
			}
			root = parsed.getAsJsonObject();
		} catch (RuntimeException exception) {
			StellaLearn.LOGGER.warn("Learning config is not valid JSON; using the defaults: {}", exception.getMessage());
			return defaults();
		}

		return new LearnConfig(
			number(root, CALLOUT_RADIUS, DEFAULT_CALLOUT_RADIUS),
			(int) number(root, MAX_RESPONDERS, DEFAULT_MAX_RESPONDERS),
			(int) number(root, CALLOUT_COOLDOWN_TICKS, DEFAULT_CALLOUT_COOLDOWN_TICKS),
			(long) number(root, PROFILE_HALF_LIFE_TICKS, DEFAULT_PROFILE_HALF_LIFE_TICKS),
			number(root, RUSH_BAND_GAIN, DEFAULT_RUSH_BAND_GAIN),
			number(root, RUSH_SATURATION, DEFAULT_RUSH_SATURATION)
		);
	}

	private static double number(final JsonObject root, final String key, final double fallback) {
		JsonElement element = root.get(key);
		if (element == null) {
			return fallback;
		}

		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber() || element.getAsDouble() <= 0.0) {
			StellaLearn.LOGGER.warn("Learning config entry {} must be a positive number; keeping {}", key, fallback);
			return fallback;
		}

		return element.getAsDouble();
	}
}
