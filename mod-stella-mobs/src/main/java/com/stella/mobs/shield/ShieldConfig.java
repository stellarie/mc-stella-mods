package com.stella.mobs.shield;

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
import net.fabricmc.loader.api.FabricLoader;

/**
 * Reads {@code config/mod-stella-mobs/shield_generator.json}. A missing file is written with the default. A bad
 * value is reported and the default stays.
 */
public final class ShieldConfig {
	/** One kill in ten thousand: 0.01 percent. */
	public static final double DEFAULT_DROP_CHANCE = 0.0001;
	private static final String FILE_NAME = "shield_generator.json";
	private static final String DROP_CHANCE = "drop_chance";
	private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();
	private static volatile double dropChance = DEFAULT_DROP_CHANCE;

	private ShieldConfig() {
	}

	public static double dropChance() {
		return dropChance;
	}

	public static void setDropChance(final double chance) {
		dropChance = chance;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(StellaMobs.MOD_ID).resolve(FILE_NAME);
	}

	public static void load() {
		Path file = path();
		try {
			if (Files.notExists(file)) {
				Files.createDirectories(file.getParent());
				JsonObject defaults = new JsonObject();
				defaults.addProperty(DROP_CHANCE, DEFAULT_DROP_CHANCE);
				Files.writeString(file, PRETTY.toJson(defaults) + System.lineSeparator(), StandardCharsets.UTF_8);
			}
			dropChance = parse(Files.readString(file, StandardCharsets.UTF_8));
		} catch (IOException | RuntimeException e) {
			StellaMobs.LOGGER.warn("Cannot read {}: {}; using the default drop chance", file, e.getMessage());
			dropChance = DEFAULT_DROP_CHANCE;
		}
	}

	static double parse(final String json) {
		JsonElement parsed = JsonParser.parseString(json);
		if (parsed.isJsonObject()) {
			JsonElement value = parsed.getAsJsonObject().get(DROP_CHANCE);
			if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
				double chance = value.getAsDouble();
				if (chance >= 0.0 && chance <= 1.0) {
					return chance;
				}
			}
		}
		StellaMobs.LOGGER.warn("shield_generator.json needs a '{}' number from 0 to 1; using the default", DROP_CHANCE);
		return DEFAULT_DROP_CHANCE;
	}
}
