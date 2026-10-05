package com.stella.tower;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * The maze: paths six blocks wide between walls one block thick, on a grid of cells seven blocks apart. The room
 * fits nine cells to a side exactly, so every path is six wide; where a hall or a safe zone cuts across the grid, a
 * path beside it can come out narrower, but never under three.
 * <p>
 * A labyrinth floor ({@link TowerArchetype#LABYRINTH}) is two mazes, one above the other: the arrival in the lower,
 * the portal in a dead end of the upper, and one stair between. An elite floor opens a hall in the middle of its one
 * maze for its guardian, a boss floor a greater one, and the portal stands in the hall's middle.
 * <p>
 * Where the ends and the safe zones stand is the floor's {@link TowerPlan}; this class only carves. The
 * {@code classic} layout, the arrival on the middle line at the west and the portal in a nook at the east, is the
 * fallback a plan drops to if it finds no path.
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
	/** The labyrinth's stair: two wide, from the lower maze's floor to the upper's. */
	static final int STAIR_WIDTH = 2;
	private static final int PAIR_TRIES = 64;
	private static final int[][] WAYS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	private TowerMaze() {
	}

	/** The classic portal nook: the last cell column, three cell rows round the middle. */
	public static final int NOOK_LOW_X = 1 + (CELLS - 1) * PITCH;
	public static final int NOOK_HIGH_X = CELLS * PITCH;
	public static final int NOOK_LOW_Z = 1 + (TowerPlan.CLASSIC_MIDDLE_Z / PITCH - 1) * PITCH;
	public static final int NOOK_HIGH_Z = (TowerPlan.CLASSIC_MIDDLE_Z / PITCH + 2) * PITCH - 1;
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

	/**
	 * A safe zone, by the offset of its low corner from the room's inner corner: a square {@link #SAFE} to a side, from
	 * its story's height {@code y} up to {@code top}, which it does not include.
	 */
	public record Zone(int x, int z, int y, int top) {
		/** A zone of the ground story, up to the roof. */
		public Zone(final int x, final int z) {
			this(x, z, 0, TowerLayout.HEIGHT);
		}

		public boolean contains(final int atX, final int atZ) {
			return atX >= this.x && atX < this.x + SAFE && atZ >= this.z && atZ < this.z + SAFE;
		}

		/** Whether the spot, at a height from {@link TowerLayout#BASE_Y}, is in the zone's square and its band. */
		public boolean contains(final int atX, final int atY, final int atZ) {
			return contains(atX, atZ) && atY >= this.y && atY < this.top;
		}
	}

	/** The classic floor's safe zones, under a hall floor's lid: its entrance, and on a boss floor the room before the hall. */
	static List<Zone> classicZones(final int floor) {
		Zone entrance = new Zone(ENTRANCE_X, SAFE_Z, 0, TowerPlan.HALL_TOP);
		return hall(floor) == BOSS_HALL ? List.of(entrance, new Zone(BOSS_ROOM_X, SAFE_Z, 0, TowerPlan.HALL_TOP)) : List.of(entrance);
	}

	/** The side of the floor's hall, or nought for an ordinary floor. */
	public static int hall(final int floor) {
		if (floor % TowerElites.BOSS_EVERY == 0) {
			return BOSS_HALL;
		}
		return floor % TowerElites.ELITE_EVERY == 0 ? ELITE_HALL : 0;
	}

	/**
	 * Where a body cannot walk on the ground story of the floor built to its plan, by offset from the room's inner
	 * corner, [x][z]. A pure function of the seed and the floor, and the plan's own array: never write to it.
	 */
	public static boolean[][] walls(final long seed, final int floor) {
		return TowerPlan.of(seed, floor).walls();
	}

	/**
	 * The classic layout: carved as a perfect maze from the arrival's cell at the west, so every cell has
	 * exactly one way to every other; then the hall or the portal's nook at the east is opened, and a boss floor's
	 * safe room walled in a ring.
	 */
	static boolean[][] classicWalls(final Random random, final int floor) {
		int size = TowerLayout.SIZE;
		boolean[][] wall = solid();
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
		carve(random, wall, seen, startX, startZ, null);
		if (hall > 0) {
			open(wall, hallLow, hallLow, hall, hall);
		} else {
			open(wall, NOOK_LOW_X, NOOK_LOW_Z, NOOK_HIGH_X - NOOK_LOW_X, NOOK_HIGH_Z - NOOK_LOW_Z + 1);
			// Solid behind the portal, so the way to it is three wide in front, not a two wide pocket beyond.
			for (int x = TowerPlan.CLASSIC_PORTAL_X + 1; x < NOOK_HIGH_X; x++) {
				for (int z = NOOK_LOW_Z; z <= NOOK_HIGH_Z; z++) {
					wall[x][z] = true;
				}
			}
			for (int dz = 0; dz < TowerLayout.PORTAL_INNER_WIDTH; dz++) {
				wall[TowerPlan.CLASSIC_PORTAL_X][TowerPlan.CLASSIC_MIDDLE_Z - 1 + dz] = false;
			}
		}
		for (Zone zone : classicZones(floor)) {
			open(wall, zone.x(), zone.z(), SAFE, SAFE);
		}
		if (hall == BOSS_HALL) {
			bossRoom(wall);
		}
		return wall;
	}

	/**
	 * The boss floor's safe room, in the walls carved so far: the zone's own outermost blocks are solid but for two
	 * openings two wide, the exit through the hall's wall beside it and, across the room, the entrance onto the
	 * maze's cell.
	 */
	static void bossRoom(final boolean[][] wall) {
		for (int x = BOSS_ROOM_X; x < BOSS_ROOM_X + SAFE; x++) {
			for (int z = SAFE_Z; z < SAFE_Z + SAFE; z++) {
				if (x == BOSS_ROOM_X || x == ROOM_ENTRANCE_X || z == SAFE_Z || z == SAFE_Z + SAFE - 1) {
					wall[x][z] = true;
				}
			}
		}
		open(wall, BOSS_ROOM_X - WALL, GATE_Z, WALL + 1, 2);
		open(wall, ROOM_ENTRANCE_X, GATE_Z, 1, 2);
	}

	/**
	 * Carves a perfect maze over the cells not yet seen, from a start cell already marked seen. Where {@code joined}
	 * is given, each join is marked in it, both ways, by cell and by way ({@link #WAYS}' order).
	 */
	static void carve(final Random random, final boolean[][] wall, final boolean[][] seen, final int startX, final int startZ,
		final boolean[][][] joined) {
		ArrayDeque<int[]> trail = new ArrayDeque<>();
		trail.push(new int[] {startX, startZ});
		while (!trail.isEmpty()) {
			int[] at = trail.peek();
			List<int[]> next = new ArrayList<>();
			for (int w = 0; w < WAYS.length; w++) {
				int nx = at[0] + WAYS[w][0];
				int nz = at[1] + WAYS[w][1];
				if (nx >= 0 && nz >= 0 && nx < CELLS && nz < CELLS && !seen[nx][nz]) {
					next.add(new int[] {nx, nz, w});
				}
			}
			if (next.isEmpty()) {
				trail.pop();
				continue;
			}
			int[] to = next.get(random.nextInt(next.size()));
			join(wall, at[0], at[1], to[0], to[1]);
			if (joined != null) {
				joined[at[0]][at[1]][to[2]] = true;
				joined[to[0]][to[1]][to[2] ^ 1] = true;
			}
			seen[to[0]][to[1]] = true;
			trail.push(new int[] {to[0], to[1]});
		}
	}

	/** Opens the wall line between two neighbouring cells along the path's width. */
	static void join(final boolean[][] wall, final int ax, final int az, final int bx, final int bz) {
		int lowX = Math.min(cell(ax), cell(bx));
		int lowZ = Math.min(cell(az), cell(bz));
		open(wall, lowX, lowZ, ax != bx ? PITCH + PATH : PATH, az != bz ? PITCH + PATH : PATH);
	}

	/**
	 * A maze with its ends where the plan put them: a perfect maze carved from a start cell over every cell but the
	 * portal's, which is then joined to one neighbour along x, so it is a dead end whose one way in is from that side.
	 * The far side of the portal's cell is filled solid, leaving the way to the portal {@link #PORTAL_FRONT} wide in
	 * front of it.
	 */
	static boolean[][] scatteredWalls(final Random random, final int startCx, final int startCz, final int portalCx, final int portalCz,
		final int facing) {
		boolean[][] wall = cellsOpen();
		boolean[][] seen = new boolean[CELLS][CELLS];
		seen[portalCx][portalCz] = true;
		seen[startCx][startCz] = true;
		carve(random, wall, seen, startCx, startCz, null);
		join(wall, portalCx, portalCz, portalCx + facing, portalCz);
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
		return wall;
	}

	/**
	 * A labyrinth: the arrival in the lower maze, the portal in a dead end of the upper, and a stair two wide between
	 * them. The stair climbs along a corridor of two neighbouring cells the lower maze is made to join, against the
	 * corridor's side that opens on no other cell, so its low wall faces the corridor and no doorway. Null when no such
	 * pair is found clear of the arrival's zone.
	 */
	static TowerPlan.@Nullable Draft labyrinth(final Random random) {
		int top = TowerPlan.STORY - 1;
		boolean alongX = random.nextBoolean();
		boolean arrivalLow = random.nextBoolean();
		int arrivalAlong = arrivalLow ? random.nextInt(TowerPlan.END_CELLS) : CELLS - 1 - random.nextInt(TowerPlan.END_CELLS);
		int portalAlong = arrivalLow ? CELLS - 1 - random.nextInt(TowerPlan.END_CELLS) : random.nextInt(TowerPlan.END_CELLS);
		int arrivalAcross = random.nextInt(CELLS);
		int portalAcross = random.nextInt(CELLS);
		int arrivalCx = alongX ? arrivalAlong : arrivalAcross;
		int arrivalCz = alongX ? arrivalAcross : arrivalAlong;
		int portalCx = alongX ? portalAlong : portalAcross;
		int portalCz = alongX ? portalAcross : portalAlong;
		boolean lowNeighbour = portalCx > 0 && (portalCx == CELLS - 1 || random.nextBoolean());
		int facing = lowNeighbour ? -1 : 1;
		int arrivalX = cell(arrivalCx) + PATH / 2;
		int arrivalZ = cell(arrivalCz) + PATH / 2;
		int zoneMax = TowerLayout.SIZE - 1 - SAFE;
		Zone zone = new Zone(Math.max(1, Math.min(zoneMax, arrivalX - 3)), Math.max(1, Math.min(zoneMax, arrivalZ - 4)), 0, top);
		boolean[][] lower = cellsOpen();
		boolean[][] seen = new boolean[CELLS][CELLS];
		boolean[][][] joined = new boolean[CELLS][CELLS][WAYS.length];
		seen[arrivalCx][arrivalCz] = true;
		carve(random, lower, seen, arrivalCx, arrivalCz, joined);
		TowerFlight flight = null;
		for (int tries = 0; tries < PAIR_TRIES && flight == null; tries++) {
			flight = stairBetween(random, joined, zone, arrivalCx, arrivalCz, portalCx, portalCz);
		}
		if (flight == null) {
			return null;
		}
		int ax = flight.cellX(-1, 0);
		int az = flight.cellZ(-1, 0);
		int bx = flight.cellX(flight.length(), 0);
		int bz = flight.cellZ(flight.length(), 0);
		join(lower, (ax - 1) / PITCH, (az - 1) / PITCH, (bx - 1) / PITCH, (bz - 1) / PITCH);
		open(lower, zone.x(), zone.z(), SAFE, SAFE);
		boolean[][] upper = scatteredWalls(random, (bx - 1) / PITCH, (bz - 1) / PITCH, portalCx, portalCz, facing);
		int portalX = cell(portalCx) + portalLocal(facing);
		int portalZ = cell(portalCz) + 2;
		List<TowerStory> stories = List.of(
			TowerStory.of(0, top, TowerStory.full(), lower),
			TowerStory.of(TowerPlan.STORY, TowerPlan.STORY + top, TowerStory.full(), upper));
		return new TowerPlan.Draft(stories, List.of(flight), arrivalX, arrivalZ, portalX, portalZ, facing, 1, List.of(zone), null);
	}

	/**
	 * One try at the stair: two neighbouring cells, neither the arrival's nor the portal's, along a corridor side
	 * that neither cell opens through, clear of the zone. The stair's entry is the first cell's near end, its eight
	 * steps run on through the wall line between, and its exit is in the second cell.
	 */
	private static @Nullable TowerFlight stairBetween(final Random random, final boolean[][][] joined, final Zone zone,
		final int arrivalCx, final int arrivalCz, final int portalCx, final int portalCz) {
		int way = random.nextInt(WAYS.length);
		int acx = random.nextInt(CELLS);
		int acz = random.nextInt(CELLS);
		int bcx = acx + WAYS[way][0];
		int bcz = acz + WAYS[way][1];
		if (bcx < 0 || bcz < 0 || bcx >= CELLS || bcz >= CELLS) {
			return null;
		}
		boolean ends = acx == arrivalCx && acz == arrivalCz || bcx == arrivalCx && bcz == arrivalCz
			|| acx == portalCx && acz == portalCz || bcx == portalCx && bcz == portalCz;
		if (ends) {
			return null;
		}
		Direction direction = switch (way) {
			case 0 -> Direction.EAST;
			case 1 -> Direction.WEST;
			case 2 -> Direction.SOUTH;
			default -> Direction.NORTH;
		};
		boolean high = random.nextBoolean();
		// The ways across: the side the stair keeps to must open on no other cell from either.
		int sideWay = direction.getAxis() == Direction.Axis.X ? (high ? 2 : 3) : (high ? 0 : 1);
		if (joined[acx][acz][sideWay] || joined[bcx][bcz][sideWay]) {
			return null;
		}
		boolean positive = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE;
		int rise = TowerPlan.STORY;
		int alongStart = direction.getAxis() == Direction.Axis.X ? cell(acx) : cell(acz);
		int across = direction.getAxis() == Direction.Axis.X ? cell(acz) : cell(acx);
		int stripLow = high ? across + PATH - STAIR_WIDTH : across;
		// Step 0 is one block in from the cell's near end, which is its entry.
		int first = positive ? alongStart + 1 : alongStart + PATH - 2;
		Direction clockwise = direction.getClockWise();
		boolean clockwisePositive = clockwise.getAxisDirection() == Direction.AxisDirection.POSITIVE;
		int rightEdge = clockwisePositive ? stripLow : stripLow + STAIR_WIDTH - 1;
		int x = direction.getAxis() == Direction.Axis.X ? first : rightEdge;
		int z = direction.getAxis() == Direction.Axis.X ? rightEdge : first;
		TowerFlight flight = new TowerFlight(x, z, direction, STAIR_WIDTH, 0, rise, TowerFlight.STAIRS);
		// The whole corridor the stair runs in, entry to exit, keeps clear of the zone.
		for (int i = -1; i <= flight.length(); i++) {
			for (int w = -PATH; w <= PATH; w++) {
				int cx = flight.cellX(i, w);
				int cz = flight.cellZ(i, w);
				if (Math.abs(cx - (zone.x() + SAFE / 2)) <= SAFE / 2 + 1 && Math.abs(cz - (zone.z() + SAFE / 2)) <= SAFE / 2 + 1) {
					return null;
				}
			}
		}
		return flight;
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

	private static boolean[][] solid() {
		boolean[][] wall = new boolean[TowerLayout.SIZE][TowerLayout.SIZE];
		for (boolean[] row : wall) {
			Arrays.fill(row, true);
		}
		return wall;
	}

	/** Solid but for every cell's open square. */
	private static boolean[][] cellsOpen() {
		boolean[][] wall = solid();
		for (int cx = 0; cx < CELLS; cx++) {
			for (int cz = 0; cz < CELLS; cz++) {
				open(wall, cell(cx), cell(cz), PATH, PATH);
			}
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

	static void open(final boolean[][] wall, final int x, final int z, final int width, final int length) {
		for (int dx = 0; dx < width; dx++) {
			for (int dz = 0; dz < length; dz++) {
				wall[x + dx][z + dz] = false;
			}
		}
	}
}
