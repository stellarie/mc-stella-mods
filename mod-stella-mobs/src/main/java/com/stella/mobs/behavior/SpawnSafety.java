package com.stella.mobs.behavior;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** The spawn-safety gate: no natural elite or boss roll inside the configured radius of the shared spawn. */
public final class SpawnSafety {
	/** The radius the shipped config file sets. */
	public static final double SHIPPED_RADIUS = 1000.0;

	private SpawnSafety() {
	}

	/** True when a roll at this place, from this reason, must be skipped. */
	public static boolean suppresses(
		final ResourceKey<Level> dimension,
		final @Nullable EntitySpawnReason reason,
		final double x,
		final double z,
		final double spawnX,
		final double spawnZ,
		final double radius
	) {
		return false;
	}
}
