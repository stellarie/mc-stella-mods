package com.stella.mobs.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stella.mobs.StellaMobs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Reads {@code config/mod-stella-mobs/scaling.json}. A missing file is written with the defaults. A missing key
 * falls back to its default, and an unknown key is reported once and ignored.
 */
public final class ScalingConfigStore {
	private static final String FILE_NAME = "scaling.json";
	private static final String XP_BASE_KEY = "xp_base";
	private static final Set<String> RETIRED_KEYS = Set.of("xp_level_divisor");
	private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();
	private static volatile ScalingConfig config = ScalingConfig.DEFAULTS;

	private ScalingConfigStore() {
	}

	public static ScalingConfig get() {
		return config;
	}

	public static void set(final ScalingConfig value) {
		config = value == null ? ScalingConfig.DEFAULTS : value;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(StellaMobs.MOD_ID).resolve(FILE_NAME);
	}

	public static ScalingConfig load() {
		return loadFrom(path(), message -> StellaMobs.LOGGER.warn("{}", message));
	}

	public static ScalingConfig loadFrom(final Path file, final Consumer<String> onError) {
		try {
			if (Files.notExists(file)) {
				Files.createDirectories(file.getParent());
				Files.writeString(file, PRETTY.toJson(toJson(ScalingConfig.DEFAULTS)) + System.lineSeparator(), StandardCharsets.UTF_8);
			}
			config = parse(Files.readString(file, StandardCharsets.UTF_8), onError);
		} catch (IOException | RuntimeException e) {
			onError.accept("Cannot read " + file + ": " + e.getMessage() + "; using the defaults");
			config = ScalingConfig.DEFAULTS;
		}
		return config;
	}

	public static ScalingConfig parse(final String json, final Consumer<String> onError) {
		JsonElement parsed = JsonParser.parseString(json);
		if (!parsed.isJsonObject()) {
			onError.accept("scaling.json must be an object; using the defaults");
			return ScalingConfig.DEFAULTS;
		}
		JsonObject root = parsed.getAsJsonObject();
		Map<String, Double> known = values(ScalingConfig.DEFAULTS);
		Map<String, Integer> xpBase = new LinkedHashMap<>(ScalingConfig.DEFAULTS.xpBase());
		for (String key : root.keySet()) {
			if (RETIRED_KEYS.contains(key)) {
				onError.accept("scaling.json key '" + key + "' is retired and ignored");
				continue;
			}
			if (XP_BASE_KEY.equals(key)) {
				parseXpBase(root.get(key), xpBase, onError);
				continue;
			}
			if (!known.containsKey(key)) {
				onError.accept("scaling.json key '" + key + "' is unknown and ignored");
				continue;
			}
			JsonElement value = root.get(key);
			if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
				known.put(key, value.getAsDouble());
			} else {
				onError.accept("scaling.json key '" + key + "' must be a number; using the default");
			}
		}
		return new ScalingConfig(
			(int) Math.round(known.get("floor_radius")),
			(int) Math.round(known.get("floor_offset")),
			known.get("depth_blocks_per_level"),
			known.get("height_blocks_per_level"),
			known.get("health_per_level"),
			known.get("damage_growth"),
			(int) Math.round(known.get("tail_start")),
			known.get("tail_max"),
			known.get("tail_midpoint"),
			known.get("tail_width"),
			known.get("tail_end"),
			known.get("tail_flat_per_level"),
			known.get("pierce_per_level"),
			known.get("pierce_cap"),
			xpBase,
			(int) Math.round(known.get("xp_base_hostile")),
			(int) Math.round(known.get("xp_base_passive")),
			known.get("xp_level_factor"),
			known.get("xp_gap_step"),
			known.get("xp_gap_min"),
			known.get("xp_gap_max"),
			known.get("xp_elite_multiplier"),
			known.get("xp_nemesis_multiplier"),
			known.get("health_tier_factor"),
			Math.max(1, (int) Math.round(known.get("health_tier_step")))
		);
	}

	/** An entry overrides the default for that entity. A bare name means the minecraft namespace. */
	private static void parseXpBase(final JsonElement value, final Map<String, Integer> xpBase, final Consumer<String> onError) {
		if (!value.isJsonObject()) {
			onError.accept("scaling.json key '" + XP_BASE_KEY + "' must be an object; using the defaults");
			return;
		}
		JsonObject entries = value.getAsJsonObject();
		for (String id : entries.keySet()) {
			JsonElement entry = entries.get(id);
			if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isNumber()) {
				String key = id.contains(":") ? id : "minecraft:" + id;
				xpBase.put(key, Math.max(0, (int) Math.round(entry.getAsDouble())));
			} else {
				onError.accept("scaling.json " + XP_BASE_KEY + " entry '" + id + "' must be a number; using the default");
			}
		}
	}

	static JsonObject toJson(final ScalingConfig value) {
		JsonObject out = new JsonObject();
		values(value).forEach(out::addProperty);
		JsonObject xpBase = new JsonObject();
		new TreeMap<>(value.xpBase()).forEach(xpBase::addProperty);
		out.add(XP_BASE_KEY, xpBase);
		return out;
	}

	private static Map<String, Double> values(final ScalingConfig value) {
		Map<String, Double> map = new LinkedHashMap<>();
		map.put("floor_radius", (double) value.floorRadius());
		map.put("floor_offset", (double) value.floorOffset());
		map.put("depth_blocks_per_level", value.depthBlocksPerLevel());
		map.put("height_blocks_per_level", value.heightBlocksPerLevel());
		map.put("health_per_level", value.healthPerLevel());
		map.put("damage_growth", value.damageGrowth());
		map.put("tail_start", (double) value.tailStart());
		map.put("tail_max", value.tailMax());
		map.put("tail_midpoint", value.tailMidpoint());
		map.put("tail_width", value.tailWidth());
		map.put("tail_end", value.tailEnd());
		map.put("tail_flat_per_level", value.tailFlatPerLevel());
		map.put("pierce_per_level", value.piercePerLevel());
		map.put("pierce_cap", value.pierceCap());
		map.put("xp_base_hostile", (double) value.xpBaseHostile());
		map.put("xp_base_passive", (double) value.xpBasePassive());
		map.put("xp_level_factor", value.xpLevelFactor());
		map.put("xp_gap_step", value.xpGapStep());
		map.put("xp_gap_min", value.xpGapMin());
		map.put("xp_gap_max", value.xpGapMax());
		map.put("xp_elite_multiplier", value.xpEliteMultiplier());
		map.put("xp_nemesis_multiplier", value.xpNemesisMultiplier());
		map.put("health_tier_factor", value.healthTierFactor());
		map.put("health_tier_step", (double) value.healthTierStep());
		return map;
	}
}
