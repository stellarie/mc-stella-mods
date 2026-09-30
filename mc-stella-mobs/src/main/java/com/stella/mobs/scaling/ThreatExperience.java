package com.stella.mobs.scaling;

import net.minecraft.util.Mth;

/** Scales the vanilla, enchantment-adjusted reward against the killer's XP level. */
public final class ThreatExperience {
	public static final double LEVEL_STEP = 10.0;
	public static final double MIN_MULTIPLIER = 0.25;
	public static final double MAX_MULTIPLIER = 4.0;

	private ThreatExperience() {
	}

	public static int reward(final int vanilla, final int threatLevel, final int playerLevel) {
		if (vanilla <= 0) {
			return vanilla;
		}
		double multiplier = Mth.clamp(1.0 + ((long) threatLevel - playerLevel) / LEVEL_STEP,
			MIN_MULTIPLIER, MAX_MULTIPLIER);
		return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, Math.round(vanilla * multiplier)));
	}
}
