package com.stella.end;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * The castle's plan as pure data, free of any world: every coordinate is local to the 32 by 32 footprint, so the
 * same plan builds at any origin. A world seed and a floor number fix a floor, so two worlds differ and one world
 * never changes between loads. The vault chests and the stair corners sit at fixed places in every plan.
 */
public final class CastleLayout {
	/** The cell kinds of a floor grid. A door is an opening in a wall line, with solid wall above its lintel. */
	public static final byte OPEN = 0;
	public static final byte WALL = 1;
	public static final byte DOOR = 2;
	/** The smallest side of a room. Four such rooms always fit in a floor, and a corner room always holds a stairwell. */
	public static final int MIN_ROOM = 7;
	/** A door's clear height above the floor slab; the wall above it stays. */
	public static final int DOOR_HEIGHT = 5;

	private final long seed;
	private final List<FloorPlan> floors;
	private final RestPlan rest;

	CastleLayout(final long seed, final List<FloorPlan> floors, final RestPlan rest) {
		this.seed = seed;
		this.floors = floors;
		this.rest = rest;
	}

	/** The plan of a castle under a world seed. */
	public static CastleLayout generate(final long seed) {
		List<FloorPlan> floors = new ArrayList<>();
		for (int number = 1; number <= EndCastle.FLOORS; number++) {
			floors.add(number == EndCastle.FLOORS ? arena(seed, number) : divided(seed, number));
		}
		return new CastleLayout(seed, List.copyOf(floors), restPlan());
	}

	// -- Fixed places ----------------------------------------------------------------------------------------------

	private static final int SIZE = EndCastle.SIZE;
	/** The lowest and highest local cell of a floor's interior; the cells beyond are the outer wall. */
	private static final int LOW = 1;
	private static final int HIGH = SIZE - 2;
	/** The side of a reserved corner square: a stairwell with a cell to walk round it. */
	private static final int CORNER = 5;
	private static final Shaft NORTH_WEST = new Shaft(2, 2);
	private static final Shaft SOUTH_EAST = new Shaft(SIZE - 5, SIZE - 5);
	private static final Chest VAULT_NORTH_EAST = new Chest(HIGH, 2, Direction.WEST, true);
	private static final Chest VAULT_SOUTH_WEST = new Chest(LOW, HIGH - 1, Direction.EAST, true);

	/** The stairwell that climbs from a floor: south-east on odd floors, north-west on even ones. */
	static Shaft climbShaft(final int floor) {
		return floor % 2 == 1 ? SOUTH_EAST : NORTH_WEST;
	}

	/** The vault chest of a floor: north-east on odd floors, south-west on even ones. Never moves with the seed. */
	static Chest vaultChest(final int floor) {
		return floor % 2 == 1 ? VAULT_NORTH_EAST : VAULT_SOUTH_WEST;
	}

	private static RestPlan restPlan() {
		return new RestPlan(NORTH_WEST, new Shaft(7, 7), 2, 8, 7, 2, 9, 2, 10, 4);
	}

	private static Random random(final long seed, final int floor) {
		long mixed = seed * 0x9E3779B97F4A7C15L + floor * 0xC2B2AE3D27D4EB4FL + 0x165667B19E3779F9L;
		mixed ^= mixed >>> 31;
		mixed *= 0xBF58476D1CE4E5B9L;
		mixed ^= mixed >>> 29;
		return new Random(mixed);
	}

	private static boolean inCorner(final int x, final int z) {
		return (x <= CORNER || x >= SIZE - 1 - CORNER) && (z <= CORNER || z >= SIZE - 1 - CORNER);
	}

	// -- A floor cut into rooms ------------------------------------------------------------------------------------

	/** A floor of 4 to 8 rooms, found by cutting the interior in two again and again along walls with doorways. */
	private static FloorPlan divided(final long seed, final int floor) {
		Random random = random(seed, floor);
		byte[] cells = borderedGrid();
		List<int[]> leaves = new ArrayList<>();
		leaves.add(new int[] {LOW, LOW, HIGH, HIGH});
		List<int[]> walls = new ArrayList<>();
		int target = 4 + random.nextInt(5);
		while (leaves.size() < target) {
			List<int[]> splittable = new ArrayList<>();
			for (int[] leaf : leaves) {
				if (width(leaf) >= 2 * MIN_ROOM + 1 || depth(leaf) >= 2 * MIN_ROOM + 1) {
					splittable.add(leaf);
				}
			}
			if (splittable.isEmpty()) {
				break;
			}
			int[] leaf = splittable.get(random.nextInt(splittable.size()));
			boolean canX = width(leaf) >= 2 * MIN_ROOM + 1;
			boolean canZ = depth(leaf) >= 2 * MIN_ROOM + 1;
			boolean cutAcrossX = canX && (!canZ || width(leaf) > depth(leaf) || (width(leaf) == depth(leaf) && random.nextBoolean()));
			leaves.remove(leaf);
			if (cutAcrossX) {
				int c = leaf[0] + MIN_ROOM + random.nextInt(width(leaf) - 2 * MIN_ROOM);
				for (int z = leaf[1]; z <= leaf[3]; z++) {
					cells[c * SIZE + z] = WALL;
				}
				walls.add(new int[] {0, c, leaf[1], leaf[3]});
				leaves.add(new int[] {leaf[0], leaf[1], c - 1, leaf[3]});
				leaves.add(new int[] {c + 1, leaf[1], leaf[2], leaf[3]});
			} else {
				int c = leaf[1] + MIN_ROOM + random.nextInt(depth(leaf) - 2 * MIN_ROOM);
				for (int x = leaf[0]; x <= leaf[2]; x++) {
					cells[x * SIZE + c] = WALL;
				}
				walls.add(new int[] {1, c, leaf[0], leaf[2]});
				leaves.add(new int[] {leaf[0], leaf[1], leaf[2], c - 1});
				leaves.add(new int[] {leaf[0], c + 1, leaf[2], leaf[3]});
			}
		}
		List<Door> doors = new ArrayList<>();
		for (int[] wall : walls) {
			doors.add(cutDoor(cells, wall, random));
		}
		byte[] rooms = new byte[SIZE * SIZE];
		int id = 0;
		for (int[] leaf : leaves) {
			id++;
			for (int x = leaf[0]; x <= leaf[2]; x++) {
				for (int z = leaf[1]; z <= leaf[3]; z++) {
					rooms[x * SIZE + z] = (byte) id;
				}
			}
		}
		Shaft up = climbShaft(floor);
		Shaft arrival = floor == 1 ? null : climbShaft(floor - 1);
		boolean[] reserved = new boolean[SIZE * SIZE];
		for (int x = LOW; x <= HIGH; x++) {
			for (int z = LOW; z <= HIGH; z++) {
				reserved[x * SIZE + z] = inCorner(x, z);
			}
		}
		return finish(floor, cells, rooms, id, doors, up, arrival, reserved, random);
	}

	private static int width(final int[] leaf) {
		return leaf[2] - leaf[0] + 1;
	}

	private static int depth(final int[] leaf) {
		return leaf[3] - leaf[1] + 1;
	}

	private static byte[] borderedGrid() {
		byte[] cells = new byte[SIZE * SIZE];
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				boolean edge = x == 0 || z == 0 || x == SIZE - 1 || z == SIZE - 1;
				cells[x * SIZE + z] = edge ? WALL : OPEN;
			}
		}
		return cells;
	}

	/**
	 * Opens a doorway in a dividing wall: three cells wide where the wall allows, one where it does not. A doorway
	 * needs open room on both sides of every one of its cells, so it never lands on a T-junction.
	 */
	private static Door cutDoor(final byte[] cells, final int[] wall, final Random random) {
		boolean alongZ = wall[0] == 0;
		for (int half = 1; half >= 0; half--) {
			List<Integer> options = new ArrayList<>();
			for (int p = wall[2] + half; p <= wall[3] - half; p++) {
				boolean fits = true;
				for (int t = -half; t <= half; t++) {
					int x = alongZ ? wall[1] : p + t;
					int z = alongZ ? p + t : wall[1];
					int beforeX = alongZ ? x - 1 : x;
					int beforeZ = alongZ ? z : z - 1;
					int afterX = alongZ ? x + 1 : x;
					int afterZ = alongZ ? z : z + 1;
					fits &= cells[x * SIZE + z] == WALL && cells[beforeX * SIZE + beforeZ] == OPEN && cells[afterX * SIZE + afterZ] == OPEN;
				}
				if (fits) {
					options.add(p);
				}
			}
			if (!options.isEmpty()) {
				int p = options.get(random.nextInt(options.size()));
				int x0 = alongZ ? wall[1] : p - half;
				int z0 = alongZ ? p - half : wall[1];
				int x1 = alongZ ? wall[1] : p + half;
				int z1 = alongZ ? p + half : wall[1];
				for (int x = x0; x <= x1; x++) {
					for (int z = z0; z <= z1; z++) {
						cells[x * SIZE + z] = DOOR;
					}
				}
				return new Door(x0, z0, x1, z1, false);
			}
		}
		throw new IllegalStateException("no doorway fits the wall " + java.util.Arrays.toString(wall));
	}

	// -- Floor 6: an arena and four corner chambers ----------------------------------------------------------------

	/**
	 * Floor 6 is one open arena with four chambers in its corners: the stairs in, the barrier door and the stairs out,
	 * the vault, and a spare room. The arena is the room of the boss; the chambers keep the floor above four rooms.
	 */
	private static FloorPlan arena(final long seed, final int floor) {
		Random random = random(seed, floor);
		byte[] cells = borderedGrid();
		byte[] rooms = new byte[SIZE * SIZE];
		for (int x = LOW; x <= HIGH; x++) {
			for (int z = LOW; z <= HIGH; z++) {
				rooms[x * SIZE + z] = 1;
			}
		}
		boolean[] reserved = new boolean[SIZE * SIZE];
		List<Door> doors = new ArrayList<>();
		// Chamber ids follow the corners: north-west 2, north-east 3, south-west 4, south-east 5.
		for (int corner = 0; corner < 4; corner++) {
			boolean east = corner % 2 == 1;
			boolean south = corner >= 2;
			int wallX = east ? SIZE - 1 - CORNER - 1 : CORNER + 1;
			int wallZ = south ? SIZE - 1 - CORNER - 1 : CORNER + 1;
			int x0 = east ? wallX : LOW;
			int x1 = east ? HIGH : wallX;
			int z0 = south ? wallZ : LOW;
			int z1 = south ? HIGH : wallZ;
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					boolean wall = x == wallX || z == wallZ;
					cells[x * SIZE + z] = wall ? WALL : OPEN;
					rooms[x * SIZE + z] = (byte) (wall ? 0 : corner + 2);
					reserved[x * SIZE + z] = !wall && corner != 1;
				}
			}
			int doorZ = south ? SIZE - 1 - 4 : 2;
			for (int z = doorZ; z < doorZ + 3; z++) {
				cells[wallX * SIZE + z] = DOOR;
			}
			doors.add(new Door(wallX, doorZ, wallX, doorZ + 2, corner == 0));
		}
		return finish(floor, cells, rooms, 5, doors, NORTH_WEST, climbShaft(floor - 1), reserved, random);
	}

	// -- What both kinds of floor share ----------------------------------------------------------------------------

	private static FloorPlan finish(
		final int floor, final byte[] cells, final byte[] rooms, final int roomCount, final List<Door> doors, final Shaft up,
		final @Nullable Shaft arrival, final boolean[] reserved, final Random random
	) {
		Chest vault = vaultChest(floor);
		List<int[]> spawnCells = new ArrayList<>();
		List<int[]> perimeter = new ArrayList<>();
		for (int x = LOW; x <= HIGH; x++) {
			for (int z = LOW; z <= HIGH; z++) {
				if (cells[x * SIZE + z] != OPEN || rooms[x * SIZE + z] == 0 || reserved[x * SIZE + z] || nearDoor(cells, x, z)) {
					continue;
				}
				spawnCells.add(new int[] {x, z});
				if (facing(cells, x, z) != null) {
					perimeter.add(new int[] {x, z, rooms[x * SIZE + z]});
				}
			}
		}
		List<Chest> chests = new ArrayList<>();
		chests.add(vault);
		for (int placed = 0; placed < 2 && !perimeter.isEmpty(); placed++) {
			// A random room first, then a random wall cell in it, so a big room does not take every chest.
			List<Integer> roomIds = perimeter.stream().map(cell -> cell[2]).distinct().toList();
			int room = roomIds.get(random.nextInt(roomIds.size()));
			List<int[]> inRoom = perimeter.stream().filter(cell -> cell[2] == room).toList();
			int[] pick = inRoom.get(random.nextInt(inRoom.size()));
			chests.add(new Chest(pick[0], pick[1], facing(cells, pick[0], pick[1]), false));
			perimeter.remove(pick);
		}
		int landingX = arrival == null ? 3 : arrival.topX();
		int landingZ = arrival == null ? 3 : arrival.topZ();
		spawnCells.removeIf(cell -> chests.stream().anyMatch(chest -> chest.x() == cell[0] && chest.z() == cell[1]));
		return new FloorPlan(floor, cells, rooms, roomCount, List.copyOf(doors), up, arrival, List.copyOf(chests), landingX, landingZ, List.copyOf(spawnCells));
	}

	private static boolean nearDoor(final byte[] cells, final int x, final int z) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (cells[(x + dx) * SIZE + z + dz] == DOOR) {
					return true;
				}
			}
		}
		return false;
	}

	/** The way a chest at a cell faces: away from a wall that backs it. Null when no wall backs the cell. */
	private static @Nullable Direction facing(final byte[] cells, final int x, final int z) {
		for (Direction side : Direction.Plane.HORIZONTAL) {
			Direction away = side.getOpposite();
			if (cells[(x + side.getStepX()) * SIZE + z + side.getStepZ()] == WALL
				&& cells[(x + away.getStepX()) * SIZE + z + away.getStepZ()] == OPEN) {
				return away;
			}
		}
		return null;
	}

	public long seed() {
		return seed;
	}

	/** One floor's plan, 1 to {@link EndCastle#FLOORS}. */
	public FloorPlan floor(final int number) {
		return floors.get(number - 1);
	}

	public RestPlan rest() {
		return rest;
	}

	/** A 3 by 3 spiral stairwell. {@code ox} and {@code oz} are its lowest local x and z. */
	public record Shaft(int ox, int oz) {
		public static final int WIDTH = 3;
		/** The steps of one climb: one floor of rise. */
		public static final int STEPS = 16;
		private static final int RING = 8;
		/** Cells of the ceiling a stairwell leaves air: the last four steps open it, and the top step fills its own cell. */
		public static final int CEILING_HOLES = 3;
		private static final int[][] CELLS = {{0, 0}, {1, 0}, {2, 0}, {2, 1}, {2, 2}, {1, 2}, {0, 2}, {0, 1}};

		/** The local x of step {@code k}. Steps go round the pillar, two laps to a floor. */
		public int stepX(final int k) {
			return ox + CELLS[k % RING][0];
		}

		public int stepZ(final int k) {
			return oz + CELLS[k % RING][1];
		}

		/** The way step {@code k} rises: toward the next step round the ring. */
		public Direction stepFacing(final int k) {
			int[] from = CELLS[k % RING];
			int[] to = CELLS[(k + 1) % RING];
			if (to[0] != from[0]) {
				return to[0] > from[0] ? Direction.EAST : Direction.WEST;
			}
			return to[1] > from[1] ? Direction.SOUTH : Direction.NORTH;
		}

		/**
		 * Whether the ceiling above step {@code k} must be open. A climber stepping off step 12 or later lifts a head
		 * past the ceiling slab over the step they leave, so the last four steps of the climb have no slab above them.
		 */
		public boolean overheadOpen(final int k) {
			return k % RING >= RING - 4;
		}

		public int pillarX() {
			return ox + 1;
		}

		public int pillarZ() {
			return oz + 1;
		}

		public boolean contains(final int x, final int z) {
			return x >= ox && x < ox + WIDTH && z >= oz && z < oz + WIDTH;
		}

		/** The local x of the cell a climber steps onto at the top. */
		public int topX() {
			return ox;
		}

		public int topZ() {
			return oz;
		}
	}

	/** A chest, facing into its room. A vault chest sits at a fixed place; the others move with the seed. */
	public record Chest(int x, int z, Direction facing, boolean vault) {
	}

	/** A rectangle of door cells, inclusive. A barrier door stays shut until a later rule opens it. */
	public record Door(int x0, int z0, int x1, int z1, boolean barrier) {
		public boolean contains(final int x, final int z) {
			return x >= x0 && x <= x1 && z >= z0 && z <= z1;
		}
	}

	/** One floor: its walls, rooms, doors, stairs and chests, all in local cells. */
	public static final class FloorPlan {
		private final int number;
		private final byte[] cells;
		private final byte[] rooms;
		private final int roomCount;
		private final List<Door> doors;
		private final @Nullable Shaft up;
		private final @Nullable Shaft arrival;
		private final List<Chest> chests;
		private final int landingX;
		private final int landingZ;
		private final List<int[]> spawnCells;

		FloorPlan(
			final int number, final byte[] cells, final byte[] rooms, final int roomCount, final List<Door> doors,
			final @Nullable Shaft up, final @Nullable Shaft arrival, final List<Chest> chests, final int landingX,
			final int landingZ, final List<int[]> spawnCells
		) {
			this.number = number;
			this.cells = cells;
			this.rooms = rooms;
			this.roomCount = roomCount;
			this.doors = doors;
			this.up = up;
			this.arrival = arrival;
			this.chests = chests;
			this.landingX = landingX;
			this.landingZ = landingZ;
			this.spawnCells = spawnCells;
		}

		public int number() {
			return number;
		}

		/** {@link #OPEN}, {@link #WALL} or {@link #DOOR} at a local cell. */
		public byte kind(final int x, final int z) {
			return cells[x * EndCastle.SIZE + z];
		}

		/** The id, from 1, of the room an open cell belongs to; 0 for a wall or a door. */
		public int room(final int x, final int z) {
			return rooms[x * EndCastle.SIZE + z];
		}

		public int roomCount() {
			return roomCount;
		}

		public List<Door> doors() {
			return doors;
		}

		/** The stairwell that climbs to the next level. Every floor has one. */
		public @Nullable Shaft up() {
			return up;
		}

		/** The stairwell that arrives from the floor below; null on floor 1. */
		public @Nullable Shaft arrival() {
			return arrival;
		}

		public List<Chest> chests() {
			return chests;
		}

		public Chest vault() {
			return chests.stream().filter(Chest::vault).findFirst().orElseThrow();
		}

		/** The local x of an open cell where a climber from below, or a player from the End, stands first. */
		public int landingX() {
			return landingX;
		}

		public int landingZ() {
			return landingZ;
		}

		/** Local cells where a mob may spawn: open, in a room, clear of stairs and chests. Each is {x, z}. */
		public List<int[]> spawnCells() {
			return spawnCells;
		}
	}

	/** The rest area and the stairs to the rooftop, in local cells of the level above floor 6. */
	public record RestPlan(Shaft arrival, Shaft up, int bedX, int bedZ, int tableX, int tableZ, int anvilX, int anvilZ, int chestX, int chestZ) {
	}
}
