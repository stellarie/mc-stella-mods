package com.stella.mobs.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The numbers behind the Wither boss fight, each read by a dotted key. A missing key falls back to its default here.
 * The file groups keys by the part before the dot.
 */
public record WitherConfig(Map<String, Double> values) {
	public static final String LEVEL_BASE = "level.base";
	public static final String LEVEL_OVER_PLAYER = "level.over_player";
	public static final String STATS_VIT = "stats.vit";
	public static final String STATS_MIN = "stats.min";
	public static final String STATS_MAX = "stats.max";
	public static final String MOVE_CEILING = "move.ceiling_above_ground";
	public static final String MOVE_STOP_DISTANCE = "move.stop_distance";
	public static final String MOVE_GROUND_SEARCH = "move.ground_search";

	private static final Map<String, Double> DEFAULTS_MAP = defaults();
	public static final WitherConfig DEFAULTS = new WitherConfig(defaults());

	public WitherConfig {
		values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
	}

	public double get(final String key) {
		Double value = this.values.get(key);
		return value != null ? value : DEFAULTS_MAP.get(key);
	}

	public int getInt(final String key) {
		return (int) Math.round(get(key));
	}

	private static Map<String, Double> defaults() {
		Map<String, Double> map = new LinkedHashMap<>();
		map.put(LEVEL_BASE, 100.0);
		map.put(LEVEL_OVER_PLAYER, 20.0);
		map.put(STATS_VIT, 200.0);
		map.put(STATS_MIN, 50.0);
		map.put(STATS_MAX, 150.0);
		map.put(MOVE_CEILING, 3.0);
		map.put(MOVE_STOP_DISTANCE, 9.0);
		map.put(MOVE_GROUND_SEARCH, 64.0);
		return map;
	}
}
