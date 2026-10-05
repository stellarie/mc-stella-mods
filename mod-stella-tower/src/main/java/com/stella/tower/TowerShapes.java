package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The walls of a hall floor's variations other than the plain maze, drawn from a floor's own {@link Random}. Every
 * generator is pure: the same random gives the same walls. A generator may give up (null) when its draw does not
 * fit, and {@link TowerPlan} then draws again.
 * <p>
 * Every layout is an array of walls by offset from the room's inner corner, {@code wall[x][z]}, the room's border
 * solid. Two grids are used: the maze's (nine cells of six, walls one thick) and the wide maze's (twenty-one cells
 * of two).
 */
final class TowerShapes {
	private static final int SIZE = TowerLayout.SIZE;
	private static final int[][] WAYS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	/** The wide maze: cells two wide, walls one thick, twenty-one to a side. */
	static final int WIDE_PATH = 2;
	static final int WIDE_PITCH = WIDE_PATH + TowerMaze.WALL;
	static final int WIDE_CELLS = (SIZE - TowerMaze.WALL) / WIDE_PITCH;
	/** The rooms of the wide maze: how many at least, the long side, and the short sides they come in. */
	static final int MIN_ROOMS = 3;
	static final int ROOM_LONG = 6;
	static final int[] ROOM_SHORT = {3, 4, 5};

	/** The staggered rooms: nine rows of six, rooms eight wide in a row that starts at the wall, offset by half in the next. */
	static final int BRICK_ROWS = TowerMaze.CELLS;
	static final int BRICK_WIDTH = 8;
	static final int BRICK_PITCH = BRICK_WIDTH + TowerMaze.WALL;
	/** The narrowest a door may be, in blocks. */
	static final int DOOR = 2;

	private TowerShapes() {
	}

	/** An elite or boss floor's canonical walls, and the rooms it was given (wide rooms only). */
	record HallWalls(boolean[][] walls, List<TowerPlan.Room> rooms) {
	}

	private static boolean[][] solid() {
		boolean[][] wall = new boolean[SIZE][SIZE];
		for (boolean[] row : wall) {
			java.util.Arrays.fill(row, true);
		}
		return wall;
	}

	// ------------------------------------------------------------------ a perfect maze on any grid

	/** Carves a perfect maze over the cells of a square grid not yet seen, from a start cell already marked seen. */
	private static void carve(final Random random, final boolean[][] wall, final boolean[][] seen, final int cells, final int pitch, final int path,
		final int startX, final int startZ) {
		java.util.ArrayDeque<int[]> trail = new java.util.ArrayDeque<>();
		trail.push(new int[] {startX, startZ});
		while (!trail.isEmpty()) {
			int[] at = trail.peek();
			List<int[]> next = new ArrayList<>();
			for (int[] way : WAYS) {
				int nx = at[0] + way[0];
				int nz = at[1] + way[1];
				if (nx >= 0 && nz >= 0 && nx < cells && nz < cells && !seen[nx][nz]) {
					next.add(new int[] {nx, nz});
				}
			}
			if (next.isEmpty()) {
				trail.pop();
				continue;
			}
			int[] to = next.get(random.nextInt(next.size()));
			join(wall, at, to, pitch, path);
			seen[to[0]][to[1]] = true;
			trail.push(to);
		}
	}

	/** Opens the wall between two neighbouring cells of a grid, along the path's width. */
	private static void join(final boolean[][] wall, final int[] from, final int[] to, final int pitch, final int path) {
		int lowX = 1 + Math.min(from[0], to[0]) * pitch;
		int lowZ = 1 + Math.min(from[1], to[1]) * pitch;
		TowerMaze.open(wall, lowX, lowZ, from[0] != to[0] ? pitch + path : path, from[1] != to[1] ? pitch + path : path);
	}

	private static int cellAt(final int index, final int pitch) {
		return 1 + index * pitch;
	}

	// ------------------------------------------------------------------ wide rooms

	/** The wide maze over every cell but the ones marked seen, carved from a start cell (marked seen by the caller). */
	private static boolean[][] wideMaze(final Random random, final boolean[][] seen, final int startX, final int startZ) {
		boolean[][] wall = solid();
		for (int cx = 0; cx < WIDE_CELLS; cx++) {
			for (int cz = 0; cz < WIDE_CELLS; cz++) {
				TowerMaze.open(wall, cellAt(cx, WIDE_PITCH), cellAt(cz, WIDE_PITCH), WIDE_PATH, WIDE_PATH);
			}
		}
		carve(random, wall, seen, WIDE_CELLS, WIDE_PITCH, WIDE_PATH, startX, startZ);
		return wall;
	}

	/**
	 * Rooms dropped at random over a maze of two wide paths: at least {@link #MIN_ROOMS}, each six by three, four or
	 * five, turned either way, clear of the given stretches and of each other. A room overlaps the paths it lies
	 * over, which joins it to them. Null when fewer than the least would fit.
	 */
	static List<TowerPlan.Room> dropRooms(final Random random, final boolean[][] wall, final List<TowerPlan.Room> keepClear) {
		int want = MIN_ROOMS + random.nextInt(3);
		List<TowerPlan.Room> rooms = new ArrayList<>();
		for (int tries = 0; tries < 400 && rooms.size() < want; tries++) {
			int shortSide = ROOM_SHORT[random.nextInt(ROOM_SHORT.length)];
			boolean turned = random.nextBoolean();
			int width = turned ? shortSide : ROOM_LONG;
			int length = turned ? ROOM_LONG : shortSide;
			TowerPlan.Room room = new TowerPlan.Room(1 + random.nextInt(SIZE - 1 - width), 1 + random.nextInt(SIZE - 1 - length), width, length);
			boolean clear = true;
			for (TowerPlan.Room avoid : keepClear) {
				clear &= !room.touches(avoid, 1);
			}
			for (TowerPlan.Room other : rooms) {
				clear &= !room.touches(other, 1);
			}
			if (clear) {
				rooms.add(room);
				TowerMaze.open(wall, room.x(), room.z(), room.width(), room.length());
			}
		}
		return rooms.size() >= MIN_ROOMS ? rooms : null;
	}

	// ------------------------------------------------------------------ staggered rooms

	/** The wall lines of a brick row, as x offsets from the room's corner: the border, every room's sides, the far border. */
	static int[] brickWalls(final int row, final int offset) {
		List<Integer> lines = new ArrayList<>();
		lines.add(0);
		int first = row % 2 == 0 ? BRICK_PITCH : offset;
		for (int x = first; x < SIZE - 1 - 2; x += BRICK_PITCH) {
			lines.add(x);
		}
		lines.add(SIZE - 1);
		return lines.stream().mapToInt(Integer::intValue).toArray();
	}

	/** The z of a brick row's first line of floor. */
	static int brickZ(final int row) {
		return TowerMaze.cell(row);
	}

	/**
	 * The room grid, rooms laid like bricks: rows of six, each room eight wide in the even rows, every odd row
	 * offset by half a room ({@code offset} blocks from the wall), so each room has a door to every room it touches.
	 * {@code blockedLow} and {@code blockedHigh} keep a stretch of the two ends' columns free of doors, for a portal
	 * pocket: the doors beside it then avoid the pocket's far blocks.
	 */
	static boolean[][] bricks(final Random random, final int offset, final int pocketRow, final int pocketSide) {
		boolean[][] wall = solid();
		int[][] lines = new int[BRICK_ROWS][];
		for (int row = 0; row < BRICK_ROWS; row++) {
			lines[row] = brickWalls(row, offset);
			for (int i = 0; i + 1 < lines[row].length; i++) {
				TowerMaze.open(wall, lines[row][i] + 1, brickZ(row), lines[row][i + 1] - lines[row][i] - 1, TowerMaze.PATH);
			}
		}
		// A door in every wall between two rooms of a row.
		for (int row = 0; row < BRICK_ROWS; row++) {
			for (int i = 1; i + 1 < lines[row].length; i++) {
				int at = brickZ(row) + random.nextInt(TowerMaze.PATH - DOOR + 1);
				TowerMaze.open(wall, lines[row][i], at, 1, DOOR);
			}
		}
		// A door between every two rooms of neighbouring rows that overlap by the door's width.
		for (int row = 0; row + 1 < BRICK_ROWS; row++) {
			int between = brickZ(row) + TowerMaze.PATH;
			for (int i = 0; i + 1 < lines[row].length; i++) {
				for (int j = 0; j + 1 < lines[row + 1].length; j++) {
					int low = Math.max(lines[row][i], lines[row + 1][j]) + 1;
					int high = Math.min(lines[row][i + 1], lines[row + 1][j + 1]) - 1;
					boolean pocket = pocketSide != 0 && (row == pocketRow && i == pocketRoom(lines[row], pocketSide)
						|| row + 1 == pocketRow && j == pocketRoom(lines[row + 1], pocketSide));
					if (pocket && pocketSide > 0) {
						// The pocket's far blocks stay solid, so a door must stop short of them.
						high = Math.min(high, SIZE - 1 - 3);
					} else if (pocket) {
						low = Math.max(low, 3);
					}
					if (high - low + 1 >= DOOR) {
						int at = low + random.nextInt(high - low + 1 - DOOR + 1);
						TowerMaze.open(wall, at, between, DOOR, 1);
					}
				}
			}
		}
		return wall;
	}

	/** The index of the room at the end of a row that a portal pocket fills: the last for the east, the first for the west. */
	private static int pocketRoom(final int[] lines, final int side) {
		return side > 0 ? lines.length - 2 : 0;
	}

	// ------------------------------------------------------------------ hall floors

	/**
	 * The canonical walls of an elite or boss floor drawn as a variation: the variation's own walls over the room, then
	 * the hall, the safe zones and on a boss floor the room's ring as the classic floor has them. Null when the draw
	 * does not fit.
	 */
	static HallWalls hallWalls(final Random random, final TowerPlan.Variation variation, final int floor) {
		int hall = TowerMaze.hall(floor);
		int hallLow = SIZE / 2 - hall / 2;
		boolean[][] wall;
		List<TowerPlan.Room> rooms = List.of();
		List<TowerPlan.Room> clear = new ArrayList<>();
		TowerPlan.Room hallBox = new TowerPlan.Room(hallLow - 2, hallLow - 2, hall + 4, hall + 4);
		TowerPlan.Room entrance = new TowerPlan.Room(TowerMaze.ENTRANCE_X, TowerMaze.SAFE_Z, TowerMaze.SAFE, TowerMaze.SAFE);
		clear.add(hallBox);
		clear.add(entrance);
		switch (variation) {
			case WIDE_ROOMS -> {
				int startX = (TowerPlan.CLASSIC_ARRIVAL_X - 1) / WIDE_PITCH;
				int startZ = (TowerPlan.CLASSIC_MIDDLE_Z - 1) / WIDE_PITCH;
				boolean[][] seen = new boolean[WIDE_CELLS][WIDE_CELLS];
				seen[startX][startZ] = true;
				wall = wideMaze(random, seen, startX, startZ);
				List<TowerPlan.Room> dropped = dropRooms(random, wall, clear);
				if (dropped == null) {
					return null;
				}
				rooms = List.copyOf(dropped);
			}
			case STAGGERED -> wall = bricks(random, random.nextBoolean() ? 4 : 5, -1, 0);
			default -> {
				return new HallWalls(TowerMaze.classicWalls(random, floor), List.of());
			}
		}
		if (hall == TowerMaze.BOSS_HALL) {
			// The hall and the room stand apart from the rest: solid between, so the room is the only way in.
			int low = TowerMaze.cell(2) - 1;
			int high = TowerMaze.cell(TowerMaze.CELLS - 3) + TowerMaze.PATH;
			for (int x = low; x <= high; x++) {
				for (int z = low; z <= high; z++) {
					wall[x][z] = true;
				}
			}
			for (int x = TowerMaze.BOSS_ROOM_X; x < TowerMaze.BOSS_ROOM_X + TowerMaze.SAFE; x++) {
				for (int z = TowerMaze.SAFE_Z; z < TowerMaze.SAFE_Z + TowerMaze.SAFE; z++) {
					wall[x][z] = true;
				}
			}
			// The cell the room's entrance opens on.
			TowerMaze.open(wall, TowerMaze.cell(TowerMaze.CELLS - 1), TowerMaze.cell(TowerPlan.CLASSIC_MIDDLE_Z / TowerMaze.PITCH), TowerMaze.PATH, TowerMaze.PATH);
		}
		TowerMaze.open(wall, hallLow, hallLow, hall, hall);
		for (TowerMaze.Zone zone : TowerMaze.classicZones(floor)) {
			TowerMaze.open(wall, zone.x(), zone.z(), TowerMaze.SAFE, TowerMaze.SAFE);
		}
		if (hall == TowerMaze.BOSS_HALL) {
			TowerMaze.bossRoom(wall);
		}
		return new HallWalls(wall, rooms);
	}
}
