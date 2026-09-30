package com.stella.rpg;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RpgConfig(
	Map<Effect, Curve> curves, double critMultiplier, DiceRules dice, Map<String, Map<Stat, Double>> mobWeights
) {
	public static final double DEFAULT_CRIT_MULTIPLIER = 1.5;
	public static final String CRIT_MULTIPLIER_KEY = "crit_multiplier";
	public static final String DICE_KEY = "dice";
	public static final String MOB_WEIGHTS_KEY = "mob_weights";
	public static final double MAX_MOB_WEIGHT = 100.0;
	public static final Map<String, Map<Stat, Double>> DEFAULT_MOB_WEIGHTS = defaultMobWeights();
	public static final List<String> REMOVED_KEYS = List.of("melee_damage", "ranged_damage");

	public record Curve(double max, double half) {
	}

	public record DiceRules(int baseCount, int baseSides, int countStep, int sidesStep) {
		public static final DiceRules DEFAULT = new DiceRules(1, 4, 50, 100);
	}

	public enum Effect {
		ATTACK_SPEED("attack_speed", 1.00, 250),
		BOW_DRAW("bow_draw", 0.50, 250),
		DAMAGE_REDUCTION("damage_reduction", 0.60, 300),
		SPELL_POWER("spell_power", 1.50, 200),
		COOLDOWN_REDUCTION("cooldown_reduction", 0.40, 300),
		CRIT_CHANCE("crit_chance", 0.50, 300);

		private final String key;
		private final Curve defaultCurve;

		Effect(final String key, final double max, final double half) {
			this.key = key;
			this.defaultCurve = new Curve(max, half);
		}

		public String key() {
			return this.key;
		}

		public Curve defaultCurve() {
			return this.defaultCurve;
		}
	}

	public static RpgConfig defaults() {
		Map<Effect, Curve> curves = new EnumMap<>(Effect.class);
		for (Effect effect : Effect.values()) {
			curves.put(effect, effect.defaultCurve());
		}
		return new RpgConfig(Map.copyOf(curves), DEFAULT_CRIT_MULTIPLIER, DiceRules.DEFAULT, DEFAULT_MOB_WEIGHTS);
	}

	private static Map<String, Map<Stat, Double>> defaultMobWeights() {
		Map<String, Map<Stat, Double>> table = new LinkedHashMap<>();
		table.put("melee", weights(1.00, 0.50, 1.00, 0.25, 0.25, 0.50));
		table.put("ranged", weights(0.25, 1.00, 0.50, 1.00, 0.25, 0.50));
		table.put("magic", weights(0.25, 0.50, 0.50, 0.25, 1.00, 0.50));
		return Map.copyOf(table);
	}

	private static Map<Stat, Double> weights(final double... values) {
		Map<Stat, Double> row = new EnumMap<>(Stat.class);
		for (Stat stat : Stat.values()) {
			row.put(stat, values[stat.ordinal()]);
		}
		return Map.copyOf(row);
	}

	public Curve curve(final Effect effect) {
		return this.curves.get(effect);
	}
}
