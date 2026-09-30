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
				"spawn_safety_radius": 240.0,
				"elite_min_distance": 500.0,
				"boss_min_distance": 2000.0,
				"elite_bonus_per_100": 0.0005,
				"elite_bonus_cap": 0.20,
				"elite_quiet_radius": 16.0,
				"boss_quiet_radius": 32.0,
				"quiet_factor": 0.5,
				"biome_levels": {
					"minecraft:plains": 0,
					"minecraft:sunflower_plains": 0,
					"minecraft:meadow": 0,
					"minecraft:cherry_grove": 0,
					"minecraft:beach": 0,
					"minecraft:snowy_beach": 0,
					"minecraft:mushroom_fields": 0,
					"minecraft:forest": 3,
					"minecraft:birch_forest": 3,
					"minecraft:dappled_forest": 3,
					"minecraft:flower_forest": 3,
					"minecraft:taiga": 3,
					"minecraft:savanna": 3,
					"minecraft:savanna_plateau": 3,
					"minecraft:snowy_plains": 3,
					"minecraft:grove": 3,
					"minecraft:river": 3,
					"minecraft:frozen_river": 3,
					"minecraft:ocean": 3,
					"minecraft:cold_ocean": 3,
					"minecraft:lukewarm_ocean": 3,
					"minecraft:warm_ocean": 3,
					"minecraft:swamp": 6,
					"minecraft:desert": 6,
					"minecraft:badlands": 6,
					"minecraft:wooded_badlands": 6,
					"minecraft:sparse_jungle": 6,
					"minecraft:snowy_taiga": 6,
					"minecraft:snowy_slopes": 6,
					"minecraft:old_growth_birch_forest": 6,
					"minecraft:old_growth_pine_taiga": 6,
					"minecraft:old_growth_spruce_taiga": 6,
					"minecraft:windswept_savanna": 6,
					"minecraft:stony_shore": 6,
					"minecraft:dark_forest": 10,
					"minecraft:jungle": 10,
					"minecraft:bamboo_jungle": 10,
					"minecraft:mangrove_swamp": 10,
					"minecraft:pale_garden": 10,
					"minecraft:eroded_badlands": 10,
					"minecraft:windswept_hills": 10,
					"minecraft:windswept_forest": 10,
					"minecraft:windswept_gravelly_hills": 10,
					"minecraft:ice_spikes": 10,
					"minecraft:frozen_peaks": 10,
					"minecraft:jagged_peaks": 10,
					"minecraft:stony_peaks": 10,
					"minecraft:frozen_ocean": 10,
					"minecraft:deep_ocean": 10,
					"minecraft:deep_cold_ocean": 10,
					"minecraft:deep_frozen_ocean": 10,
					"minecraft:deep_lukewarm_ocean": 10,
					"minecraft:lush_caves": 5,
					"minecraft:dripstone_caves": 6,
					"minecraft:sulfur_caves": 8,
					"minecraft:deep_dark": 25
				},
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
