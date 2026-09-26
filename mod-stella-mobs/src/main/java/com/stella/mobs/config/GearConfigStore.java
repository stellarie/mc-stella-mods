package com.stella.mobs.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.stella.mobs.StellaMobs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.StrictJsonParser;

public final class GearConfigStore {
	private static final String FILE_NAME = "loadouts.json";
	private static final String ROLLS = "rolls";
	/** B2: the one member of the shipped rolls object that is a table, not a scalar a file should gain. */
	private static final String ROLL_ENTITIES = "entities";
	private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();
	private static final String DEFAULT_JSON = """
		{
			"version": 1,
			"entities": {},
			"roles": {
				"minecraft:evoker": "magic"
			},
			"rolls": {
				"roll_chance": 0.30,
				"drop_chance_multiplier": 1.0,
				"elite_chance": 0.05,
				"boss_chance": 0.01,
				"war_call_brute_chance": 0.01,
				"spawn_safety_radius": 1000.0,
				"entities": {
					"minecraft:witch": { "elite_chance": 0.10 },
					"minecraft:snow_golem": { "elite_chance": 0.10 }
				}
			}
		}""";
	private static volatile GearConfig config = GearConfig.EMPTY;

	private GearConfigStore() {
	}

	public static GearConfig get() {
		return config;
	}

	public static void set(final GearConfig value) {
		config = value;
	}

	/** The config this mod ships, as a fresh install would read it. */
	public static GearConfig defaults() {
		return GearConfigParser.parse(DEFAULT_JSON);
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(StellaMobs.MOD_ID).resolve(FILE_NAME);
	}

	public static GearConfig load() {
		return loadFrom(path());
	}

	public static GearConfig loadFrom(final Path file) {
		try {
			if (Files.notExists(file)) {
				writeDefault(file);
			}

			config = GearConfigParser.parse(migrate(Files.readString(file, StandardCharsets.UTF_8), file));
		} catch (IOException e) {
			StellaMobs.LOGGER.warn("Could not read the gear config at {}: {}", file, e.getMessage());
			config = GearConfig.EMPTY;
		}

		return config;
	}

	/**
	 * B2: a file written before a roll key existed reads the code default for it, which keeps the boss
	 * roll off, so such an install can never meet a boss. Add the keys the file omits, from the shipped
	 * default, and write the file back so the user can see and tune them. A key the file already sets is
	 * never rewritten and no key is ever dropped.
	 */
	private static String migrate(final String json, final Path file) {
		JsonObject root;
		JsonObject shipped;
		try {
			JsonElement parsed = StrictJsonParser.parse(json);
			if (!parsed.isJsonObject()) {
				return json;
			}

			root = parsed.getAsJsonObject();
			shipped = shippedRolls();
		} catch (JsonSyntaxException | IllegalStateException e) {
			// A broken file is the parser's to report, and is never rewritten.
			return json;
		}

		JsonObject rolls;
		JsonElement existing = root.get(ROLLS);
		if (existing == null) {
			rolls = new JsonObject();
			root.add(ROLLS, rolls);
		} else if (existing.isJsonObject()) {
			rolls = existing.getAsJsonObject();
		} else {
			return json;
		}

		List<String> filled = new ArrayList<>();

		for (Map.Entry<String, JsonElement> entry : shipped.entrySet()) {
			// The per-type override table is a targeted choice for one mob, not a scalar the shipped
			// file carries for every mob, so a file without it stays without it.
			if (ROLL_ENTITIES.equals(entry.getKey()) || rolls.has(entry.getKey())) {
				continue;
			}

			rolls.add(entry.getKey(), entry.getValue());
			filled.add(entry.getKey());
		}

		if (filled.isEmpty()) {
			return json;
		}

		String merged = PRETTY.toJson(root);
		StellaMobs.LOGGER.info(
			"Gear config at {} was missing the roll key(s) {}, filled from the shipped default and written back",
			file,
			String.join(", ", filled)
		);

		try {
			Files.writeString(file, merged, StandardCharsets.UTF_8);
		} catch (IOException e) {
			// The fill still takes effect in memory for this session; only the file could not be updated.
			StellaMobs.LOGGER.warn("Could not write the migrated gear config at {}: {}", file, e.getMessage());
		}

		return merged;
	}

	/** The shipped rolls object, parsed from the same string the store writes for a fresh install. */
	private static JsonObject shippedRolls() {
		return StrictJsonParser.parse(DEFAULT_JSON).getAsJsonObject().getAsJsonObject(ROLLS);
	}

	private static void writeDefault(final Path file) throws IOException {
		Path parent = file.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}

		Files.writeString(file, DEFAULT_JSON, StandardCharsets.UTF_8);
	}
}
