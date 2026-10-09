package com.stella.tower;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Everything that decides where things stand on one floor, drawn once from the tower's seed and the floor number and
 * never from the world: a pure, cached record. Every part of the mod that reads an arrival, a portal or a story reads
 * the plan, so a floor's shape is one decision made in one place.
 * <p>
 * All spots are offsets from the room's inner corner ({@link TowerLayout#origin(int)}); heights are offsets from
 * {@link TowerLayout#BASE_Y}. The arrays are shared by every reader of the plan: never write to them.
 * <p>
 * A floor takes an archetype ({@link TowerArchetypes}) and its stories ({@link TowerStory}), joined by stairs and
 * ramps ({@link TowerFlight}). The arrival is always on the ground story; the portal stands on any. An elite or a boss
 * floor is a {@link TowerArchetype#HALL}: the hall keeps the middle with the portal in it, and the canonical layout,
 * the arrival at the west and on a boss floor the safe room at the east, is turned by a random number of quarter turns.
 * <p>
 * Every plan is proved by a walk on foot ({@link TowerWalk}) from the arrival to the portal over its whole volume
 * ({@link TowerVolume}), rails, lamps and chests included; a plan with no walk is drawn again, up to {@value #ATTEMPTS}
 * times, and then the floor falls back to the plain classic maze under a lid.
 */
public record TowerPlan(
	long seed,
	int floor,
	TowerArchetype archetype,
	Variation variation,
	/** Quarter turns the canonical hall layout was turned by: 0 to 3, and always 0 on any other floor. */
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
	/** The story the portal stands on. */
	int portalStory,
	/** Where a body cannot walk on the ground story: its walls, and the stairs and their low walls. */
	boolean[][] walls,
	List<TowerStory> stories,
	List<TowerFlight> flights,
	List<TowerMaze.Zone> safeZones,
	/** The side of the hall in the middle of the room, or nought. */
	int hall,
	/** At least {@link TowerChestSpots#MIN_SPOTS} double chests' spots, and on every fifth floor one more, guaranteed. */
	List<ChestSpot> chestSpots,
	/** The rooms of a hall floor drawn as {@link Variation#WIDE_ROOMS}: at least {@link TowerShapes#MIN_ROOMS}. Empty on any other. */
	List<Room> rooms,
	/** A great hall's carpet down its nave, laid into the ground; null on any other floor. */
	@Nullable Room runner,
	/** The floor whose band the plan's look follows: its own, or a lost room's borrowed floor. */
	int look
) {
	/** How many times a plan is drawn before the floor falls back to the classic maze. */
	public static final int ATTEMPTS = 8;
	/** {@link #attempt()} of a plan that fell back. */
	public static final int FALLBACK = -1;
	/**
	 * How many cells deep from its own wall an end may lie along the drawn axis: the arrival in the outer
	 * cells of one half, the portal in those of the other, so the two are always well apart.
	 */
	public static final int END_CELLS = 3;
	/** The classic layout's fixed spots. */
	public static final int CLASSIC_ARRIVAL_X = 4;
	public static final int CLASSIC_PORTAL_X = 60;
	public static final int CLASSIC_MIDDLE_Z = 32;
	/** How far an ordinary or elite floor's portal stands above its story: its frame rests on a wall block. */
	public static final int LIFT = 1;
	/** The layers of the stepped pyramid a boss floor's portal stands on. */
	public static final int PYRAMID_LAYERS = 3;
	/** A boss floor's portal lift: the pyramid's layers, then the frame's bottom row above them. */
	public static final int BOSS_LIFT = PYRAMID_LAYERS + 1;
	/** A story's height, slab to slab: an upper story stands this far above the one below. */
	public static final int STORY = 8;
	/** The lid over a hall floor: its one story's top, as every floor's ceiling was before the stories. */
	public static final int HALL_TOP = STORY - 1;
	private static final long SALT = 0x504C414EL;
	private static final long FALLBACK_SALT = 0x46414C4CL;
	private static final int CACHE = 512;
	private static final Map<Key, TowerPlan> CACHE_MAP = new LinkedHashMap<>(64, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(final Map.Entry<Key, TowerPlan> eldest) {
			return this.size() > CACHE;
		}
	};

	private static final Map<Key, TowerPlan> LEGACY_MAP = new LinkedHashMap<>(16, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(final Map.Entry<Key, TowerPlan> eldest) {
			return this.size() > CACHE;
		}
	};

	/**
	 * How a hall floor's maze round its hall is laid, drawn evenly among those the floor may take.
	 * <ul>
	 * <li>MAZE: a perfect maze of paths six wide.</li>
	 * <li>WIDE_ROOMS: a maze of paths two wide with at least three rooms of 6 x 3, 6 x 4 or 6 x 5 (elite floors).</li>
	 * <li>STAGGERED: rooms laid like bricks, every two that touch joined by a door.</li>
	 * <li>TALL_WALLS: the maze with walls {@value #TALL_WALL_HEIGHT} high, open air above them.</li>
	 * </ul>
	 * Every other floor's variation is MAZE.
	 */
	public enum Variation {
		MAZE,
		WIDE_ROOMS,
		STAGGERED,
		TALL_WALLS
	}

	/** How high the maze's walls stand on a {@link Variation#TALL_WALLS} floor; the hall's enclosure and the room's ring stay full height. */
	public static final int TALL_WALL_HEIGHT = 2;

	/** The variations a hall floor may take: not wide rooms on a boss floor. */
	static List<Variation> hallVariations(final int floor) {
		List<Variation> allowed = new ArrayList<>(List.of(Variation.values()));
		if (TowerMaze.hall(floor) == TowerMaze.BOSS_HALL) {
			allowed.remove(Variation.WIDE_ROOMS);
		}
		return allowed;
	}

	/**
	 * A double chest's spot: its first block, the way the chest faces (the open side, away from the wall it stands
	 * against), whether it is the floor's guaranteed chest, and the height of the story it stands on. The other block
	 * is {@link #second()}: beside the first along x for a chest facing north or south, along z for one facing east or west.
	 */
	public record ChestSpot(int x, int z, net.minecraft.core.Direction facing, boolean guaranteed, int y) {
		public ChestSpot(final int x, final int z, final net.minecraft.core.Direction facing, final boolean guaranteed) {
			this(x, z, facing, guaranteed, 0);
		}

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

	/** A rectangle of open ground, by the offset of its low corner and its size in blocks along x and z. */
	public record Room(int x, int z, int width, int length) {
		/** Whether this rectangle lies within {@code gap} blocks of another, or overlaps it. */
		public boolean touches(final Room other, final int gap) {
			return this.x - gap < other.x + other.width && other.x - gap < this.x + this.width
				&& this.z - gap < other.z + other.length && other.z - gap < this.z + this.length;
		}

		public boolean contains(final int atX, final int atZ) {
			return atX >= this.x && atX < this.x + this.width && atZ >= this.z && atZ < this.z + this.length;
		}

		/** The rectangle after one quarter turn of the room about its middle, as {@link #turn(int, int)} turns a spot. */
		Room turned() {
			return new Room(TowerLayout.SIZE - this.z - this.length, this.x, this.length, this.width);
		}

		Room mirrored(final boolean alongX, final boolean alongZ) {
			return new Room(alongX ? TowerLayout.SIZE - this.x - this.width : this.x, alongZ ? TowerLayout.SIZE - this.z - this.length : this.z,
				this.width, this.length);
		}
	}

	/**
	 * An archetype's draw before it is proved: its stories and ways up, its ends, its zones. A null from a generator
	 * means the draw did not fit.
	 */
	record Draft(List<TowerStory> stories, List<TowerFlight> flights, int arrivalX, int arrivalZ, int portalX, int portalZ,
		int portalFacing, int portalStory, List<TowerMaze.Zone> zones, @Nullable Room runner) {
		/** The draft mirrored across the room's middle along x, along z, or both. */
		Draft mirrored(final boolean alongX, final boolean alongZ) {
			if (!alongX && !alongZ) {
				return this;
			}
			int size = TowerLayout.SIZE;
			List<TowerStory> stories = new ArrayList<>();
			for (TowerStory story : this.stories) {
				stories.add(new TowerStory(story.y(), story.top(), story.wallTop(), mirror(story.floor(), alongX, alongZ), mirror(story.wall(), alongX, alongZ),
					story.pillar() == null ? null : mirror(story.pillar(), alongX, alongZ), story.cover() == null ? null : mirror(story.cover(), alongX, alongZ),
					null, null));
			}
			List<TowerFlight> flights = new ArrayList<>();
			for (TowerFlight flight : this.flights) {
				flights.add(flight.mirrored(alongX, alongZ));
			}
			List<TowerMaze.Zone> zones = new ArrayList<>();
			for (TowerMaze.Zone zone : this.zones) {
				zones.add(new TowerMaze.Zone(alongX ? size - TowerMaze.SAFE - zone.x() : zone.x(), alongZ ? size - TowerMaze.SAFE - zone.z() : zone.z(),
					zone.y(), zone.top()));
			}
			return new Draft(stories, flights, alongX ? size - 1 - this.arrivalX : this.arrivalX, alongZ ? size - 1 - this.arrivalZ : this.arrivalZ,
				alongX ? size - 1 - this.portalX : this.portalX,
				alongZ ? size - TowerLayout.PORTAL_INNER_WIDTH - this.portalZ : this.portalZ,
				alongX ? -this.portalFacing : this.portalFacing, this.portalStory, zones,
				this.runner == null ? null : this.runner.mirrored(alongX, alongZ));
		}

		private static byte[][] mirror(final byte[][] grid, final boolean alongX, final boolean alongZ) {
			int size = TowerLayout.SIZE;
			byte[][] out = new byte[size][size];
			for (int x = 0; x < size; x++) {
				for (int z = 0; z < size; z++) {
					out[alongX ? size - 1 - x : x][alongZ ? size - 1 - z : z] = grid[x][z];
				}
			}
			return out;
		}

		private static boolean[][] mirror(final boolean[][] grid, final boolean alongX, final boolean alongZ) {
			int size = TowerLayout.SIZE;
			boolean[][] out = new boolean[size][size];
			for (int x = 0; x < size; x++) {
				for (int z = 0; z < size; z++) {
					out[alongX ? size - 1 - x : x][alongZ ? size - 1 - z : z] = grid[x][z];
				}
			}
			return out;
		}
	}

	private record Key(long seed, int floor) {
	}

	/** The plan of a floor in a tower of this seed. Pure and cached. */
	public static TowerPlan of(final long seed, final int floor) {
		Key key = new Key(seed, floor);
		synchronized (CACHE_MAP) {
			TowerPlan plan = CACHE_MAP.get(key);
			if (plan != null) {
				return plan;
			}
		}
		TowerPlan plan = generate(seed, floor, drawn -> false);
		synchronized (CACHE_MAP) {
			CACHE_MAP.put(key, plan);
		}
		return plan;
	}

	/**
	 * The plan of a floor as the tower drew it before the biome floors, pure and cached apart from {@link #of}.
	 * A lost room borrows its plan from here ({@link LegacyRoomPlan}), so a change to how floors are drawn
	 * never moves a room.
	 */
	static TowerPlan legacy(final long seed, final int floor) {
		Key key = new Key(seed, floor);
		synchronized (LEGACY_MAP) {
			TowerPlan plan = LEGACY_MAP.get(key);
			if (plan != null) {
				return plan;
			}
		}
		TowerPlan plan = generate(seed, floor, drawn -> false);
		synchronized (LEGACY_MAP) {
			LEGACY_MAP.put(key, plan);
		}
		return plan;
	}

	/** Forgets every cached plan, as a stopped server does. */
	static void clearCache() {
		synchronized (CACHE_MAP) {
			CACHE_MAP.clear();
		}
		synchronized (LEGACY_MAP) {
			LEGACY_MAP.clear();
		}
	}

	/** The plan of a floor in this tower; a lost room's is its borrowed floor's, moved to its cell. */
	public static TowerPlan at(final ServerLevel tower, final int floor) {
		if (floor < 0) {
			return LostRooms.plan(tower.getSeed(), floor);
		}
		return of(tower.getSeed(), floor);
	}

	/** {@link #at} for a caller that must never load a chunk (the targeting mixins). Plans read no blocks, so the two agree. */
	static TowerPlan atQuiet(final ServerLevel tower, final int floor) {
		return at(tower, floor);
	}

	/**
	 * Draws a plan afresh, not from the cache. {@code veto} may turn a walked draw down, as a walk-less one is:
	 * the test seam that makes the redrawing and the fallback visible.
	 */
	static TowerPlan generate(final long seed, final int floor, final Predicate<TowerPlan> veto) {
		TowerArchetype archetype = TowerArchetypes.of(seed, floor);
		for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
			Random random = new Random(mix(seed ^ SALT ^ (floor * 0x9E3779B97F4A7C15L) ^ (attempt * 0xC2B2AE3D27D4EB4FL)));
			TowerPlan drawn = draw(seed, floor, archetype, attempt, random);
			if (drawn == null) {
				continue;
			}
			TowerPlan finished = finish(drawn, veto);
			if (finished != null) {
				return finished;
			}
		}
		TowerPlan fallback = classic(seed, floor, archetype, FALLBACK);
		TowerPlan finished = finish(fallback, drawn -> false);
		return finished != null ? finished : fallback.withStands(TowerVolume.of(fallback));
	}

	/**
	 * Proves a draw and gives it its chests: walked over its volume, read for where bodies stand, its chest spots
	 * placed, and walked again with the chests in. Null when either walk fails, the veto turns it down, or too few
	 * chest spots fit.
	 */
	private static @Nullable TowerPlan finish(final TowerPlan drawn, final Predicate<TowerPlan> veto) {
		TowerVolume volume = TowerVolume.of(drawn);
		if (!volume.reaches(drawn) || veto.test(drawn)) {
			return null;
		}
		TowerPlan read = drawn.withStands(volume);
		List<ChestSpot> chests = TowerChestSpots.place(read, volume);
		if (chests == null) {
			return null;
		}
		TowerPlan done = read.withChestSpots(chests);
		return TowerVolume.of(done).reaches(done) ? done : null;
	}

	/** This plan moved to another room: every spot keeps its offset from the room's corner, and its look. */
	TowerPlan relocated(final int room) {
		return new TowerPlan(this.seed, room, this.archetype, this.variation, this.rotation, this.attempt, this.arrivalX, this.arrivalZ,
			this.arrivalYaw, this.portalX, this.portalZ, this.portalFacing, this.portalStory, this.walls, this.stories, this.flights,
			this.safeZones, this.hall, this.chestSpots, this.rooms, this.runner, this.look);
	}

	/** This plan with its chest spots. */
	TowerPlan withChestSpots(final List<ChestSpot> spots) {
		return new TowerPlan(this.seed, this.floor, this.archetype, this.variation, this.rotation, this.attempt, this.arrivalX, this.arrivalZ,
			this.arrivalYaw, this.portalX, this.portalZ, this.portalFacing, this.portalStory, this.walls, this.stories, this.flights,
			this.safeZones, this.hall, List.copyOf(spots), this.rooms, this.runner, this.look);
	}

	/** This plan with each story read for where bodies stand over its volume, and the ground's walls read from it. */
	TowerPlan withStands(final TowerVolume volume) {
		int size = TowerLayout.SIZE;
		List<TowerStory> read = new ArrayList<>();
		for (TowerStory story : this.stories) {
			boolean[][] stand = new boolean[size][size];
			boolean[][] flat = new boolean[size][size];
			for (int x = 0; x < size; x++) {
				for (int z = 0; z < size; z++) {
					stand[x][z] = volume.stands(x, story.y(), z);
					flat[x][z] = volume.standsFlat(x, story.y(), z);
				}
			}
			read.add(story.withStands(stand, flat));
		}
		boolean[][] ground = new boolean[size][size];
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				ground[x][z] = blocksTheGround(volume.kind(x, 0, z)) || blocksTheGround(volume.kind(x, 1, z));
			}
		}
		return new TowerPlan(this.seed, this.floor, this.archetype, this.variation, this.rotation, this.attempt, this.arrivalX, this.arrivalZ,
			this.arrivalYaw, this.portalX, this.portalZ, this.portalFacing, this.portalStory, ground, List.copyOf(read), this.flights,
			this.safeZones, this.hall, this.chestSpots, this.rooms, this.runner, this.look);
	}

	/** The parts that make a ground spot a wall: the structure, not the portal's plinth and frame, a station, a light or a chest. */
	private static boolean blocksTheGround(final byte kind) {
		return switch (kind) {
			case TowerVolume.WALL, TowerVolume.PILLAR, TowerVolume.SLAB, TowerVolume.LID, TowerVolume.LAMP, TowerVolume.STEP,
				TowerVolume.SUPPORT, TowerVolume.COVER, TowerVolume.RAIL, TowerVolume.STAIR_NORTH, TowerVolume.STAIR_EAST, TowerVolume.STAIR_SOUTH,
				TowerVolume.STAIR_WEST -> true;
			default -> false;
		};
	}

	/**
	 * A splitmix64 finish, so seeds that differ a little draw unlike plans: the first draws of
	 * {@link Random}s seeded a little apart are alike, and the arrival is the very first draw.
	 */
	static long mix(final long value) {
		long z = value + 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/** One draw of the floor's archetype; null when the draw does not fit. */
	private static @Nullable TowerPlan draw(final long seed, final int floor, final TowerArchetype archetype, final int attempt, final Random random) {
		if (archetype == TowerArchetype.HALL) {
			return hallDraw(seed, floor, attempt, random);
		}
		Draft draft = switch (archetype) {
			case GREAT_HALL -> TowerGreatHall.draw(random);
			case CRYSTAL_CAVERN -> TowerCavern.draw(random);
			default -> TowerMaze.labyrinth(random);
		};
		return draft == null ? null : fromDraft(seed, floor, archetype, attempt, draft);
	}

	static TowerPlan fromDraft(final long seed, final int floor, final TowerArchetype archetype, final int attempt, final Draft draft) {
		float yaw = (float) Math.toDegrees(Math.atan2(-(draft.portalX() - draft.arrivalX()), draft.portalZ() - draft.arrivalZ()));
		return new TowerPlan(seed, floor, archetype, Variation.MAZE, 0, attempt, draft.arrivalX(), draft.arrivalZ(), yaw,
			draft.portalX(), draft.portalZ(), draft.portalFacing(), draft.portalStory(), draft.stories().get(0).wall(),
			List.copyOf(draft.stories()), List.copyOf(draft.flights()), List.copyOf(draft.zones()), 0, List.of(), List.of(), draft.runner(), floor);
	}

	/** An elite or boss floor: its variation's walls round the canonical hall, turned, under a lid. */
	private static @Nullable TowerPlan hallDraw(final long seed, final int floor, final int attempt, final Random random) {
		int hall = TowerMaze.hall(floor);
		int turns = random.nextInt(4);
		List<Variation> allowed = hallVariations(floor);
		Variation variation = allowed.get(random.nextInt(allowed.size()));
		TowerShapes.HallWalls shaped = TowerShapes.hallWalls(random, variation, floor);
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
		TowerStory story = TowerStory.of(0, HALL_TOP, TowerStory.full(), walls);
		return new TowerPlan(seed, floor, TowerArchetype.HALL, variation, turns, attempt, at[0], at[1], -90.0F + 90.0F * turns,
			TowerLayout.SIZE / 2, CLASSIC_MIDDLE_Z - 1, -1, 0, walls, List.of(story), List.of(), List.copyOf(zones), hall, List.of(),
			List.copyOf(rooms), null, floor);
	}

	/** The classic layout under a lid: the arrival at the west and the portal in a nook at the east, as a floor falls back to. */
	private static TowerPlan classic(final long seed, final int floor, final TowerArchetype archetype, final int attempt) {
		int hall = TowerMaze.hall(floor);
		boolean[][] walls = TowerMaze.classicWalls(new Random(seed ^ FALLBACK_SALT ^ (floor * 0x9E3779B97F4A7C15L)), floor);
		TowerStory story = TowerStory.of(0, HALL_TOP, TowerStory.full(), walls);
		return new TowerPlan(seed, floor, archetype, Variation.MAZE, 0, attempt, CLASSIC_ARRIVAL_X, CLASSIC_MIDDLE_Z, -90.0F,
			hall > 0 ? TowerLayout.SIZE / 2 : CLASSIC_PORTAL_X, CLASSIC_MIDDLE_Z - 1, -1, 0, walls, List.of(story), List.of(),
			TowerMaze.classicZones(floor), hall, List.of(), List.of(), null, floor);
	}

	/** One quarter turn of the room about its middle: a spot (x, z) goes to (size - 1 - z, x). */
	static int[] turn(final int x, final int z) {
		return new int[] {TowerLayout.SIZE - 1 - z, x};
	}

	static TowerMaze.Zone turn(final TowerMaze.Zone zone) {
		return new TowerMaze.Zone(TowerLayout.SIZE - TowerMaze.SAFE - zone.z(), zone.x(), zone.y(), zone.top());
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

	/** Whether a body can walk from the plan's arrival to its portal, over the plan's whole volume. */
	static boolean reaches(final TowerPlan plan) {
		return TowerVolume.of(plan).reaches(plan);
	}

	/** The plan's every block, as {@link TowerVolume} lays it. Drawn afresh each call. */
	public TowerVolume volume() {
		return TowerVolume.of(this);
	}

	/** What the floor is made of. */
	public TowerTheme theme() {
		return this.archetype.theme(this.look);
	}

	/** The height the portal's story stands at. */
	public int portalY() {
		return this.stories.get(this.portalStory).y();
	}

	/** Whether the spot is one of the two blocks either side of the portal that its frame stands on. */
	boolean isFramePost(final int x, final int z) {
		return x == this.portalX && (z == this.portalZ - 1 || z == this.portalZ + TowerLayout.PORTAL_INNER_WIDTH);
	}

	/**
	 * How many blocks high the ground story's wall at a spot stands: its story's height, except on a
	 * {@link Variation#TALL_WALLS} floor, where the maze's walls are {@value #TALL_WALL_HEIGHT} high. The boss room's
	 * ring and the walls round a hall keep the full height, so a mob or a boss in them is still shut in.
	 */
	public int wallHeightAt(final int x, final int z) {
		int full = this.stories.get(0).top();
		if (this.variation != Variation.TALL_WALLS || isRing(x, z)) {
			return full;
		}
		int low = hallLow() - 2;
		int high = hallLow() + this.hall + 1;
		return this.hall > 0 && x >= low && x <= high && z >= low && z <= high ? full : TALL_WALL_HEIGHT;
	}

	/** Whether a body cannot walk on the ground story at the spot. */
	public boolean wall(final int x, final int z) {
		return this.walls[x][z];
	}

	/** Whether the spot lies in a safe zone, at any height. */
	public boolean inSafeZone(final int x, final int z) {
		for (TowerMaze.Zone zone : this.safeZones) {
			if (zone.contains(x, z)) {
				return true;
			}
		}
		return false;
	}

	/** Whether the spot, at a height from {@link TowerLayout#BASE_Y}, lies in a safe zone: in its square and its band. */
	public boolean inSafeZone(final int x, final int y, final int z) {
		for (TowerMaze.Zone zone : this.safeZones) {
			if (zone.contains(x, y, z)) {
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

	/** Where a player lands: the middle of the arrival's block, on the ground story. */
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

	/** How far the portal's inside stands above its story: {@link #LIFT} on an ordinary or elite floor, {@link #BOSS_LIFT} on a boss floor. */
	public int lift() {
		return this.hall == TowerMaze.BOSS_HALL ? BOSS_LIFT : LIFT;
	}

	/**
	 * The tower wall the raised portal stands on, as absolute spots: on an ordinary or elite floor the blocks
	 * under the frame's bottom row, on a boss floor the pyramid, its layers from the floor up, each layer one
	 * step narrower than the one below.
	 */
	public List<BlockPos> plinth() {
		List<BlockPos> wall = new ArrayList<>();
		BlockPos origin = origin();
		int base = TowerLayout.BASE_Y + portalY();
		int x = origin.getX() + this.portalX;
		int zLow = origin.getZ() + this.portalZ - 1;
		int zHigh = origin.getZ() + this.portalZ + TowerLayout.PORTAL_INNER_WIDTH;
		if (this.hall != TowerMaze.BOSS_HALL) {
			for (int z = zLow; z <= zHigh; z++) {
				wall.add(new BlockPos(x, base + this.lift() - 1 - 1, z));
			}
			return wall;
		}
		// Layer k, counted from the floor, is PYRAMID_LAYERS + 1 - k blocks wider than the frame on every side
		// but the frame's own column: so the top layer is one step, and the first is PYRAMID_LAYERS.
		for (int layer = 0; layer < PYRAMID_LAYERS; layer++) {
			int reach = PYRAMID_LAYERS - layer;
			for (int dx = -reach; dx <= reach; dx++) {
				for (int z = zLow - reach; z <= zHigh + reach; z++) {
					wall.add(new BlockPos(x + dx, base + layer, z));
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
				inside.add(new BlockPos(origin.getX() + this.portalX, TowerLayout.BASE_Y + portalY() + lift() + dy, origin.getZ() + this.portalZ + dz));
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
		int yLow = TowerLayout.BASE_Y + portalY() + lift() - 1;
		int yHigh = TowerLayout.BASE_Y + portalY() + lift() + TowerLayout.PORTAL_INNER_HEIGHT;
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
	 * room's north wall, inside the ring: the classic row turned as the plan was. Empty on any other floor.
	 */
	public List<Station> stations() {
		if (this.hall != TowerMaze.BOSS_HALL) {
			return List.of();
		}
		List<Station> stations = new ArrayList<>();
		int[] offsets = {2, 4, 6};
		net.minecraft.core.Direction[] faces = {null, net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.SOUTH};
		for (int i = 0; i < offsets.length; i++) {
			int[] at = {TowerMaze.BOSS_ROOM_X + offsets[i], TowerMaze.SAFE_Z + 1};
			net.minecraft.core.Direction face = faces[i];
			for (int turns = 0; turns < this.rotation; turns++) {
				at = turn(at[0], at[1]);
				face = face == null ? null : face.getClockWise();
			}
			stations.add(new Station(at[0], at[1], face, i));
		}
		return stations;
	}

	/** Whether the floor's safe room is walled in a ring: a boss floor's. */
	public boolean ring() {
		return this.hall == TowerMaze.BOSS_HALL;
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
