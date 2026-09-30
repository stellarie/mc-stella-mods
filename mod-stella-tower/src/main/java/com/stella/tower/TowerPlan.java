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
	List<Spot> chestSpots
) {
	/** The plan a floor is built to now. */
	public static final int VERSION = 1;
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
	private static final long SALT = 0x504C414EL;
	private static final long FALLBACK_SALT = 0x46414C4CL;
	private static final int CACHE = 512;
	private static final Map<Key, TowerPlan> CACHE_MAP = new LinkedHashMap<>(64, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(final Map.Entry<Key, TowerPlan> eldest) {
			return this.size() > CACHE;
		}
	};

	/** How a floor is laid out. Only the maze exists so far. */
	public enum Variation {
		MAZE
	}

	/** A spot of the room, as an offset from its inner corner. */
	public record Spot(int x, int z) {
	}

	private record Key(long seed, int floor, boolean legacy) {
	}

	/** The plan a floor is built to now, for this seed. Pure and cached. */
	public static TowerPlan of(final long seed, final int floor) {
		return cached(new Key(seed, floor, false));
	}

	/** The plan of a floor built before there were plans: the classic layout. Pure and cached. */
	public static TowerPlan legacy(final long seed, final int floor) {
		return cached(new Key(seed, floor, true));
	}

	/**
	 * The plan the floor really has in this tower: the current plan for a floor not yet built or built to
	 * one, the legacy plan for a floor built without a plan marker.
	 */
	public static TowerPlan at(final ServerLevel tower, final int floor) {
		long seed = tower.getSeed();
		if (!TowerFloors.isBuilt(tower, floor) || TowerFloors.isPlanned(tower, floor)) {
			return of(seed, floor);
		}
		return legacy(seed, floor);
	}

	private static TowerPlan cached(final Key key) {
		synchronized (CACHE_MAP) {
			TowerPlan plan = CACHE_MAP.get(key);
			if (plan != null) {
				return plan;
			}
		}
		TowerPlan plan = key.legacy() ? classic(key.seed(), key.floor(), 0, 0)
			: generate(key.seed(), key.floor(), drawn -> false);
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
		for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
			Random random = new Random(mix(seed ^ SALT ^ (floor * 0x9E3779B97F4A7C15L) ^ (attempt * 0xC2B2AE3D27D4EB4FL)));
			TowerPlan drawn = draw(seed, floor, attempt, random);
			if (reaches(drawn) && !veto.test(drawn)) {
				return drawn;
			}
		}
		return classic(seed, floor, VERSION, FALLBACK);
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

	private static TowerPlan draw(final long seed, final int floor, final int attempt, final Random random) {
		int hall = TowerMaze.hall(floor);
		if (hall > 0) {
			int turns = random.nextInt(4);
			boolean[][] walls = TowerMaze.classicWalls(random, floor);
			for (int i = 0; i < turns; i++) {
				walls = turn(walls);
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
			return new TowerPlan(seed, floor, VERSION, Variation.MAZE, turns, attempt, at[0], at[1], -90.0F + 90.0F * turns,
				TowerLayout.SIZE / 2, CLASSIC_MIDDLE_Z - 1, -1, walls, List.copyOf(zones), hall, List.of());
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
		return new TowerPlan(seed, floor, VERSION, Variation.MAZE, 0, attempt, arrivalX, arrivalZ, yaw,
			portalX, portalZ, facing, walls, List.of(zone), 0, List.of());
	}

	/** The classic layout, as every floor was before the plans, and as a floor falls back to. */
	private static TowerPlan classic(final long seed, final int floor, final int version, final int attempt) {
		int hall = TowerMaze.hall(floor);
		boolean[][] walls = version == 0 ? TowerMaze.legacyWalls(seed, floor)
			: TowerMaze.classicWalls(new Random(seed ^ FALLBACK_SALT ^ (floor * 0x9E3779B97F4A7C15L)), floor);
		return new TowerPlan(seed, floor, version, Variation.MAZE, 0, attempt, CLASSIC_ARRIVAL_X, CLASSIC_MIDDLE_Z, -90.0F,
			hall > 0 ? TowerLayout.SIZE / 2 : CLASSIC_PORTAL_X, CLASSIC_MIDDLE_Z - 1, -1, walls,
			TowerMaze.classicZones(floor), hall, List.of());
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

	/** The portal frame's inside: the blocks the portal fills once the frame is lit. */
	public List<BlockPos> portalInside() {
		BlockPos origin = origin();
		List<BlockPos> inside = new ArrayList<>();
		for (int dz = 0; dz < TowerLayout.PORTAL_INNER_WIDTH; dz++) {
			for (int dy = 0; dy < TowerLayout.PORTAL_INNER_HEIGHT; dy++) {
				inside.add(new BlockPos(origin.getX() + this.portalX, TowerLayout.BASE_Y + dy, origin.getZ() + this.portalZ + dz));
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
		int yLow = TowerLayout.BASE_Y - 1;
		int yHigh = TowerLayout.BASE_Y + TowerLayout.PORTAL_INNER_HEIGHT;
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
	 * room's far wall: the classic row turned as the plan was. Empty on any other floor.
	 */
	public List<Station> stations() {
		if (this.hall != TowerMaze.BOSS_HALL) {
			return List.of();
		}
		List<Station> stations = new ArrayList<>();
		int[] offsets = {2, 4, 6};
		net.minecraft.core.Direction[] faces = {null, net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.SOUTH};
		for (int i = 0; i < offsets.length; i++) {
			int[] at = {TowerMaze.BOSS_ROOM_X + offsets[i], TowerMaze.SAFE_Z};
			net.minecraft.core.Direction face = faces[i];
			for (int turns = 0; turns < this.rotation; turns++) {
				at = turn(at[0], at[1]);
				face = face == null ? null : face.getClockWise();
			}
			stations.add(new Station(at[0], at[1], face, i));
		}
		return stations;
	}

	/** One workstation: its room offset, the way it faces (null for none), and which it is: table, furnace, anvil. */
	public record Station(int x, int z, net.minecraft.core.Direction facing, int kind) {
	}
}
