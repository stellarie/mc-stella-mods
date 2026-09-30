package com.stella.rpg;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;

public final class RpgConfigStore {
	public static final double MAX_LIMIT = 10.0;
	public static final int MAX_DICE_VALUE = 1000;

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
		return new RpgConfig(Map.copyOf(curves), critMultiplier(root, onError), dice(root, onError));
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

	private static Double number(final JsonElement element) {
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return null;
		}
		double value = element.getAsDouble();
		return Double.isFinite(value) ? value : null;
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
		RpgConfig.DiceRules rules = RpgConfig.DiceRules.DEFAULT;
		Map<String, Integer> dice = new LinkedHashMap<>();
		dice.put("base_count", rules.baseCount());
		dice.put("base_sides", rules.baseSides());
		dice.put("count_step", rules.countStep());
		dice.put("sides_step", rules.sidesStep());
		out.put(RpgConfig.DICE_KEY, dice);
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(out) + System.lineSeparator(), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			onError.accept("Cannot write " + path + ": " + exception.getMessage());
		}
	}
}
