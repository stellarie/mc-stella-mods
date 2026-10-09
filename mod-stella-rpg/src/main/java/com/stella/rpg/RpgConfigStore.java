package com.stella.rpg;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stella.rpg.roll.Affix;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public final class RpgConfigStore {
	public static final double MAX_LIMIT = 10.0;
	public static final int MAX_DICE_VALUE = 1000;
	public static final int MAX_WEIGHT = 1000;
	public static final double MAX_HEALTH_SCALE = 1000.0;
	public static final int MAX_PROFILES = 256;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static volatile RpgConfig current = RpgConfig.defaults();

	private RpgConfigStore() {
	}

	public static RpgConfig current() {
		return current;
	}

	public static void set(final RpgConfig config) {
		current = config == null ? RpgConfig.defaults() : config;
	}

	public static Path defaultPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(StellaRpg.MOD_ID).resolve("stats.json");
	}

	public static RpgConfig load(final Path path, final Consumer<String> onError) {
		if (!Files.exists(path)) {
			writeDefaults(path, onError);
			return RpgConfig.defaults();
		}
		try {
			return parse(Files.readString(path, StandardCharsets.UTF_8), onError);
		} catch (IOException exception) {
			onError.accept("Cannot read " + path + ": " + exception.getMessage());
			return RpgConfig.defaults();
		}
	}

	public static RpgConfig reload(final Path path) {
		RpgConfig config = load(path, StellaRpg.LOGGER::error);
		current = config;
		return config;
	}

	public static RpgConfig parse(final String text, final Consumer<String> onError) {
		JsonObject root;
		try {
			JsonElement parsed = JsonParser.parseString(text);
			if (!parsed.isJsonObject()) {
				onError.accept("stats.json must hold a JSON object; using defaults");
				return RpgConfig.defaults();
			}
			root = parsed.getAsJsonObject();
		} catch (RuntimeException exception) {
			onError.accept("stats.json is not valid JSON; using defaults");
			return RpgConfig.defaults();
		}

		Map<RpgConfig.Effect, RpgConfig.Curve> curves = new EnumMap<>(RpgConfig.Effect.class);
		for (RpgConfig.Effect effect : RpgConfig.Effect.values()) {
			curves.put(effect, curve(root, effect, onError));
		}
		for (String removed : RpgConfig.REMOVED_KEYS) {
			if (root.has(removed)) {
				onError.accept("stats.json key '" + removed + "' was removed; dice replace it, so it is ignored");
			}
		}
		if (root.has(RpgConfig.RETIRED_MOB_WEIGHTS_KEY)) {
			onError.accept("stats.json key 'mob_weights' was retired; mob_stats replaces it, so it is ignored");
		}
		return new RpgConfig(Map.copyOf(curves), critMultiplier(root, onError), dice(root, onError),
			mobStats(root, onError), mobAmplifier(root, onError), armorSets(root, onError), weaponRolls(root, onError),
			healthScale(root, onError), mobDiceShare(root, onError), StatTables.parse(root, onError),
			xpCurve(root, onError), magic(root, onError), scaling(root, onError), hp(root, onError),
			materialRolls(root, onError));
	}

	private static Map<String, RpgConfig.ArmorSet> armorSets(final JsonObject root, final Consumer<String> onError) {
		JsonElement element = root.get(RpgConfig.ARMOR_SETS_KEY);
		Map<String, RpgConfig.ArmorSet> defaults = RpgConfig.DEFAULT_ARMOR_SETS;
		if (element == null) {
			return defaults;
		}
		if (!element.isJsonObject()) {
			onError.accept("stats.json key 'armor_sets' must be an object; using the defaults");
			return defaults;
		}
		JsonObject object = element.getAsJsonObject();
		Map<String, RpgConfig.ArmorSet> table = new LinkedHashMap<>();
		for (Map.Entry<String, RpgConfig.ArmorSet> material : defaults.entrySet()) {
			String path = "armor_sets." + material.getKey();
			JsonElement row = object.get(material.getKey());
			if (row != null && !row.isJsonObject()) {
				onError.accept("stats.json key '" + path + "' must be an object; using the defaults");
			}
			JsonObject rowObject = row != null && row.isJsonObject() ? row.getAsJsonObject() : new JsonObject();
			RpgConfig.ArmorSet fallback = material.getValue();
			table.put(material.getKey(), new RpgConfig.ArmorSet(
				itemList(rowObject, "items", path, fallback.items(), onError),
				statBonuses(rowObject, "per_piece", path, fallback.perPiece(), onError),
				flatPerPiece(rowObject, path, fallback.flatPerPiece(), onError),
				statBonuses(rowObject, "set_bonus", path, fallback.setBonus(), onError)
			));
		}
		return Map.copyOf(table);
	}

	private static List<Identifier> itemList(
		final JsonObject row, final String key, final String path, final List<Identifier> fallback,
		final Consumer<String> onError
	) {
		JsonElement element = row.get(key);
		if (element == null) {
			return fallback;
		}
		if (!element.isJsonArray()) {
			onError.accept("stats.json key '" + path + "." + key + "' must be a list of item ids; using the default");
			return fallback;
		}
		List<Identifier> items = new ArrayList<>();
		for (JsonElement entry : element.getAsJsonArray()) {
			Identifier id = entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()
				? Identifier.tryParse(entry.getAsString()) : null;
			if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
				onError.accept("stats.json key '" + path + "." + key + "' has an unknown item id " + entry + "; skipping it");
				continue;
			}
			items.add(id);
		}
		return List.copyOf(items);
	}

	private static RpgConfig.WeaponRolls weaponRolls(final JsonObject root, final Consumer<String> onError) {
		JsonElement element = root.get(RpgConfig.WEAPON_ROLLS_KEY);
		RpgConfig.WeaponRolls defaults = RpgConfig.DEFAULT_WEAPON_ROLLS;
		if (element == null) {
			return defaults;
		}
		if (!element.isJsonObject()) {
			onError.accept("stats.json key 'weapon_rolls' must be an object; using the defaults");
			return defaults;
		}
		JsonObject object = element.getAsJsonObject();
		Map<String, RpgConfig.RollTier> tiers = new LinkedHashMap<>();
		for (Map.Entry<String, RpgConfig.RollTier> tier : defaults.tiers().entrySet()) {
			String path = "weapon_rolls." + tier.getKey();
			JsonElement row = object.get(tier.getKey());
			if (row != null && !row.isJsonObject()) {
				onError.accept("stats.json key '" + path + "' must be an object; using the defaults");
			}
			JsonObject rowObject = row != null && row.isJsonObject() ? row.getAsJsonObject() : new JsonObject();
			RpgConfig.RollTier fallback = tier.getValue();
			int[] range = rollRange(rowObject, path, fallback, onError);
			tiers.put(tier.getKey(), new RpgConfig.RollTier(
				itemList(rowObject, "items", path, fallback.items(), onError),
				itemList(rowObject, "armor_items", path, fallback.armorItems(), onError),
				range[0], range[1],
				affixRanges(rowObject, path, fallback.affixes(), onError)
			));
		}
		return new RpgConfig.WeaponRolls(
			countWeights(object, defaults.countWeights(), onError), Map.copyOf(tiers), affixChance(object, onError)
		);
	}

	private static RpgConfig.MaterialRolls materialRolls(final JsonObject root, final Consumer<String> onError) {
		JsonElement element = root.get(RpgConfig.MATERIAL_ROLLS_KEY);
		RpgConfig.MaterialRolls defaults = RpgConfig.DEFAULT_MATERIAL_ROLLS;
		if (element == null) {
			return defaults;
		}
		if (!element.isJsonObject()) {
			onError.accept("stats.json key 'material_rolls' must be an object; using the defaults");
			return defaults;
		}
		JsonObject object = element.getAsJsonObject();
		String path = RpgConfig.MATERIAL_ROLLS_KEY;
		Map<String, RpgConfig.MaterialTier> tiers = new LinkedHashMap<>();
		for (Map.Entry<String, RpgConfig.MaterialTier> tier : defaults.tiers().entrySet()) {
			tiers.put(tier.getKey(), materialTier(object.get(tier.getKey()), path + "." + tier.getKey(), tier.getValue(), onError));
		}
		return new RpgConfig.MaterialRolls(
			materialFlag(object, "craft_random_roll", path, defaults.craftRandomRoll(), onError),
			materialSeed(object, path, defaults.seed(), onError),
			materialNumber(object, "affix_share", path, 0.0, 1.0, defaults.affixShare(), onError),
			materialNumber(object, "affix_scale", path, 0.0, 10.0, defaults.affixScale(), onError),
			materialNumber(object, "depth_rank_weight", path, 0.0, MAX_LIMIT * 10, defaults.depthRankWeight(), onError),
			materialNumber(object, "depth_enchant_bonus", path, 0.0, MAX_LIMIT * 10, defaults.depthEnchantBonus(), onError),
			materialNumber(object, "second_enchant_chance", path, 0.0, 1.0, defaults.secondEnchantChance(), onError),
			Map.copyOf(tiers)
		);
	}

	private static RpgConfig.MaterialTier materialTier(
		final JsonElement element, final String path, final RpgConfig.MaterialTier fallback, final Consumer<String> onError
	) {
		if (element == null) {
			return fallback;
		}
		if (!element.isJsonObject()) {
			onError.accept("stats.json key '" + path + "' must be an object; using the defaults");
			return fallback;
		}
		JsonObject row = element.getAsJsonObject();
		Integer lowValue = rollValue(row, "min", path, onError);
		Integer highValue = rollValue(row, "max", path, onError);
		int low = lowValue != null ? lowValue : fallback.min();
		int high = highValue != null ? highValue : fallback.max();
		if (low > high) {
			onError.accept("stats.json key '" + path + ".min' must not exceed '" + path + ".max'; using the default range");
			low = fallback.min();
			high = fallback.max();
		}
		int profileCount = fallback.profiles();
		JsonElement profiles = row.get("profiles");
		if (profiles != null) {
			Double value = number(profiles);
			if (value == null || value < 1.0 || value > MAX_PROFILES || value != Math.rint(value)) {
				onError.accept("stats.json key '" + path + ".profiles' must be an integer from 1 to " + MAX_PROFILES + "; using the default");
			} else {
				profileCount = value.intValue();
			}
		}
		return new RpgConfig.MaterialTier(
			itemList(row, "items", path, fallback.items(), onError), low, high,
			materialWeights(row, path, fallback.countWeights(), onError), profileCount,
			materialNumber(row, "enchant_chance", path, 0.0, 1.0, fallback.enchantChance(), onError),
			explicitProfiles(row, path, fallback.explicit(), onError)
		);
	}

	private static List<Integer> materialWeights(
		final JsonObject row, final String path, final List<Integer> fallback, final Consumer<String> onError
	) {
		JsonElement element = row.get(RpgConfig.COUNT_WEIGHTS_KEY);
		if (element == null) {
			return fallback;
		}
		List<Integer> weights = new ArrayList<>();
		long sum = 0;
		boolean good = element.isJsonArray() && element.getAsJsonArray().size() == fallback.size();
		if (good) {
			for (JsonElement entry : element.getAsJsonArray()) {
				Double value = number(entry);
				if (value == null || value < 0.0 || value > MAX_WEIGHT || value != Math.rint(value)) {
					good = false;
					break;
				}
				weights.add(value.intValue());
				sum += value.intValue();
			}
		}
		if (!good || sum == 0) {
			onError.accept("stats.json key '" + path + ".count_weights' must list " + fallback.size()
				+ " integers from 0 to " + MAX_WEIGHT + " with a sum above 0; using the default");
			return fallback;
		}
		return List.copyOf(weights);
	}

	private static List<Map<String, Integer>> explicitProfiles(
		final JsonObject row, final String path, final List<Map<String, Integer>> fallback, final Consumer<String> onError
	) {
		JsonElement element = row.get("explicit");
		if (element == null) {
			return fallback;
		}
		if (!element.isJsonArray()) {
			onError.accept("stats.json key '" + path + ".explicit' must be a list of objects; using the default");
			return fallback;
		}
		List<Map<String, Integer>> profiles = new ArrayList<>();
		for (JsonElement entry : element.getAsJsonArray()) {
			Map<String, Integer> profile = explicitProfile(entry);
			if (profile == null) {
				onError.accept("stats.json key '" + path + ".explicit' has a bad entry " + entry
					+ "; it needs 1 to 4 known stat or affix keys with positive integers; skipping it");
				continue;
			}
			profiles.add(profile);
		}
		return List.copyOf(profiles);
	}

	private static Map<String, Integer> explicitProfile(final JsonElement entry) {
		if (!entry.isJsonObject() || entry.getAsJsonObject().size() < 1 || entry.getAsJsonObject().size() > 4) {
			return null;
		}
		Map<String, Integer> profile = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> field : entry.getAsJsonObject().entrySet()) {
			Double value = number(field.getValue());
			boolean known = Stat.byKey(field.getKey()).isPresent() || Affix.byKey(field.getKey()).isPresent();
			if (!known || value == null || value < 1.0 || value > 100000.0 || value != Math.rint(value)) {
				return null;
			}
			profile.put(field.getKey(), value.intValue());
		}
		return Map.copyOf(profile);
	}

	private static boolean materialFlag(
		final JsonObject object, final String key, final String path, final boolean fallback, final Consumer<String> onError
	) {
		JsonElement element = object.get(key);
		if (element == null) {
			return fallback;
		}
		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			onError.accept("stats.json key '" + path + "." + key + "' must be true or false; using the default");
			return fallback;
		}
		return element.getAsBoolean();
	}

	private static long materialSeed(final JsonObject object, final String path, final long fallback, final Consumer<String> onError) {
		JsonElement element = object.get("seed");
		if (element == null) {
			return fallback;
		}
		Double value = number(element);
		if (value == null || value != Math.rint(value) || Math.abs(value) > 9.0e15) {
			onError.accept("stats.json key '" + path + ".seed' must be an integer; using the default");
			return fallback;
		}
		return value.longValue();
	}

	private static double materialNumber(
		final JsonObject object, final String key, final String path, final double min, final double max, final double fallback,
		final Consumer<String> onError
	) {
		JsonElement element = object.get(key);
		if (element == null) {
			return fallback;
		}
		Double value = number(element);
		if (value == null || value < min || value > max) {
			onError.accept("stats.json key '" + path + "." + key + "' must be a number from " + min + " to " + max + "; using the default");
			return fallback;
		}
		return value;
	}

	private static double affixChance(final JsonObject object, final Consumer<String> onError) {
		JsonElement element = object.get(RpgConfig.AFFIX_CHANCE_KEY);
		if (element == null) {
			return RpgConfig.DEFAULT_AFFIX_CHANCE;
		}
		Double value = number(element);
		if (value == null || value < 0.0 || value > 1.0) {
			onError.accept("stats.json key 'weapon_rolls.affix_chance' must be a number from 0 to 1; using the default");
			return RpgConfig.DEFAULT_AFFIX_CHANCE;
		}
		return value;
	}

	private static Map<Affix, RpgConfig.AffixRange> affixRanges(
		final JsonObject row, final String path, final Map<Affix, RpgConfig.AffixRange> fallback, final Consumer<String> onError
	) {
		JsonElement element = row.get(RpgConfig.AFFIXES_KEY);
		if (element == null) {
			return fallback;
		}
		if (!element.isJsonObject()) {
			onError.accept("stats.json key '" + path + ".affixes' must be an object; using the defaults");
			return fallback;
		}
		Map<Affix, RpgConfig.AffixRange> ranges = new EnumMap<>(Affix.class);
		ranges.putAll(fallback);
		for (Affix affix : Affix.values()) {
			JsonElement entry = element.getAsJsonObject().get(affix.key());
			if (entry == null) {
				continue;
			}
			RpgConfig.AffixRange range = affixRange(entry, affix);
			if (range == null) {
				onError.accept("stats.json key '" + path + ".affixes." + affix.key() + "' must be [min, max] with 0 <= min <= max"
					+ (affix.percent() ? " <= 100" : "") + "; using the default");
				continue;
			}
			ranges.put(affix, range);
		}
		return Map.copyOf(ranges);
	}

	private static RpgConfig.AffixRange affixRange(final JsonElement entry, final Affix affix) {
		if (!entry.isJsonArray() || entry.getAsJsonArray().size() != 2) {
			return null;
		}
		Double min = number(entry.getAsJsonArray().get(0));
		Double max = number(entry.getAsJsonArray().get(1));
		double limit = affix.percent() ? 100.0 : StatSheet.CEILING * 1000.0;
		if (min == null || max == null || min < 0.0 || min > max || max > limit) {
			return null;
		}
		return new RpgConfig.AffixRange(min, max);
	}

	private static List<Integer> countWeights(
		final JsonObject object, final List<Integer> fallback, final Consumer<String> onError
	) {
		JsonElement element = object.get(RpgConfig.COUNT_WEIGHTS_KEY);
		if (element == null) {
			return fallback;
		}
		List<Integer> weights = new ArrayList<>();
		long sum = 0;
		if (element.isJsonArray() && element.getAsJsonArray().size() == fallback.size()) {
			for (JsonElement entry : element.getAsJsonArray()) {
				Double value = number(entry);
				if (value == null || value < 0.0 || value > MAX_WEIGHT || value != Math.rint(value)) {
					weights = null;
					break;
				}
				weights.add(value.intValue());
				sum += value.intValue();
			}
		} else {
			weights = null;
		}
		if (weights == null || sum == 0) {
			onError.accept("stats.json key 'weapon_rolls.count_weights' must list " + fallback.size()
				+ " integers from 0 to " + MAX_WEIGHT + " with a sum above 0; using the default");
			return fallback;
		}
		return List.copyOf(weights);
	}

	private static int[] rollRange(
		final JsonObject row, final String path, final RpgConfig.RollTier fallback, final Consumer<String> onError
	) {
		Integer min = rollValue(row, "min", path, onError);
		Integer max = rollValue(row, "max", path, onError);
		int low = min != null ? min : fallback.min();
		int high = max != null ? max : fallback.max();
		if (low > high) {
			onError.accept("stats.json key '" + path + ".min' must not exceed '" + path + ".max'; using the default range");
			return new int[] {fallback.min(), fallback.max()};
		}
		return new int[] {low, high};
	}

	private static Integer rollValue(
		final JsonObject row, final String key, final String path, final Consumer<String> onError
	) {
		JsonElement element = row.get(key);
		if (element == null) {
			return null;
		}
		Double value = number(element);
		if (value == null || value < 1.0 || value > StatSheet.CEILING || value != Math.rint(value)) {
			onError.accept("stats.json key '" + path + "." + key + "' must be an integer from 1 to "
				+ StatSheet.CEILING + "; using the default");
			return null;
		}
		return value.intValue();
	}

	private static Map<Stat, Integer> statBonuses(
		final JsonObject row, final String key, final String path, final Map<Stat, Integer> fallback,
		final Consumer<String> onError
	) {
		JsonElement element = row.get(key);
		if (element == null) {
			return fallback;
		}
		Map<Stat, Integer> bonuses = element.isJsonObject() ? new EnumMap<>(Stat.class) : null;
		if (bonuses != null) {
			for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
				Double value = number(entry.getValue());
				Stat stat = Stat.byKey(entry.getKey()).orElse(null);
				if (stat == null || value == null || value < 0.0 || value > StatSheet.CEILING || value != Math.rint(value)) {
					bonuses = null;
					break;
				}
				bonuses.put(stat, value.intValue());
			}
		}
		if (bonuses == null) {
			onError.accept("stats.json key '" + path + "." + key + "' must map stat keys to integers from 0 to "
				+ StatSheet.CEILING + "; using the default");
			return fallback;
		}
		return Map.copyOf(bonuses);
	}

	private static double flatPerPiece(
		final JsonObject row, final String path, final double fallback, final Consumer<String> onError
	) {
		JsonElement element = row.get("flat_per_piece");
		if (element == null) {
			return fallback;
		}
		Double value = number(element);
		if (value == null || value < 0.0) {
			onError.accept("stats.json key '" + path + ".flat_per_piece' must be a number of at least 0; using the default");
			return fallback;
		}
		return value;
	}

	private static RpgConfig.DiceRules dice(final JsonObject root, final Consumer<String> onError) {
		JsonElement element = root.get(RpgConfig.DICE_KEY);
		RpgConfig.DiceRules fallback = RpgConfig.DiceRules.DEFAULT;
		if (element == null) {
			return fallback;
		}
		if (!element.isJsonObject()) {
			onError.accept("stats.json key 'dice' must be an object; using the defaults");
			return fallback;
		}
		JsonObject object = element.getAsJsonObject();
		return new RpgConfig.DiceRules(
			positive(object, "base_count", fallback.baseCount(), onError),
			positive(object, "base_sides", fallback.baseSides(), onError),
			positive(object, "count_step", fallback.countStep(), onError),
			positive(object, "sides_step", fallback.sidesStep(), onError)
		);
	}

	private static RpgConfig.ScalingRules scaling(final JsonObject root, final Consumer<String> onError) {
		RpgConfig.ScalingRules fallback = RpgConfig.ScalingRules.DEFAULT;
		JsonElement element = root.get(RpgConfig.ScalingRules.KEY);
		if (element == null) {
			return fallback;
		}
		if (!element.isJsonObject()) {
			onError.accept("stats.json key 'scaling' must be an object; using the defaults");
			return fallback;
		}
		JsonObject o = element.getAsJsonObject();
		return new RpgConfig.ScalingRules(
			scalingPart(o, "dex_max_roll_per_point", fallback.dexMaxRollPerPoint(), 0.0, onError),
			(int) scalingPart(o, "bonus_level_step", fallback.bonusLevelStep(), 1.0, onError),
			(int) scalingPart(o, "bonus_points", fallback.bonusPoints(), 0.0, onError),
			(int) scalingPart(o, "gap_level_step", fallback.gapLevelStep(), 1.0, onError),
			scalingPart(o, "gap_percent", fallback.gapPercent(), 0.0, onError)
		);
	}

	private static RpgConfig.HpRules hp(final JsonObject root, final Consumer<String> onError) {
		RpgConfig.HpRules fallback = RpgConfig.HpRules.DEFAULT;
		JsonElement element = root.get(RpgConfig.HpRules.KEY);
		if (element == null) {
			return fallback;
		}
		if (!element.isJsonObject()) {
			onError.accept("stats.json key 'hp' must be an object; using the defaults");
			return fallback;
		}
		JsonObject o = element.getAsJsonObject();
		return new RpgConfig.HpRules(
			hpPart(o, "base", fallback.base(), 1.0, onError),
			hpPart(o, "per_level", fallback.perLevel(), 0.0, onError),
			hpPart(o, "vit_percent", fallback.vitPercent(), 0.0, onError),
			(int) hpPart(o, "regen_interval", fallback.regenInterval(), 1.0, onError),
			hpPart(o, "regen_base", fallback.regenBase(), 0.0, onError),
			hpPart(o, "regen_per_vit", fallback.regenPerVit(), 0.0, onError),
			hpPart(o, "duration_int_divisor", fallback.durationIntDivisor(), 1.0, onError),
			hpPart(o, "duration_cap", fallback.durationCap(), 1.0, onError),
			jobFactors(o, fallback.jobFactors(), onError)
		);
	}

	private static Map<String, Double> jobFactors(
		final JsonObject hp, final Map<String, Double> fallback, final Consumer<String> onError
	) {
		JsonElement element = hp.get(RpgConfig.HpRules.JOB_FACTORS_KEY);
		if (element == null) {
			return fallback;
		}
		if (!element.isJsonObject()) {
			onError.accept("stats.json key 'hp.job_factors' must be an object; using the defaults");
			return fallback;
		}
		Map<String, Double> factors = new LinkedHashMap<>(fallback);
		for (String key : fallback.keySet()) {
			JsonElement entry = element.getAsJsonObject().get(key);
			if (entry == null) {
				continue;
			}
			Double value = number(entry);
			if (value == null || value <= 0.0 || value > MAX_LIMIT) {
				onError.accept("stats.json key 'hp.job_factors." + key + "' must be a number above 0 and at most "
					+ MAX_LIMIT + "; using the default");
				continue;
			}
			factors.put(key, value);
		}
		return Map.copyOf(factors);
	}

	private static double hpPart(
		final JsonObject object, final String key, final double fallback, final double min, final Consumer<String> onError
	) {
		JsonElement element = object.get(key);
		if (element == null) {
			return fallback;
		}
		Double value = number(element);
		if (value == null || value < min) {
			onError.accept("stats.json key 'hp." + key + "' must be a number of at least " + min + "; using the default");
			return fallback;
		}
		return value;
	}

	private static double scalingPart(
		final JsonObject object, final String key, final double fallback, final double min, final Consumer<String> onError
	) {
		JsonElement element = object.get(key);
		if (element == null) {
			return fallback;
		}
		Double value = number(element);
		if (value == null || value < min) {
			onError.accept("stats.json key 'scaling." + key + "' must be a number of at least " + min + "; using the default");
			return fallback;
		}
		return value;
	}

	private static RpgConfig.MagicRules magic(final JsonObject root, final Consumer<String> onError) {
		RpgConfig.MagicRules fallback = RpgConfig.MagicRules.DEFAULT;
		JsonElement element = root.get(RpgConfig.MagicRules.KEY);
		if (element == null) {
			return fallback;
		}
		if (!element.isJsonObject()) {
			onError.accept("stats.json key 'magic' must be an object; using the defaults");
			return fallback;
		}
		JsonObject o = element.getAsJsonObject();
		return new RpgConfig.MagicRules(
			magicPart(o, "spell_base", fallback.spellBase(), 0.0, onError),
			magicPart(o, "spell_growth", fallback.spellGrowth(), 0.0, onError),
			magicPart(o, "matk_per_int", fallback.matkPerInt(), 0.0, onError),
			magicPart(o, "mob_percent_per_point", fallback.mobPercentPerPoint(), 0.0, onError),
			magicPart(o, "resist_base", fallback.resistBase(), 0.0, onError),
			magicPart(o, "resist_span", fallback.resistSpan(), 0.0, onError),
			magicPart(o, "resist_midpoint", fallback.resistMidpoint(), 0.0, onError),
			magicPart(o, "resist_width", fallback.resistWidth(), Double.MIN_NORMAL, onError),
			magicPart(o, "resist_max_int", fallback.resistMaxInt(), 1.0, onError),
			magicPart(o, "resist_cap", fallback.resistCap(), 0.0, onError),
			magicPart(o, "armor_pen_base", fallback.armorPenBase(), 0.0, onError),
			magicPart(o, "armor_pen_cap", fallback.armorPenCap(), 0.0, onError)
		);
	}

	private static double magicPart(
		final JsonObject object, final String key, final double fallback, final double min, final Consumer<String> onError
	) {
		JsonElement element = object.get(key);
		if (element == null) {
			return fallback;
		}
		Double value = number(element);
		if (value == null || value < min) {
			onError.accept("stats.json key 'magic." + key + "' must be a number of at least " + min + "; using the default");
			return fallback;
		}
		return value;
	}

	private static XpCurve xpCurve(final JsonObject root, final Consumer<String> onError) {
		JsonElement element = root.get(XpCurve.KEY);
		if (element == null) {
			return XpCurve.DEFAULT;
		}
		if (!element.isJsonObject()) {
			onError.accept("stats.json key 'xp_curve' must be an object; using the defaults");
			return XpCurve.DEFAULT;
		}
		JsonObject curve = element.getAsJsonObject();
		double base = curvePart(curve, "base", XpCurve.DEFAULT.base(), 0.0, onError);
		double factor = curvePart(curve, "factor", XpCurve.DEFAULT.factor(), 0.0, onError);
		double exponent = curvePart(curve, "exponent", XpCurve.DEFAULT.exponent(), 0.0, onError);
		return new XpCurve(base, factor, exponent);
	}

	private static double curvePart(
		final JsonObject curve, final String key, final double fallback, final double min, final Consumer<String> onError
	) {
		JsonElement element = curve.get(key);
		if (element == null) {
			return fallback;
		}
		Double value = number(element);
		if (value == null || !Double.isFinite(value) || value < min) {
			onError.accept("stats.json key 'xp_curve." + key + "' must be a number of at least " + min + "; using the default");
			return fallback;
		}
		return value;
	}

	private static double mobDiceShare(final JsonObject root, final Consumer<String> onError) {
		JsonElement element = root.get(RpgConfig.MOB_DICE_SHARE_KEY);
		if (element == null) {
			return RpgConfig.DEFAULT_MOB_DICE_SHARE;
		}
		Double value = number(element);
		if (value == null || value < 0.0 || value > 1.0) {
			onError.accept("stats.json key 'mob_dice_share' must be a number from 0 to 1; using the default");
			return RpgConfig.DEFAULT_MOB_DICE_SHARE;
		}
		return value;
	}

	private static double mobAmplifier(final JsonObject root, final Consumer<String> onError) {
		JsonElement element = root.get(RpgConfig.MOB_AMPLIFIER_KEY);
		if (element == null) {
			return RpgConfig.DEFAULT_MOB_AMPLIFIER;
		}
		Double value = number(element);
		if (value == null || value < 0.0) {
			onError.accept("stats.json key 'mob_amplifier' must be a number of at least 0; using the default");
			return RpgConfig.DEFAULT_MOB_AMPLIFIER;
		}
		return value;
	}

	private static Map<String, Map<Stat, Double>> mobStats(final JsonObject root, final Consumer<String> onError) {
		JsonElement element = root.get(RpgConfig.MOB_STATS_KEY);
		Map<String, Map<Stat, Double>> defaults = RpgConfig.DEFAULT_MOB_STATS;
		if (element == null) {
			return defaults;
		}
		if (!element.isJsonObject()) {
			onError.accept("stats.json key 'mob_stats' must be an object; using the defaults");
			return defaults;
		}
		JsonObject object = element.getAsJsonObject();
		Map<String, Map<Stat, Double>> table = new LinkedHashMap<>();
		for (Map.Entry<String, Map<Stat, Double>> role : defaults.entrySet()) {
			JsonElement row = object.get(role.getKey());
			if (row != null && !row.isJsonObject()) {
				onError.accept("stats.json key 'mob_stats." + role.getKey() + "' must be an object; using the defaults");
			}
			JsonObject rowObject = row != null && row.isJsonObject() ? row.getAsJsonObject() : new JsonObject();
			Map<Stat, Double> weights = new EnumMap<>(Stat.class);
			for (Stat stat : Stat.values()) {
				weights.put(stat, weight(rowObject, role.getKey(), stat, role.getValue().get(stat), onError));
			}
			table.put(role.getKey(), Map.copyOf(weights));
		}
		return Map.copyOf(table);
	}

	private static double weight(
		final JsonObject row, final String role, final Stat stat, final double fallback, final Consumer<String> onError
	) {
		JsonElement element = row.get(stat.key());
		if (element == null) {
			return fallback;
		}
		Double value = number(element);
		if (value == null || value < 0.0 || value > RpgConfig.MAX_MOB_BASE) {
			onError.accept("stats.json key 'mob_stats." + role + "." + stat.key()
				+ "' must be a number from 0 to " + RpgConfig.MAX_MOB_BASE + "; using the default");
			return fallback;
		}
		return value;
	}

	private static int positive(
		final JsonObject object, final String key, final int fallback, final Consumer<String> onError
	) {
		JsonElement element = object.get(key);
		if (element == null) {
			return fallback;
		}
		Double value = number(element);
		if (value == null || value < 1.0 || value > MAX_DICE_VALUE || value != Math.rint(value)) {
			onError.accept("stats.json key 'dice." + key + "' must be an integer from 1 to "
				+ MAX_DICE_VALUE + "; using the default");
			return fallback;
		}
		return value.intValue();
	}

	private static RpgConfig.Curve curve(
		final JsonObject root, final RpgConfig.Effect effect, final Consumer<String> onError
	) {
		JsonElement element = root.get(effect.key());
		if (element == null) {
			return effect.defaultCurve();
		}
		Double max = element.isJsonObject() ? number(element.getAsJsonObject().get("max")) : null;
		Double half = element.isJsonObject() ? number(element.getAsJsonObject().get("half")) : null;
		if (max == null || half == null || max < 0.0 || max > MAX_LIMIT || half <= 0.0) {
			onError.accept("stats.json key '" + effect.key()
				+ "' needs max in [0, " + MAX_LIMIT + "] and half above 0; using the default");
			return effect.defaultCurve();
		}
		return new RpgConfig.Curve(max, half);
	}

	private static double critMultiplier(final JsonObject root, final Consumer<String> onError) {
		JsonElement element = root.get(RpgConfig.CRIT_MULTIPLIER_KEY);
		if (element == null) {
			return RpgConfig.DEFAULT_CRIT_MULTIPLIER;
		}
		Double value = number(element);
		if (value == null || value < 1.0) {
			onError.accept("stats.json key '" + RpgConfig.CRIT_MULTIPLIER_KEY
				+ "' must be a number of at least 1; using the default");
			return RpgConfig.DEFAULT_CRIT_MULTIPLIER;
		}
		return value;
	}

	private static double healthScale(final JsonObject root, final Consumer<String> onError) {
		JsonElement element = root.get(RpgConfig.HEALTH_SCALE_KEY);
		if (element == null) {
			return RpgConfig.DEFAULT_HEALTH_SCALE;
		}
		Double value = number(element);
		if (value == null || value < 1.0 || value > MAX_HEALTH_SCALE) {
			onError.accept("stats.json key '" + RpgConfig.HEALTH_SCALE_KEY + "' must be a number from 1 to "
				+ MAX_HEALTH_SCALE + "; using the default");
			return RpgConfig.DEFAULT_HEALTH_SCALE;
		}
		return value;
	}

	private static Double number(final JsonElement element) {
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return null;
		}
		double value = element.getAsDouble();
		return Double.isFinite(value) ? value : null;
	}

	private static Map<String, Integer> statKeys(final Map<Stat, Integer> bonuses) {
		Map<String, Integer> keyed = new LinkedHashMap<>();
		for (Stat stat : Stat.values()) {
			if (bonuses.containsKey(stat)) {
				keyed.put(stat.key(), bonuses.get(stat));
			}
		}
		return keyed;
	}

	private static Map<String, Object> weaponRollsJson(final RpgConfig.WeaponRolls rolls) {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put(RpgConfig.COUNT_WEIGHTS_KEY, rolls.countWeights());
		out.put(RpgConfig.AFFIX_CHANCE_KEY, rolls.affixChance());
		for (Map.Entry<String, RpgConfig.RollTier> tier : rolls.tiers().entrySet()) {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("items", tier.getValue().items().stream().map(Identifier::toString).toList());
			row.put("armor_items", tier.getValue().armorItems().stream().map(Identifier::toString).toList());
			row.put("min", tier.getValue().min());
			row.put("max", tier.getValue().max());
			Map<String, List<Double>> affixes = new LinkedHashMap<>();
			for (Affix affix : Affix.values()) {
				RpgConfig.AffixRange range = tier.getValue().affixes().get(affix);
				affixes.put(affix.key(), List.of(range.min(), range.max()));
			}
			row.put(RpgConfig.AFFIXES_KEY, affixes);
			out.put(tier.getKey(), row);
		}
		return out;
	}

	private static Map<String, Object> materialRollsJson(final RpgConfig.MaterialRolls rolls) {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("craft_random_roll", rolls.craftRandomRoll());
		out.put("seed", rolls.seed());
		out.put("affix_share", rolls.affixShare());
		out.put("affix_scale", rolls.affixScale());
		out.put("depth_rank_weight", rolls.depthRankWeight());
		out.put("depth_enchant_bonus", rolls.depthEnchantBonus());
		out.put("second_enchant_chance", rolls.secondEnchantChance());
		for (Map.Entry<String, RpgConfig.MaterialTier> tier : rolls.tiers().entrySet()) {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("items", tier.getValue().items().stream().map(Identifier::toString).toList());
			row.put("min", tier.getValue().min());
			row.put("max", tier.getValue().max());
			row.put(RpgConfig.COUNT_WEIGHTS_KEY, tier.getValue().countWeights());
			row.put("profiles", tier.getValue().profiles());
			row.put("enchant_chance", tier.getValue().enchantChance());
			row.put("explicit", tier.getValue().explicit());
			out.put(tier.getKey(), row);
		}
		return out;
	}

	private static void writeDefaults(final Path path, final Consumer<String> onError) {
		Map<String, Object> out = new LinkedHashMap<>();
		for (RpgConfig.Effect effect : RpgConfig.Effect.values()) {
			Map<String, Double> curve = new LinkedHashMap<>();
			curve.put("max", effect.defaultCurve().max());
			curve.put("half", effect.defaultCurve().half());
			out.put(effect.key(), curve);
		}
		out.put(RpgConfig.CRIT_MULTIPLIER_KEY, RpgConfig.DEFAULT_CRIT_MULTIPLIER);
		out.put(RpgConfig.HEALTH_SCALE_KEY, RpgConfig.DEFAULT_HEALTH_SCALE);
		RpgConfig.DiceRules rules = RpgConfig.DiceRules.DEFAULT;
		Map<String, Integer> dice = new LinkedHashMap<>();
		dice.put("base_count", rules.baseCount());
		dice.put("base_sides", rules.baseSides());
		dice.put("count_step", rules.countStep());
		dice.put("sides_step", rules.sidesStep());
		out.put(RpgConfig.DICE_KEY, dice);
		Map<String, Map<String, Double>> weights = new LinkedHashMap<>();
		for (Map.Entry<String, Map<Stat, Double>> role : RpgConfig.DEFAULT_MOB_STATS.entrySet()) {
			Map<String, Double> row = new LinkedHashMap<>();
			for (Stat stat : Stat.values()) {
				row.put(stat.key(), role.getValue().get(stat));
			}
			weights.put(role.getKey(), row);
		}
		out.put(RpgConfig.MOB_STATS_KEY, weights);
		out.put(RpgConfig.MOB_AMPLIFIER_KEY, RpgConfig.DEFAULT_MOB_AMPLIFIER);
		out.put(RpgConfig.MOB_DICE_SHARE_KEY, RpgConfig.DEFAULT_MOB_DICE_SHARE);
		out.put(XpCurve.KEY, XpCurve.DEFAULT.toJson());
		out.put(RpgConfig.MagicRules.KEY, RpgConfig.MagicRules.DEFAULT.toJson());
		out.put(RpgConfig.ScalingRules.KEY, RpgConfig.ScalingRules.DEFAULT.toJson());
		out.put(RpgConfig.HpRules.KEY, RpgConfig.HpRules.DEFAULT.toJson());
		StatTables.write(out);
		Map<String, Map<String, Object>> armorSets = new LinkedHashMap<>();
		for (Map.Entry<String, RpgConfig.ArmorSet> material : RpgConfig.DEFAULT_ARMOR_SETS.entrySet()) {
			RpgConfig.ArmorSet set = material.getValue();
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("items", set.items().stream().map(Identifier::toString).toList());
			row.put("per_piece", statKeys(set.perPiece()));
			row.put("flat_per_piece", set.flatPerPiece());
			row.put("set_bonus", statKeys(set.setBonus()));
			armorSets.put(material.getKey(), row);
		}
		out.put(RpgConfig.ARMOR_SETS_KEY, armorSets);
		out.put(RpgConfig.WEAPON_ROLLS_KEY, weaponRollsJson(RpgConfig.DEFAULT_WEAPON_ROLLS));
		out.put(RpgConfig.MATERIAL_ROLLS_KEY, materialRollsJson(RpgConfig.DEFAULT_MATERIAL_ROLLS));
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(out) + System.lineSeparator(), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			onError.accept("Cannot write " + path + ": " + exception.getMessage());
		}
	}
}
