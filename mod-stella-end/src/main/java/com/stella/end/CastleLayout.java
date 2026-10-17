package com.stella.end;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The castle's plan as pure data, free of any world: every coordinate is local to the 32 by 32 keep, so the same plan
 * builds at any origin. A world seed and a floor number fix a floor, so two worlds differ and one world never changes
 * between loads. The stairs, the vault chests and the great hall never move with the seed.
 */
public final class CastleLayout {
	/** The cell kinds of a floor grid. A door is an opening in a wall line; a void has no floor under it. */
	public static final byte OPEN = 0;
	public static final byte WALL = 1;
	public static final byte DOOR = 2;
	public static final byte VOID = 3;
	/** A door's clear height above the floor slab; the wall above it stays. */
	public static final int DOOR_HEIGHT = 5;

	private static final int SIZE = EndCastle.SIZE;

	/** The open air of the courtyard, from the floor 1 ground to the underside of the floor 4 slab. */
	public static final Rect COURTYARD = new Rect(10, 10, 21, 21);
	/** The wall lines of the floor 4 middle room, which stands over the courtyard. */
	public static final Rect MIDDLE_ROOM = new Rect(11, 11, 20, 20);
	/** The wall lines of the great hall on floor 6. */
	public static final Rect HALL = new Rect(4, 4, 27, 27);
	/** The inside of the rest room above the hall. */
	public static final Rect REST_ROOM = new Rect(8, 5, 26, 13);

	/**
	 * The three flights of the ring floors, one per floor, each in a wing of the ring round the courtyard. Flight 1 stands
	 * in a closed shaft, so nothing of it shows from the gate or from the portal landing.
	 */
	public static final Flight FLIGHT_1 = new Flight(2, 27, Direction.NORTH, true);
	public static final Flight FLIGHT_2 = new Flight(6, 2, Direction.EAST);
	public static final Flight FLIGHT_3 = new Flight(26, 3, Direction.SOUTH);
	/** The four flights from floor 4 to floor 5, one along each outer wall of the middle room. */
	public static final List<Flight> FLIGHTS_4 = List.of(
		new Flight(3, 8, Direction.EAST), new Flight(21, 3, Direction.SOUTH),
		new Flight(28, 21, Direction.WEST), new Flight(8, 28, Direction.NORTH)
	);
	/** The flight from floor 5 into the south-east corner of the arena. */
	public static final Flight FLIGHT_5 = new Flight(9, 24, Direction.EAST);
	/** The row of the floor 5 wall behind flight 5: the cells of the third lane touch it. */
	private static final int FLIGHT_5_BACK = 27;
	/** The stair behind the sealed door at the far end of the hall, up to the rest room. */
	public static final Flight APSE = new Flight(9, 5, Direction.EAST);
	/** The stair from the rest room to the hatch at the rooftop edge. */
	public static final Flight REST_EXIT = new Flight(24, 16, Direction.SOUTH);
	/** The 3 wide door in the south wall of floor 1. */
	public static final Rect GATE = new Rect(15, SIZE - 1, 17, SIZE - 1);

	/** The step of the floor 5 flight a player must reach to wake the Sovereign: the middle one, in the middle lane. */
	private static final int TRIGGER_STEP = Flight.STEPS / 2;

	private final long seed;
	private final List<FloorPlan> floors;
	private final RestPlan rest;
	private final RoofPlan roof;

	CastleLayout(final long seed, final List<FloorPlan> floors, final RestPlan rest, final RoofPlan roof) {
		this.seed = seed;
		this.floors = floors;
		this.rest = rest;
		this.roof = roof;
	}

	public static CastleLayout generate(final long seed) {
		List<FloorPlan> floors = new ArrayList<>();
		floors.add(ring(seed, 1));
		floors.add(ring(seed, 2));
		floors.add(ring(seed, 3));
		floors.add(middle(seed));
		floors.add(upper(seed));
		floors.add(hall(seed));
		return new CastleLayout(seed, List.copyOf(floors), restPlan(), roofPlan(seed));
	}

	/** The block of the middle step of the flight into the arena, in the world of the real castle. Reaching it wakes the Sovereign. */
	public static BlockPos sovereignTrigger() {
		int lane = Flight.WIDTH / 2;
		return new BlockPos(EndCastle.MIN_X + FLIGHT_5.x(TRIGGER_STEP, lane), EndCastle.slabY(5) + 1 + TRIGGER_STEP, EndCastle.MIN_Z + FLIGHT_5.z(TRIGGER_STEP, lane));
	}

	private static Random random(final long seed, final int floor) {
		long mixed = seed * 0x9E3779B97F4A7C15L + floor * 0xC2B2AE3D27D4EB4FL + 0x165667B19E3779F9L;
		mixed ^= mixed >>> 31;
		mixed *= 0xBF58476D1CE4E5B9L;
		mixed ^= mixed >>> 29;
		return new Random(mixed);
	}

	private static RestPlan restPlan() {
		return new RestPlan(REST_ROOM, REST_EXIT, 16, 5, 18, 5, 20, 5, 9, 5, 6,
			List.of(new Candle(9, 12, 3), new Candle(16, 12, 2), new Candle(24, 12, 4), new Candle(8, 9, 2)));
	}

	// -- The rooftop ---------------------------------------------------------------------------------------------------------

	/** The deck's middle cell on each axis. */
	public static final int ROOF_CENTRE = SIZE / 2;
	/** Cells within this many of the middle line on either axis are the dive lanes and the portal; ruins keep off them. */
	public static final int LANE_HALF = 3;
	/** The tallest ruin, in blocks over the deck. */
	public static final int RUIN_HEIGHT = 6;
	/** A ruin's far corners stay inside this squared distance from the middle, short of the parapet. */
	private static final int RUIN_REACH = 330;
	/** The squared distance from which the parapet stands. */
	public static final int PARAPET_FROM = 420;

	/** The house over the hatch: the cells of the rail columns, the run of the hole and the cell before it. */
	public static Rect hatchHouse() {
		Flight exit = restPlan().exit();
		return new Rect(exit.x(0, Flight.RAIL_LOW), exit.z(Flight.HOLE_FROM - 1, 0), exit.x(0, Flight.RAIL_HIGH), exit.z(Flight.STEPS - 1, 0));
	}

	private static RoofPlan roofPlan(final long seed) {
		Random random = random(seed, 7);
		Rect house = hatchHouse();
		Rect keepClear = new Rect(house.x0() - 2, house.z0() - 2, house.x1() + 2, house.z1() + 2);
		List<int[]> quadrants = new ArrayList<>(List.of(new int[] {-1, -1}, new int[] {1, -1}, new int[] {-1, 1}, new int[] {1, 1}));
		Collections.shuffle(quadrants, random);
		int wanted = 3 + random.nextInt(2);
		List<Ruin> ruins = new ArrayList<>();
		for (int[] quadrant : quadrants) {
			if (ruins.size() >= wanted) {
				break;
			}
			for (int attempt = 0; attempt < 40; attempt++) {
				int width = 5 + random.nextInt(3);
				int depth = 5 + random.nextInt(3);
				int a = LANE_HALF + 1 + random.nextInt(6);
				int b = LANE_HALF + 1 + random.nextInt(6);
				int xa = ROOF_CENTRE + quadrant[0] * a;
				int xb = ROOF_CENTRE + quadrant[0] * (a + width - 1);
				int za = ROOF_CENTRE + quadrant[1] * b;
				int zb = ROOF_CENTRE + quadrant[1] * (b + depth - 1);
				Rect bounds = new Rect(Math.min(xa, xb), Math.min(za, zb), Math.max(xa, xb), Math.max(za, zb));
				int far = (xb - ROOF_CENTRE) * (xb - ROOF_CENTRE) + (zb - ROOF_CENTRE) * (zb - ROOF_CENTRE);
				boolean overHatch = bounds.x1() >= keepClear.x0() && bounds.x0() <= keepClear.x1() && bounds.z1() >= keepClear.z0() && bounds.z0() <= keepClear.z1();
				if (far <= RUIN_REACH && !overHatch) {
					ruins.add(new Ruin(bounds, 4 + random.nextInt(RUIN_HEIGHT - 3)));
					break;
				}
			}
		}
		return new RoofPlan(List.copyOf(ruins), house);
	}

	// -- Shapes --------------------------------------------------------------------------------------------------------

	/** A rectangle of local cells, inclusive. */
	public record Rect(int x0, int z0, int x1, int z1) {
		public boolean contains(final int x, final int z) {
			return x >= x0 && x <= x1 && z >= z0 && z <= z1;
		}
	}

	/**
	 * A straight flight of {@link #STEPS} steps, {@link #WIDTH} lanes wide. Step {@code k} lies {@code k} cells along
	 * {@code direction} from the start and one block higher than step {@code k - 1}. Its last step is level with the
	 * slab above, and the slab over the three steps before it is open, so a mob that jumps up the last steps clears it.
	 * A rail column runs along each side of the run, one lane outside it. A shaft flight stands in a closed hall: walls
	 * enclose its run and a vestibule of {@link #FOOT_ROWS} rows at its foot, and the only door is in the vestibule's
	 * high side wall.
	 */
	public record Flight(int startX, int startZ, Direction direction, boolean shaft) {
		public static final int WIDTH = 3;
		public static final int STEPS = 16;
		/** The first step whose jump from the step below reaches into the slab above. */
		public static final int HOLE_FROM = 12;
		/** The lanes of the rail columns, on the low and the high side of the run. */
		public static final int RAIL_LOW = -1;
		public static final int RAIL_HIGH = WIDTH;
		/** The rows of the vestibule in front of a shaft flight, from step -3 to step -1. */
		public static final int FOOT_ROWS = 3;

		public Flight(final int startX, final int startZ, final Direction direction) {
			this(startX, startZ, direction, false);
		}

		/** The local x of step {@code k} in lane {@code lane}; {@code k} may be -1 for the foot or 16 for the landing. */
		public int x(final int k, final int lane) {
			return switch (direction) {
				case EAST -> startX + k;
				case WEST -> startX - k;
				default -> startX + lane;
			};
		}

		public int z(final int k, final int lane) {
			return switch (direction) {
				case SOUTH -> startZ + k;
				case NORTH -> startZ - k;
				default -> startZ + lane;
			};
		}

		/** Whether the slab above step {@code k} must be open: the steps before the last, whose climb reaches into it. */
		public boolean hole(final int k) {
			return k >= HOLE_FROM && k < STEPS - 1;
		}

		/** The step index of the run cell at a local cell, or -1 when the cell is not under the flight. */
		public int stepAt(final int x, final int z) {
			for (int k = 0; k < STEPS; k++) {
				for (int lane = 0; lane < WIDTH; lane++) {
					if (x(k, lane) == x && z(k, lane) == z) {
						return k;
					}
				}
			}
			return -1;
		}

		public boolean contains(final int x, final int z) {
			return stepAt(x, z) >= 0;
		}

		/** The cells of the walls that close a shaft flight: both sides, the cap at the foot and the cap over the top. Each is {x, z}. */
		public List<int[]> shaftWalls() {
			List<int[]> cells = new ArrayList<>();
			for (int k = -FOOT_ROWS - 1; k <= STEPS; k++) {
				cells.add(new int[] {x(k, RAIL_LOW - 1), z(k, RAIL_LOW - 1)});
				cells.add(new int[] {x(k, RAIL_HIGH + 1), z(k, RAIL_HIGH + 1)});
			}
			for (int lane = RAIL_LOW; lane <= RAIL_HIGH; lane++) {
				cells.add(new int[] {x(-FOOT_ROWS - 1, lane), z(-FOOT_ROWS - 1, lane)});
				cells.add(new int[] {x(STEPS, lane), z(STEPS, lane)});
			}
			return cells;
		}

		/** The open cells inside a shaft: the run, both rail columns and the vestibule. Each is {x, z}. */
		public List<int[]> shaftInterior() {
			List<int[]> cells = new ArrayList<>();
			for (int k = -FOOT_ROWS; k < STEPS; k++) {
				for (int lane = RAIL_LOW; lane <= RAIL_HIGH; lane++) {
					cells.add(new int[] {x(k, lane), z(k, lane)});
				}
			}
			return cells;
		}

		/** The doorway of a shaft: three cells in the high side wall, beside the vestibule. */
		public Door footDoor() {
			int lane = RAIL_HIGH + 1;
			int ax = x(-FOOT_ROWS, lane);
			int az = z(-FOOT_ROWS, lane);
			int bx = x(-1, lane);
			int bz = z(-1, lane);
			return new Door(Math.min(ax, bx), Math.min(az, bz), Math.max(ax, bx), Math.max(az, bz), false);
		}
	}

	/** A double chest: this is the left half, facing into its room. The right half lies one cell clockwise from it. */
	public record Chest(int x, int z, Direction facing, boolean vault) {
		public int partnerX() {
			return x + facing.getClockWise().getStepX();
		}

		public int partnerZ() {
			return z + facing.getClockWise().getStepZ();
		}
	}

	/** A rectangle of door cells, inclusive. A barrier door stays shut until a later rule opens it. */
	public record Door(int x0, int z0, int x1, int z1, boolean barrier) {
		public boolean contains(final int x, final int z) {
			return x >= x0 && x <= x1 && z >= z0 && z <= z1;
		}
	}

	/** A candle stand on the floor; the count is how many candles share the cell. */
	public record Candle(int x, int z, int count) {
	}

	/**
	 * A plank ledge along a wall, six blocks over the floor. {@code edge} is the wall-row cell where it starts and
	 * {@code toWall} the direction of the wall behind it. The ledge runs {@code length} cells along that wall, two
	 * cells deep, with bars on its open row; the wall row is the one a mob stands on. A ladder stands in the next cell.
	 */
	public record Ledge(int edgeX, int edgeZ, Direction toWall, int length) {
		public static final int LONGEST = 6;
		public static final int SHORTEST = 3;
		/** A floor gets ledges until it holds this many. */
		public static final int WANTED = 2;
		/** The planks lie this many blocks over the floor slab; a mob on them stands one block higher. */
		public static final int RISE = 6;

		public Direction along() {
			return toWall.getClockWise();
		}

		public Direction inward() {
			return toWall.getOpposite();
		}

		/** The wall-row cell {@code i} along the ledge; {@code i} equal to {@link #length} is the ladder cell. */
		public int x(final int i) {
			return edgeX + along().getStepX() * i;
		}

		public int z(final int i) {
			return edgeZ + along().getStepZ() * i;
		}

		/** The cell on the open row, beside wall-row cell {@code i}. */
		public int openX(final int i) {
			return x(i) + inward().getStepX();
		}

		public int openZ(final int i) {
			return z(i) + inward().getStepZ();
		}
	}

	/** A ruined room on the rooftop: its footprint in local cells and its height over the deck, in blocks. */
	public record Ruin(Rect bounds, int height) {
	}

	/** The rooftop above the deck: the ruined rooms, and the house that roofs the hatch. */
	public record RoofPlan(List<Ruin> ruins, Rect hatchHouse) {
	}

	/** The rest room above the hall, its props, and the stair from it to the hatch in the rooftop. */
	public record RestPlan(Rect room, Flight exit, int tableX, int tableZ, int anvilX, int anvilZ, int enderX, int enderZ, int chestX, int chestZ,
		int chestCount, List<Candle> candles) {
	}

	// -- A grid under construction -------------------------------------------------------------------------------------

	private static final class Grid {
		final byte[] cells = new byte[SIZE * SIZE];

		byte get(final int x, final int z) {
			return x < 0 || z < 0 || x >= SIZE || z >= SIZE ? WALL : cells[x * SIZE + z];
		}

		void set(final int x, final int z, final byte kind) {
			cells[x * SIZE + z] = kind;
		}

		void box(final int x0, final int z0, final int x1, final int z1, final byte kind) {
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					set(x, z, kind);
				}
			}
		}

		void outline(final Rect rect, final byte kind) {
			for (int x = rect.x0(); x <= rect.x1(); x++) {
				set(x, rect.z0(), kind);
				set(x, rect.z1(), kind);
			}
			for (int z = rect.z0(); z <= rect.z1(); z++) {
				set(rect.x0(), z, kind);
				set(rect.x1(), z, kind);
			}
		}

		static Grid bordered() {
			Grid grid = new Grid();
			grid.outline(new Rect(0, 0, SIZE - 1, SIZE - 1), WALL);
			return grid;
		}
	}

	/** A dividing wall: {@code alongZ} walls hold their x and run in z. */
	private record Divider(boolean alongZ, int fixed, int lo, int hi) {
		void place(final Grid grid) {
			for (int p = lo; p <= hi; p++) {
				grid.set(alongZ ? fixed : p, alongZ ? p : fixed, WALL);
			}
		}
	}

	/** Opens a doorway in a wall: three cells where it fits, one where it does not. Both sides of every cell must be open and unobstructed. */
	private static Door cutDoor(final Grid grid, final Divider wall, final boolean[] avoid, final Random random) {
		for (int width = 3; width >= 1; width -= 2) {
			int span = width - 1;
			boolean roomy = wall.hi() - wall.lo() + 1 >= width + 2;
			List<Integer> options = new ArrayList<>();
			for (int s = wall.lo() + (roomy ? 1 : 0); s + span <= wall.hi() - (roomy ? 1 : 0); s++) {
				boolean fits = true;
				for (int p = s; p <= s + span; p++) {
					int x = wall.alongZ() ? wall.fixed() : p;
					int z = wall.alongZ() ? p : wall.fixed();
					int bx = wall.alongZ() ? x - 1 : x;
					int bz = wall.alongZ() ? z : z - 1;
					int ax = wall.alongZ() ? x + 1 : x;
					int az = wall.alongZ() ? z : z + 1;
					fits &= grid.get(x, z) == WALL && grid.get(bx, bz) == OPEN && grid.get(ax, az) == OPEN
						&& !avoid[x * SIZE + z] && !avoid[bx * SIZE + bz] && !avoid[ax * SIZE + az];
				}
				if (fits) {
					options.add(s);
				}
			}
			if (!options.isEmpty()) {
				int s = options.get(random.nextInt(options.size()));
				int x0 = wall.alongZ() ? wall.fixed() : s;
				int z0 = wall.alongZ() ? s : wall.fixed();
				int x1 = wall.alongZ() ? wall.fixed() : s + span;
				int z1 = wall.alongZ() ? s + span : wall.fixed();
				grid.box(x0, z0, x1, z1, DOOR);
				return new Door(x0, z0, x1, z1, false);
			}
		}
		throw new IllegalStateException("no doorway fits the wall " + wall);
	}

	/** Closes a shaft flight in walls, opens its inside and cuts its doorway. */
	private static void raiseShaft(final Grid grid, final Flight flight, final List<Door> doors) {
		for (int[] cell : flight.shaftWalls()) {
			grid.set(cell[0], cell[1], WALL);
		}
		for (int[] cell : flight.shaftInterior()) {
			grid.set(cell[0], cell[1], OPEN);
		}
		Door door = flight.footDoor();
		grid.box(door.x0(), door.z0(), door.x1(), door.z1(), DOOR);
		doors.add(door);
	}

	private static boolean[] mask() {
		return new boolean[SIZE * SIZE];
	}

	private static void mark(final boolean[] mask, final int x, final int z) {
		if (x >= 0 && z >= 0 && x < SIZE && z < SIZE) {
			mask[x * SIZE + z] = true;
		}
	}

	/** Cells a door must not open onto: the low steps of a flight and its rails stand in the way of a walker, and the high ones open into the floor above. */
	private static void obstruct(final boolean[] mask, final List<Flight> ups, final List<Flight> arrivals) {
		for (Flight flight : ups) {
			for (int k = -1; k <= 1; k++) {
				for (int lane = Flight.RAIL_LOW; lane <= Flight.RAIL_HIGH; lane++) {
					mark(mask, flight.x(k, lane), flight.z(k, lane));
				}
			}
		}
		for (Flight flight : arrivals) {
			for (int k = Flight.HOLE_FROM - 1; k <= Flight.STEPS; k++) {
				for (int lane = Flight.RAIL_LOW; lane <= Flight.RAIL_HIGH; lane++) {
					mark(mask, flight.x(k, lane), flight.z(k, lane));
				}
			}
		}
	}

	private static void keepOff(final boolean[] mask, final Chest chest) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				mark(mask, chest.x() + dx, chest.z() + dz);
				mark(mask, chest.partnerX() + dx, chest.partnerZ() + dz);
			}
		}
	}

	// -- Floors 1 to 3: rooms in a ring round the courtyard -------------------------------------------------------------

	private static final List<Divider> RING_DIVIDERS = List.of(
		new Divider(true, 9, 1, 8), new Divider(true, 22, 1, 8), new Divider(false, 9, 23, 30), new Divider(false, 22, 23, 30),
		new Divider(true, 22, 23, 30), new Divider(true, 9, 23, 30), new Divider(false, 22, 1, 8), new Divider(false, 9, 1, 8)
	);

	/** The dividers a floor's flights run through; a flight needs one open hall, so these walls never stand. */
	private static List<Integer> forcedMerges(final int floor) {
		return switch (floor) {
			case 1 -> List.of(6);
			case 2 -> List.of(0);
			default -> List.of(1, 2);
		};
	}

	/** How many times floor 1 is drawn again when its flight shows from an entrance. */
	private static final int ENTRANCE_TRIES = 400;

	private static FloorPlan ring(final long seed, final int floor) {
		for (int attempt = 0; ; attempt++) {
			FloorPlan plan = ringOnce(seed, floor, attempt);
			// A seed that no draw satisfies keeps its last plan: a flight in sight beats a castle that cannot be built.
			if (floor != 1 || attempt >= ENTRANCE_TRIES || hiddenFromEntrances(plan)) {
				return plan;
			}
		}
	}

	private static FloorPlan ringOnce(final long seed, final int floor, final int attempt) {
		Random random = random(seed, floor + 16 * attempt);
		Grid grid = Grid.bordered();
		Rect box = new Rect(COURTYARD.x0() - 1, COURTYARD.z0() - 1, COURTYARD.x1() + 1, COURTYARD.z1() + 1);
		grid.outline(box, WALL);
		grid.box(COURTYARD.x0(), COURTYARD.z0(), COURTYARD.x1(), COURTYARD.z1(), floor == 1 ? OPEN : VOID);
		List<Integer> merged = new ArrayList<>(forcedMerges(floor));
		int extra = floor == 3 ? 1 : 2;
		List<Integer> spare = new ArrayList<>();
		for (int i = 0; i < RING_DIVIDERS.size(); i++) {
			if (!merged.contains(i)) {
				spare.add(i);
			}
		}
		for (int added = 0; added < extra; added++) {
			if (random.nextInt(4) == 0) {
				merged.add(spare.remove(random.nextInt(spare.size())));
			}
		}
		for (int i = 0; i < RING_DIVIDERS.size(); i++) {
			if (!merged.contains(i)) {
				RING_DIVIDERS.get(i).place(grid);
			}
		}
		List<Flight> ups = floor == 1 ? List.of(FLIGHT_1) : floor == 2 ? List.of(FLIGHT_2) : List.of(FLIGHT_3);
		List<Flight> arrivals = floor == 1 ? List.of() : floor == 2 ? List.of(FLIGHT_1) : List.of(FLIGHT_2);
		Chest vault = vaultChest(floor);
		boolean[] avoid = mask();
		obstruct(avoid, ups, arrivals);
		keepOff(avoid, vault);
		List<Door> doors = new ArrayList<>();
		for (Flight flight : ups) {
			if (flight.shaft()) {
				raiseShaft(grid, flight, doors);
			}
		}
		for (int i = 0; i < RING_DIVIDERS.size(); i++) {
			if (!merged.contains(i)) {
				doors.add(cutDoor(grid, RING_DIVIDERS.get(i), avoid, random));
			}
		}
		if (floor == 1) {
			grid.box(GATE.x0(), GATE.z0(), GATE.x1(), GATE.z1(), DOOR);
			doors.add(new Door(GATE.x0(), GATE.z0(), GATE.x1(), GATE.z1(), false));
			doors.add(cutDoor(grid, new Divider(false, box.z0(), COURTYARD.x0(), COURTYARD.x1()), avoid, random));
			doors.add(cutDoor(grid, new Divider(false, box.z1(), COURTYARD.x0(), COURTYARD.x1()), avoid, random));
			doors.add(cutDoor(grid, new Divider(true, box.x0(), COURTYARD.z0(), COURTYARD.z1()), avoid, random));
			doors.add(cutDoor(grid, new Divider(true, box.x1(), COURTYARD.z0(), COURTYARD.z1()), avoid, random));
		}
		boolean[] skip = mask();
		for (int x = COURTYARD.x0(); x <= COURTYARD.x1(); x++) {
			for (int z = COURTYARD.z0(); z <= COURTYARD.z1(); z++) {
				mark(skip, x, z);
			}
		}
		int landX = floor == 1 ? PORTAL_LANDING_X : landingX(arrivals.get(0));
		int landZ = floor == 1 ? PORTAL_LANDING_Z : landingZ(arrivals.get(0));
		return finish(floor, grid, skip, doors, ups, arrivals, mask(), vault, landX, landZ, random);
	}

	// -- What a player sees on coming in ---------------------------------------------------------------------------------

	/** Where a player from the End portal lands on floor 1: the north-west corner room. */
	public static final int PORTAL_LANDING_X = 3;
	public static final int PORTAL_LANDING_Z = 3;
	/** How far apart the sight lines to one step cell are, in cells; the extra lines run through its corners. */
	private static final double SIGHT_SPREAD = 0.35;
	private static final double SIGHT_STEP = 0.05;

	/** The points a player stands at on coming in: the three cells of the gate, and the portal landing. Each is {x, z} in cells. */
	public static List<double[]> entrances() {
		List<double[]> points = new ArrayList<>();
		for (int x = GATE.x0(); x <= GATE.x1(); x++) {
			points.add(new double[] {x + 0.5, GATE.z0() + 0.5});
		}
		points.add(new double[] {PORTAL_LANDING_X + 0.5, PORTAL_LANDING_Z + 0.5});
		return points;
	}

	private static boolean hiddenFromEntrances(final FloorPlan plan) {
		for (double[] from : entrances()) {
			for (Flight flight : plan.ups()) {
				if (shows(plan, flight, from[0], from[1])) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Whether any cell of the run of a flight can be seen from a point of the floor: a straight line from the point
	 * to the cell, or to a point near its corners, crosses no wall. A doorway is open at every height here, so a line
	 * that clears one counts as seen. That is stricter than the view of a player under a doorway lintel.
	 */
	public static boolean shows(final FloorPlan plan, final Flight flight, final double fromX, final double fromZ) {
		for (int k = 0; k < Flight.STEPS; k++) {
			for (int lane = 0; lane < Flight.WIDTH; lane++) {
				double cx = flight.x(k, lane) + 0.5;
				double cz = flight.z(k, lane) + 0.5;
				for (double[] offset : new double[][] {{0, 0}, {SIGHT_SPREAD, SIGHT_SPREAD}, {-SIGHT_SPREAD, SIGHT_SPREAD}, {SIGHT_SPREAD, -SIGHT_SPREAD},
					{-SIGHT_SPREAD, -SIGHT_SPREAD}}) {
					if (clearLine(plan, fromX, fromZ, cx + offset[0], cz + offset[1])) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static boolean clearLine(final FloorPlan plan, final double fromX, final double fromZ, final double toX, final double toZ) {
		double dx = toX - fromX;
		double dz = toZ - fromZ;
		int steps = (int) Math.ceil(Math.hypot(dx, dz) / SIGHT_STEP);
		int startX = (int) Math.floor(fromX);
		int startZ = (int) Math.floor(fromZ);
		int endX = (int) Math.floor(toX);
		int endZ = (int) Math.floor(toZ);
		for (int i = 1; i < steps; i++) {
			int x = (int) Math.floor(fromX + dx * i / steps);
			int z = (int) Math.floor(fromZ + dz * i / steps);
			boolean end = (x == startX && z == startZ) || (x == endX && z == endZ);
			if (!end && plan.kind(x, z) == WALL) {
				return false;
			}
		}
		return true;
	}

	// -- Floor 4: a middle room over the courtyard and four flights round it ---------------------------------------------

	private static FloorPlan middle(final long seed) {
		int floor = 4;
		Random random = random(seed, floor);
		Grid grid = Grid.bordered();
		grid.outline(MIDDLE_ROOM, WALL);
		List<Divider> dividers = List.of(
			new Divider(true, 20, 1, 10), new Divider(false, 20, 21, 30), new Divider(true, 11, 21, 30), new Divider(false, 11, 1, 10)
		);
		for (Divider divider : dividers) {
			divider.place(grid);
		}
		List<Flight> ups = FLIGHTS_4;
		List<Flight> arrivals = List.of(FLIGHT_3);
		Chest vault = vaultChest(floor);
		boolean[] avoid = mask();
		obstruct(avoid, ups, arrivals);
		keepOff(avoid, vault);
		List<Door> doors = new ArrayList<>();
		for (Divider divider : dividers) {
			doors.add(cutDoor(grid, divider, avoid, random));
		}
		for (Rect door : List.of(new Rect(17, 11, 19, 11), new Rect(20, 17, 20, 19), new Rect(12, 20, 14, 20), new Rect(11, 12, 11, 14))) {
			grid.box(door.x0(), door.z0(), door.x1(), door.z1(), DOOR);
			doors.add(new Door(door.x0(), door.z0(), door.x1(), door.z1(), false));
		}
		return finish(floor, grid, mask(), doors, ups, arrivals, mask(), vault, landingX(FLIGHT_3), landingZ(FLIGHT_3), random);
	}

	// -- Floor 5: a 20 wide hall with an open balcony on each side ---------------------------------------------------------

	private static FloorPlan upper(final long seed) {
		int floor = 5;
		Random random = random(seed, floor);
		Grid grid = new Grid();
		grid.outline(new Rect(6, 0, 25, SIZE - 1), WALL);
		for (int x : new int[] {0, SIZE - 3}) {
			for (int z : new int[] {0, SIZE - 3}) {
				grid.box(x, z, x + 2, z + 2, WALL);
			}
		}
		int northSplit = 12 + random.nextInt(2);
		int middleSplit = 14 + random.nextInt(4);
		List<Divider> dividers = List.of(
			new Divider(false, 11, 7, 24), new Divider(false, 20, 7, 24), new Divider(true, northSplit, 1, 10), new Divider(true, middleSplit, 12, 19)
		);
		for (Divider divider : dividers) {
			divider.place(grid);
		}
		// The wall under the hall's south wall backs the stair into the arena along its whole length. One cell at its west end is the way round.
		new Divider(false, FLIGHT_5_BACK, 7, 24).place(grid);
		grid.set(7, FLIGHT_5_BACK, DOOR);
		List<Flight> ups = List.of(FLIGHT_5);
		List<Flight> arrivals = FLIGHTS_4;
		Chest vault = vaultChest(floor);
		boolean[] avoid = mask();
		obstruct(avoid, ups, arrivals);
		keepOff(avoid, vault);
		List<Door> doors = new ArrayList<>();
		for (Divider divider : dividers) {
			doors.add(cutDoor(grid, divider, avoid, random));
		}
		doors.add(new Door(7, FLIGHT_5_BACK, 7, FLIGHT_5_BACK, false));
		for (int wall : new int[] {6, 25}) {
			for (int[] band : new int[][] {{1, 10}, {12, 19}, {21, 30}}) {
				doors.add(cutDoor(grid, new Divider(true, wall, band[0], band[1]), avoid, random));
			}
		}
		boolean[] balcony = mask();
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				if (x < 6 || x > 25) {
					mark(balcony, x, z);
				}
			}
		}
		Flight east = FLIGHTS_4.get(1);
		return finish(floor, grid, mask(), doors, ups, arrivals, balcony, vault, landingX(east), landingZ(east), random);
	}

	// -- Floor 6: the great hall, its terrace, and the stair behind the sealed door --------------------------------------

	private static FloorPlan hall(final long seed) {
		int floor = 6;
		Random random = random(seed, floor);
		Grid grid = new Grid();
		for (int x : new int[] {0, SIZE - 3}) {
			for (int z : new int[] {0, SIZE - 3}) {
				grid.box(x, z, x + 2, z + 2, WALL);
			}
		}
		grid.outline(HALL, WALL);
		new Divider(false, 8, HALL.x0() + 1, HALL.x1() - 1).place(grid);
		List<Door> doors = new ArrayList<>();
		grid.box(6, 8, 8, 8, DOOR);
		doors.add(new Door(6, 8, 8, 8, true));
		for (Rect door : List.of(new Rect(4, 14, 4, 16), new Rect(27, 14, 27, 16), new Rect(14, 27, 16, 27))) {
			grid.box(door.x0(), door.z0(), door.x1(), door.z1(), DOOR);
			doors.add(new Door(door.x0(), door.z0(), door.x1(), door.z1(), false));
		}
		Chest vault = vaultChest(floor);
		boolean[] terrace = mask();
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				if (!HALL.contains(x, z)) {
					mark(terrace, x, z);
				}
			}
		}
		byte[] rooms = new byte[SIZE * SIZE];
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				if (grid.get(x, z) == OPEN) {
					rooms[x * SIZE + z] = (byte) (terrace[x * SIZE + z] ? 3 : z >= 9 ? 1 : 2);
				}
			}
		}
		return finish(floor, grid, rooms, 3, doors, List.of(APSE), List.of(FLIGHT_5), terrace, vault, landingX(FLIGHT_5), landingZ(FLIGHT_5), random);
	}

	// -- What every floor shares ------------------------------------------------------------------------------------

	/** The vault of a floor sits at a fixed place, backed by a wall: it never moves with the seed. */
	static Chest vaultChest(final int floor) {
		return switch (floor) {
			case 1 -> new Chest(30, 5, Direction.WEST, true);
			case 2 -> new Chest(1, 26, Direction.EAST, true);
			case 3 -> new Chest(30, 27, Direction.WEST, true);
			case 4 -> new Chest(1, 22, Direction.EAST, true);
			case 5 -> new Chest(24, 3, Direction.WEST, true);
			default -> new Chest(5, 20, Direction.EAST, true);
		};
	}

	private static int landingX(final Flight arrival) {
		return arrival.x(Flight.STEPS, Flight.WIDTH / 2);
	}

	private static int landingZ(final Flight arrival) {
		return arrival.z(Flight.STEPS, Flight.WIDTH / 2);
	}

	private static FloorPlan finish(
		final int floor, final Grid grid, final boolean[] skip, final List<Door> doors, final List<Flight> ups, final List<Flight> arrivals,
		final boolean[] noSpawn, final Chest vault, final int landX, final int landZ, final Random random
	) {
		byte[] rooms = new byte[SIZE * SIZE];
		int count = 0;
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				if (grid.get(x, z) == OPEN && rooms[x * SIZE + z] == 0 && !skip[x * SIZE + z]) {
					count++;
					flood(grid, skip, rooms, x, z, (byte) count);
				}
			}
		}
		return finish(floor, grid, rooms, count, doors, ups, arrivals, noSpawn, vault, landX, landZ, random);
	}

	private static void flood(final Grid grid, final boolean[] skip, final byte[] rooms, final int startX, final int startZ, final byte id) {
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		queue.add(new int[] {startX, startZ});
		rooms[startX * SIZE + startZ] = id;
		int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!queue.isEmpty()) {
			int[] at = queue.poll();
			for (int[] step : steps) {
				int x = at[0] + step[0];
				int z = at[1] + step[1];
				if (x >= 0 && z >= 0 && x < SIZE && z < SIZE && grid.get(x, z) == OPEN && !skip[x * SIZE + z] && rooms[x * SIZE + z] == 0) {
					rooms[x * SIZE + z] = id;
					queue.add(new int[] {x, z});
				}
			}
		}
	}

	private static FloorPlan finish(
		final int floor, final Grid grid, final byte[] rooms, final int roomCount, final List<Door> doors, final List<Flight> ups,
		final List<Flight> arrivals, final boolean[] noSpawn, final Chest vault, final int landX, final int landZ, final Random random
	) {
		boolean[] stairs = mask();
		for (Flight flight : ups) {
			for (int k = 0; k < Flight.STEPS; k++) {
				for (int lane = Flight.RAIL_LOW; lane <= Flight.RAIL_HIGH; lane++) {
					mark(stairs, flight.x(k, lane), flight.z(k, lane));
				}
			}
			for (int lane = 0; lane < Flight.WIDTH; lane++) {
				mark(stairs, flight.x(-1, lane), flight.z(-1, lane));
			}
		}
		for (Flight flight : arrivals) {
			for (int k = Flight.HOLE_FROM - 1; k <= Flight.STEPS; k++) {
				for (int lane = Flight.RAIL_LOW; lane <= Flight.RAIL_HIGH; lane++) {
					mark(stairs, flight.x(k, lane), flight.z(k, lane));
				}
			}
		}
		boolean[] clear = mask();
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				if (grid.get(x, z) == DOOR) {
					for (int dx = -1; dx <= 1; dx++) {
						for (int dz = -1; dz <= 1; dz++) {
							mark(clear, x + dx, z + dz);
						}
					}
				}
				if (stairs[x * SIZE + z]) {
					mark(clear, x, z);
				}
			}
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				mark(clear, landX + dx, landZ + dz);
			}
		}
		boolean[] usable = mask();
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				usable[x * SIZE + z] = grid.get(x, z) == OPEN && rooms[x * SIZE + z] > 0 && !clear[x * SIZE + z];
			}
		}
		List<Chest> chests = new ArrayList<>();
		if (!pairFits(grid, usable, vault)) {
			throw new IllegalStateException("the vault of floor " + floor + " does not fit at " + vault);
		}
		chests.add(vault);
		keepOff(clear, vault);
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				usable[x * SIZE + z] &= !clear[x * SIZE + z];
			}
		}
		boolean[] taken = mask();
		keepOff(taken, vault);
		List<Chest> candidates = new ArrayList<>();
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				for (Direction facing : Direction.Plane.HORIZONTAL) {
					Chest candidate = new Chest(x, z, facing, false);
					if (!noSpawn[x * SIZE + z] && pairFits(grid, usable, candidate) && !overlaps(taken, candidate)) {
						candidates.add(candidate);
					}
				}
			}
		}
		for (int placed = 0; placed < 2 && !candidates.isEmpty(); placed++) {
			// A random room first, then a random spot in it, so a big room does not take every chest.
			List<Integer> roomIds = candidates.stream().map(chest -> (int) rooms[chest.x() * SIZE + chest.z()]).distinct().toList();
			int room = roomIds.get(random.nextInt(roomIds.size()));
			List<Chest> inRoom = candidates.stream().filter(chest -> rooms[chest.x() * SIZE + chest.z()] == room).toList();
			Chest pick = inRoom.get(random.nextInt(inRoom.size()));
			chests.add(pick);
			keepOff(taken, pick);
			candidates.removeIf(chest -> overlaps(taken, chest));
		}
		boolean[] chestCell = mask();
		for (Chest chest : chests) {
			mark(chestCell, chest.x(), chest.z());
			mark(chestCell, chest.partnerX(), chest.partnerZ());
			mark(chestCell, chest.x() + chest.facing().getStepX(), chest.z() + chest.facing().getStepZ());
			mark(chestCell, chest.partnerX() + chest.facing().getStepX(), chest.partnerZ() + chest.facing().getStepZ());
		}
		List<int[]> interior = new ArrayList<>();
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				if (usable[x * SIZE + z] && !noSpawn[x * SIZE + z] && !chestCell[x * SIZE + z] && insideRoom(grid, x, z)) {
					interior.add(new int[] {x, z, rooms[x * SIZE + z]});
				}
			}
		}
		List<int[]> props = new ArrayList<>();
		boolean[] propCell = mask();
		if (floor <= 5) {
			for (int room = 1; room <= roomCount; room++) {
				int id = room;
				List<int[]> cells = new ArrayList<>(interior.stream().filter(cell -> cell[2] == id).toList());
				for (int placed = 0; placed < 2 && !cells.isEmpty(); placed++) {
					int[] pick = cells.remove(random.nextInt(cells.size()));
					props.add(new int[] {pick[0], pick[1]});
					mark(propCell, pick[0], pick[1]);
				}
			}
		}
		List<int[]> spawnCells = new ArrayList<>();
		for (int[] cell : interior) {
			if (floor <= 5 && !propCell[cell[0] * SIZE + cell[1]]) {
				spawnCells.add(new int[] {cell[0], cell[1]});
			}
		}
		List<Candle> candles = new ArrayList<>();
		for (int room = 1; room <= roomCount; room++) {
			int id = room;
			List<int[]> edge = new ArrayList<>();
			for (int x = 0; x < SIZE; x++) {
				for (int z = 0; z < SIZE; z++) {
					if (usable[x * SIZE + z] && rooms[x * SIZE + z] == id && !chestCell[x * SIZE + z] && !propCell[x * SIZE + z] && !insideRoom(grid, x, z)) {
						edge.add(new int[] {x, z});
					}
				}
			}
			int wanted = 2 + random.nextInt(3);
			if (edge.size() < wanted) {
				for (int x = 0; x < SIZE; x++) {
					for (int z = 0; z < SIZE; z++) {
						if (usable[x * SIZE + z] && rooms[x * SIZE + z] == id && !chestCell[x * SIZE + z] && !propCell[x * SIZE + z] && insideRoom(grid, x, z)) {
							edge.add(new int[] {x, z});
						}
					}
				}
			}
			for (int placed = 0; placed < wanted && !edge.isEmpty(); placed++) {
				int[] pick = edge.remove(random.nextInt(edge.size()));
				candles.add(new Candle(pick[0], pick[1], 1 + random.nextInt(4)));
			}
		}
		boolean[] lit = mask();
		for (Candle candle : candles) {
			mark(lit, candle.x(), candle.z());
		}
		List<int[]> edges = new ArrayList<>();
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				if (usable[x * SIZE + z] && !noSpawn[x * SIZE + z] && !chestCell[x * SIZE + z] && !propCell[x * SIZE + z] && !lit[x * SIZE + z]
					&& !insideRoom(grid, x, z)) {
					for (Direction side : Direction.Plane.HORIZONTAL) {
						if (grid.get(x + side.getStepX(), z + side.getStepZ()) == WALL) {
							edges.add(new int[] {x, z, side.get2DDataValue()});
							break;
						}
					}
				}
			}
		}
		FloorPlan plan = new FloorPlan(floor, grid.cells, rooms, roomCount, List.copyOf(doors), List.copyOf(ups), List.copyOf(arrivals), List.copyOf(chests), landX,
			landZ, List.copyOf(spawnCells), List.copyOf(props), List.copyOf(candles), List.copyOf(edges), stairs);
		if (floor <= 5) {
			plan.ledges = List.copyOf(findLedges(plan, new Random(random.nextLong())));
		}
		return plan;
	}

	/**
	 * The ledges of a floor. A first pass gives each room the longest ledge that fits. While the floor holds fewer than
	 * {@link Ledge#WANTED}, further passes try shorter ledges in the rooms that have none, then in every room.
	 */
	private static List<Ledge> findLedges(final FloorPlan plan, final Random random) {
		List<Ledge> found = new ArrayList<>();
		Set<Long> taken = new HashSet<>();
		for (int length = Ledge.LONGEST; length >= Ledge.SHORTEST; length--) {
			for (int room = 1; room <= plan.roomCount(); room++) {
				int id = room;
				boolean has = found.stream().anyMatch(ledge -> plan.room(ledge.edgeX(), ledge.edgeZ()) == id);
				if ((length == Ledge.LONGEST || found.size() < Ledge.WANTED) && !has) {
					addLedge(plan, room, length, random, taken, found);
				}
			}
		}
		for (int room = 1; room <= plan.roomCount() && found.size() < Ledge.WANTED; room++) {
			addLedge(plan, room, Ledge.SHORTEST, random, taken, found);
		}
		return found;
	}

	private static boolean inGrid(final int x, final int z) {
		return x >= 0 && z >= 0 && x < SIZE && z < SIZE;
	}

	private static boolean walled(final FloorPlan plan, final int x, final int z) {
		return inGrid(x, z) && plan.kind(x, z) == WALL;
	}

	private static void addLedge(final FloorPlan plan, final int room, final int length, final Random random, final Set<Long> taken, final List<Ledge> found) {
		List<Ledge> starts = new ArrayList<>();
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				if (plan.room(x, z) != room) {
					continue;
				}
				for (Direction toWall : Direction.Plane.HORIZONTAL) {
					if (walled(plan, x + toWall.getStepX(), z + toWall.getStepZ())) {
						starts.add(new Ledge(x, z, toWall, length));
					}
				}
			}
		}
		Collections.shuffle(starts, random);
		for (Ledge ledge : starts) {
			boolean fits = true;
			for (int i = 0; i <= length && fits; i++) {
				int x = ledge.x(i);
				int z = ledge.z(i);
				int ox = ledge.openX(i);
				int oz = ledge.openZ(i);
				fits = walled(plan, x + ledge.toWall().getStepX(), z + ledge.toWall().getStepZ()) && inGrid(ox, oz) && plan.calm(x, z) && plan.calm(ox, oz)
					&& plan.room(ox, oz) == room && !taken.contains((long) x * SIZE + z) && !taken.contains((long) ox * SIZE + oz);
			}
			if (fits) {
				for (int i = 0; i <= length; i++) {
					taken.add((long) ledge.x(i) * SIZE + ledge.z(i));
					taken.add((long) ledge.openX(i) * SIZE + ledge.openZ(i));
				}
				found.add(ledge);
				return;
			}
		}
	}

	/** Whether no wall, door or void touches the cell, even by a corner: a spawn or a prop stands clear of every wall. */
	private static boolean insideRoom(final Grid grid, final int x, final int z) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (grid.get(x + dx, z + dz) != OPEN) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean overlaps(final boolean[] taken, final Chest chest) {
		return taken[chest.x() * SIZE + chest.z()] || taken[chest.partnerX() * SIZE + chest.partnerZ()];
	}

	/** Whether a double chest fits: both halves and the cells before them are usable, and a wall backs each half. */
	private static boolean pairFits(final Grid grid, final boolean[] usable, final Chest chest) {
		int stepX = chest.facing().getStepX();
		int stepZ = chest.facing().getStepZ();
		for (int half = 0; half < 2; half++) {
			int x = half == 0 ? chest.x() : chest.partnerX();
			int z = half == 0 ? chest.z() : chest.partnerZ();
			if (x < 0 || z < 0 || x >= SIZE || z >= SIZE || !usable[x * SIZE + z]) {
				return false;
			}
			int fx = x + stepX;
			int fz = z + stepZ;
			if (fx < 0 || fz < 0 || fx >= SIZE || fz >= SIZE || grid.get(fx, fz) != OPEN || !usable[fx * SIZE + fz]) {
				return false;
			}
			if (grid.get(x - stepX, z - stepZ) != WALL) {
				return false;
			}
		}
		return true;
	}

	public long seed() {
		return seed;
	}

	public FloorPlan floor(final int number) {
		return floors.get(number - 1);
	}

	public RestPlan rest() {
		return rest;
	}

	public RoofPlan roof() {
		return roof;
	}

	/** One floor: its walls, rooms, doors, stairs, chests and the cells mobs may spawn on, all in local cells. */
	public static final class FloorPlan {
		private final int number;
		private final byte[] cells;
		private final byte[] rooms;
		private final int roomCount;
		private final List<Door> doors;
		private final List<Flight> ups;
		private final List<Flight> arrivals;
		private final List<Chest> chests;
		private final int landingX;
		private final int landingZ;
		private final List<int[]> spawnCells;
		private final List<int[]> props;
		private final List<Candle> candles;
		private final List<int[]> edges;
		private final boolean[] stairs;
		private List<Ledge> ledges = List.of();

		FloorPlan(
			final int number, final byte[] cells, final byte[] rooms, final int roomCount, final List<Door> doors, final List<Flight> ups,
			final List<Flight> arrivals, final List<Chest> chests, final int landingX, final int landingZ, final List<int[]> spawnCells,
			final List<int[]> props, final List<Candle> candles, final List<int[]> edges, final boolean[] stairs
		) {
			this.number = number;
			this.cells = cells;
			this.rooms = rooms;
			this.roomCount = roomCount;
			this.doors = doors;
			this.ups = ups;
			this.arrivals = arrivals;
			this.chests = chests;
			this.landingX = landingX;
			this.landingZ = landingZ;
			this.spawnCells = spawnCells;
			this.props = props;
			this.candles = candles;
			this.edges = edges;
			this.stairs = stairs;
		}

		public int number() {
			return number;
		}

		public byte kind(final int x, final int z) {
			return cells[x * EndCastle.SIZE + z];
		}

		/** The id, from 1, of the room an open cell belongs to; 0 for a wall, a door, a void and the courtyard ground. */
		public int room(final int x, final int z) {
			return rooms[x * EndCastle.SIZE + z];
		}

		public int roomCount() {
			return roomCount;
		}

		public List<Door> doors() {
			return doors;
		}

		/** The flights that climb from this floor to the next; floor 4 has four. */
		public List<Flight> ups() {
			return ups;
		}

		/** The flights that arrive from the floor below. */
		public List<Flight> arrivals() {
			return arrivals;
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

		/** Local cells where a mob may spawn: open, in a room, clear of walls, stairs, chests and props. Each is {x, z}. */
		public List<int[]> spawnCells() {
			return spawnCells;
		}

		/** Cells inside a room that hold a floor prop, so no mob spawns there. Each is {x, z}. */
		public List<int[]> props() {
			return props;
		}

		/** The candles of the floor: two to four in every room, and the only light of the castle. */
		public List<Candle> candles() {
			return candles;
		}

		/** Free cells against a wall, where the dressing of a room goes. Each is {x, z, direction of the wall as a 2D data value}. */
		public List<int[]> edges() {
			return edges;
		}

		/** Whether a flight's run, its rails, a stair hole or a top step takes the cell at this floor. */
		public boolean stairAt(final int x, final int z) {
			return stairs[x * EndCastle.SIZE + z];
		}

		/**
		 * Whether a cell is walked by a run other than {@code own} on this floor: a lane of a run that starts here, or the top
		 * stretch and landing of one that arrives here. A rail or a pillar there would shut that walkway.
		 */
		public boolean walkedByAnother(final Flight own, final int x, final int z) {
			for (Flight other : ups) {
				if (!other.equals(own) && other.stepAt(x, z) >= 0) {
					return true;
				}
			}
			for (Flight other : arrivals) {
				if (other.equals(own)) {
					continue;
				}
				boolean landing = false;
				for (int lane = 0; lane < Flight.WIDTH; lane++) {
					landing |= other.x(Flight.STEPS, lane) == x && other.z(Flight.STEPS, lane) == z;
				}
				if (other.stepAt(x, z) >= Flight.HOLE_FROM - 1 || landing) {
					return true;
				}
			}
			return false;
		}

		/** The ledges of the floor, at most one to a room. Archers spawn on them. */
		public List<Ledge> ledges() {
			return ledges;
		}

		/** Cells a roof or a ledge may use: open floor in a room, with no stair or doorway close by. */
		public boolean calm(final int x, final int z) {
			if (x < 1 || z < 1 || x > SIZE - 2 || z > SIZE - 2 || kind(x, z) != OPEN || room(x, z) == 0) {
				return false;
			}
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					int nx = x + dx;
					int nz = z + dz;
					if (stairAt(nx, nz) || kind(nx, nz) == DOOR || kind(nx, nz) == VOID) {
						return false;
					}
				}
			}
			return true;
		}
	}
}
