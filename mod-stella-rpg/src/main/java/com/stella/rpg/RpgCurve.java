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

	public static double meleeMultiplier(final int str) {
		return 1.0 + value(Effect.MELEE_DAMAGE, str);
	}

	public static double rangedMultiplier(final int dex) {
		return 1.0 + value(Effect.RANGED_DAMAGE, dex);
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

	public static double spellPowerMultiplier(final int intel) {
		return 1.0 + value(Effect.SPELL_POWER, intel);
	}

	public static double cooldownMultiplier(final int intel) {
		return 1.0 - value(Effect.COOLDOWN_REDUCTION, intel);
	}
}
