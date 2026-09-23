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

	private static void warn(final String message) {
		StellaEquips.LOGGER.warn("Power config: {}", message);
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
			int version = intValue(object, "version", 0, PowerConfig::version);
			int levelCap = intValueAtLeast(object, "level_cap", 1, PowerConfig::levelCap);

			JsonObject points = enter("points_per_level");
			PowerConfig.PointsPerLevel pointsPerLevel = new PowerConfig.PointsPerLevel(
				longValueAtLeast(points, "base", 0L, config -> config.pointsPerLevel().base()),
				longValueAtLeast(points, "per_level", 0L, config -> config.pointsPerLevel().perLevel())
			);

			JsonObject distance = enter("distance");
			PowerConfig.Distance distanceSpec = new PowerConfig.Distance(
				doubleValueAbove(distance, "blocks_per_point", 0.0, config -> config.distance().blocksPerPoint()),
				longValueAtLeast(distance, "max_points", 0L, config -> config.distance().maxPoints())
			);

			JsonObject depth = enter("depth");
			PowerConfig.Depth depthSpec = new PowerConfig.Depth(
				doubleValueAbove(depth, "blocks_per_point", 0.0, config -> config.depth().blocksPerPoint()),
				doubleValue(depth, "min_y", 0.0, config -> config.depth().minY()),
				longValueAtLeast(depth, "max_points", 0L, config -> config.depth().maxPoints())
			);

			JsonObject kills = enter("kills");
			PowerConfig.Kills killsSpec = new PowerConfig.Kills(
				longValueAtLeast(kills, "points_per_kill", 0L, config -> config.kills().pointsPerKill()),
				longValueAtLeast(kills, "points_per_boss", 0L, config -> config.kills().pointsPerBoss()),
				identifiers(kills, "boss_types", config -> config.kills().bossTypes())
			);

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

			JsonObject xp = enter("xp_multiplier");
			PowerConfig.XpMultiplier xpMultiplier = new PowerConfig.XpMultiplier(
				doubleValue(xp, "even_fight", 0.0, config -> config.xpMultiplier().evenFight()),
				doubleValueAtLeast(xp, "per_level_gap", 0.0, config -> config.xpMultiplier().perLevelGap()),
				doubleValue(xp, "min", 0.0, config -> config.xpMultiplier().min()),
				doubleValue(xp, "max", 0.0, config -> config.xpMultiplier().max())
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

			JsonObject distanceAttributes = enter("attributes.distance");
			PowerConfig.Survivability survivability = new PowerConfig.Survivability(
				doubleValueAtLeast(
					distanceAttributes, "max_health_per_point", 0.0,
					config -> config.attributes().distance().maxHealthPerPoint()
				),
				doubleValueAtLeast(
					distanceAttributes, "armor_per_level", 0.0,
					config -> config.attributes().distance().armorPerLevel()
				),
				doubleValueAtLeast(
					distanceAttributes, "knockback_resistance_per_level", 0.0,
					config -> config.attributes().distance().knockbackResistancePerLevel()
				)
			);

			JsonObject combatAttributes = enter("attributes.combat");
			PowerConfig.Combat combat = new PowerConfig.Combat(
				doubleValueAtLeast(
					combatAttributes, "attack_damage_max", 0.0, config -> config.attributes().combat().attackDamageMax()
				),
				doubleValueAtLeast(
					combatAttributes, "attack_speed_max", 0.0, config -> config.attributes().combat().attackSpeedMax()
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
				pointsPerLevel,
				distanceSpec,
				depthSpec,
				killsSpec,
				mobSpec,
				xpMultiplier,
				bandSpec,
				trackerSpec,
				new PowerConfig.Attributes(survivability, combat),
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

		private Set<Identifier> identifiers(
			final @Nullable JsonObject parent, final String key, final Function<PowerConfig, Set<Identifier>> fromFallback
		) {
			JsonElement element = value(parent, key);
			if (element == null || !element.isJsonArray()) {
				bad(parent, key);
				return this.fallback == null ? Set.of() : fromFallback.apply(this.fallback);
			}

			Set<Identifier> found = new LinkedHashSet<>();
			for (JsonElement entry : element.getAsJsonArray()) {
				Identifier id = identifier(entry);
				if (id == null) {
					problem("skipping an entry in '" + this.path + "." + key + "': it is not an identifier");
					continue;
				}

				found.add(id);
			}

			return found;
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

	private static @Nullable Identifier identifier(final @Nullable JsonElement element) {
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			return null;
		}

		return Identifier.tryParse(element.getAsString());
	}
}
