package com.stella.mobs.behavior;

import com.stella.mobs.config.WorldRules;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * How far from 0,0 an elite or a boss may first appear, and how the elite chance grows with distance. The gates follow
 * the spawn safety gate's rule: they bind only what the world spawns by itself, only in the Overworld, and a minimum of
 * zero switches one off.
 */
public final class DistanceGates {
	private static final double BLOCKS_PER_STEP = 100.0;

	private DistanceGates() {
	}

	/** True when a world spawn here may roll elite. */
	public static boolean eliteAllowed(
		final ResourceKey<Level> dimension, final @Nullable EntitySpawnReason reason, final double x, final double z, final double minDistance
	) {
		return allowed(dimension, reason, x, z, minDistance);
	}

	/** True when a world spawn here may roll boss. */
	public static boolean bossAllowed(
		final ResourceKey<Level> dimension, final @Nullable EntitySpawnReason reason, final double x, final double z, final double minDistance
	) {
		return allowed(dimension, reason, x, z, minDistance);
	}

	/**
	 * The elite chance here: the mob kind's own chance plus a bonus per 100 blocks from 0,0, capped. A kind whose chance
	 * is zero stays at zero, so a config that switches elites off for a kind is never overridden by distance.
	 */
	public static double eliteChance(
		final double base, final ResourceKey<Level> dimension, final double x, final double z, final WorldRules rules
	) {
		if (base <= 0.0 || !Level.OVERWORLD.equals(dimension)) {
			return base;
		}

		double bonus = Math.min(rules.eliteBonusCap(), rules.eliteBonusPer100() * Math.hypot(x, z) / BLOCKS_PER_STEP);
		return Math.min(1.0, base + Math.max(0.0, bonus));
	}

	private static boolean allowed(
		final ResourceKey<Level> dimension, final @Nullable EntitySpawnReason reason, final double x, final double z, final double minDistance
	) {
		if (minDistance <= 0.0 || reason == null || !SpawnSafety.WORLD_REASONS.contains(reason) || !Level.OVERWORLD.equals(dimension)) {
			return true;
		}

		return Math.hypot(x, z) >= minDistance;
	}
}
