package com.stella.learn.gym;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Walking rules for a 16 by 16 arena: level steps, one-block steps up, and drops of up to three blocks. The layout
 * generator uses them to prove a path exists, and a bot uses them on the live blocks to find its way.
 */
public final class Walker {
	private static final int OPEN = Arena.OPEN;
	private static final int FLOOR = Arena.FLOOR;
	private static final int MAX_DROP = 3;
	private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	/** The block at a cell relative to the arena origin. */
	@FunctionalInterface
	public interface Cells {
		BlockState at(int dx, int dy, int dz);
	}

	private Walker() {
	}

	public static boolean free(final Cells cells, final int dx, final int dy, final int dz) {
		BlockState state = cells.at(dx, dy, dz);
		return state.isAir() || state.getBlock() == Blocks.WATER;
	}

	/** A feet cell with two free cells for the body and a solid block below. */
	public static boolean standable(final Cells cells, final int dx, final int dy, final int dz) {
		return free(cells, dx, dy, dz) && free(cells, dx, dy + 1, dz) && !free(cells, dx, dy - 1, dz);
	}

	/** The feet cells from `from` to `to`, both included, or null when no walk joins them. */
	public static List<BlockPos> path(final Cells cells, final BlockPos from, final BlockPos to) {
		Map<Integer, Integer> parent = new HashMap<>();
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		int start = key(from.getX(), from.getY(), from.getZ());
		parent.put(start, start);
		queue.add(new int[] {from.getX(), from.getY(), from.getZ()});
		int goal = key(to.getX(), to.getY(), to.getZ());
		while (!queue.isEmpty()) {
			int[] at = queue.poll();
			int here = key(at[0], at[1], at[2]);
			if (here == goal) {
				List<BlockPos> out = new ArrayList<>();
				int cursor = here;
				while (cursor != start) {
					out.add(decode(cursor));
					cursor = parent.get(cursor);
				}

				out.add(from);
				Collections.reverse(out);
				return out;
			}

			for (int[] step : STEPS) {
				int nx = at[0] + step[0];
				int nz = at[2] + step[1];
				int ny = heightAfterStep(cells, at[0], at[1], at[2], nx, nz);
				if (ny == Integer.MIN_VALUE) {
					continue;
				}

				int next = key(nx, ny, nz);
				if (parent.putIfAbsent(next, here) == null) {
					queue.add(new int[] {nx, ny, nz});
				}
			}
		}

		return null;
	}

	private static int key(final int dx, final int dy, final int dz) {
		return ((dy + FLOOR) * OPEN + dz) * OPEN + dx;
	}

	private static BlockPos decode(final int key) {
		int dx = key % OPEN;
		int dz = (key / OPEN) % OPEN;
		int dy = key / (OPEN * OPEN) - FLOOR;
		return new BlockPos(dx, dy, dz);
	}

	/** The feet height a walker reaches by stepping from (x, y, z) to column (nx, nz), or MIN_VALUE for none. */
	static int heightAfterStep(final Cells cells, final int x, final int y, final int z, final int nx, final int nz) {
		if (nx < 0 || nx >= OPEN || nz < 0 || nz >= OPEN) {
			return Integer.MIN_VALUE;
		}

		if (standable(cells, nx, y, nz)) {
			return y;
		}

		if (free(cells, x, y + 2, z) && standable(cells, nx, y + 1, nz)) {
			return y + 1;
		}

		if (free(cells, nx, y, nz) && free(cells, nx, y + 1, nz)) {
			for (int drop = 1; drop <= MAX_DROP; drop++) {
				if (!free(cells, nx, y - drop + 1, nz)) {
					break;
				}

				if (standable(cells, nx, y - drop, nz)) {
					return y - drop;
				}
			}
		}

		return Integer.MIN_VALUE;
	}
}
