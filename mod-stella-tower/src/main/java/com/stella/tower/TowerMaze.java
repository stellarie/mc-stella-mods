package com.stella.tower;

/**
 * Each floor is a maze: paths six blocks wide between walls one block thick, on a grid of cells seven blocks
 * apart. The room fits nine cells to a side exactly, so every path is six wide; where a hall or a safe zone
 * cuts across the grid, a path beside it can come out narrower, but never under three. An elite floor opens
 * a hall in its middle for its guardian, a boss floor a greater one, and the portal stands in the hall's
 * middle; an ordinary floor keeps its portal in a nook across from the arrival.
 */
public final class TowerMaze {
	public static final int PATH = 6;
	/** The narrowest a path may come out, where a hall or a safe zone cuts the grid. */
	public static final int MIN_PATH = 3;
	public static final int WALL = 1;
	public static final int PITCH = PATH + WALL;
	/** Cells along each side: the room less its last wall line, seven blocks a cell. */
	public static final int CELLS = (TowerLayout.SIZE - WALL) / PITCH;
	public static final int ELITE_HALL = 16;
	public static final int BOSS_HALL = 32;

	private TowerMaze() {
	}

	/** The portal's nook on an ordinary floor: the last cell column, three cell rows round the middle. */
	public static final int NOOK_LOW_X = 1 + (CELLS - 1) * PITCH;
	public static final int NOOK_HIGH_X = CELLS * PITCH;
	public static final int NOOK_LOW_Z = 1 + (TowerLayout.MIDDLE_Z / PITCH - 1) * PITCH;
	public static final int NOOK_HIGH_Z = (TowerLayout.MIDDLE_Z / PITCH + 2) * PITCH - 1;
	private static final long SALT = 0x4D415A45L;
	/** A safe zone's side: no mob is raised in it. */
	public static final int SAFE = 8;
	/** The safe zones' line across the room: round the middle, so the arrival and the doors fall in them. */
	public static final int SAFE_Z = TowerLayout.MIDDLE_Z - SAFE / 2;
	/** The entrance zone's first column: the room's first cell column, which holds the arrival. */
	public static final int ENTRANCE_X = 1;
	/** A boss floor's safe room sits just past its hall, a wall's thickness beyond it. */
	public static final int BOSS_ROOM_X = TowerLayout.SIZE / 2 + BOSS_HALL / 2 + WALL;

	/** A safe zone, by the offset of its low corner from the room's inner corner. */
	public record Zone(int x, int z) {
		public boolean contains(final int atX, final int atZ) {
			return atX >= this.x && atX < this.x + SAFE && atZ >= this.z && atZ < this.z + SAFE;
		}
	}

	/** The floor's safe zones: its entrance, and on a boss floor the room before the hall. */
	public static java.util.List<Zone> safeZones(final int floor) {
		Zone entrance = new Zone(ENTRANCE_X, SAFE_Z);
		return hall(floor) == BOSS_HALL ? java.util.List.of(entrance, new Zone(BOSS_ROOM_X, SAFE_Z)) : java.util.List.of(entrance);
	}

	public static boolean inSafeZone(final int floor, final int x, final int z) {
		for (Zone zone : safeZones(floor)) {
			if (zone.contains(x, z)) {
				return true;
			}
		}
		return false;
	}

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
		java.util.Random random = new java.util.Random(seed ^ SALT ^ (floor * 0x9E3779B97F4A7C15L));
		boolean[][] seen = new boolean[CELLS][CELLS];
		int hall = hall(floor);
		int hallLow = size / 2 - hall / 2;
		if (hall == BOSS_HALL) {
			// The great hall and its safe room stand apart from the maze, so the room is the only way in.
			shut(seen, hallLow, hallLow, hall, hall);
			shut(seen, BOSS_ROOM_X, SAFE_Z, SAFE, SAFE);
		}
		// A shut cell stays solid: the hall and the room are opened over it exactly, leaving no sliver of it.
		for (int cx = 0; cx < CELLS; cx++) {
			for (int cz = 0; cz < CELLS; cz++) {
				if (!seen[cx][cz]) {
					open(wall, cell(cx), cell(cz), PATH, PATH);
				}
			}
		}
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
		if (hall > 0) {
			open(wall, hallLow, hallLow, hall, hall);
		} else {
			open(wall, NOOK_LOW_X, NOOK_LOW_Z, NOOK_HIGH_X - NOOK_LOW_X, NOOK_HIGH_Z - NOOK_LOW_Z + 1);
		}
		if (hall == 0) {
			// Solid behind the portal, so the way to it is three wide in front, not a two wide pocket beyond.
			int behind = TowerLayout.PORTAL_X + 1;
			for (int x = behind; x < NOOK_HIGH_X; x++) {
				for (int z = NOOK_LOW_Z; z <= NOOK_HIGH_Z; z++) {
					wall[x][z] = true;
				}
			}
			// The portal's own slot stays open.
			for (int dz = 0; dz < TowerLayout.PORTAL_INNER_WIDTH; dz++) {
				wall[TowerLayout.PORTAL_X][TowerLayout.MIDDLE_Z - 1 + dz] = false;
			}
		}
		for (Zone zone : safeZones(floor)) {
			open(wall, zone.x(), zone.z(), SAFE, SAFE);
		}
		if (hall == BOSS_HALL) {
			// A door from the hall into the safe room, as wide as a path. The room's far side opens onto the
			// maze by itself, its wall being cleared with the room.
			int door = 1 + (TowerLayout.MIDDLE_Z - 1) / PITCH * PITCH;
			open(wall, BOSS_ROOM_X - WALL, door, WALL, PATH);
		}
		return wall;
	}

	/** Marks every cell within a stretch of the room as taken, so the maze carves no way into it. */
	private static void shut(final boolean[][] seen, final int x, final int z, final int width, final int length) {
		for (int cx = (x - 1) / PITCH; cx <= (x + width - 2) / PITCH; cx++) {
			for (int cz = (z - 1) / PITCH; cz <= (z + length - 2) / PITCH; cz++) {
				seen[cx][cz] = true;
			}
		}
	}

	private static void open(final boolean[][] wall, final int x, final int z, final int width, final int length) {
		for (int dx = 0; dx < width; dx++) {
			for (int dz = 0; dz < length; dz++) {
				wall[x + dx][z + dz] = false;
			}
		}
	}
}
