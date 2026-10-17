package com.stella.rpg.roll;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Deeper ore gives more experience. The multiplier is 1 at {@link #SURFACE_Y} and above, and grows by one for every
 * {@link #DEPTH_PER_STEP} blocks below it, so it is 3 at y -64. Only an ore that a player breaks scales.
 */
public final class OreExperience {
	public static final int SURFACE_Y = 64;
	public static final int DEPTH_PER_STEP = 64;
	private static final ThreadLocal<ServerPlayer> BREAKER = new ThreadLocal<>();

	private OreExperience() {
	}

	public static double multiplier(final double y) {
		return 1.0 + Math.max(0.0, SURFACE_Y - y) / DEPTH_PER_STEP;
	}

	/** The scaled amount. A fraction rounds up with its own chance, so the average stays exact. */
	public static int scale(final int amount, final double y, final RandomSource random) {
		double scaled = amount * multiplier(y);
		int whole = (int) Math.floor(scaled);
		return random.nextDouble() < scaled - whole ? whole + 1 : whole;
	}

	/** Runs a block's after-break step, and marks the player as its breaker when the block is an ore. */
	public static void during(final BlockState state, final Entity breaker, final Runnable step) {
		ServerPlayer previous = BREAKER.get();
		BREAKER.set(breaker instanceof ServerPlayer player && state.is(BlockTags.ORES) ? player : null);
		try {
			step.run();
		} finally {
			BREAKER.set(previous);
		}
	}

	/** The experience an ore gives at this height; unchanged when no player is breaking an ore. */
	public static int forBreak(final int amount, final double y, final RandomSource random) {
		return BREAKER.get() == null ? amount : scale(amount, y, random);
	}
}
