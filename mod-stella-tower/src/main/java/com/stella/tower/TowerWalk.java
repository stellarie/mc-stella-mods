package com.stella.tower;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Walking a floor in three dimensions, on foot: the one set of rules a plan is proved by and a built floor is walked
 * by, so the two can be held to each other.
 * <p>
 * A room is a grid of cells by offsets from its inner corner, each {@link #OPEN} (nothing to collide with),
 * {@link #SOLID} (something to stand on, no higher than a block) or {@link #TALL} (a rail or wall top, higher than a
 * block, which no body stands on or steps over). A body stands at a cell with solid under it and open air at it and
 * above it. From there it walks to a neighbour across x or z: level; one up, with air above its head to jump; or one
 * down. It never drops further, and never climbs.
 * <p>
 * A room's {@link Space} answers solid for every cell outside the room, so a walk needs no bounds of its own; its tables
 * are as large as the largest room, and a spot's index does not depend on the room's size.
 */
public final class TowerWalk {
	public static final byte OPEN = 0;
	public static final byte SOLID = 1;
	public static final byte TALL = 2;
	/** The feet heights a walk covers: a biome floor's wading level to just under its roof. */
	public static final int LOW = -1;
	public static final int HIGH = TowerLayout.ROOM_HEIGHT - 1;
	/** The span of x and of z a walk's tables hold: the largest room. A smaller room's space is solid past its walls. */
	private static final int SIZE = TowerLayout.MAX_SIZE;
	private static final int SPAN = HIGH - LOW + 1;
	private static final int[][] WAYS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	private TowerWalk() {
	}

	/** A room's cells, by offsets from its inner corner. Outside the room every cell is solid. */
	public interface Space {
		byte at(int x, int y, int z);
	}

	public static boolean stands(final Space space, final int x, final int y, final int z) {
		return space.at(x, y - 1, z) == SOLID && space.at(x, y, z) == OPEN && space.at(x, y + 1, z) == OPEN;
	}

	/** The index of a stand spot in a walk's distances. */
	public static int index(final int x, final int y, final int z) {
		return (x * SPAN + (y - LOW)) * SIZE + z;
	}

	public static boolean inRoom(final int x, final int y, final int z) {
		return x >= 0 && z >= 0 && x < SIZE && z < SIZE && y >= LOW && y <= HIGH;
	}

	/** The walk from a spot to every spot reached, in steps; -1 where it does not reach. Empty walk from a spot no body stands at. */
	public static int[] distances(final Space space, final int fromX, final int fromY, final int fromZ) {
		int[] dist = new int[SIZE * SPAN * SIZE];
		Arrays.fill(dist, -1);
		if (!inRoom(fromX, fromY, fromZ) || !stands(space, fromX, fromY, fromZ)) {
			return dist;
		}
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		dist[index(fromX, fromY, fromZ)] = 0;
		queue.add(new int[] {fromX, fromY, fromZ});
		while (!queue.isEmpty()) {
			int[] at = queue.poll();
			int here = dist[index(at[0], at[1], at[2])];
			for (int[] next : moves(space, at[0], at[1], at[2])) {
				int i = index(next[0], next[1], next[2]);
				if (dist[i] < 0) {
					dist[i] = here + 1;
					queue.add(next);
				}
			}
		}
		return dist;
	}

	/** The spots a body standing here walks to in one step. */
	public static List<int[]> moves(final Space space, final int x, final int y, final int z) {
		List<int[]> moves = new ArrayList<>(4);
		for (int[] way : WAYS) {
			int nx = x + way[0];
			int nz = z + way[1];
			if (nx < 0 || nz < 0 || nx >= SIZE || nz >= SIZE) {
				continue;
			}
			if (inRoom(nx, y, nz) && stands(space, nx, y, nz)) {
				moves.add(new int[] {nx, y, nz});
			} else if (inRoom(nx, y + 1, nz) && stands(space, nx, y + 1, nz) && space.at(x, y + 2, z) == OPEN) {
				moves.add(new int[] {nx, y + 1, nz});
			} else if (inRoom(nx, y - 1, nz) && stands(space, nx, y - 1, nz) && space.at(nx, y + 1, nz) == OPEN) {
				moves.add(new int[] {nx, y - 1, nz});
			}
		}
		return moves;
	}

	/** The shortest walk from a spot to the nearest goal, both ends included; empty when no goal is reached. */
	public static List<int[]> path(final Space space, final int[] from, final List<int[]> goals) {
		if (!inRoom(from[0], from[1], from[2]) || !stands(space, from[0], from[1], from[2])) {
			return List.of();
		}
		boolean[] goal = new boolean[SIZE * SPAN * SIZE];
		for (int[] each : goals) {
			if (inRoom(each[0], each[1], each[2])) {
				goal[index(each[0], each[1], each[2])] = true;
			}
		}
		int[] came = new int[SIZE * SPAN * SIZE];
		Arrays.fill(came, -1);
		int start = index(from[0], from[1], from[2]);
		came[start] = start;
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		queue.add(from);
		while (!queue.isEmpty()) {
			int[] at = queue.poll();
			int i = index(at[0], at[1], at[2]);
			if (goal[i]) {
				List<int[]> steps = new ArrayList<>();
				int back = i;
				while (true) {
					steps.add(spot(back));
					if (came[back] == back) {
						break;
					}
					back = came[back];
				}
				Collections.reverse(steps);
				return steps;
			}
			for (int[] next : moves(space, at[0], at[1], at[2])) {
				int n = index(next[0], next[1], next[2]);
				if (came[n] < 0) {
					came[n] = i;
					queue.add(next);
				}
			}
		}
		return List.of();
	}

	private static int[] spot(final int index) {
		int z = index % SIZE;
		int rest = index / SIZE;
		return new int[] {rest / SPAN, rest % SPAN + LOW, z};
	}

	/** Whether a walk from a spot reaches any goal. */
	public static boolean reaches(final Space space, final int[] from, final List<int[]> goals) {
		int[] dist = distances(space, from[0], from[1], from[2]);
		for (int[] goal : goals) {
			if (inRoom(goal[0], goal[1], goal[2]) && dist[index(goal[0], goal[1], goal[2])] >= 0) {
				return true;
			}
		}
		return false;
	}

	/** How a block counts to a walking body: by its collision shape. */
	public static byte classify(final ServerLevel level, final BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		// Lava and powder snow have no collision, yet nobody walks into them: they count as higher than a block.
		if (level.getFluidState(pos).is(net.minecraft.tags.FluidTags.LAVA) || state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)) {
			return TALL;
		}
		VoxelShape shape = state.getCollisionShape(level, pos);
		if (shape.isEmpty()) {
			return OPEN;
		}
		return shape.max(Direction.Axis.Y) > 1.0 ? TALL : SOLID;
	}

	/** A built floor's room as it stands, read from the world. */
	public static Space world(final ServerLevel level, final int floor) {
		BlockPos origin = TowerLayout.origin(floor);
		TowerPlan plan = TowerPlan.at(level, floor);
		int width = plan.width();
		int depth = plan.depth();
		return (x, y, z) -> {
			if (x < 0 || z < 0 || x >= width || z >= depth || y >= TowerLayout.ROOM_HEIGHT || y < -2) {
				return SOLID;
			}
			return classify(level, origin.offset(x, y, z));
		};
	}
}
