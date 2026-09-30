package com.stella.tower;

/**
 * Each floor is a maze: paths six blocks wide between walls one block thick, on a grid of cells seven blocks
 * apart. The room fits nine cells to a side exactly, so every path is six wide; where a hall or a safe zone
 * cuts across the grid, a path beside it can come out narrower, but never under three. An elite floor opens
 * a hall in its middle for its guardian, a boss floor a greater one, and the portal stands in the hall's
 * middle; an ordinary floor keeps its portal in a dead-end cell across the room from the arrival.
 * <p>
 * Where the ends and the safe zones stand is the floor's {@link TowerPlan}; this class only carves. The
 * {@code classic} layout, the arrival on the middle line at the west and the portal in a nook at the east,
 * is what every floor built before the plans has, and the fallback a plan drops to if it finds no path.
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

	/** The classic portal nook: the last cell column, three cell rows round the middle. */
	public static final int NOOK_LOW_X = 1 + (CELLS - 1) * PITCH;
	public static final int NOOK_HIGH_X = CELLS * PITCH;
	public static final int NOOK_LOW_Z = 1 + (TowerPlan.CLASSIC_MIDDLE_Z / PITCH - 1) * PITCH;
	public static final int NOOK_HIGH_Z = (TowerPlan.CLASSIC_MIDDLE_Z / PITCH + 2) * PITCH - 1;
	static final long SALT = 0x4D415A45L;
	/** A safe zone's side: no mob is raised in it. */
	public static final int SAFE = 8;
	/** The classic safe zones' line across the room: round the middle, so the arrival and the doors fall in them. */
	public static final int SAFE_Z = TowerPlan.CLASSIC_MIDDLE_Z - SAFE / 2;
	/** The classic entrance zone's first column: the room's first cell column, which holds the arrival. */
	public static final int ENTRANCE_X = 1;
	/** The classic boss floor's safe room sits just past its hall, a wall's thickness beyond it. */
	public static final int BOSS_ROOM_X = TowerLayout.SIZE / 2 + BOSS_HALL / 2 + WALL;

	/**
	 * The safe room's openings, canonical layout: two blocks wide, at this z (and the next), the exit through the
	 * hall's wall and the ring's west side, the entrance through the ring's east side.
	 */
	public static final int GATE_Z = SAFE_Z + SAFE / 2 - 1;
	/** The room's ring stands on the zone's outermost blocks. The east side of the ring, the entrance's. */
	public static final int ROOM_ENTRANCE_X = BOSS_ROOM_X + SAFE - 1;

	/** A safe zone, by the offset of its low corner from the room's inner corner. */
	public record Zone(int x, int z) {
		public boolean contains(final int atX, final int atZ) {
			return atX >= this.x && atX < this.x + SAFE && atZ >= this.z && atZ < this.z + SAFE;
		}
	}

	/** The classic floor's safe zones: its entrance, and on a boss floor the room before the hall. */
	static java.util.List<Zone> classicZones(final int floor) {
		Zone entrance = new Zone(ENTRANCE_X, SAFE_Z);
		return hall(floor) == BOSS_HALL ? java.util.List.of(entrance, new Zone(BOSS_ROOM_X, SAFE_Z)) : java.util.List.of(entrance);
	}

	/** The side of the floor's hall, or nought for an ordinary floor. */
	public static int hall(final int floor) {
		if (floor % TowerElites.BOSS_EVERY == 0) {
			return BOSS_HALL;
		}
		return floor % TowerElites.ELITE_EVERY == 0 ? ELITE_HALL : 0;
	}

	/**
	 * Where full-height walls stand on the floor built to its plan, by offset from the room's inner corner,
	 * [x][z]. A pure function of the seed and the floor, and the plan's own array: never write to it.
	 */
	public static boolean[][] walls(final long seed, final int floor) {
		return TowerPlan.of(seed, floor).walls();
	}

	/** The walls of a floor built before the plans: the classic layout, drawn from the seed and the floor. */
	static boolean[][] legacyWalls(final long seed, final int floor) {
		return classicWalls(new java.util.Random(seed ^ SALT ^ (floor * 0x9E3779B97F4A7C15L)), floor, false);
	}

	/**
	 * The classic layout: carved as a perfect maze from the arrival's cell at the west, so every cell has
	 * exactly one way to every other; then the hall or the portal's nook at the east is opened.
	 */
	static boolean[][] classicWalls(final java.util.Random random, final int floor) {
		return classicWalls(random, floor, false);
	}

	/**
	 * The classic layout, with the boss floor's safe room walled as a ring (from {@link TowerPlan#RING_VERSION}) or joined to the hall
	 * by a path-wide door (before).
	 */
	static boolean[][] classicWalls(final java.util.Random random, final int floor, final boolean ring) {
		int size = TowerLayout.SIZE;
		boolean[][] wall = new boolean[size][size];
		for (boolean[] row : wall) {
			java.util.Arrays.fill(row, true);
		}
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
		int startX = (TowerPlan.CLASSIC_ARRIVAL_X - 1) / PITCH;
		int startZ = (TowerPlan.CLASSIC_MIDDLE_Z - 1) / PITCH;
		seen[startX][startZ] = true;
		carve(random, wall, seen, startX, startZ);
		if (hall > 0) {
			open(wall, hallLow, hallLow, hall, hall);
		} else {
			open(wall, NOOK_LOW_X, NOOK_LOW_Z, NOOK_HIGH_X - NOOK_LOW_X, NOOK_HIGH_Z - NOOK_LOW_Z + 1);
		}
		if (hall == 0) {
			// Solid behind the portal, so the way to it is three wide in front, not a two wide pocket beyond.
			int behind = TowerPlan.CLASSIC_PORTAL_X + 1;
			for (int x = behind; x < NOOK_HIGH_X; x++) {
				for (int z = NOOK_LOW_Z; z <= NOOK_HIGH_Z; z++) {
					wall[x][z] = true;
				}
			}
			// The portal's own slot stays open.
			for (int dz = 0; dz < TowerLayout.PORTAL_INNER_WIDTH; dz++) {
				wall[TowerPlan.CLASSIC_PORTAL_X][TowerPlan.CLASSIC_MIDDLE_Z - 1 + dz] = false;
			}
		}
		for (Zone zone : classicZones(floor)) {
			open(wall, zone.x(), zone.z(), SAFE, SAFE);
		}
		if (hall == BOSS_HALL) {
			bossRoom(wall, ring);
		}
		return wall;
	}

	/**
	 * The boss floor's safe room, in the walls carved so far: with a ring, the zone's own outermost blocks are solid but
	 * for two openings two wide (the exit through the hall's wall beside it and, across the room, the entrance onto the
	 * maze's cell); without one (before {@link TowerPlan#RING_VERSION}) a door from the hall as wide as a path.
	 */
	static void bossRoom(final boolean[][] wall, final boolean ring) {
		if (ring) {
			for (int x = BOSS_ROOM_X; x < BOSS_ROOM_X + SAFE; x++) {
				for (int z = SAFE_Z; z < SAFE_Z + SAFE; z++) {
					if (x == BOSS_ROOM_X || x == ROOM_ENTRANCE_X || z == SAFE_Z || z == SAFE_Z + SAFE - 1) {
						wall[x][z] = true;
					}
				}
			}
			open(wall, BOSS_ROOM_X - WALL, GATE_Z, WALL + 1, 2);
			open(wall, ROOM_ENTRANCE_X, GATE_Z, 1, 2);
		} else {
			// A door from the hall into the safe room, as wide as a path. The room's far side opens onto the
			// maze by itself, its wall being cleared with the room.
			int door = 1 + (TowerPlan.CLASSIC_MIDDLE_Z - 1) / PITCH * PITCH;
			open(wall, BOSS_ROOM_X - WALL, door, WALL, PATH);
		}
	}

	/** Carves a perfect maze over the cells not yet seen, from a start cell already marked seen. */
	static void carve(final java.util.Random random, final boolean[][] wall, final boolean[][] seen, final int startX, final int startZ) {
		java.util.ArrayDeque<int[]> trail = new java.util.ArrayDeque<>();
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
	}

	/**
	 * An ordinary floor's walls with its ends where the plan put them: a perfect maze carved from the
	 * arrival's cell over every cell but the portal's, which is then joined to one neighbour along x, so it
	 * is a dead end whose one way in is from that side. The far side of the portal's cell is filled solid,
	 * as the classic nook's is, leaving the way to the portal {@code FRONT} wide in front of it. The
	 * arrival's safe zone is opened last.
	 */
	static boolean[][] scatteredWalls(final java.util.Random random, final int arrivalCx, final int arrivalCz,
		final int portalCx, final int portalCz, final int facing, final Zone zone) {
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
		boolean[][] seen = new boolean[CELLS][CELLS];
		seen[portalCx][portalCz] = true;
		seen[arrivalCx][arrivalCz] = true;
		carve(random, wall, seen, arrivalCx, arrivalCz);
		int lowX = Math.min(cell(portalCx), cell(portalCx + facing));
		open(wall, lowX, cell(portalCz), PITCH + PATH, PATH);
		// Solid behind the portal, so the way to it is three wide in front, not a pocket beyond.
		int local = portalLocal(facing);
		int behind = cell(portalCx) + (facing < 0 ? local + 1 : 0);
		int behindEnd = cell(portalCx) + (facing < 0 ? PATH - 1 : local - 1);
		for (int x = behind; x <= behindEnd; x++) {
			for (int z = cell(portalCz); z < cell(portalCz) + PATH; z++) {
				wall[x][z] = true;
			}
		}
		// The two blocks of the portal's column beside its frame's posts are solid too, so no pocket is left.
		wall[cell(portalCx) + local][cell(portalCz)] = true;
		wall[cell(portalCx) + local][cell(portalCz) + PATH - 1] = true;
		open(wall, zone.x(), zone.z(), SAFE, SAFE);
		return wall;
	}

	/** The portal's column within its cell: {@link #PORTAL_FRONT} blocks in from the side it is entered by. */
	public static int portalLocal(final int facing) {
		return facing < 0 ? PORTAL_FRONT : PATH - 1 - PORTAL_FRONT;
	}

	/** How many blocks of the portal's cell lie in front of the portal: the way to it is this wide. */
	public static final int PORTAL_FRONT = 3;

	/** The low corner of a cell's open square, as an offset along either side. */
	static int cell(final int index) {
		return 1 + index * PITCH;
	}

	/** Marks every cell within a stretch of the room as taken, so the maze carves no way into it. */
	private static void shut(final boolean[][] seen, final int x, final int z, final int width, final int length) {
		for (int cx = (x - 1) / PITCH; cx <= (x + width - 2) / PITCH; cx++) {
			for (int cz = (z - 1) / PITCH; cz <= (z + length - 2) / PITCH; cz++) {
				seen[cx][cz] = true;
			}
		}
	}

	static void open(final boolean[][] wall, final int x, final int z, final int width, final int length) {
		for (int dx = 0; dx < width; dx++) {
			for (int dz = 0; dz < length; dz++) {
				wall[x + dx][z + dz] = false;
			}
		}
	}
}
