package com.stella.spells;

import net.minecraft.world.entity.player.Player;

/** An RPG mod whose MATK calls take another shape than the bridge expects: they must be left unbound. */
public final class RpgSpellsMismatchedStandIn {
	private RpgSpellsMismatchedStandIn() {
	}

	public static double power(final Player player) {
		return 1.0;
	}

	public static double cooldown(final Player player) {
		return 0.5;
	}

	public static double spellDamage(final Player caster, final double skillPercent) {
		return skillPercent;
	}

	public static float spellHeal(final net.minecraft.world.entity.LivingEntity caster, final double coefficient) {
		return (float) coefficient;
	}
}
