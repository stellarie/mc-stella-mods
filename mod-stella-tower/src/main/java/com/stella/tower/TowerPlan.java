package com.stella.tower;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Everything that decides where things stand on one floor, drawn once from the tower's seed and the floor
 * number and never from the world: a pure, cached record. Every part of the mod that used to read a fixed
 * arrival, portal or nook reads the plan instead, so a floor's shape is one decision made in one place.
 * <p>
 * All spots are offsets from the room's inner corner ({@link TowerLayout#origin(int)}), as the maze's own
 * are. The walls array is shared by every reader of the plan: never write to it.
 * <p>
 * A plan has a {@link #version()}. Version {@value #VERSION} is the plan a floor is built to now; version 0
 * is the layout every floor had before plans existed, the arrival at the west and the portal at the east,
 * and a floor built then keeps it: a built floor carries {@link TowerFloors#planMarker(int)} if it was
 * built to a plan, and {@link #at(ServerLevel, int)} gives one without it the version 0 plan.
 * <p>
 * How the ends are placed, on an ordinary floor: an axis and a side are drawn, the arrival takes a cell in
 * one half of the room along that axis and the portal a cell in the other, and the maze is carved between
 * them with the portal's cell a dead end entered from the side of its {@link #portalFacing()}. On an elite or
 * boss floor the hall keeps the middle with the portal in it, and the canonical layout, the arrival at the
 * west and on a boss floor the safe room at the east, is turned by a random number of quarter turns. Every
 * plan is proved by a walk from the arrival to the portal; a plan with no walk is drawn again, up to
 * {@value #ATTEMPTS} times, and then the floor falls back to the plain classic maze.
 */
public record TowerPlan(
	long seed,
	int floor,
	int version,
	Variation variation,
	/** Quarter turns the canonical layout was turned by: 0 to 3, and always 0 on an ordinary floor. */
	int rotation,
	/** Which draw gave this plan: 0 to {@value #ATTEMPTS} less one, or {@link #FALLBACK}. */
	int attempt,
	int arrivalX,
	int arrivalZ,
	float arrivalYaw,
	/** The portal's inside, its column and the low one of its two rows. */
	int portalX,
	int portalZ,
	/** The side the portal is entered from along x: -1 from the lower x, 1 from the higher. */
	int portalFacing,
	boolean[][] walls,
	List<TowerMaze.Zone> safeZones,
	/** The side of the hall in the middle of the room, or nought. */
	int hall,
	/** At least {@link TowerChestSpots#MIN_SPOTS} double chests' spots, and on every fifth floor one more, guaranteed. Empty on a legacy floor. */
	List<ChestSpot> chestSpots,
	/** The rooms of a {@link Variation#WIDE_ROOMS} floor: at least {@link TowerShapes#MIN_ROOMS}, each 6 x 3, 4 or 5 either way round. Empty on any other. */
	List<Room> rooms
) {
	/** The plan a floor is built to now. */
	public static final int VERSION = 1;
	/** The first plan version whose boss floors wall their safe room in a ring; a legacy floor keeps its wide door. */
	public static final int RING_VERSION = 1;
	/** How many times a plan is drawn before the floor falls back to the classic maze. */
	public static final int ATTEMPTS = 8;
	/** {@link #attempt()} of a plan that fell back. */
	public static final int FALLBACK = -1;
	/**
	 * How many cells deep from its own wall an end may lie along the drawn axis: the arrival in the outer
	 * cells of one half, the portal in those of the other, so the two are always well apart.
	 */
	public static final int END_CELLS = 3;
	/** The classic layout's fixed spots, every floor's before the plans. */
	public static final int CLASSIC_ARRIVAL_X = 4;
	public static final int CLASSIC_PORTAL_X = 60;
	public static final int CLASSIC_MIDDLE_Z = 32;
	/** How far a planned ordinary or elite floor's portal stands above the old one: its frame rests on a wall block. */
	public static final int LIFT = 1;
	/** The layers of the stepped pyramid a boss floor's portal stands on. */
	public static final int PYRAMID_LAYERS = 3;
	/** A boss floor's portal lift: the pyramid's layers, then the frame's bottom row above them. */
	public static final int BOSS_LIFT = PYRAMID_LAYERS + 1;
	private static final long SALT = 0x504C414EL;
	private static final long FALLBACK_SALT = 0x46414C4CL;
	private static final int CACHE = 512;
	private static final Map<Key, TowerPlan> CACHE_MAP = new LinkedHashMap<>(64, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(final Map.Entry<Key, TowerPlan> eldest) {
			return this.size() > CACHE;
		}
	};

	/**
	 * How a floor is laid out, with the weight it is drawn at (see {@link TowerVariations}).
	 * <ul>
	 * <li>MAZE: a perfect maze of paths six wide.</li>
	 * <li>WIDE_ROOMS: a maze of paths two wide with at least three rooms of 6 x 3, 6 x 4 or 6 x 5.</li>
	 * <li>STAGGERED: rooms laid like bricks, every two that touch joined by a door.</li>
	 * <li>TALL_WALLS: the maze with walls {@value #TALL_WALL_HEIGHT} high, open air above them.</li>
	 * <li>SPIRAL: one corridor from the arrival to the portal, winding over the whole floor.</li>
	 * </ul>
	 */
	public enum Variation {
		MAZE(35),
		WIDE_ROOMS(20),
		STAGGERED(15),
		TALL_WALLS(15),
		SPIRAL(15);

		private final int weight;

		Variation(final int weight) {
			this.weight = weight;
		}

		public int weight() {
			return this.weight;
		}
	}

	/** How high the maze's walls stand on a {@link Variation#TALL_WALLS} floor; the hall's enclosure and the room's ring stay full height. */
	public static final int TALL_WALL_HEIGHT = 2;

	/** The variation of a floor in a tower of this seed: a pure function of the two. */
	public static Variation variationOf(final long seed, final int floor) {
		return TowerVariations.of(seed, floor);
	}

	/**
	 * A double chest's spot: its first block, the way the chest faces (the open side, away from the wall it stands
	 * against), and whether it is the floor's guaranteed chest. The other block is {@link #second()}: beside the first
	 * along x for a chest facing north or south, along z for one facing east or west.
	 */
	public record ChestSpot(int x, int z, net.minecraft.core.Direction facing, boolean guaranteed) {
		public Spot second() {
			return this.facing.getAxis() == net.minecraft.core.Direction.Axis.Z ? new Spot(this.x + 1, this.z) : new Spot(this.x, this.z + 1);
		}

		/** Both blocks, the first then the second. */
		public List<Spot> cells() {
			return List.of(new Spot(this.x, this.z), second());
		}
	}

	/** A spot of the room, as an offset from its inner corner. */
	public record Spot(int x, int z) {
	}

	/** A rectangle of open ground the variation laid, by the offset of its low corner and its size in blocks along x and z. */
	public record Room(int x, int z, int width, int length) {
		/** Whether this rectangle lies within {@code gap} blocks of another, or overlaps it. */
		public boolean touches(final Room other, final int gap) {
			return this.x - gap < other.x + other.width && other.x - gap < this.x + this.width
				&& this.z - gap < other.z + other.length && other.z - gap < this.z + this.length;
		}

		/** The rectangle after one quarter turn of the room about its middle, as {@link #turn(int, int)} turns a spot. */
		Room turned() {
			return new Room(TowerLayout.SIZE - this.z - this.length, this.x, this.length, this.width);
		}
	}

	private record Key(long seed, int floor, int version) {
	}

	/** The plan a floor is built to now, for this seed. Pure and cached. */
	public static TowerPlan of(final long seed, final int floor) {
		return cached(new Key(seed, floor, VERSION));
	}

	/**
	 * The plan of a floor as the generator of a given version drew it. Every version ever released keeps its
	 * generator, frozen, in {@link #draw}, so a floor built to it is served the plan it was built to. Version 0
	 * is the layout of the floors built before there were plans. A version this mod does not know, from a later
	 * one, is served the newest plan it does know, and a warning is logged (once for each such version): the floor
	 * may not match it, and someone should know that the world was played on a newer mod.
	 */
	public static TowerPlan of(final long seed, final int floor, final int version) {
		if (version > VERSION) {
			warnUnknown(version);
		}
		return cached(new Key(seed, floor, Math.min(Math.max(version, 0), VERSION)));
	}

	private static final java.util.Set<Integer> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();

	private static void warnUnknown(final int version) {
		if (WARNED.add(version)) {
			StellaTower.LOGGER.warn("[TOWER] a floor carries plan version {}, newer than this mod's newest, {}: it is served the version {} plan and may not match its blocks",
				version, VERSION, VERSION);
		}
	}

	/** Whether a warning has been logged for a plan version newer than this mod knows. */
	static boolean warnedAbout(final int version) {
		return WARNED.contains(version);
	}

	/** Forgets every cached plan, and which unknown versions have been warned of, as a stopped server does. */
	static void clearCache() {
		synchronized (CACHE_MAP) {
			CACHE_MAP.clear();
		}
		WARNED.clear();
	}

	/** The plan of a floor built before there were plans: the classic layout. Pure and cached. */
	public static TowerPlan legacy(final long seed, final int floor) {
		return cached(new Key(seed, floor, 0));
	}

	/**
	 * The plan the floor really has in this tower: the current plan for a floor not yet built, and for one
	 * built, the plan of the version its markers record, with none meaning the legacy plan. Whether a floor
	 * is old or planned is read from its blocks once, when it stands, and kept.
	 */
	public static TowerPlan at(final ServerLevel tower, final int floor) {
		int built = TowerFloors.builtVersion(tower, floor);
		return built < 0 ? of(tower.getSeed(), floor) : of(tower.getSeed(), floor, built);
	}

	/**
	 * {@link #at} for a caller that must never load a chunk (the targeting mixins): a floor whose markers lie in a chunk
	 * that is not loaded is served from the remembered version, or the current plan if none is remembered.
	 */
	static TowerPlan atQuiet(final ServerLevel tower, final int floor) {
		int built = TowerFloors.builtVersionQuiet(tower, floor);
		return built < 0 ? of(tower.getSeed(), floor) : of(tower.getSeed(), floor, built);
	}

	private static TowerPlan cached(final Key key) {
		synchronized (CACHE_MAP) {
			TowerPlan plan = CACHE_MAP.get(key);
			if (plan != null) {
				return plan;
			}
		}
		TowerPlan plan = key.version() == 0 ? classic(key.seed(), key.floor(), 0, 0)
			: generate(key.seed(), key.floor(), key.version(), drawn -> false);
		synchronized (CACHE_MAP) {
			CACHE_MAP.put(key, plan);
		}
		return plan;
	}

	/**
	 * Draws a plan afresh, not from the cache. {@code veto} may turn a draw down, as a walk-less one is:
	 * the test seam that makes the reseeding and the fallback visible.
	 */
	static TowerPlan generate(final long seed, final int floor, final Predicate<TowerPlan> veto) {
		return generate(seed, floor, VERSION, veto);
	}

	static TowerPlan generate(final long seed, final int floor, final int version, final Predicate<TowerPlan> veto) {
		for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
			Random random = new Random(mix(seed ^ SALT ^ (floor * 0x9E3779B97F4A7C15L) ^ (attempt * 0xC2B2AE3D27D4EB4FL)));
			TowerPlan drawn = draw(seed, floor, version, attempt, random);
			if (drawn == null || !reaches(drawn) || veto.test(drawn)) {
				continue;
			}
			List<ChestSpot> chests = TowerChestSpots.place(drawn);
			if (chests != null) {
				return drawn.withChestSpots(chests);
			}
		}
		TowerPlan fallback = classic(seed, floor, version, FALLBACK);
		List<ChestSpot> chests = TowerChestSpots.place(fallback);
		return chests == null ? fallback : fallback.withChestSpots(chests);
	}

	/** This plan with its chest spots. */
	TowerPlan withChestSpots(final List<ChestSpot> spots) {
		return new TowerPlan(this.seed, this.floor, this.version, this.variation, this.rotation, this.attempt, this.arrivalX, this.arrivalZ,
			this.arrivalYaw, this.portalX, this.portalZ, this.portalFacing, this.walls, this.safeZones, this.hall, List.copyOf(spots), this.rooms);
	}

	/**
	 * A splitmix64 finish, so seeds that differ a little draw unlike plans: the first draws of
	 * {@link Random}s seeded a little apart are alike, and the arrival is the very first draw.
	 */
	private static long mix(final long value) {
		long z = value + 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/**
	 * One draw of a version's generator. A generator, once a mod release has built floors with it, is never
	 * changed: a change to how a floor is drawn adds a new version, with its own case here, and bumps
	 * {@link #VERSION}. Version 1 is the only one so far; its boss floors wall the safe room in a ring, where version 0's joined it to the hall by a path-wide door.
	 */
	private static @org.jspecify.annotations.Nullable TowerPlan draw(final long seed, final int floor, final int version, final int attempt, final Random random) {
		return switch (version) {
			case 1 -> drawFrom(seed, floor, version, attempt, random);
			default -> throw new IllegalArgumentException("no generator for plan version " + version);
		};
	}

	/** One draw of a version's generator: the ends and the maze, and a boss floor's safe room walled in a ring from {@link #RING_VERSION}. */
	private static TowerPlan drawFrom(final long seed, final int floor, final int version, final int attempt, final Random random) {
		Variation variation = variationOf(seed, floor);
		int hall = TowerMaze.hall(floor);
		if (hall > 0) {
			int turns = random.nextInt(4);
			TowerShapes.HallWalls shaped = TowerShapes.hallWalls(random, variation, floor, version >= RING_VERSION);
			if (shaped == null) {
				return null;
			}
			boolean[][] walls = shaped.walls();
			List<Room> rooms = shaped.rooms();
			for (int i = 0; i < turns; i++) {
				walls = turn(walls);
				List<Room> turned = new ArrayList<>();
				for (Room room : rooms) {
					turned.add(room.turned());
				}
				rooms = turned;
			}
			List<TowerMaze.Zone> zones = new ArrayList<>();
			for (TowerMaze.Zone zone : TowerMaze.classicZones(floor)) {
				TowerMaze.Zone turned = zone;
				for (int i = 0; i < turns; i++) {
					turned = turn(turned);
				}
				zones.add(turned);
			}
			int[] at = {CLASSIC_ARRIVAL_X, CLASSIC_MIDDLE_Z};
			for (int i = 0; i < turns; i++) {
				at = turn(at[0], at[1]);
			}
			return new TowerPlan(seed, floor, version, variation, turns, attempt, at[0], at[1], -90.0F + 90.0F * turns,
				TowerLayout.SIZE / 2, CLASSIC_MIDDLE_Z - 1, -1, walls, List.copyOf(zones), hall, List.of(), List.copyOf(rooms));
		}
		TowerShapes.Layout layout = switch (variation) {
			case WIDE_ROOMS -> TowerShapes.wideRooms(random);
			case STAGGERED -> TowerShapes.staggered(random);
			case SPIRAL -> TowerShapes.spiral(random);
			default -> null;
		};
		if (layout != null) {
			float yaw = (float) Math.toDegrees(Math.atan2(-(layout.portalX() - layout.arrivalX()), layout.portalZ() - layout.arrivalZ()));
			return new TowerPlan(seed, floor, version, variation, 0, attempt, layout.arrivalX(), layout.arrivalZ(), yaw,
				layout.portalX(), layout.portalZ(), layout.facing(), layout.walls(), List.of(layout.zone()), 0, List.of(), layout.rooms());
		}
		if (variation != Variation.MAZE && variation != Variation.TALL_WALLS) {
			// A variation whose draw did not fit: drawn again.
			return null;
		}
		// The arrival takes a cell in one half of the room along one axis, the portal a cell in the other.
		boolean alongX = random.nextBoolean();
		boolean arrivalLow = random.nextBoolean();
		int arrivalAlong = arrivalLow ? random.nextInt(END_CELLS) : TowerMaze.CELLS - 1 - random.nextInt(END_CELLS);
		int portalAlong = arrivalLow ? TowerMaze.CELLS - 1 - random.nextInt(END_CELLS) : random.nextInt(END_CELLS);
		int arrivalAcross = random.nextInt(TowerMaze.CELLS);
		int portalAcross = random.nextInt(TowerMaze.CELLS);
		int arrivalCx = alongX ? arrivalAlong : arrivalAcross;
		int arrivalCz = alongX ? arrivalAcross : arrivalAlong;
		int portalCx = alongX ? portalAlong : portalAcross;
		int portalCz = alongX ? portalAcross : portalAlong;
		// The portal's cell is a dead end, joined to a neighbour along x.
		boolean lowNeighbour = portalCx > 0 && (portalCx == TowerMaze.CELLS - 1 || random.nextBoolean());
		int facing = lowNeighbour ? -1 : 1;
		int arrivalX = TowerMaze.cell(arrivalCx) + TowerMaze.PATH / 2;
		int arrivalZ = TowerMaze.cell(arrivalCz) + TowerMaze.PATH / 2;
		int portalX = TowerMaze.cell(portalCx) + TowerMaze.portalLocal(facing);
		int portalZ = TowerMaze.cell(portalCz) + 2;
		int zoneMax = TowerLayout.SIZE - 1 - TowerMaze.SAFE;
		TowerMaze.Zone zone = new TowerMaze.Zone(Math.max(1, Math.min(zoneMax, arrivalX - 3)), Math.max(1, Math.min(zoneMax, arrivalZ - 4)));
		boolean[][] walls = TowerMaze.scatteredWalls(random, arrivalCx, arrivalCz, portalCx, portalCz, facing, zone);
		float yaw = (float) Math.toDegrees(Math.atan2(-(portalX - arrivalX), portalZ - arrivalZ));
		return new TowerPlan(seed, floor, version, variation, 0, attempt, arrivalX, arrivalZ, yaw,
			portalX, portalZ, facing, walls, List.of(zone), 0, List.of(), List.of());
	}

	/** The classic layout, as every floor was before the plans, and as a floor falls back to. */
	private static TowerPlan classic(final long seed, final int floor, final int version, final int attempt) {
		int hall = TowerMaze.hall(floor);
		boolean[][] walls = version == 0 ? TowerMaze.legacyWalls(seed, floor)
			: TowerMaze.classicWalls(new Random(seed ^ FALLBACK_SALT ^ (floor * 0x9E3779B97F4A7C15L)), floor, version >= RING_VERSION);
		return new TowerPlan(seed, floor, version, Variation.MAZE, 0, attempt, CLASSIC_ARRIVAL_X, CLASSIC_MIDDLE_Z, -90.0F,
			hall > 0 ? TowerLayout.SIZE / 2 : CLASSIC_PORTAL_X, CLASSIC_MIDDLE_Z - 1, -1, walls,
			TowerMaze.classicZones(floor), hall, List.of(), List.of());
	}

	/** One quarter turn of the room about its middle: a spot (x, z) goes to (size - 1 - z, x). */
	static int[] turn(final int x, final int z) {
		return new int[] {TowerLayout.SIZE - 1 - z, x};
	}

	static TowerMaze.Zone turn(final TowerMaze.Zone zone) {
		return new TowerMaze.Zone(TowerLayout.SIZE - TowerMaze.SAFE - zone.z(), zone.x());
	}

	static boolean[][] turn(final boolean[][] walls) {
		int size = TowerLayout.SIZE;
		boolean[][] turned = new boolean[size][size];
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				turned[size - 1 - z][x] = walls[x][z];
			}
		}
		return turned;
	}

	/** Whether a body can walk from the plan's arrival to its portal, the frame's posts in the way. */
	static boolean reaches(final TowerPlan plan) {
		int size = TowerLayout.SIZE;
		boolean[][] seen = new boolean[size][size];
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		if (plan.walls()[plan.arrivalX()][plan.arrivalZ()]) {
			return false;
		}
		seen[plan.arrivalX()][plan.arrivalZ()] = true;
		queue.add(new int[] {plan.arrivalX(), plan.arrivalZ()});
		int[][] ways = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!queue.isEmpty()) {
			int[] at = queue.poll();
			if (at[0] == plan.portalX() && at[1] >= plan.portalZ() && at[1] < plan.portalZ() + TowerLayout.PORTAL_INNER_WIDTH) {
				return true;
			}
			for (int[] way : ways) {
				int x = at[0] + way[0];
				int z = at[1] + way[1];
				if (x < 0 || z < 0 || x >= size || z >= size || seen[x][z] || plan.walls()[x][z] || plan.isFramePost(x, z)) {
					continue;
				}
				seen[x][z] = true;
				queue.add(new int[] {x, z});
			}
		}
		return false;
	}

	/** Whether the spot is one of the two blocks either side of the portal that its frame stands on. */
	boolean isFramePost(final int x, final int z) {
		return x == this.portalX && (z == this.portalZ - 1 || z == this.portalZ + TowerLayout.PORTAL_INNER_WIDTH);
	}

	/**
	 * How many blocks high the wall at a spot stands: the room's height, except on a {@link Variation#TALL_WALLS} floor,
	 * where the maze's walls are {@link #TALL_WALL_HEIGHT} high. The boss room's ring and the walls round a hall keep
	 * the full height, so a mob or a boss in them is still shut in.
	 */
	public int wallHeightAt(final int x, final int z) {
		if (this.variation != Variation.TALL_WALLS || isRing(x, z)) {
			return TowerLayout.HEIGHT;
		}
		int low = hallLow() - 2;
		int high = hallLow() + this.hall + 1;
		return this.hall > 0 && x >= low && x <= high && z >= low && z <= high ? TowerLayout.HEIGHT : TALL_WALL_HEIGHT;
	}

	public boolean wall(final int x, final int z) {
		return this.walls[x][z];
	}

	public boolean inSafeZone(final int x, final int z) {
		for (TowerMaze.Zone zone : this.safeZones) {
			if (zone.contains(x, z)) {
				return true;
			}
		}
		return false;
	}

	public int hallLow() {
		return TowerLayout.SIZE / 2 - this.hall / 2;
	}

	/** The room's inner corner. */
	public BlockPos origin() {
		return TowerLayout.origin(this.floor);
	}

	/** Where a player lands: the middle of the arrival's block, on the floor. */
	public Vec3 arrivalPos() {
		BlockPos origin = origin();
		return new Vec3(origin.getX() + this.arrivalX + 0.5, TowerLayout.BASE_Y, origin.getZ() + this.arrivalZ + 0.5);
	}

	/** The portal's cell in the maze's grid: its column and row. */
	public int[] portalCell() {
		return new int[] {(this.portalX - 1) / TowerMaze.PITCH, (this.portalZ - 1) / TowerMaze.PITCH};
	}

	/** The arrival's cell in the maze's grid: its column and row. */
	public int[] arrivalCell() {
		return new int[] {(this.arrivalX - 1) / TowerMaze.PITCH, (this.arrivalZ - 1) / TowerMaze.PITCH};
	}

	/**
	 * How far the portal's inside stands above {@link TowerLayout#BASE_Y}: {@link #LIFT} on an ordinary or elite
	 * floor, {@link #BOSS_LIFT} on a boss floor, and none on a legacy floor, whose portal stays flat.
	 */
	public int lift() {
		if (this.version == 0) {
			return 0;
		}
		return this.hall == TowerMaze.BOSS_HALL ? BOSS_LIFT : LIFT;
	}

	/**
	 * The tower wall the raised portal stands on, as absolute spots: on an ordinary or elite floor the blocks
	 * under the frame's bottom row, on a boss floor the pyramid, its layers from the floor up, each layer one
	 * step narrower than the one below. Empty on a legacy floor.
	 */
	public List<BlockPos> plinth() {
		List<BlockPos> wall = new ArrayList<>();
		if (this.version == 0) {
			return wall;
		}
		BlockPos origin = origin();
		int x = origin.getX() + this.portalX;
		int zLow = origin.getZ() + this.portalZ - 1;
		int zHigh = origin.getZ() + this.portalZ + TowerLayout.PORTAL_INNER_WIDTH;
		if (this.hall != TowerMaze.BOSS_HALL) {
			for (int z = zLow; z <= zHigh; z++) {
				wall.add(new BlockPos(x, TowerLayout.BASE_Y + this.lift() - 1 - 1, z));
			}
			return wall;
		}
		// Layer k, counted from the floor, is PYRAMID_LAYERS + 1 - k blocks wider than the frame on every side
		// but the frame's own column: so the top layer is one step, and the first is PYRAMID_LAYERS.
		for (int layer = 0; layer < PYRAMID_LAYERS; layer++) {
			int reach = PYRAMID_LAYERS - layer;
			for (int dx = -reach; dx <= reach; dx++) {
				for (int z = zLow - reach; z <= zHigh + reach; z++) {
					wall.add(new BlockPos(x + dx, TowerLayout.BASE_Y + layer, z));
				}
			}
		}
		return wall;
	}

	/** The portal frame's inside: the blocks the portal fills once the frame is lit. */
	public List<BlockPos> portalInside() {
		BlockPos origin = origin();
		List<BlockPos> inside = new ArrayList<>();
		for (int dz = 0; dz < TowerLayout.PORTAL_INNER_WIDTH; dz++) {
			for (int dy = 0; dy < TowerLayout.PORTAL_INNER_HEIGHT; dy++) {
				inside.add(new BlockPos(origin.getX() + this.portalX, TowerLayout.BASE_Y + lift() + dy, origin.getZ() + this.portalZ + dz));
			}
		}
		return inside;
	}

	/** The frame around {@link #portalInside()}, corners included. */
	public List<BlockPos> portalFrame() {
		BlockPos origin = origin();
		int x = origin.getX() + this.portalX;
		int zLow = origin.getZ() + this.portalZ - 1;
		int zHigh = origin.getZ() + this.portalZ + TowerLayout.PORTAL_INNER_WIDTH;
		int yLow = TowerLayout.BASE_Y + lift() - 1;
		int yHigh = TowerLayout.BASE_Y + lift() + TowerLayout.PORTAL_INNER_HEIGHT;
		List<BlockPos> frame = new ArrayList<>();
		for (int z = zLow; z <= zHigh; z++) {
			for (int y = yLow; y <= yHigh; y++) {
				if (z == zLow || z == zHigh || y == yLow || y == yHigh) {
					frame.add(new BlockPos(x, y, z));
				}
			}
		}
		return frame;
	}

	/**
	 * A boss floor's crafting table, furnace and anvil, by room offset and the way each faces, along the safe
	 * room's north wall, inside the ring where there is one: the classic row turned as the plan was. Empty on any other floor.
	 */
	public List<Station> stations() {
		if (this.hall != TowerMaze.BOSS_HALL) {
			return List.of();
		}
		List<Station> stations = new ArrayList<>();
		int[] offsets = {2, 4, 6};
		net.minecraft.core.Direction[] faces = {null, net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.SOUTH};
		for (int i = 0; i < offsets.length; i++) {
			int[] at = {TowerMaze.BOSS_ROOM_X + offsets[i], TowerMaze.SAFE_Z + (ring() ? 1 : 0)};
			net.minecraft.core.Direction face = faces[i];
			for (int turns = 0; turns < this.rotation; turns++) {
				at = turn(at[0], at[1]);
				face = face == null ? null : face.getClockWise();
			}
			stations.add(new Station(at[0], at[1], face, i));
		}
		return stations;
	}

	/** Whether the boss floor's safe room is walled in a ring: a boss floor of {@link #RING_VERSION} or later. */
	public boolean ring() {
		return this.hall == TowerMaze.BOSS_HALL && this.version >= RING_VERSION;
	}

	/** Whether the spot is a block of the room's ring: the outermost blocks of the safe room, its openings included. */
	public boolean isRing(final int x, final int z) {
		if (!ring()) {
			return false;
		}
		TowerMaze.Zone room = this.safeZones.get(1);
		return room.contains(x, z) && (x == room.x() || x == room.x() + TowerMaze.SAFE - 1 || z == room.z() || z == room.z() + TowerMaze.SAFE - 1);
	}

	/**
	 * The room's two openings, as room offsets, the canonical layout's turned as the plan was: the exit into the
	 * hall, two wide and two thick (the ring and the hall's wall), and the entrance from the maze, two wide.
	 * Each opening is two high; its {@code jambs} are the wall blocks at its sides. Empty without a ring.
	 */
	public List<Gate> gates() {
		if (!ring()) {
			return List.of();
		}
		int first = TowerMaze.GATE_Z;
		int last = TowerMaze.GATE_Z + 1;
		List<Spot> exit = new ArrayList<>();
		List<Spot> exitJambs = new ArrayList<>();
		List<Spot> entrance = new ArrayList<>();
		List<Spot> entranceJambs = new ArrayList<>();
		for (int x = TowerMaze.BOSS_ROOM_X - TowerMaze.WALL; x <= TowerMaze.BOSS_ROOM_X; x++) {
			exit.add(turned(x, first));
			exit.add(turned(x, last));
			exitJambs.add(turned(x, first - 1));
			exitJambs.add(turned(x, last + 1));
		}
		entrance.add(turned(TowerMaze.ROOM_ENTRANCE_X, first));
		entrance.add(turned(TowerMaze.ROOM_ENTRANCE_X, last));
		entranceJambs.add(turned(TowerMaze.ROOM_ENTRANCE_X, first - 1));
		entranceJambs.add(turned(TowerMaze.ROOM_ENTRANCE_X, last + 1));
		return List.of(new Gate(true, List.copyOf(exit), List.copyOf(exitJambs)), new Gate(false, List.copyOf(entrance), List.copyOf(entranceJambs)));
	}

	private Spot turned(final int x, final int z) {
		int[] at = {x, z};
		for (int i = 0; i < this.rotation; i++) {
			at = turn(at[0], at[1]);
		}
		return new Spot(at[0], at[1]);
	}

	/** One opening of the safe room's ring: the blocks it leaves open, and the wall blocks at its sides. */
	public record Gate(boolean exit, List<Spot> cells, List<Spot> jambs) {
	}

	/** One workstation: its room offset, the way it faces (null for none), and which it is: table, furnace, anvil. */
	public record Station(int x, int z, net.minecraft.core.Direction facing, int kind) {
	}
}
