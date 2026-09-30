package com.stella.rpg;

import java.util.EnumMap;
import java.util.Map;

public record RpgConfig(Map<Effect, Curve> curves, double critMultiplier) {
	public static final double DEFAULT_CRIT_MULTIPLIER = 1.5;
	public static final String CRIT_MULTIPLIER_KEY = "crit_multiplier";

	public record Curve(double max, double half) {
	}

	public enum Effect {
		MELEE_DAMAGE("melee_damage", 1.50, 200),
		ATTACK_SPEED("attack_speed", 1.00, 250),
		BOW_DRAW("bow_draw", 0.50, 250),
		DAMAGE_REDUCTION("damage_reduction", 0.60, 300),
		RANGED_DAMAGE("ranged_damage", 1.50, 200),
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
		return new RpgConfig(Map.copyOf(curves), DEFAULT_CRIT_MULTIPLIER);
	}

	public Curve curve(final Effect effect) {
		return this.curves.get(effect);
	}
}
