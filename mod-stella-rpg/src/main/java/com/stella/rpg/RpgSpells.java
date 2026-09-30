package com.stella.rpg;

import net.minecraft.world.entity.player.Player;

/**
 * The INT multipliers other mods apply to player spells. {@code mod-stella-spells} finds this class by name, so
 * the class name and both method signatures are a public contract.
 */
public final class RpgSpells {
	private RpgSpells() {
	}

	public static double power(final Player player) {
		return RpgCurve.spellPowerMultiplier(StatBonuses.effective(player, Stat.INT));
	}

	public static double cooldown(final Player player) {
		return RpgCurve.cooldownMultiplier(StatBonuses.effective(player, Stat.INT));
	}
}
