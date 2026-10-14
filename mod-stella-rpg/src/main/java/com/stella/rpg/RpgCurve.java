package com.stella.rpg;

import com.stella.rpg.RpgConfig.Curve;
import com.stella.rpg.RpgConfig.Effect;

public final class RpgCurve {
	private RpgCurve() {
	}

	public static double saturate(final int stat, final double max, final double half) {
		return max * stat / (stat + half);
	}

	private static double value(final Effect effect, final int stat) {
		Curve curve = RpgConfigStore.current().curve(effect);
		return saturate(stat, curve.max(), curve.half());
	}

	public static double damageTakenMultiplier(final int vit) {
		return 1.0 - Math.min(1.0, value(Effect.DAMAGE_REDUCTION, vit) * StatMilestones.factor(vit));
	}

	public static double attackSpeedBonus(final int agi) {
		return value(Effect.ATTACK_SPEED, agi) * StatMilestones.factor(agi);
	}

	public static double critChance(final int luk) {
		return value(Effect.CRIT_CHANCE, luk) * StatMilestones.factor(luk);
	}

	public static double critMultiplier() {
		return RpgConfigStore.current().critMultiplier();
	}

	/** The chance that one physical die rolls its maximum: a share per DEX, at most 100 percent. */
	public static double dexMaxRollChance(final int dex) {
		return Math.min(1.0, Math.max(0, dex) * RpgConfigStore.current().scaling().dexMaxRollPerPoint() * StatMilestones.factor(dex));
	}

	/** The INT share of magic resistance: base plus the span times the logistic, at most the cap. */
	public static double magicResistance(final int intel) {
		RpgConfig.MagicRules rules = RpgConfigStore.current().magic();
		double x = Math.max(0.0, Math.min(rules.resistMaxInt(), intel));
		double low = logistic(0.0, rules);
		double span = logistic(rules.resistMaxInt(), rules) - low;
		double share = span > 0.0 ? (logistic(x, rules) - low) / span : 0.0;
		return Math.min(rules.resistCap(), rules.resistBase() + rules.resistSpan() * share);
	}

	private static double logistic(final double x, final RpgConfig.MagicRules rules) {
		return 1.0 / (1.0 + Math.exp(-(x - rules.resistMidpoint()) / rules.resistWidth()));
	}

	/** The multiplier on a critical hit taken. */
	public static double critDamageTakenMultiplier(final int luk) {
		return 1.0 - value(Effect.CRIT_REDUCTION, luk);
	}

	/** The most a cooldown falls, and the INT plus DEX at which it falls half of that. */
	public static final double COOLDOWN_MAX = 0.80;
	public static final double COOLDOWN_HALF = 200.0;

	/** The skill and spell cooldown multiplier for INT plus DEX: {@code 1 - 0.8 * s / (s + 200)}. It is fixed in code. */
	public static double cooldownMultiplier(final int intelAndDex) {
		return 1.0 - saturate(Math.max(0, intelAndDex), COOLDOWN_MAX, COOLDOWN_HALF);
	}
}
