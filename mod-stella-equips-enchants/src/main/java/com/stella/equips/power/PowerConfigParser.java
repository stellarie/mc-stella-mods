package com.stella.equips.power;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.stella.equips.StellaEquips;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StrictJsonParser;
import org.jspecify.annotations.Nullable;

public final class PowerConfigParser {
	private PowerConfigParser() {
	}

	public static Optional<PowerConfig> parseComplete(final String json) {
		Reader reader = reader(json, null);
		if (reader == null) {
			return Optional.empty();
		}

		PowerConfig config = reader.build();
		return reader.problems.isEmpty() ? Optional.of(config) : Optional.empty();
	}

	public static PowerConfig parse(final String json, final PowerConfig fallback) {
		Reader reader = reader(json, fallback);
		return reader == null ? fallback : reader.build();
	}

	private static @Nullable Reader reader(final String json, final @Nullable PowerConfig fallback) {
		try {
			JsonElement element = StrictJsonParser.parse(json);
			if (!element.isJsonObject()) {
				warn("the document is not a JSON object");
				return null;
			}

			return new Reader(element.getAsJsonObject(), fallback);
		} catch (JsonSyntaxException | IllegalStateException e) {
			warn("the document is not valid JSON: " + e.getMessage());
			return null;
		}
	}

	private static volatile java.util.List<String> ignored = java.util.List.of();

	/** The keys the last parse did not know, so a test can name them. */
	public static java.util.List<String> ignoredKeys() {
		return ignored;
	}

	private static final java.util.Set<String> KNOWN_KEYS = java.util.Set.of(
		"_note", "version", "level_cap",
		"mob_level.blocks_per_level", "mob_level.cap", "mob_level.depth_bonus_cap",
		"mob_level.depth_bonus_blocks", "mob_level.depth_reference_y", "mob_level.level_cap",
		"mob_level.other_dimension_scale", "mob_level.dimension_scale",
		"bands.red_above", "bands.yellow_above", "bands.orange_above", "bands.green_within",
		"tracker.scan_interval_ticks", "tracker.heartbeat_ticks",
		"attributes.health_base", "attributes.health_per_level",
		"readout.enabled_by_default", "readout.radius", "readout.max_per_player",
		"readout.height_offset", "readout.view_range"
	);

	/** A key this build does not know is a key an older file wins with. Name them. */
	private static void reportUnknownKeys(final JsonObject object) {
		java.util.List<String> unknown = new java.util.ArrayList<>();
		collectUnknown("", object, unknown);
		ignored = java.util.List.copyOf(unknown);
		if (!unknown.isEmpty()) {
			warn("ignoring " + unknown.size() + " key(s) this build does not know, so the bundled value wins: "
				+ String.join(", ", unknown));
		}
	}

	/** A stale file nests sections, so walk to the leaves and name each one by its full path. */
	private static void collectUnknown(
		final String prefix, final JsonObject object, final java.util.List<String> unknown
	) {
		for (String key : object.keySet()) {
			String path = prefix + key;
			if (!(object.get(key) instanceof JsonObject nested)) {
				if (!known(path)) {
					unknown.add(path);
				}

				continue;
			}

			if (nested.isEmpty() && !known(path)) {
				unknown.add(path);
				continue;
			}

			collectUnknown(path + ".", nested, unknown);
		}
	}

	/** A dimension scale holds one entry per dimension id, so its children are known by prefix. */
	private static boolean known(final String path) {
		if (KNOWN_KEYS.contains(path) || path.startsWith("mob_level.dimension_scale.")) {
			return true;
		}

		String section = path + ".";

		for (String key : KNOWN_KEYS) {
			if (key.startsWith(section)) {
				return true;
			}
		}

		return false;
	}

	private static void warn(final String message) {
		StellaEquips.LOGGER.warn("Power config: {}", message);
		PowerLog.recorder().configProblem(message);
	}

	private static final class Reader {
		private final JsonObject root;
		private final @Nullable PowerConfig fallback;
		private final List<String> problems = new ArrayList<>();
		private String path = "the document";

		private Reader(final JsonObject root, final @Nullable PowerConfig fallback) {
			this.root = root;
			this.fallback = fallback;
		}

		private PowerConfig build() {
			JsonObject object = this.root;
			reportUnknownKeys(object);
			int version = intValue(object, "version", 0, PowerConfig::version);
			int levelCap = intValueAtLeast(object, "level_cap", 1, PowerConfig::levelCap);

			JsonObject mob = enter("mob_level");
			PowerConfig.MobLevel mobSpec = new PowerConfig.MobLevel(
				doubleValueAbove(mob, "blocks_per_level", 0.0, config -> config.mobLevel().blocksPerLevel()),
				intValueAtLeast(mob, "cap", 0, config -> config.mobLevel().cap()),
				intValueAtLeast(mob, "depth_bonus_cap", 0, config -> config.mobLevel().depthBonusCap()),
				doubleValueAbove(mob, "depth_bonus_blocks", 0.0, config -> config.mobLevel().depthBonusBlocks()),
				doubleValue(mob, "depth_reference_y", 0.0, config -> config.mobLevel().depthReferenceY()),
				intValueAtLeast(mob, "level_cap", 0, config -> config.mobLevel().levelCap()),
				doubleValueAbove(mob, "other_dimension_scale", 0.0, config -> config.mobLevel().otherDimensionScale()),
				scaleMap(mob, "dimension_scale", config -> config.mobLevel().dimensionScale())
			);

			JsonObject bands = enter("bands");
			PowerConfig.Bands bandSpec = new PowerConfig.Bands(
				intValueAtLeast(bands, "red_above", 0, config -> config.bands().redAbove()),
				intValueAtLeast(bands, "yellow_above", 0, config -> config.bands().yellowAbove()),
				intValueAtLeast(bands, "orange_above", 0, config -> config.bands().orangeAbove()),
				intValueAtLeast(bands, "green_within", 0, config -> config.bands().greenWithin())
			);

			JsonObject tracker = enter("tracker");
			PowerConfig.Tracker trackerSpec = new PowerConfig.Tracker(
				intValueAtLeast(tracker, "scan_interval_ticks", 1, config -> config.tracker().scanIntervalTicks()),
				intValueAtLeast(tracker, "heartbeat_ticks", 1, config -> config.tracker().heartbeatTicks())
			);

			JsonObject attributes = enter("attributes");
			PowerConfig.Attributes attributeSpec = new PowerConfig.Attributes(
				doubleValueAtLeast(
					attributes, "health_base", 0.0, config -> config.attributes().healthBase()
				),
				doubleValueAtLeast(
					attributes, "health_per_level", 0.0, config -> config.attributes().healthPerLevel()
				)
			);

			JsonObject readout = enter("readout");
			PowerConfig.Readout readoutSpec = new PowerConfig.Readout(
				booleanValue(readout, "enabled_by_default", true, config -> config.readout().enabledByDefault()),
				doubleValueAbove(readout, "radius", 0.0, config -> config.readout().radius()),
				intValueAtLeast(readout, "max_per_player", 0, config -> config.readout().maxPerPlayer()),
				doubleValueAtLeast(readout, "height_offset", 0.0, config -> config.readout().heightOffset()),
				doubleValueAbove(readout, "view_range", 0.0, config -> config.readout().viewRange())
			);

			return new PowerConfig(
				version,
				levelCap,
				mobSpec,
				bandSpec,
				trackerSpec,
				attributeSpec,
				readoutSpec
			);
		}

		private @Nullable JsonObject enter(final String sectionPath) {
			this.path = sectionPath;
			JsonObject current = this.root;

			for (String part : sectionPath.split("\\.")) {
				JsonElement element = current.get(part);
				if (element == null || !element.isJsonObject()) {
					this.problem("the '" + sectionPath + "' section is missing or is not a JSON object");
					return null;
				}

				current = element.getAsJsonObject();
			}

			return current;
		}

		private int intValue(
			final @Nullable JsonObject parent, final String key, final int neutral, final ToIntFunction<PowerConfig> fromFallback
		) {
			JsonElement element = value(parent, key);
			if (element == null || !isNumber(element)) {
				return missingInt(parent, key, neutral, fromFallback);
			}

			return element.getAsInt();
		}

		private int intValueAtLeast(
			final @Nullable JsonObject parent,
			final String key,
			final int minimum,
			final ToIntFunction<PowerConfig> fromFallback
		) {
			JsonElement element = value(parent, key);
			if (element == null || !isNumber(element)) {
				return missingInt(parent, key, 0, fromFallback);
			}

			int value = element.getAsInt();
			if (value < minimum) {
				problem("'" + this.path + "." + key + "' is below " + minimum + ": " + value);
				return fallbackInt(0, fromFallback);
			}

			return value;
		}

		private long longValueAtLeast(
			final @Nullable JsonObject parent,
			final String key,
			final long minimum,
			final ToLongFunction<PowerConfig> fromFallback
		) {
			JsonElement element = value(parent, key);
			if (element == null || !isNumber(element)) {
				bad(parent, key);
				return this.fallback == null ? 0L : fromFallback.applyAsLong(this.fallback);
			}

			long value = element.getAsLong();
			if (value < minimum) {
				problem("'" + this.path + "." + key + "' is below " + minimum + ": " + value);
				return this.fallback == null ? 0L : fromFallback.applyAsLong(this.fallback);
			}

			return value;
		}

		private double doubleValue(
			final @Nullable JsonObject parent, final String key, final double neutral, final ToDoubleFunction<PowerConfig> fromFallback
		) {
			JsonElement element = value(parent, key);
			if (element == null || !isNumber(element) || !Double.isFinite(element.getAsDouble())) {
				bad(parent, key);
				return this.fallback == null ? neutral : fromFallback.applyAsDouble(this.fallback);
			}

			return element.getAsDouble();
		}

		private boolean booleanValue(
			final @Nullable JsonObject parent,
			final String key,
			final boolean neutral,
			final Predicate<PowerConfig> fromFallback
		) {
			JsonElement element = value(parent, key);
			if (!isBoolean(element)) {
				badBoolean(parent, key);
				return this.fallback == null ? neutral : fromFallback.test(this.fallback);
			}

			return element.getAsBoolean();
		}

		private void badBoolean(final @Nullable JsonObject parent, final String key) {
			if (parent == null) {
				return;
			}

			problem("'" + this.path + "." + key + "' is missing or is not a boolean");
		}

		private double doubleValueAtLeast(
			final @Nullable JsonObject parent,
			final String key,
			final double minimum,
			final ToDoubleFunction<PowerConfig> fromFallback
		) {
			JsonElement element = value(parent, key);
			if (element == null || !isNumber(element) || !Double.isFinite(element.getAsDouble())) {
				bad(parent, key);
				return this.fallback == null ? 0.0 : fromFallback.applyAsDouble(this.fallback);
			}

			double value = element.getAsDouble();
			if (value < minimum) {
				problem("'" + this.path + "." + key + "' is below " + minimum + ": " + value);
				return this.fallback == null ? 0.0 : fromFallback.applyAsDouble(this.fallback);
			}

			return value;
		}

		private double doubleValueAbove(
			final @Nullable JsonObject parent,
			final String key,
			final double exclusiveMinimum,
			final ToDoubleFunction<PowerConfig> fromFallback
		) {
			JsonElement element = value(parent, key);
			if (element == null || !isNumber(element) || !Double.isFinite(element.getAsDouble())) {
				bad(parent, key);
				return this.fallback == null ? 0.0 : fromFallback.applyAsDouble(this.fallback);
			}

			double value = element.getAsDouble();
			if (!(value > exclusiveMinimum)) {
				problem("'" + this.path + "." + key + "' is not above " + exclusiveMinimum + ": " + value);
				return this.fallback == null ? 0.0 : fromFallback.applyAsDouble(this.fallback);
			}

			return value;
		}

		private Map<Identifier, Double> scaleMap(
			final @Nullable JsonObject parent, final String key, final Function<PowerConfig, Map<Identifier, Double>> fromFallback
		) {
			JsonElement element = value(parent, key);
			if (element == null || !element.isJsonObject()) {
				bad(parent, key);
				return this.fallback == null ? Map.of() : fromFallback.apply(this.fallback);
			}

			Map<Identifier, Double> found = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
				Identifier id = Identifier.tryParse(entry.getKey());
				if (id == null || !isNumber(entry.getValue())) {
					problem("skipping '" + this.path + "." + key + "." + entry.getKey() + "': it is not a number under an identifier");
					continue;
				}

				double scale = entry.getValue().getAsDouble();
				if (!(scale > 0.0)) {
					problem("skipping '" + this.path + "." + key + "." + entry.getKey() + "': the scale is not above 0");
					continue;
				}

				found.put(id, scale);
			}

			return found;
		}

		private int missingInt(
			final @Nullable JsonObject parent, final String key, final int neutral, final ToIntFunction<PowerConfig> fromFallback
		) {
			this.bad(parent, key);
			return fallbackInt(neutral, fromFallback);
		}

		private int fallbackInt(final int neutral, final ToIntFunction<PowerConfig> fromFallback) {
			return this.fallback == null ? neutral : fromFallback.applyAsInt(this.fallback);
		}

		private @Nullable JsonElement value(final @Nullable JsonObject parent, final String key) {
			return parent == null ? null : parent.get(key);
		}

		private void bad(final @Nullable JsonObject parent, final String key) {
			if (parent == null) {
				return;
			}

			problem("'" + this.path + "." + key + "' is missing or is not a number");
		}

		private void problem(final String message) {
			this.problems.add(message);
			warn(message);
		}
	}

	private static boolean isNumber(final @Nullable JsonElement element) {
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber();
	}

	private static boolean isBoolean(final @Nullable JsonElement element) {
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean();
	}
}
