package com.stella.end;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * A walker that reads the built world: a standing spot has room for the feet and the head and a solid block under
 * it, and one step moves a block sideways, up one block, or down to three. It is the model of a player on foot.
 */
final class CastleWalk {
	/** The most a walker falls in one step without being hurt much. */
	private static final int DROP = 3;
	private static final double CARPET = 0.07;
	private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	private CastleWalk() {
	}

	private static boolean free(final ServerLevel end, final int x, final int y, final int z) {
		BlockPos at = new BlockPos(x, y, z);
		net.minecraft.world.phys.shapes.VoxelShape shape = end.getBlockState(at).getCollisionShape(end, at);
		// A carpet is a floor covering, not a step: a walker crosses it.
		return shape.isEmpty() || shape.max(net.minecraft.core.Direction.Axis.Y) <= CARPET;
	}

	static boolean standable(final ServerLevel end, final int x, final int y, final int z) {
		BlockPos below = new BlockPos(x, y - 1, z);
		return free(end, x, y, z) && free(end, x, y + 1, z) && !end.getBlockState(below).getCollisionShape(end, below).isEmpty();
	}

	/** Every standing spot a walker reaches from a start inside the castle's zone, as packed block positions of the feet. */
	static Set<Long> reach(final ServerLevel end, final BlockPos start) {
		Set<Long> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		if (standable(end, start.getX(), start.getY(), start.getZ())) {
			seen.add(start.asLong());
			queue.add(start);
		}
		int top = EndCastle.ROOF_Y + EndCastle.TOWER_HEIGHT + 2;
		while (!queue.isEmpty()) {
			BlockPos at = queue.poll();
			for (int[] step : STEPS) {
				int x = at.getX() + step[0];
				int z = at.getZ() + step[1];
				if (x < EndCastle.ZONE_MIN_X - 1 || z < EndCastle.ZONE_MIN_Z - 1 || x > EndCastle.ZONE_MIN_X + EndCastle.ZONE_SIZE
					|| z > EndCastle.ZONE_MIN_Z + EndCastle.ZONE_SIZE) {
					continue;
				}
				for (int rise = 1; rise >= -DROP; rise--) {
					int y = at.getY() + rise;
					if (y < EndCastle.BASE_Y || y > top || !standable(end, x, y, z)) {
						continue;
					}
					boolean clear = true;
					for (int level = Math.max(y, at.getY()); level >= y && clear; level--) {
						clear = free(end, x, level, z) && free(end, x, level + 1, z);
					}
					if (clear && seen.add(new BlockPos(x, y, z).asLong())) {
						queue.add(new BlockPos(x, y, z));
					}
					break;
				}
			}
		}
		return seen;
	}

	static boolean reaches(final Set<Long> reached, final int x, final int y, final int z) {
		return reached.contains(new BlockPos(x, y, z).asLong());
	}
}
