package com.stella.rpg;

import com.stella.rpg.RpgConfig.Curve;
import com.stella.rpg.RpgConfig.Effect;

public final class RpgCurve {
	private static final int VANILLA_DRAW_TICKS = 20;

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
		return 1.0 - value(Effect.DAMAGE_REDUCTION, vit);
	}

	public static double attackSpeedBonus(final int agi) {
		return value(Effect.ATTACK_SPEED, agi);
	}

	public static int fullDrawTicks(final int agi) {
		long ticks = Math.round(VANILLA_DRAW_TICKS * (1.0 - value(Effect.BOW_DRAW, agi)));
		return (int) Math.max(1, Math.min(VANILLA_DRAW_TICKS, ticks));
	}

	public static double critChance(final int luk) {
		return value(Effect.CRIT_CHANCE, luk);
	}

	public static double critMultiplier() {
		return RpgConfigStore.current().critMultiplier();
	}

	/** The chance that one physical die rolls its maximum. */
	public static double dexMaxRollChance(final int dex) {
		return value(Effect.DEX_MAX_ROLL, dex);
	}

	/** The chance that a MATK roll is the maximum. */
	public static double intMaxRollChance(final int intel) {
		return value(Effect.INT_MAX_ROLL, intel);
	}

	/** The multiplier on magic damage taken. */
	public static double magicDamageTakenMultiplier(final int intel) {
		return 1.0 - value(Effect.MAGIC_DEFENSE, intel);
	}

	/** The multiplier on a critical hit taken. */
	public static double critDamageTakenMultiplier(final int luk) {
		return 1.0 - value(Effect.CRIT_REDUCTION, luk);
	}

	public static double cooldownMultiplier(final int intel) {
		return 1.0 - value(Effect.COOLDOWN_REDUCTION, intel);
	}
}
