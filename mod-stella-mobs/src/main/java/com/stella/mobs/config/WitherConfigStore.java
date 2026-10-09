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
 * Reads {@code config/mod-stella-mobs/wither.json}. A missing file is written with the defaults. A missing key falls
 * back to its default, an unknown key is reported and ignored, and a bad value is reported and skipped.
 */
public final class WitherConfigStore {
	private static final String FILE_NAME = "wither.json";
	private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();
	private static volatile WitherConfig config = WitherConfig.DEFAULTS;

	private WitherConfigStore() {
	}

	public static WitherConfig get() {
		return config;
	}

	public static void set(final WitherConfig value) {
		config = value == null ? WitherConfig.DEFAULTS : value;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(StellaMobs.MOD_ID).resolve(FILE_NAME);
	}

	public static WitherConfig load() {
		return loadFrom(path(), message -> StellaMobs.LOGGER.warn("{}", message));
	}

	public static WitherConfig loadFrom(final Path file, final Consumer<String> onError) {
		try {
			if (Files.notExists(file)) {
				Files.createDirectories(file.getParent());
				Files.writeString(file, PRETTY.toJson(toJson(WitherConfig.DEFAULTS)) + System.lineSeparator(), StandardCharsets.UTF_8);
			}
			config = parse(Files.readString(file, StandardCharsets.UTF_8), onError);
		} catch (IOException | RuntimeException e) {
			onError.accept("Cannot read " + file + ": " + e.getMessage() + "; using the defaults");
			config = WitherConfig.DEFAULTS;
		}
		return config;
	}

	public static WitherConfig parse(final String json, final Consumer<String> onError) {
		JsonElement parsed = JsonParser.parseString(json);
		if (!parsed.isJsonObject()) {
			onError.accept("wither.json must be an object; using the defaults");
			return WitherConfig.DEFAULTS;
		}
		Map<String, Double> values = new LinkedHashMap<>(WitherConfig.DEFAULTS.values());
		JsonObject root = parsed.getAsJsonObject();
		for (String section : root.keySet()) {
			JsonElement group = root.get(section);
			if (!group.isJsonObject()) {
				onError.accept("wither.json section '" + section + "' must be an object; ignored");
				continue;
			}
			JsonObject entries = group.getAsJsonObject();
			for (String name : entries.keySet()) {
				String key = section + "." + name;
				JsonElement value = entries.get(name);
				if (!values.containsKey(key)) {
					onError.accept("wither.json key '" + key + "' is unknown and ignored");
				} else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
					values.put(key, value.getAsDouble());
				} else {
					onError.accept("wither.json key '" + key + "' must be a number; using the default");
				}
			}
		}
		return new WitherConfig(values);
	}

	static JsonObject toJson(final WitherConfig value) {
		JsonObject out = new JsonObject();
		value.values().forEach((key, number) -> {
			int dot = key.indexOf('.');
			String section = key.substring(0, dot);
			if (!out.has(section)) {
				out.add(section, new JsonObject());
			}
			out.getAsJsonObject(section).addProperty(key.substring(dot + 1), number);
		});
		return out;
	}
}
