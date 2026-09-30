package com.stella.spells;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** The shape of the RPG mod's {@code RpgSpells}, for the bridge to bind to in a harness that lacks the mod. */
public final class RpgSpellsStandIn {
	private RpgSpellsStandIn() {
	}

	public static double power(final Player player) {
		return 1.0;
	}

	public static double cooldown(final Player player) {
		return 0.5;
	}

	public static double spellDamage(final LivingEntity caster, final double skillPercent) {
		return damage(skillPercent);
	}

	public static double spellHeal(final LivingEntity caster, final double coefficient) {
		return heal(coefficient);
	}

	static double damage(final double skillPercent) {
		return skillPercent * 0.25;
	}

	static double heal(final double coefficient) {
		return coefficient * 7.0;
	}
}
