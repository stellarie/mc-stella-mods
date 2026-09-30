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
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Reads {@code config/mod-stella-mobs/scaling.json}. A missing file is written with the defaults. A missing key
 * falls back to its default, and an unknown key is reported once and ignored.
 */
public final class ScalingConfigStore {
	private static final String FILE_NAME = "scaling.json";
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
		for (String key : root.keySet()) {
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
			known.get("xp_level_divisor")
		);
	}

	static JsonObject toJson(final ScalingConfig value) {
		JsonObject out = new JsonObject();
		values(value).forEach(out::addProperty);
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
		map.put("xp_level_divisor", value.xpLevelDivisor());
		return map;
	}
}
