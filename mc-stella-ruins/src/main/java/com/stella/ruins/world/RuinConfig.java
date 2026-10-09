package com.stella.ruins.world;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stella.ruins.StellaRuins;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The world library's numbers, read from {@code config/mod-stella-ruins/ruins.json}. A bad entry logs and keeps
 * its default, so a typo never stops the server.
 */
public final class RuinConfig {
	public static final double DEFAULT_CLUE_CHANCE = 0.08;
	public static final double DEFAULT_UNIQUE_DROP_CHANCE = 0.001;
	public static final int DEFAULT_VAULT_LEVELS_PER_ROLL = 25;
	public static final int DEFAULT_VAULT_ROLLS_MAX = 5;
	public static final double DEFAULT_TROPHY_CHANCE = 0.04;
	public static final double DEFAULT_PITY_STEP = 0.06;
	public static final double DEFAULT_LEAD_CHANCE = 0.5;

	/** One reading of the file. */
	public record Values(double clueChance, double uniqueDropChance, int vaultLevelsPerRoll, int vaultRollsMax, Map<String, Integer> resetDays,
		double trophyChance, double pityStep, double leadChance) {
		public Values {
			resetDays = Map.copyOf(resetDays);
		}

		public static Values defaults() {
			return new Values(DEFAULT_CLUE_CHANCE, DEFAULT_UNIQUE_DROP_CHANCE, DEFAULT_VAULT_LEVELS_PER_ROLL, DEFAULT_VAULT_ROLLS_MAX, Map.of(),
				DEFAULT_TROPHY_CHANCE, DEFAULT_PITY_STEP, DEFAULT_LEAD_CHANCE);
		}

		public Values withClueChance(final double value) {
			return new Values(value, this.uniqueDropChance, this.vaultLevelsPerRoll, this.vaultRollsMax, this.resetDays, this.trophyChance, this.pityStep, this.leadChance);
		}

		public Values withUniqueDropChance(final double value) {
			return new Values(this.clueChance, value, this.vaultLevelsPerRoll, this.vaultRollsMax, this.resetDays, this.trophyChance, this.pityStep, this.leadChance);
		}

		public Values withResetDays(final Map<String, Integer> value) {
			return new Values(this.clueChance, this.uniqueDropChance, this.vaultLevelsPerRoll, this.vaultRollsMax, value, this.trophyChance, this.pityStep, this.leadChance);
		}

		public Values withTrophy(final double chance, final double step) {
			return new Values(this.clueChance, this.uniqueDropChance, this.vaultLevelsPerRoll, this.vaultRollsMax, this.resetDays, chance, step, this.leadChance);
		}

		public Values withLeadChance(final double value) {
			return new Values(this.clueChance, this.uniqueDropChance, this.vaultLevelsPerRoll, this.vaultRollsMax, this.resetDays, this.trophyChance, this.pityStep, value);
		}

		/** How many times a vault rolls its table at this level. */
		public int vaultRolls(final int level) {
			return Math.min(this.vaultRollsMax, 1 + Math.max(0, level) / this.vaultLevelsPerRoll);
		}
	}

	private static volatile Values current = Values.defaults();

	private RuinConfig() {
	}

	public static Values get() {
		return current;
	}

	/** Replaces the values and returns the old ones, so a test can restore them in a finally block. */
	public static Values replace(final Values values) {
		Values old = current;
		current = values;
		return old;
	}

	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(StellaRuins.MOD_ID).resolve("ruins.json");
	}

	/** Reads the file, writing the defaults first when it is missing. */
	public static void load() {
		Path file = file();
		if (!Files.exists(file)) {
			try {
				Files.createDirectories(file.getParent());
				Files.writeString(file, defaultText(), StandardCharsets.UTF_8);
			} catch (IOException e) {
				StellaRuins.LOGGER.warn("[RUINS] could not write {}: {}", file, e.toString());
			}
			current = Values.defaults();
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			current = parse(JsonParser.parseReader(reader));
		} catch (IOException | RuntimeException e) {
			StellaRuins.LOGGER.warn("[RUINS] could not read {}, using defaults: {}", file, e.toString());
			current = Values.defaults();
		}
	}

	/** Parses one document; every bad entry is logged and skipped. */
	public static Values parse(final JsonElement root) {
		Values defaults = Values.defaults();
		if (root == null || !root.isJsonObject()) {
			StellaRuins.LOGGER.warn("[RUINS] ruins.json is not an object, using defaults");
			return defaults;
		}
		JsonObject object = root.getAsJsonObject();
		double clue = chance(object, "clue_chance", defaults.clueChance());
		double unique = chance(object, "unique_drop_chance", defaults.uniqueDropChance());
		int perRoll = positive(object, "vault_levels_per_roll", defaults.vaultLevelsPerRoll());
		int rollsMax = positive(object, "vault_rolls_max", defaults.vaultRollsMax());
		Map<String, Integer> days = new LinkedHashMap<>();
		JsonElement resets = object.get("reset_days");
		if (resets != null && resets.isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : resets.getAsJsonObject().entrySet()) {
				Integer value = integer(entry.getValue());
				if (value == null || value < 1) {
					StellaRuins.LOGGER.warn("[RUINS] reset_days entry {} is not a positive whole number, skipped", entry.getKey());
				} else {
					days.put(entry.getKey(), value);
				}
			}
		} else if (resets != null) {
			StellaRuins.LOGGER.warn("[RUINS] reset_days is not an object, skipped");
		}
		double trophy = chance(object, "trophy_chance", defaults.trophyChance());
		double pity = chance(object, "pity_step", defaults.pityStep());
		double lead = chance(object, "lead_chance", defaults.leadChance());
		return new Values(clue, unique, perRoll, rollsMax, days, trophy, pity, lead);
	}

	private static double chance(final JsonObject object, final String key, final double fallback) {
		JsonElement element = object.get(key);
		if (element == null) {
			return fallback;
		}
		try {
			double value = element.getAsDouble();
			if (value >= 0.0 && value <= 1.0) {
				return value;
			}
		} catch (RuntimeException e) {
			// A string or an object falls through to the warning.
		}
		StellaRuins.LOGGER.warn("[RUINS] {} must be a number from 0 to 1, using {}", key, fallback);
		return fallback;
	}

	private static int positive(final JsonObject object, final String key, final int fallback) {
		JsonElement element = object.get(key);
		if (element == null) {
			return fallback;
		}
		Integer value = integer(element);
		if (value != null && value >= 1) {
			return value;
		}
		StellaRuins.LOGGER.warn("[RUINS] {} must be a positive whole number, using {}", key, fallback);
		return fallback;
	}

	private static Integer integer(final JsonElement element) {
		try {
			double value = element.getAsDouble();
			return value == Math.rint(value) ? (int) value : null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static String defaultText() {
		return """
			{
			  "clue_chance": %s,
			  "unique_drop_chance": %s,
			  "vault_levels_per_roll": %d,
			  "vault_rolls_max": %d,
			  "reset_days": {},
			  "trophy_chance": %s,
			  "pity_step": %s,
			  "lead_chance": %s
			}
			""".formatted(DEFAULT_CLUE_CHANCE, DEFAULT_UNIQUE_DROP_CHANCE, DEFAULT_VAULT_LEVELS_PER_ROLL, DEFAULT_VAULT_ROLLS_MAX,
			DEFAULT_TROPHY_CHANCE, DEFAULT_PITY_STEP, DEFAULT_LEAD_CHANCE);
	}
}
