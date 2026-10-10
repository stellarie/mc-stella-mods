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
	public static final String AIM_INTERVAL_MAX = "aim.interval_max";
	public static final String AIM_INTERVAL_MIN = "aim.interval_min";
	public static final String AIM_SHARE = "aim.share";
	public static final String AIM_WITHER_TICKS = "aim.wither_ticks";
	public static final String AIM_WITHER_AMP = "aim.wither_amp";
	public static final String BLAST_RADIUS = "blast.radius";
	public static final String BLAST_RIDER_CHANCE = "blast.rider_chance";
	public static final String BLAST_CHILL_TICKS = "blast.chill_ticks";
	public static final String BLAST_BURN_SECONDS = "blast.burn_seconds";
	public static final String SKULL_LIVE_CAP = "skull.live_cap";
	public static final String SKULL_LIFE_TICKS = "skull.life_ticks";
	public static final String MINIONS_WEIGHT_SKELETON = "minions.weight_skeleton";
	public static final String MINIONS_WEIGHT_ZOMBIE = "minions.weight_zombie";
	public static final String MINIONS_WEIGHT_HUSK = "minions.weight_husk";
	public static final String MINIONS_WEIGHT_STRAY = "minions.weight_stray";
	public static final String MINIONS_WEIGHT_WITHER_SKELETON = "minions.weight_wither_skeleton";
	public static final String MINIONS_CAP_BASE = "minions.cap_base";
	public static final String MINIONS_CAP_PER_PLAYER = "minions.cap_per_player";
	public static final String MINIONS_CAP_MAX = "minions.cap_max";
	public static final String MINIONS_CAP_RADIUS = "minions.cap_radius";
	public static final String MINIONS_FIRST_WAVE = "minions.first_wave";
	public static final String MINIONS_WAVE_INTERVAL = "minions.wave_interval";
	public static final String MINIONS_WAVE_SIZE = "minions.wave_size";
	public static final String MINIONS_SPAWN_MIN = "minions.spawn_min";
	public static final String MINIONS_SPAWN_MAX = "minions.spawn_max";
	public static final String MINIONS_PLAYER_CLEARANCE = "minions.player_clearance";
	public static final String MINIONS_RAGE_PAUSE = "minions.rage_pause";
	public static final String STARS_BASE = "stars.base";
	public static final String STARS_PER_LEVEL = "stars.per_level";
	public static final String STARS_MAX = "stars.max_extra";
	public static final String BOOKS_COUNT = "books.count";
	public static final String BOOKS_THREE_CHANCE = "books.three_chance";
	public static final String RAGE_TICKS = "rage.ticks";
	public static final String RAGE_A_THRESHOLD = "rage_a.threshold";
	public static final String RAGE_A_RING_SIZE = "rage_a.ring_size";
	public static final String RAGE_A_RING_INTERVAL = "rage_a.ring_interval";
	public static final String RAGE_A_SINGLE_INTERVAL = "rage_a.single_interval";
	public static final String RAGE_A_RANGE = "rage_a.range";
	public static final String RAGE_A_SHARE = "rage_a.share";
	public static final String RAGE_A_WITHER_TICKS = "rage_a.wither_ticks";
	public static final String RAGE_A_WITHER_AMP = "rage_a.wither_amp";
	public static final String RAGE_B_THRESHOLD = "rage_b.threshold";
	public static final String RAGE_B_INTERVAL = "rage_b.interval";
	public static final String RAGE_B_RADIUS = "rage_b.radius";
	public static final String RAGE_B_HEIGHT = "rage_b.height";
	public static final String RAGE_B_SHARE = "rage_b.share";
	public static final String RAGE_B_WITHER_TICKS = "rage_b.wither_ticks";
	public static final String RAGE_B_WITHER_AMP = "rage_b.wither_amp";
	public static final String RAGE_B_PLAYER_BIAS = "rage_b.player_bias";
	public static final String RAGE_B_NEAR_PLAYER = "rage_b.near_player";
	public static final String RAGE_B_LIFE_TICKS = "rage_b.life_ticks";

	private static final Map<String, Double> DEFAULTS_MAP = defaults();
	public static final WitherConfig DEFAULTS = new WitherConfig(DEFAULTS_MAP);

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

	/** A copy with one value changed, for a test that needs a different number. */
	public WitherConfig with(final String key, final double value) {
		Map<String, Double> copy = new LinkedHashMap<>(this.values);
		copy.put(key, value);
		return new WitherConfig(copy);
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
		map.put(AIM_INTERVAL_MAX, 40.0);
		map.put(AIM_INTERVAL_MIN, 12.0);
		map.put(AIM_SHARE, 0.60);
		map.put(AIM_WITHER_TICKS, 100.0);
		map.put(AIM_WITHER_AMP, 1.0);
		map.put(BLAST_RADIUS, 2.0);
		map.put(BLAST_RIDER_CHANCE, 0.40);
		map.put(BLAST_CHILL_TICKS, 100.0);
		map.put(BLAST_BURN_SECONDS, 4.0);
		map.put(SKULL_LIVE_CAP, 40.0);
		map.put(SKULL_LIFE_TICKS, 100.0);
		map.put(MINIONS_WEIGHT_SKELETON, 25.0);
		map.put(MINIONS_WEIGHT_ZOMBIE, 25.0);
		map.put(MINIONS_WEIGHT_HUSK, 15.0);
		map.put(MINIONS_WEIGHT_STRAY, 15.0);
		map.put(MINIONS_WEIGHT_WITHER_SKELETON, 20.0);
		map.put(MINIONS_CAP_BASE, 6.0);
		map.put(MINIONS_CAP_PER_PLAYER, 3.0);
		map.put(MINIONS_CAP_MAX, 18.0);
		map.put(MINIONS_CAP_RADIUS, 48.0);
		map.put(MINIONS_FIRST_WAVE, 3.0);
		map.put(MINIONS_WAVE_INTERVAL, 160.0);
		map.put(MINIONS_WAVE_SIZE, 2.0);
		map.put(MINIONS_SPAWN_MIN, 10.0);
		map.put(MINIONS_SPAWN_MAX, 32.0);
		map.put(MINIONS_PLAYER_CLEARANCE, 6.0);
		map.put(MINIONS_RAGE_PAUSE, 100.0);
		map.put(STARS_BASE, 0.25);
		map.put(STARS_PER_LEVEL, 0.0125);
		map.put(STARS_MAX, 7.0);
		map.put(BOOKS_COUNT, 5.0);
		map.put(BOOKS_THREE_CHANCE, 0.5);
		map.put(RAGE_TICKS, 600.0);
		map.put(RAGE_A_THRESHOLD, 0.50);
		map.put(RAGE_A_RING_SIZE, 6.0);
		map.put(RAGE_A_RING_INTERVAL, 24.0);
		map.put(RAGE_A_SINGLE_INTERVAL, 6.0);
		map.put(RAGE_A_RANGE, 16.0);
		map.put(RAGE_A_SHARE, 0.12);
		map.put(RAGE_A_WITHER_TICKS, 60.0);
		map.put(RAGE_A_WITHER_AMP, 0.0);
		map.put(RAGE_B_THRESHOLD, 0.25);
		map.put(RAGE_B_INTERVAL, 3.0);
		map.put(RAGE_B_RADIUS, 24.0);
		map.put(RAGE_B_HEIGHT, 14.0);
		map.put(RAGE_B_SHARE, 0.20);
		map.put(RAGE_B_WITHER_TICKS, 60.0);
		map.put(RAGE_B_WITHER_AMP, 0.0);
		map.put(RAGE_B_PLAYER_BIAS, 0.5);
		map.put(RAGE_B_NEAR_PLAYER, 6.0);
		map.put(RAGE_B_LIFE_TICKS, 60.0);
		return map;
	}
}
