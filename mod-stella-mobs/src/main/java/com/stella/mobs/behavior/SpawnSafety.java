package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.config.GearConfigStore;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The spawn-safety gate: no elite or boss roll inside the configured radius of 0,0, for any mob the world spawns on
 * its own. A mob a player spawns on purpose, from an egg or a command, still rolls.
 */
public final class SpawnSafety {
	/** The radius the shipped config file sets. */
	public static final double SHIPPED_RADIUS = 240.0;
	/** The centre of every distance rule. */
	public static final double ORIGIN_X = 0.0;
	public static final double ORIGIN_Z = 0.0;
	/** Every way the world spawns a mob by itself. */
	public static final java.util.Set<EntitySpawnReason> WORLD_REASONS = java.util.EnumSet.of(
		EntitySpawnReason.NATURAL, EntitySpawnReason.CHUNK_GENERATION, EntitySpawnReason.SPAWNER, EntitySpawnReason.STRUCTURE,
		EntitySpawnReason.PATROL, EntitySpawnReason.TRIAL_SPAWNER, EntitySpawnReason.REINFORCEMENT, EntitySpawnReason.JOCKEY,
		EntitySpawnReason.EVENT
	);

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
		if (radius <= 0.0 || reason == null || !WORLD_REASONS.contains(reason) || !Level.OVERWORLD.equals(dimension)) {
			return false;
		}

		double dx = x - spawnX;
		double dz = z - spawnZ;
		return dx * dx + dz * dz <= radius * radius;
	}

	/** The gate for a mob entering this level, read against 0,0 and the live config. The world spawn plays no part. */
	public static boolean suppresses(final Mob mob, final ServerLevel level, final @Nullable EntitySpawnReason reason) {
		boolean suppressed = suppresses(
			level.dimension(), reason, mob.getX(), mob.getZ(), ORIGIN_X, ORIGIN_Z, GearConfigStore.get().spawnSafetyRadius()
		);

		if (suppressed) {
			StellaMobs.LOGGER.debug(
				"[BEHAVIOR] test=spawnSafety SUPPRESS type={} distance={} radius={}",
				mob.getType(), Math.hypot(mob.getX() - ORIGIN_X, mob.getZ() - ORIGIN_Z), GearConfigStore.get().spawnSafetyRadius()
			);
		}

		return suppressed;
	}
}
