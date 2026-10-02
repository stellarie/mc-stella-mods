package com.stella.mobs.behavior;

import net.minecraft.world.entity.Mob;

/**
 * The firing band of a ranged unit under its stance. A kiting unit keeps farther from the player. A unit that presses a
 * hurt player comes in closer. Every other stance keeps the base band. The base is the band that the unit's role and its
 * config give it.
 */
public final class StanceBands {
	public static final double KITE_NEAR = 12.0;
	public static final double PURSUE_NEAR = 3.0;
	public static final double PURSUE_FAR = 12.0;

	private StanceBands() {
	}

	public static double near(final Mob mob, final double base) {
		return switch (Stance.of(mob)) {
			case KITE -> Math.max(base, KITE_NEAR);
			case PURSUE -> Math.min(base, PURSUE_NEAR);
			default -> base;
		};
	}

	public static double far(final Mob mob, final double base) {
		return switch (Stance.of(mob)) {
			// The far edge never falls under the near one.
			case KITE -> Math.max(base, KITE_NEAR);
			case PURSUE -> Math.min(base, PURSUE_FAR);
			default -> base;
		};
	}
}
