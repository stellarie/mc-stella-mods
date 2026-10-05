package com.stella.tower;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * The crystal cavern ({@link TowerArchetype#CRYSTAL_CAVERN}): two or three cave stories, one above another, joined by
 * ramps three wide that climb one block for every two.
 * <p>
 * The fixed parts are placed first: the arrival's clearing on the ground story, the portal's nook on the top story,
 * and each ramp, its body and its trench cut into rock and a clearing at each end. Each story's rock is then grown
 * round them by a cellular automaton; any open spot not part of an open two by two square is filled, so a broad mob
 * finds room everywhere; and every cave left apart is joined to the rest by a tunnel three wide, so the story is one
 * cave. Null when a ramp, the nook or a tunnel finds no room.
 */
final class TowerCavern {
	private static final int SIZE = TowerLayout.SIZE;
	static final int RAMP_WIDTH = 3;
	/** In a hundred: how much of a story's free ground starts as rock. */
	static final int FILL = 46;
	private static final int SMOOTHING = 4;
	/** A cave smaller than this, holding no fixed part, is filled rather than joined. */
	private static final int SMALL_CAVE = 12;
	private static final int PLACE_TRIES = 60;
	private static final byte FREE = 0;
	private static final byte ROCK = 1;
	private static final byte OPEN = 2;
	private static final Direction[] WAYS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	private TowerCavern() {
	}

	static TowerPlan.@Nullable Draft draw(final Random random) {
		int count = 2 + random.nextInt(2);
		int[] ys = new int[count];
		int[] tops = new int[count];
		for (int s = 0; s < count; s++) {
			ys[s] = s * TowerPlan.STORY;
		}
		for (int s = 0; s < count; s++) {
			tops[s] = s + 1 < count ? ys[s + 1] - 1 : TowerLayout.HEIGHT;
		}
		byte[][][] fixed = new byte[count][SIZE][SIZE];
		for (byte[][] story : fixed) {
			for (int i = 0; i < SIZE; i++) {
				story[i][0] = ROCK;
				story[i][SIZE - 1] = ROCK;
				story[0][i] = ROCK;
				story[SIZE - 1][i] = ROCK;
			}
		}
		// The arrival's clearing: the zone and a block round it.
		int arrivalX = 7 + random.nextInt(SIZE - 14);
		int arrivalZ = 7 + random.nextInt(SIZE - 14);
		TowerMaze.Zone zone = new TowerMaze.Zone(arrivalX - 3, arrivalZ - 4, 0, tops[0]);
		mark(fixed[0], zone.x() - 1, zone.z() - 1, zone.x() + TowerMaze.SAFE, zone.z() + TowerMaze.SAFE, OPEN);
		// The portal's nook, on the top story: open in front, rock behind and beside the frame.
		int top = count - 1;
		int facing = random.nextBoolean() ? 1 : -1;
		int portalX = facing > 0 ? 2 + random.nextInt(SIZE - 10) : SIZE - 3 - random.nextInt(SIZE - 10);
		int portalZ = 4 + random.nextInt(SIZE - 10);
		int frontLow = Math.min(portalX + facing, portalX + 5 * facing);
		int frontHigh = Math.max(portalX + facing, portalX + 5 * facing);
		mark(fixed[top], frontLow, portalZ - 2, frontHigh, portalZ + 3, OPEN);
		mark(fixed[top], portalX, portalZ - 1, portalX, portalZ + 2, OPEN);
		mark(fixed[top], portalX, portalZ - 2, portalX, portalZ - 2, ROCK);
		mark(fixed[top], portalX, portalZ + 3, portalX, portalZ + 3, ROCK);
		mark(fixed[top], portalX - facing, portalZ - 2, portalX - facing, portalZ + 3, ROCK);
		List<TowerFlight> flights = new ArrayList<>();
		for (int s = 0; s + 1 < count; s++) {
			int ramps = 1 + random.nextInt(2);
			int placed = 0;
			for (int tries = 0; tries < PLACE_TRIES && placed < ramps; tries++) {
				TowerFlight ramp = ramp(random, ys[s]);
				if (fits(ramp, fixed[s], fixed[s + 1])) {
					claim(ramp, fixed[s], fixed[s + 1]);
					flights.add(ramp);
					placed++;
				}
			}
			if (placed == 0) {
				return null;
			}
		}
		List<TowerStory> stories = new ArrayList<>();
		for (int s = 0; s < count; s++) {
			boolean[][] rock = grow(random, fixed[s]);
			int[] anchor = s == 0 ? new int[] {arrivalX, arrivalZ} : anyOpen(fixed[s]);
			if (anchor == null || !joinCaves(rock, fixed[s], anchor)) {
				return null;
			}
			stories.add(TowerStory.of(ys[s], tops[s], TowerStory.full(), rock));
		}
		return new TowerPlan.Draft(stories, flights, arrivalX, arrivalZ, portalX, portalZ, facing, top, List.of(zone), null);
	}

	/** A ramp of random way and place, climbing from the story at {@code base}; the caller sees whether it fits. */
	private static TowerFlight ramp(final Random random, final int base) {
		Direction direction = WAYS[random.nextInt(WAYS.length)];
		return new TowerFlight(2 + random.nextInt(SIZE - 4), 2 + random.nextInt(SIZE - 4), direction, RAMP_WIDTH, base, TowerPlan.STORY, TowerFlight.RAMP);
	}

	/** The steps whose headroom reaches into the story above: the ramp's trench there. */
	private static int trenchFrom(final TowerFlight ramp) {
		int i = 0;
		while (i < ramp.length() && ramp.surface(i) + 2 < ramp.top() - 1) {
			i++;
		}
		return i;
	}

	/**
	 * Whether a ramp fits: its body and the low walls either side on free ground below, its entry's clearing on
	 * free or open ground, its trench free above and its exit's clearing free or open, all inside the room's border.
	 */
	private static boolean fits(final TowerFlight ramp, final byte[][] below, final byte[][] above) {
		int trench = trenchFrom(ramp);
		for (int i = -3; i < ramp.length() + 3; i++) {
			for (int w = -1; w <= ramp.width(); w++) {
				int x = ramp.cellX(i, w);
				int z = ramp.cellZ(i, w);
				if (x < 2 || z < 2 || x > SIZE - 3 || z > SIZE - 3) {
					return false;
				}
				boolean body = i >= 0 && i < ramp.length();
				if (i < 0 && below[x][z] == ROCK || body && below[x][z] != FREE) {
					return false;
				}
				if (i >= trench && i < ramp.length() && above[x][z] != FREE || i >= ramp.length() && above[x][z] == ROCK) {
					return false;
				}
			}
		}
		return true;
	}

	private static void claim(final TowerFlight ramp, final byte[][] below, final byte[][] above) {
		int trench = trenchFrom(ramp);
		for (int i = -3; i < ramp.length() + 3; i++) {
			for (int w = -1; w <= ramp.width(); w++) {
				int x = ramp.cellX(i, w);
				int z = ramp.cellZ(i, w);
				if (i < 0) {
					below[x][z] = OPEN;
				} else if (i < ramp.length()) {
					below[x][z] = ROCK;
					if (i >= trench) {
						above[x][z] = ROCK;
					}
				} else {
					above[x][z] = OPEN;
				}
			}
		}
	}

	private static void mark(final byte[][] grid, final int lowX, final int lowZ, final int highX, final int highZ, final byte kind) {
		for (int x = Math.max(0, lowX); x <= Math.min(SIZE - 1, highX); x++) {
			for (int z = Math.max(0, lowZ); z <= Math.min(SIZE - 1, highZ); z++) {
				grid[x][z] = kind;
			}
		}
	}

	private static int @Nullable [] anyOpen(final byte[][] fixed) {
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				if (fixed[x][z] == OPEN) {
					return new int[] {x, z};
				}
			}
		}
		return null;
	}

	/** A story's rock, grown by the automaton round its fixed parts, then thinned of every spot too narrow to stand in. */
	private static boolean[][] grow(final Random random, final byte[][] fixed) {
		boolean[][] rock = new boolean[SIZE][SIZE];
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				rock[x][z] = fixed[x][z] == ROCK || fixed[x][z] == FREE && random.nextInt(100) < FILL;
			}
		}
		for (int pass = 0; pass < SMOOTHING; pass++) {
			boolean[][] next = new boolean[SIZE][SIZE];
			for (int x = 0; x < SIZE; x++) {
				for (int z = 0; z < SIZE; z++) {
					int around = 0;
					for (int dx = -1; dx <= 1; dx++) {
						for (int dz = -1; dz <= 1; dz++) {
							int nx = x + dx;
							int nz = z + dz;
							if ((dx != 0 || dz != 0) && (nx < 0 || nz < 0 || nx >= SIZE || nz >= SIZE || rock[nx][nz])) {
								around++;
							}
						}
					}
					boolean was = rock[x][z];
					next[x][z] = fixed[x][z] == ROCK || fixed[x][z] != OPEN && (around >= 5 || around >= 4 && was);
				}
			}
			rock = next;
		}
		boolean changed = true;
		while (changed) {
			changed = false;
			for (int x = 1; x < SIZE - 1; x++) {
				for (int z = 1; z < SIZE - 1; z++) {
					if (!rock[x][z] && fixed[x][z] != OPEN && !inOpenSquare(rock, x, z)) {
						rock[x][z] = true;
						changed = true;
					}
				}
			}
		}
		return rock;
	}

	private static boolean inOpenSquare(final boolean[][] rock, final int x, final int z) {
		for (int dx = -1; dx <= 0; dx++) {
			for (int dz = -1; dz <= 0; dz++) {
				int lx = x + dx;
				int lz = z + dz;
				if (lx >= 0 && lz >= 0 && lx + 1 < SIZE && lz + 1 < SIZE
					&& !rock[lx][lz] && !rock[lx + 1][lz] && !rock[lx][lz + 1] && !rock[lx + 1][lz + 1]) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Joins every cave of a story to the anchor's: a small cave with no fixed part is filled; any other is joined by
	 * the shortest tunnel through ground no fixed rock holds, carved three wide. False when a cave cannot be reached.
	 */
	private static boolean joinCaves(final boolean[][] rock, final byte[][] fixed, final int[] anchor) {
		for (int round = 0; round < SIZE * 4; round++) {
			int[][] caves = label(rock);
			int main = caves[anchor[0]][anchor[1]];
			if (main < 0) {
				return false;
			}
			int other = -1;
			int size = 0;
			boolean holdsFixed = false;
			for (int x = 0; x < SIZE && other < 0; x++) {
				for (int z = 0; z < SIZE; z++) {
					if (caves[x][z] >= 0 && caves[x][z] != main) {
						other = caves[x][z];
						break;
					}
				}
			}
			if (other < 0) {
				return true;
			}
			for (int x = 0; x < SIZE; x++) {
				for (int z = 0; z < SIZE; z++) {
					if (caves[x][z] == other) {
						size++;
						holdsFixed |= fixed[x][z] == OPEN;
					}
				}
			}
			if (size < SMALL_CAVE && !holdsFixed) {
				for (int x = 0; x < SIZE; x++) {
					for (int z = 0; z < SIZE; z++) {
						if (caves[x][z] == other) {
							rock[x][z] = true;
						}
					}
				}
				continue;
			}
			if (!tunnel(rock, fixed, caves, other, main)) {
				return false;
			}
		}
		return false;
	}

	/** Each open spot's cave, numbered from 0; -1 for rock. */
	private static int[][] label(final boolean[][] rock) {
		int[][] caves = new int[SIZE][SIZE];
		for (int[] row : caves) {
			java.util.Arrays.fill(row, -1);
		}
		int next = 0;
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				if (rock[x][z] || caves[x][z] >= 0) {
					continue;
				}
				ArrayDeque<int[]> queue = new ArrayDeque<>();
				caves[x][z] = next;
				queue.add(new int[] {x, z});
				while (!queue.isEmpty()) {
					int[] at = queue.poll();
					for (Direction side : Direction.Plane.HORIZONTAL) {
						int nx = at[0] + side.getStepX();
						int nz = at[1] + side.getStepZ();
						if (nx >= 0 && nz >= 0 && nx < SIZE && nz < SIZE && !rock[nx][nz] && caves[nx][nz] < 0) {
							caves[nx][nz] = next;
							queue.add(new int[] {nx, nz});
						}
					}
				}
				next++;
			}
		}
		return caves;
	}

	/** The shortest way from one cave to another through any ground but fixed rock, carved three wide. */
	private static boolean tunnel(final boolean[][] rock, final byte[][] fixed, final int[][] caves, final int from, final int to) {
		int[][] came = new int[SIZE][SIZE];
		for (int[] row : came) {
			java.util.Arrays.fill(row, -2);
		}
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				if (caves[x][z] == from) {
					came[x][z] = -1;
					queue.add(new int[] {x, z});
				}
			}
		}
		while (!queue.isEmpty()) {
			int[] at = queue.poll();
			if (caves[at[0]][at[1]] == to) {
				int x = at[0];
				int z = at[1];
				while (came[x][z] >= 0) {
					carve(rock, fixed, x, z);
					int back = came[x][z];
					x = back / SIZE;
					z = back % SIZE;
				}
				carve(rock, fixed, x, z);
				return true;
			}
			for (Direction side : Direction.Plane.HORIZONTAL) {
				int nx = at[0] + side.getStepX();
				int nz = at[1] + side.getStepZ();
				if (nx >= 1 && nz >= 1 && nx < SIZE - 1 && nz < SIZE - 1 && came[nx][nz] == -2 && fixed[nx][nz] != ROCK) {
					came[nx][nz] = at[0] * SIZE + at[1];
					queue.add(new int[] {nx, nz});
				}
			}
		}
		return false;
	}

	/** Opens the spot and the ring round it, but never fixed rock or the room's border. */
	private static void carve(final boolean[][] rock, final byte[][] fixed, final int x, final int z) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				int cx = x + dx;
				int cz = z + dz;
				if (cx >= 1 && cz >= 1 && cx < SIZE - 1 && cz < SIZE - 1 && fixed[cx][cz] != ROCK) {
					rock[cx][cz] = false;
				}
			}
		}
	}
}
