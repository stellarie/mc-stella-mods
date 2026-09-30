package com.stella.rpg;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * The tables of batch 3: weapon dice, the dice multiplier, and the stats that enchantments and effects grant.
 * Every number is in vanilla units. The config keys are {@code weapon_dice}, {@code material_dice_multiplier},
 * {@code dice_multiplier_per_level}, {@code refine_dice_multiplier}, {@code refine_extra_dice},
 * {@code enchant_stats}, and {@code effect_stats}.
 */
public record StatTables(
	Map<String, Map<String, RpgDice.Dice>> weaponDice,
	Map<String, Double> materialDiceMultiplier,
	double dicePerLevel,
	NavigableMap<Integer, Double> refineDiceMultiplier,
	NavigableMap<Integer, Integer> refineExtraDice,
	Map<String, Map<Stat, Integer>> enchantStats,
	Map<String, EffectStat> effectStats
) {
	/** The material key of a weapon type that has one die for every material. */
	public static final String ANY = "*";
	public static final double DEFAULT_DICE_PER_LEVEL = 0.02;
	public static final String WEAPON_DICE_KEY = "weapon_dice";
	public static final String MATERIAL_MULTIPLIER_KEY = "material_dice_multiplier";
	public static final String PER_LEVEL_KEY = "dice_multiplier_per_level";
	public static final String REFINE_MULTIPLIER_KEY = "refine_dice_multiplier";
	public static final String REFINE_DICE_KEY = "refine_extra_dice";
	public static final String ENCHANT_STATS_KEY = "enchant_stats";
	public static final String EFFECT_STATS_KEY = "effect_stats";
	public static final String PER_LEVEL_FLAG = "per_level";
	public static final int MAX_STAT = 1024;
	public static final int MAX_DIE_COUNT = 20;
	public static final int MAX_DIE_SIDES = 100;
	public static final List<String> MATERIALS = List.of("wood", "stone", "copper", "gold", "iron", "diamond", "netherite", "void");

	/** An effect's stats. A food effect is flat. Every other effect adds its stats for each level. */
	public record EffectStat(Map<Stat, Integer> stats, boolean perLevel) {
	}

	public static final StatTables DEFAULT = defaults();

	private static StatTables defaults() {
		Map<String, Map<String, RpgDice.Dice>> dice = new LinkedHashMap<>();
		dice.put("sword", materials("1d2", "1d3", "1d3", "1d6", "1d4", "1d6", "1d8", "2d6"));
		dice.put("axe", materials("1d3", "1d4", "1d4", "1d8", "1d6", "1d8", "2d6", "2d8"));
		dice.put("spear", materials("1d2", "1d3", "1d3", "1d6", "1d4", "1d6", "1d8", "2d6"));
		dice.put("mace", Map.of(ANY, new RpgDice.Dice(1, 10)));
		dice.put("trident", Map.of(ANY, new RpgDice.Dice(1, 6)));
		dice.put("bow", Map.of(ANY, new RpgDice.Dice(1, 4)));
		dice.put("crossbow", Map.of(ANY, new RpgDice.Dice(1, 6)));

		Map<String, Double> multiplier = new LinkedHashMap<>();
		double[] values = {1.0, 1.1, 1.1, 1.3, 1.2, 1.4, 1.6, 2.0};
		for (int i = 0; i < MATERIALS.size(); i++) {
			multiplier.put(MATERIALS.get(i), values[i]);
		}

		NavigableMap<Integer, Double> refineMultiplier = new TreeMap<>(Map.of(10, 1.15, 12, 2.30, 15, 3.45));
		NavigableMap<Integer, Integer> refineDice = new TreeMap<>(Map.of(10, 1, 12, 2, 15, 3));
		return new StatTables(copy(dice), Map.copyOf(multiplier), DEFAULT_DICE_PER_LEVEL, refineMultiplier, refineDice,
			defaultEnchantStats(), defaultEffectStats());
	}

	private static Map<String, RpgDice.Dice> materials(final String... dice) {
		Map<String, RpgDice.Dice> row = new LinkedHashMap<>();
		for (int i = 0; i < MATERIALS.size(); i++) {
			row.put(MATERIALS.get(i), parseDice(dice[i]));
		}
		return Map.copyOf(row);
	}

	private static Map<String, Map<String, RpgDice.Dice>> copy(final Map<String, Map<String, RpgDice.Dice>> table) {
		return Map.copyOf(table);
	}

	private static Map<String, Map<Stat, Integer>> defaultEnchantStats() {
		Map<String, Map<Stat, Integer>> table = new LinkedHashMap<>();
		put(table, Stat.STR, 2, "sharpness", "impaling", "density", "wounding", "impale", "shockwave");
		put(table, Stat.STR, 1, "smite", "bane_of_arthropods", "breach", "knockback", "sweeping_edge");
		put(table, Stat.DEX, 2, "power", "marksman", "railgun");
		put(table, Stat.DEX, 1, "piercing", "longshot", "arrow_shower", "farsight", "reach");
		put(table, Stat.AGI, 2, "quick_charge", "fleetfoot", "repeater", "momentum", "tempo");
		put(table, Stat.AGI, 1, "lunge", "swift_sneak", "feather_falling");
		put(table, Stat.VIT, 1, "protection", "blast_protection", "fire_protection", "projectile_protection", "respiration");
		put(table, Stat.VIT, 2, "bulwark", "guarding_ward", "second_wind");
		put(table, Stat.INT, 2, "channeling", "stormcaller", "meteor", "void_brand", "ender_ward");
		put(table, Stat.INT, 1, "loyalty", "riptide", "frostbite", "chill");
		put(table, Stat.LUK, 2, "looting", "fortune", "luck_of_the_sea", "greed", "prospector", "enchanted_yield");
		table.put("reckless", stats(Stat.STR, 3, Stat.VIT, -2));
		table.put("berserker", stats(Stat.STR, 3, Stat.VIT, -2));
		table.put("heavyweight", stats(Stat.VIT, 3, Stat.AGI, -2));
		table.put("leadfoot", stats(Stat.AGI, -2));
		table.put("sureshot", stats(Stat.DEX, 3, Stat.AGI, -2));
		table.put("deadeye", stats(Stat.DEX, 3, Stat.LUK, -2));
		return Map.copyOf(table);
	}

	private static Map<String, EffectStat> defaultEffectStats() {
		Map<String, EffectStat> table = new LinkedHashMap<>();
		table.put("speed", new EffectStat(stats(Stat.AGI, 10), true));
		table.put("slowness", new EffectStat(stats(Stat.AGI, -10), true));
		table.put("haste", new EffectStat(stats(Stat.DEX, 10), true));
		table.put("mining_fatigue", new EffectStat(stats(Stat.DEX, -10), true));
		table.put("resistance", new EffectStat(stats(Stat.VIT, 10), true));
		table.put("luck", new EffectStat(stats(Stat.LUK, 10), true));
		table.put("unluck", new EffectStat(stats(Stat.LUK, -10), true));
		table.put("hero_of_the_village", new EffectStat(stats(Stat.LUK, 10), true));
		table.put("strength", new EffectStat(stats(Stat.STR, 10), true));
		table.put("weakness", new EffectStat(stats(Stat.STR, -10), true));
		table.put("surefooted", new EffectStat(stats(Stat.STR, 20), false));
		table.put("deep_breath", new EffectStat(stats(Stat.VIT, 20), false));
		table.put("light_step", new EffectStat(stats(Stat.AGI, 20), false));
		table.put("current_rider", new EffectStat(stats(Stat.DEX, 20), false));
		table.put("ember_skin", new EffectStat(stats(Stat.INT, 20), false));
		table.put("second_wind", new EffectStat(stats(Stat.LUK, 20), false));
		for (Stat stat : Stat.values()) {
			table.put("seasoned_" + stat.key(), new EffectStat(stats(stat, 1), true));
		}
		return Map.copyOf(table);
	}

	private static void put(final Map<String, Map<Stat, Integer>> table, final Stat stat, final int value, final String... names) {
		for (String name : names) {
			table.put(name, stats(stat, value));
		}
	}

	private static Map<Stat, Integer> stats(final Stat stat, final int value) {
		return Map.of(stat, value);
	}

	private static Map<Stat, Integer> stats(final Stat first, final int firstValue, final Stat second, final int secondValue) {
		return Map.of(first, firstValue, second, secondValue);
	}

	/** Parses {@code 1d6}. Returns null for anything else. */
	public static RpgDice.Dice parseDice(final String text) {
		if (text == null) {
			return null;
		}
		int d = text.indexOf('d');
		if (d <= 0 || d == text.length() - 1) {
			return null;
		}
		try {
			int count = Integer.parseInt(text.substring(0, d));
			int sides = Integer.parseInt(text.substring(d + 1));
			return count >= 1 && count <= MAX_DIE_COUNT && sides >= 1 && sides <= MAX_DIE_SIDES ? new RpgDice.Dice(count, sides) : null;
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	/** The die of a weapon type and material, or null when the type has none. */
	public RpgDice.Dice dice(final String type, final String material) {
		Map<String, RpgDice.Dice> row = this.weaponDice.get(type);
		if (row == null) {
			return null;
		}
		RpgDice.Dice any = row.get(ANY);
		return any != null ? any : row.get(material);
	}

	public double materialMultiplier(final String material) {
		return this.materialDiceMultiplier.getOrDefault(material, 1.0);
	}

	/** The multiplier of the highest refine milestone at or below the level. 1.0 below the first. */
	public double refineMultiplier(final int level) {
		Map.Entry<Integer, Double> milestone = this.refineDiceMultiplier.floorEntry(level);
		return milestone == null ? 1.0 : milestone.getValue();
	}

	public int refineDice(final int level) {
		Map.Entry<Integer, Integer> milestone = this.refineExtraDice.floorEntry(level);
		return milestone == null ? 0 : milestone.getValue();
	}

	public static StatTables parse(final JsonObject root, final Consumer<String> onError) {
		StatTables d = DEFAULT;
		return new StatTables(
			weaponDice(root, d.weaponDice, onError), materialMultiplier(root, d.materialDiceMultiplier, onError),
			perLevel(root, onError), milestones(root, REFINE_MULTIPLIER_KEY, d.refineDiceMultiplier, onError, true),
			milestones(root, REFINE_DICE_KEY, d.refineExtraDice, onError, false),
			enchantStats(root, d.enchantStats, onError), effectStats(root, d.effectStats, onError)
		);
	}

	public static void write(final Map<String, Object> out) {
		StatTables d = DEFAULT;
		Map<String, Object> dice = new LinkedHashMap<>();
		d.weaponDice.forEach((type, row) -> {
			if (row.containsKey(ANY)) {
				dice.put(type, row.get(ANY).toString());
				return;
			}
			Map<String, String> materials = new LinkedHashMap<>();
			for (String material : MATERIALS) {
				materials.put(material, row.get(material).toString());
			}
			dice.put(type, materials);
		});
		out.put(WEAPON_DICE_KEY, dice);
		Map<String, Double> multiplier = new LinkedHashMap<>();
		for (String material : MATERIALS) {
			multiplier.put(material, d.materialDiceMultiplier.get(material));
		}
		out.put(MATERIAL_MULTIPLIER_KEY, multiplier);
		out.put(PER_LEVEL_KEY, DEFAULT_DICE_PER_LEVEL);
		out.put(REFINE_MULTIPLIER_KEY, keyed(d.refineDiceMultiplier));
		out.put(REFINE_DICE_KEY, keyed(d.refineExtraDice));
		Map<String, Object> enchants = new LinkedHashMap<>();
		new java.util.TreeMap<>(d.enchantStats).forEach((name, stats) -> enchants.put(name, statKeys(stats)));
		out.put(ENCHANT_STATS_KEY, enchants);
		Map<String, Object> effects = new LinkedHashMap<>();
		new java.util.TreeMap<>(d.effectStats).forEach((name, effect) -> {
			Map<String, Object> row = statKeys(effect.stats());
			if (!effect.perLevel()) {
				row.put(PER_LEVEL_FLAG, false);
			}
			effects.put(name, row);
		});
		out.put(EFFECT_STATS_KEY, effects);
	}

	private static <V> Map<String, V> keyed(final NavigableMap<Integer, V> map) {
		Map<String, V> out = new LinkedHashMap<>();
		map.forEach((level, value) -> out.put(Integer.toString(level), value));
		return out;
	}

	private static Map<String, Object> statKeys(final Map<Stat, Integer> stats) {
		Map<String, Object> out = new LinkedHashMap<>();
		for (Stat stat : Stat.values()) {
			if (stats.containsKey(stat)) {
				out.put(stat.key(), stats.get(stat));
			}
		}
		return out;
	}

	private static Map<String, Map<String, RpgDice.Dice>> weaponDice(
		final JsonObject root, final Map<String, Map<String, RpgDice.Dice>> fallback, final Consumer<String> onError
	) {
		JsonElement element = root.get(WEAPON_DICE_KEY);
		if (element == null) {
			return fallback;
		}
		if (!element.isJsonObject()) {
			onError.accept("stats.json key 'weapon_dice' must be an object; using the defaults");
			return fallback;
		}
		Map<String, Map<String, RpgDice.Dice>> table = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, RpgDice.Dice>> type : fallback.entrySet()) {
			JsonElement row = element.getAsJsonObject().get(type.getKey());
			table.put(type.getKey(), row == null ? type.getValue() : diceRow(type.getKey(), row, type.getValue(), onError));
		}
		return Map.copyOf(table);
	}

	private static Map<String, RpgDice.Dice> diceRow(
		final String type, final JsonElement row, final Map<String, RpgDice.Dice> fallback, final Consumer<String> onError
	) {
		String path = "weapon_dice." + type;
		if (row.isJsonPrimitive()) {
			RpgDice.Dice dice = row.getAsJsonPrimitive().isString() ? parseDice(row.getAsString()) : null;
			if (dice == null) {
				onError.accept("stats.json key '" + path + "' must be dice such as \"1d6\"; using the default");
				return fallback;
			}
			return Map.of(ANY, dice);
		}
		if (!row.isJsonObject()) {
			onError.accept("stats.json key '" + path + "' must be dice or an object of material to dice; using the default");
			return fallback;
		}
		Map<String, RpgDice.Dice> out = new LinkedHashMap<>(fallback);
		out.remove(ANY);
		for (String material : MATERIALS) {
			JsonElement entry = row.getAsJsonObject().get(material);
			if (entry == null) {
				continue;
			}
			RpgDice.Dice dice = entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString() ? parseDice(entry.getAsString()) : null;
			if (dice == null) {
				onError.accept("stats.json key '" + path + "." + material + "' must be dice such as \"1d6\"; using the default");
				continue;
			}
			out.put(material, dice);
		}
		for (String material : MATERIALS) {
			out.putIfAbsent(material, parseDice("1d4"));
		}
		return Map.copyOf(out);
	}

	private static Map<String, Double> materialMultiplier(
		final JsonObject root, final Map<String, Double> fallback, final Consumer<String> onError
	) {
		JsonElement element = root.get(MATERIAL_MULTIPLIER_KEY);
		if (element == null) {
			return fallback;
		}
		if (!element.isJsonObject()) {
			onError.accept("stats.json key 'material_dice_multiplier' must be an object; using the defaults");
			return fallback;
		}
		Map<String, Double> out = new LinkedHashMap<>(fallback);
		for (String material : MATERIALS) {
			JsonElement entry = element.getAsJsonObject().get(material);
			if (entry == null) {
				continue;
			}
			Double value = number(entry);
			if (value == null || value < 0.0 || value > 100.0) {
				onError.accept("stats.json key 'material_dice_multiplier." + material + "' must be a number from 0 to 100; using the default");
				continue;
			}
			out.put(material, value);
		}
		return Map.copyOf(out);
	}

	private static double perLevel(final JsonObject root, final Consumer<String> onError) {
		JsonElement element = root.get(PER_LEVEL_KEY);
		if (element == null) {
			return DEFAULT_DICE_PER_LEVEL;
		}
		Double value = number(element);
		if (value == null || value < 0.0 || value > 10.0) {
			onError.accept("stats.json key 'dice_multiplier_per_level' must be a number from 0 to 10; using the default");
			return DEFAULT_DICE_PER_LEVEL;
		}
		return value;
	}

	@SuppressWarnings("unchecked")
	private static <V extends Number> NavigableMap<Integer, V> milestones(
		final JsonObject root, final String key, final NavigableMap<Integer, V> fallback, final Consumer<String> onError,
		final boolean decimal
	) {
		JsonElement element = root.get(key);
		if (element == null) {
			return fallback;
		}
		NavigableMap<Integer, V> out = element.isJsonObject() ? new TreeMap<>() : null;
		if (out != null) {
			for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
				Double value = number(entry.getValue());
				Integer level = level(entry.getKey());
				if (level == null || value == null || value < 0.0 || value > 100.0 || !decimal && value != Math.rint(value)) {
					out = null;
					break;
				}
				out.put(level, decimal ? (V) value : (V) Integer.valueOf(value.intValue()));
			}
		}
		if (out == null) {
			onError.accept("stats.json key '" + key + "' must map a refine level to a " + (decimal ? "number" : "whole number") + "; using the default");
			return fallback;
		}
		return out;
	}

	private static Integer level(final String text) {
		try {
			int level = Integer.parseInt(text);
			return level >= 1 && level <= 1000 ? level : null;
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private static Map<String, Map<Stat, Integer>> enchantStats(
		final JsonObject root, final Map<String, Map<Stat, Integer>> fallback, final Consumer<String> onError
	) {
		JsonElement element = root.get(ENCHANT_STATS_KEY);
		if (element == null) {
			return fallback;
		}
		if (!element.isJsonObject()) {
			onError.accept("stats.json key 'enchant_stats' must be an object; using the defaults");
			return fallback;
		}
		Map<String, Map<Stat, Integer>> out = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
			Map<Stat, Integer> stats = entry.getValue().isJsonObject() ? statMap(entry.getValue().getAsJsonObject()) : null;
			if (stats == null) {
				onError.accept("stats.json key 'enchant_stats." + entry.getKey() + "' must map stat keys to integers from -" + MAX_STAT + " to "
					+ MAX_STAT + "; skipping it");
				continue;
			}
			out.put(entry.getKey(), stats);
		}
		return Map.copyOf(out);
	}

	private static Map<String, EffectStat> effectStats(
		final JsonObject root, final Map<String, EffectStat> fallback, final Consumer<String> onError
	) {
		JsonElement element = root.get(EFFECT_STATS_KEY);
		if (element == null) {
			return fallback;
		}
		if (!element.isJsonObject()) {
			onError.accept("stats.json key 'effect_stats' must be an object; using the defaults");
			return fallback;
		}
		Map<String, EffectStat> out = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
			EffectStat effect = entry.getValue().isJsonObject() ? effectStat(entry.getValue().getAsJsonObject()) : null;
			if (effect == null) {
				onError.accept("stats.json key 'effect_stats." + entry.getKey() + "' must map stat keys to integers from -" + MAX_STAT + " to "
					+ MAX_STAT + ", with an optional per_level flag; skipping it");
				continue;
			}
			out.put(entry.getKey(), effect);
		}
		return Map.copyOf(out);
	}

	private static EffectStat effectStat(final JsonObject object) {
		boolean perLevel = true;
		JsonObject stats = new JsonObject();
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			if (entry.getKey().equals(PER_LEVEL_FLAG)) {
				if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isBoolean()) {
					return null;
				}
				perLevel = entry.getValue().getAsBoolean();
			} else {
				stats.add(entry.getKey(), entry.getValue());
			}
		}
		Map<Stat, Integer> map = statMap(stats);
		return map == null ? null : new EffectStat(map, perLevel);
	}

	private static Map<Stat, Integer> statMap(final JsonObject object) {
		Map<Stat, Integer> map = new EnumMap<>(Stat.class);
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			Stat stat = Stat.byKey(entry.getKey()).orElse(null);
			Double value = number(entry.getValue());
			if (stat == null || value == null || value != Math.rint(value) || Math.abs(value) > MAX_STAT) {
				return null;
			}
			map.put(stat, value.intValue());
		}
		return Map.copyOf(map);
	}

	private static Double number(final JsonElement element) {
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return null;
		}
		double value = element.getAsDouble();
		return Double.isFinite(value) ? value : null;
	}
}
