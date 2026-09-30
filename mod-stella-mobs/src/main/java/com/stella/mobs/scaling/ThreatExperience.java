package com.stella.mobs.scaling;

import com.stella.mobs.config.ScalingConfigStore;
import net.minecraft.util.Mth;

/** Scales the vanilla reward by the mob's own level, then by its gap to the killer's XP level. */
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
		double own = 1.0 + Math.max(0, threatLevel) / ScalingConfigStore.get().xpLevelDivisor();
		double gap = Mth.clamp(1.0 + ((long) threatLevel - playerLevel) / LEVEL_STEP, MIN_MULTIPLIER, MAX_MULTIPLIER);
		double multiplier = own * gap;
		return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, Math.round(vanilla * multiplier)));
	}
}
