package com.stella.mobs.entity.spawn;

import net.minecraft.util.RandomSource;

/**
 * Red and blue slimes thin out with distance from 0,0: the spawn chance is multiplied by {@link #FACTOR} for each
 * {@link #STEP} blocks, measured sideways, and never falls below {@link #FLOOR}.
 */
public final class SlimeFalloff {
	public static final double FACTOR = 0.85;
	public static final int STEP = 200;
	public static final double FLOOR = 0.10;

	/** Test seam: decides a spawn from its chance. */
	@FunctionalInterface
	public interface Roller {
		boolean passes(RandomSource random, double chance);
	}

	private static final Roller FAIR = (random, chance) -> random.nextDouble() < chance;
	private static volatile Roller roller = FAIR;

	private SlimeFalloff() {
	}

	public static void setRoller(final Roller newRoller) {
		roller = newRoller == null ? FAIR : newRoller;
	}

	/** The spawn chance at a column: 0.85 to the power of the whole 200-block steps from 0,0, at least 0.10. */
	public static double chance(final double x, final double z) {
		int steps = (int) Math.floor(Math.sqrt(x * x + z * z) / STEP);
		return Math.max(FLOOR, Math.pow(FACTOR, steps));
	}

	public static boolean passes(final RandomSource random, final double x, final double z) {
		return roller.passes(random, chance(x, z));
	}
}
