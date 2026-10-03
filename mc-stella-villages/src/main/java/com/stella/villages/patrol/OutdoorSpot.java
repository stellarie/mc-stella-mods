package com.stella.villages.patrol;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where guards and the mage may appear: under open sky and at street level. The rule reads blocks only, so it
 * keeps them out of houses, towers and roofs from any structure mod without a list of structures.
 */
public final class OutdoorSpot {
	/** A spot this far above or below the bell is a roof or a cellar, not the street. */
	public static final int STREET_TOLERANCE = 3;
	public static final int SEARCH_RADIUS = 16;

	private OutdoorSpot() {
	}

	/** Nothing solid above the feet but leaves, and the feet within the street tolerance of the bell. */
	public static boolean isOutdoor(final ServerLevel level, final BlockPos feet, final int streetY) {
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, feet.getX(), feet.getZ());
		return feet.getY() >= top && Math.abs(feet.getY() - streetY) <= STREET_TOLERANCE;
	}

	/** The nearest standable outdoor spot around the bell, searching outward ring by ring, or null. */
	public static @Nullable BlockPos near(final ServerLevel level, final BlockPos bell, final Mob mob) {
		return near(level, bell, mob.getType().getDimensions());
	}

	/** The same search for a mob that does not exist yet, sized by its type. */
	public static @Nullable BlockPos near(final ServerLevel level, final BlockPos bell, final EntityDimensions size) {
		for (int radius = 1; radius <= SEARCH_RADIUS; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
						continue;
					}
					int x = bell.getX() + dx;
					int z = bell.getZ() + dz;
					if (!level.hasChunkAt(new BlockPos(x, bell.getY(), z))) {
						continue;
					}
					BlockPos candidate = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
					if (standable(level, candidate, size) && isOutdoor(level, candidate, bell.getY())) {
						return candidate;
					}
				}
			}
		}
		return null;
	}

	static boolean standable(final ServerLevel level, final BlockPos feet, final EntityDimensions size) {
		BlockPos support = feet.below();
		return level.getBlockState(feet).isAir()
			&& level.getFluidState(support).isEmpty()
			&& level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)
			&& level.noCollision(size.makeBoundingBox(Vec3.atBottomCenterOf(feet)));
	}
}
