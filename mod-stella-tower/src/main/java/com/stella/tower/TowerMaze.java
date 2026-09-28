package com.stella.tower;

/**
 * Each floor is a maze: paths two blocks wide between walls one block thick, on a grid of cells three blocks
 * apart. An elite floor opens a hall in its middle for its guardian, a boss floor a greater one, and the
 * portal stands in the hall's middle; an ordinary floor keeps its portal in a nook across from the arrival.
 */
public final class TowerMaze {
	public static final int PATH = 2;
	public static final int WALL = 1;
	public static final int PITCH = PATH + WALL;
	/** Cells along each side: the room less its last wall line, three blocks a cell. */
	public static final int CELLS = (TowerLayout.SIZE - WALL) / PITCH;
	public static final int ELITE_HALL = 16;
	public static final int BOSS_HALL = 32;

	private TowerMaze() {
	}

	/** The portal's nook on an ordinary floor: the last two cell columns, three cell rows round the middle. */
	public static final int NOOK_LOW_X = 1 + (CELLS - 2) * PITCH;
	public static final int NOOK_HIGH_X = CELLS * PITCH;
	public static final int NOOK_LOW_Z = 1 + (TowerLayout.MIDDLE_Z / PITCH - 1) * PITCH;
	public static final int NOOK_HIGH_Z = (TowerLayout.MIDDLE_Z / PITCH + 2) * PITCH - 1;
	private static final long SALT = 0x4D415A45L;

	/** The side of the floor's hall, or nought for an ordinary floor. */
	public static int hall(final int floor) {
		if (floor % TowerElites.BOSS_EVERY == 0) {
			return BOSS_HALL;
		}
		return floor % TowerElites.ELITE_EVERY == 0 ? ELITE_HALL : 0;
	}

	/** The low corner of a cell's open square, as an offset along either side. */
	private static int cell(final int index) {
		return 1 + index * PITCH;
	}

	/**
	 * Where full-height walls stand, by offset from the room's inner corner, [x][z]. Carved as a perfect
	 * maze from the arrival's cell, so every cell has exactly one way to every other; then the hall or the
	 * portal's nook is opened. A pure function of the seed and the floor.
	 */
	public static boolean[][] walls(final long seed, final int floor) {
		int size = TowerLayout.SIZE;
		boolean[][] wall = new boolean[size][size];
		for (boolean[] row : wall) {
			java.util.Arrays.fill(row, true);
		}
		for (int cx = 0; cx < CELLS; cx++) {
			for (int cz = 0; cz < CELLS; cz++) {
				open(wall, cell(cx), cell(cz), PATH, PATH);
			}
		}
		java.util.Random random = new java.util.Random(seed ^ SALT ^ (floor * 0x9E3779B97F4A7C15L));
		boolean[][] seen = new boolean[CELLS][CELLS];
		java.util.ArrayDeque<int[]> trail = new java.util.ArrayDeque<>();
		int startX = (TowerLayout.ARRIVAL_X - 1) / PITCH;
		int startZ = (TowerLayout.MIDDLE_Z - 1) / PITCH;
		seen[startX][startZ] = true;
		trail.push(new int[] {startX, startZ});
		int[][] ways = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!trail.isEmpty()) {
			int[] at = trail.peek();
			java.util.List<int[]> next = new java.util.ArrayList<>();
			for (int[] way : ways) {
				int nx = at[0] + way[0];
				int nz = at[1] + way[1];
				if (nx >= 0 && nz >= 0 && nx < CELLS && nz < CELLS && !seen[nx][nz]) {
					next.add(new int[] {nx, nz});
				}
			}
			if (next.isEmpty()) {
				trail.pop();
				continue;
			}
			int[] to = next.get(random.nextInt(next.size()));
			// The wall line between the two cells opens along the path's width.
			int lowX = Math.min(cell(at[0]), cell(to[0]));
			int lowZ = Math.min(cell(at[1]), cell(to[1]));
			open(wall, lowX, lowZ, at[0] != to[0] ? PITCH + PATH : PATH, at[1] != to[1] ? PITCH + PATH : PATH);
			seen[to[0]][to[1]] = true;
			trail.push(to);
		}
		int hall = hall(floor);
		if (hall > 0) {
			open(wall, size / 2 - hall / 2, size / 2 - hall / 2, hall, hall);
		} else {
			open(wall, NOOK_LOW_X, NOOK_LOW_Z, NOOK_HIGH_X - NOOK_LOW_X, NOOK_HIGH_Z - NOOK_LOW_Z + 1);
		}
		return wall;
	}

	private static void open(final boolean[][] wall, final int x, final int z, final int width, final int length) {
		for (int dx = 0; dx < width; dx++) {
			for (int dz = 0; dz < length; dz++) {
				wall[x + dx][z + dz] = false;
			}
		}
	}
}
