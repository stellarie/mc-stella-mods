package com.stella.tower;

import com.stella.tower.LostCastleGrid.Side;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * What a lost castle holds, decided from its seed alone. The plan never reads a block, so every chunk of
 * one castle draws the same plan and the castle comes out whole however it is cut into chunks. Spots are
 * in the piece's own frame; {@link LostCastleBuilder} paints the plan.
 * <p>
 * Floors are numbered from the top, 0, down. The stair from floor {@code k} to the floor below it stands on
 * one side of the castle, and the stair from floor {@code k + 1} stands on the other side. Floor 0's stair
 * stands on the side opposite the entrance stair, which lands on the south side.
 */
public record LostCastlePlan(int floors, int drop, List<Level> levels, Vault vault) {
	private static final long SALT = 0x464C4F52L;
	/** Where a spawner stands along its wall, from the room's corner. */
	public static final int SPAWNER_OFFSET = 2;
	/** The spawners of every floor. */
	public static final int SPAWNERS = 2;
	/** The double chests a floor holds, at least and at most. */
	public static final int MIN_CHESTS = 1;
	public static final int MAX_CHESTS = 2;
	/** The empty niches a floor holds besides its chests. */
	public static final int NICHES = 3;
	/** Extra doorways beyond the ones that join every room. */
	public static final int EXTRA_DOORS = 2;
	/** Chest alcoves and niches are cut into an outer wall this wide and this high. */
	public static final int ALCOVE_WIDTH = 3;
	public static final int ALCOVE_HEIGHT = 4;
	public static final int ALCOVE_FROM = 10;

	/** A doorway between room {@code (cx, cz)} and the next room along x, or along z. */
	public record Door(int cx, int cz, boolean alongX) {
	}

	/**
	 * A stair down from one floor to the next, along an outer wall: {@code north} is the low-z wall, and it
	 * descends toward higher x when {@code eastward}. Column {@code j} of the run lies {@code j} blocks
	 * lower than column 0; column 0 is on the upper floor and column {@value LostCastleGrid#STAIR_RUN} is
	 * on the lower floor.
	 */
	public record Stair(int cx, boolean north, boolean eastward) {
		public int room() {
			return this.north ? this.cx : LostCastleGrid.COLS * (LostCastleGrid.COLS - 1) + this.cx;
		}

		public int cz() {
			return this.north ? 0 : LostCastleGrid.COLS - 1;
		}

		/** The x of the run's first column. */
		public int topX() {
			return this.eastward ? LostCastleGrid.cellLow(this.cx) : LostCastleGrid.cellLow(this.cx) + LostCastleGrid.STAIR_RUN;
		}

		public int step() {
			return this.eastward ? 1 : -1;
		}

		public int xAt(final int j) {
			return topX() + step() * j;
		}

		/** The first of the two rows the run lies on. */
		public int z0() {
			return this.north ? LostCastleGrid.OUTER : LostCastleGrid.SIZE - LostCastleGrid.OUTER - LostCastleGrid.STAIR_WIDTH;
		}

		/** The row beside the run, on the room's side, where the low barrier stands on the upper floor. */
		public int barrierZ() {
			return this.north ? z0() + LostCastleGrid.STAIR_WIDTH : z0() - 1;
		}

		public Side wall() {
			return this.north ? Side.LOW_Z : Side.HIGH_Z;
		}
	}

	/** A spawner set in the wall of room {@code (cx, cz)}, in the wall layer next to the room. */
	public record Spawner(int cx, int cz, Side side) {
		public int x() {
			return faceX(this.cx, this.side, SPAWNER_OFFSET);
		}

		public int z() {
			return faceZ(this.cz, this.side, SPAWNER_OFFSET);
		}
	}

	/** A recess in an outer wall; a chest alcove holds a double chest, a niche holds nothing. */
	public record Alcove(int cx, int cz, Side side, int first, boolean chest) {
	}

	/** A 2 by 2 pillar from the floor up {@code height} blocks. */
	public record Pillar(int x, int z, int height) {
	}

	/** A broken wall segment one block thick, {@code length} blocks long, no higher than {@code height}. */
	public record Fragment(int x, int z, boolean alongX, int length, int height) {
	}

	/** The loose ruin on a floor. */
	public enum ScatterKind {
		CANDLE,
		SKULL,
		RUBBLE,
		MOSS,
		COBWEB,
		CHAIN
	}

	public record Scatter(ScatterKind kind, int x, int z, int arg) {
	}

	/** One floor. {@code exit} is null on the bottom floor, {@code arrival} on the top floor. */
	public record Level(int index, LostFloorKind kind, List<Door> doors, @Nullable Stair exit, @Nullable Stair arrival,
		List<Spawner> spawners, List<Alcove> alcoves, List<Pillar> pillars, List<Fragment> fragments, List<Scatter> scatter) {
	}

	/**
	 * The hidden vault in a corner room of the bottom floor. It fills the corner of the room: four by four
	 * inside, walled by the outer wall on two sides and by two partitions on the others. One partition is
	 * cracked stone brick.
	 */
	public record Vault(int cx, int cz, boolean crackedAlongX) {
		public static final int INSIDE = 4;

		public boolean lowX() {
			return this.cx == 0;
		}

		public boolean lowZ() {
			return this.cz == 0;
		}

		/** The inside's first and last column along x. */
		public int x0() {
			return this.lowX() ? LostCastleGrid.cellLow(this.cx) : LostCastleGrid.cellHigh(this.cx) - INSIDE + 1;
		}

		public int x1() {
			return x0() + INSIDE - 1;
		}

		public int z0() {
			return this.lowZ() ? LostCastleGrid.cellLow(this.cz) : LostCastleGrid.cellHigh(this.cz) - INSIDE + 1;
		}

		public int z1() {
			return z0() + INSIDE - 1;
		}

		/** The column of the partition that closes the inside along x, and the row of the one along z. */
		public int wallX() {
			return this.lowX() ? x1() + 1 : x0() - 1;
		}

		public int wallZ() {
			return this.lowZ() ? z1() + 1 : z0() - 1;
		}

		/** The chest stands in the inside's corner, against the outer wall. */
		public int chestX() {
			return this.lowX() ? x0() : x1();
		}

		public int chestZ() {
			return this.lowZ() ? z0() : z1();
		}
	}

	/** The number of floors a castle draws: 3 to 6, each as often as another. */
	public static int floorCount(final RandomSource random) {
		return LostCastleGrid.MIN_FLOORS + random.nextInt(LostCastleGrid.MAX_FLOORS - LostCastleGrid.MIN_FLOORS + 1);
	}

	static int faceX(final int cx, final Side side, final int offset) {
		return switch (side) {
			case LOW_X -> LostCastleGrid.cellLow(cx) - 1;
			case HIGH_X -> LostCastleGrid.cellHigh(cx) + 1;
			default -> LostCastleGrid.cellLow(cx) + offset;
		};
	}

	static int faceZ(final int cz, final Side side, final int offset) {
		return switch (side) {
			case LOW_Z -> LostCastleGrid.cellLow(cz) - 1;
			case HIGH_Z -> LostCastleGrid.cellHigh(cz) + 1;
			default -> LostCastleGrid.cellLow(cz) + offset;
		};
	}

	/** The columns of the room just inside a face: {@code depth} columns from the wall, over the offsets from {@code from} to {@code to}. */
	static List<int[]> front(final int cx, final int cz, final Side side, final int from, final int to, final int depth) {
		List<int[]> columns = new ArrayList<>();
		for (int offset = from; offset <= to; offset++) {
			for (int d = 0; d < depth; d++) {
				int x = switch (side) {
					case LOW_X -> LostCastleGrid.cellLow(cx) + d;
					case HIGH_X -> LostCastleGrid.cellHigh(cx) - d;
					default -> LostCastleGrid.cellLow(cx) + offset;
				};
				int z = switch (side) {
					case LOW_Z -> LostCastleGrid.cellLow(cz) + d;
					case HIGH_Z -> LostCastleGrid.cellHigh(cz) - d;
					default -> LostCastleGrid.cellLow(cz) + offset;
				};
				columns.add(new int[] {x, z});
			}
		}
		return columns;
	}

	/** The plan a seed gives for a castle of {@code floors} floors with a ring {@code drop} blocks over floor 0. */
	public static LostCastlePlan of(final long seed, final int floors, final int drop) {
		RandomSource rng = RandomSource.create(seed ^ SALT);
		LostFloorKind[] kinds = kinds(floors, rng);
		Stair[] exits = new Stair[floors - 1];
		for (int k = 0; k < floors - 1; k++) {
			// The entrance lands on the south side, so floor 0's stair is on the north side, and the sides alternate.
			exits[k] = new Stair(rng.nextInt(LostCastleGrid.COLS), k % 2 == 0, rng.nextBoolean());
		}
		Vault vault = vault(exits[floors - 2], rng);
		List<Level> levels = new ArrayList<>();
		for (int k = 0; k < floors; k++) {
			levels.add(level(k, floors, kinds[k], k < floors - 1 ? exits[k] : null, k > 0 ? exits[k - 1] : null, vault, rng));
		}
		return new LostCastlePlan(floors, drop, List.copyOf(levels), vault);
	}

	/** The kinds of the floors, top to bottom: no kind twice in a row, and the bottom floor is always the sanctum. */
	private static LostFloorKind[] kinds(final int floors, final RandomSource rng) {
		LostFloorKind[] kinds = new LostFloorKind[floors];
		LostFloorKind[] top = LostFloorKind.of(LostFloorKind.Slot.TOP);
		kinds[0] = top[rng.nextInt(top.length)];
		List<LostFloorKind> pool = new ArrayList<>();
		for (int k = 1; k < floors - 1; k++) {
			if (pool.isEmpty()) {
				for (LostFloorKind kind : LostFloorKind.of(LostFloorKind.Slot.MIDDLE)) {
					if (kind != kinds[k - 1]) {
						pool.add(kind);
					}
				}
			}
			kinds[k] = pool.remove(rng.nextInt(pool.size()));
		}
		kinds[floors - 1] = LostFloorKind.SANCTUM;
		return kinds;
	}

	/** A corner room of the bottom floor, other than the room the last stair lands in. */
	private static Vault vault(final Stair lastExit, final RandomSource rng) {
		List<int[]> corners = new ArrayList<>();
		for (int cz = 0; cz < LostCastleGrid.COLS; cz += LostCastleGrid.COLS - 1) {
			for (int cx = 0; cx < LostCastleGrid.COLS; cx += LostCastleGrid.COLS - 1) {
				if (lastExit.room() != cz * LostCastleGrid.COLS + cx) {
					corners.add(new int[] {cx, cz});
				}
			}
		}
		int[] corner = corners.get(rng.nextInt(corners.size()));
		return new Vault(corner[0], corner[1], rng.nextBoolean());
	}

	private static Level level(final int index, final int floors, final LostFloorKind kind, final @Nullable Stair exit, final @Nullable Stair arrival,
		final Vault vault, final RandomSource rng) {
		boolean top = index == 0;
		boolean bottom = index == floors - 1;
		int hub = LostCastleGrid.COLS + 1;
		Set<Integer> stairRooms = new HashSet<>();
		if (exit != null) {
			stairRooms.add(exit.room());
		}
		if (arrival != null) {
			stairRooms.add(arrival.room());
		}
		Set<Long> taken = new HashSet<>();
		for (Stair stair : new Stair[] {exit, arrival}) {
			if (stair != null) {
				// The whole band along the outer wall is the stair's: the run, its barrier and the way to it.
				int rows = LostCastleGrid.STAIR_WIDTH + 2;
				for (int x = LostCastleGrid.cellLow(stair.cx()); x <= LostCastleGrid.cellHigh(stair.cx()); x++) {
					for (int r = 0; r < rows; r++) {
						taken.add(LostCastleGrid.key(x, stair.north() ? LostCastleGrid.OUTER + r : LostCastleGrid.SIZE - LostCastleGrid.OUTER - 1 - r));
					}
				}
			}
		}
		if (top) {
			// The entrance stair's run and the way beside it, from the middle of the footprint to its south side.
			for (int x = LostCastleGrid.MID - 2; x <= LostCastleGrid.MID + 3; x++) {
				for (int z = LostCastleGrid.ENTRY_Z; z < LostCastleGrid.SIZE - LostCastleGrid.OUTER; z++) {
					taken.add(LostCastleGrid.key(x, z));
				}
			}
		}
		if (bottom) {
			for (int[] column : roomColumns(1, 1)) {
				taken.add(LostCastleGrid.key(column[0], column[1]));
			}
			for (int x = vault.x0() - 1; x <= vault.x1() + 2; x++) {
				for (int z = vault.z0() - 1; z <= vault.z1() + 2; z++) {
					taken.add(LostCastleGrid.key(x, z));
				}
			}
		}
		List<Door> doors = doors(rng);
		List<Spawner> spawners = spawners(top, bottom, stairRooms, hub, vault, rng);
		for (Spawner spawner : spawners) {
			for (int[] column : front(spawner.cx(), spawner.cz(), spawner.side(), SPAWNER_OFFSET - 1, SPAWNER_OFFSET + 1, 2)) {
				taken.add(LostCastleGrid.key(column[0], column[1]));
			}
		}
		List<Alcove> alcoves = alcoves(stairRooms, spawners, vault, bottom, rng);
		for (Alcove alcove : alcoves) {
			for (int[] column : front(alcove.cx(), alcove.cz(), alcove.side(), alcove.first() - 1, alcove.first() + ALCOVE_WIDTH - 1, 2)) {
				taken.add(LostCastleGrid.key(column[0], column[1]));
			}
		}
		List<Pillar> pillars = new ArrayList<>();
		List<Fragment> fragments = new ArrayList<>();
		hiding(pillars, fragments, taken, bottom, hub, rng);
		List<Scatter> scatter = scatter(taken, rng);
		return new Level(index, kind, List.copyOf(doors), exit, arrival, List.copyOf(spawners), List.copyOf(alcoves), List.copyOf(pillars),
			List.copyOf(fragments), List.copyOf(scatter));
	}

	private static List<int[]> roomColumns(final int cx, final int cz) {
		List<int[]> columns = new ArrayList<>();
		for (int x = LostCastleGrid.cellLow(cx); x <= LostCastleGrid.cellHigh(cx); x++) {
			for (int z = LostCastleGrid.cellLow(cz); z <= LostCastleGrid.cellHigh(cz); z++) {
				columns.add(new int[] {x, z});
			}
		}
		return columns;
	}

	/** A doorway between every pair of rooms a walk through all nine needs, and a few more. */
	private static List<Door> doors(final RandomSource rng) {
		int cols = LostCastleGrid.COLS;
		boolean[] seen = new boolean[cols * cols];
		List<Door> doors = new ArrayList<>();
		List<Integer> trail = new ArrayList<>();
		int start = rng.nextInt(cols * cols);
		seen[start] = true;
		trail.add(start);
		while (!trail.isEmpty()) {
			int at = trail.get(trail.size() - 1);
			int cx = at % cols;
			int cz = at / cols;
			List<Integer> ways = new ArrayList<>();
			if (cx + 1 < cols && !seen[at + 1]) {
				ways.add(at + 1);
			}
			if (cx > 0 && !seen[at - 1]) {
				ways.add(at - 1);
			}
			if (cz + 1 < cols && !seen[at + cols]) {
				ways.add(at + cols);
			}
			if (cz > 0 && !seen[at - cols]) {
				ways.add(at - cols);
			}
			if (ways.isEmpty()) {
				trail.remove(trail.size() - 1);
				continue;
			}
			int to = ways.get(rng.nextInt(ways.size()));
			doors.add(door(at, to));
			seen[to] = true;
			trail.add(to);
		}
		for (int extra = 0; extra < EXTRA_DOORS; extra++) {
			int cx = rng.nextInt(cols);
			int cz = rng.nextInt(cols);
			boolean alongX = rng.nextBoolean();
			Door door = new Door(cx, cz, alongX);
			boolean inside = alongX ? cx + 1 < cols : cz + 1 < cols;
			if (inside && !doors.contains(door)) {
				doors.add(door);
			}
		}
		return doors;
	}

	private static Door door(final int a, final int b) {
		int low = Math.min(a, b);
		boolean alongX = Math.abs(a - b) == 1;
		return new Door(low % LostCastleGrid.COLS, low / LostCastleGrid.COLS, alongX);
	}

	/** An id of the wall a room's face is part of, equal for the two rooms that share a wall. */
	private static int wallId(final int cx, final int cz, final Side side) {
		int cols = LostCastleGrid.COLS;
		return switch (side) {
			case HIGH_X -> cx == cols - 1 ? outer(cx, cz, side) : 100 + cz * cols + cx;
			case LOW_X -> cx == 0 ? outer(cx, cz, side) : 100 + cz * cols + cx - 1;
			case HIGH_Z -> cz == cols - 1 ? outer(cx, cz, side) : 200 + cz * cols + cx;
			case LOW_Z -> cz == 0 ? outer(cx, cz, side) : 200 + (cz - 1) * cols + cx;
		};
	}

	private static int outer(final int cx, final int cz, final Side side) {
		return 1000 + (cz * LostCastleGrid.COLS + cx) * 4 + side.ordinal();
	}

	private static boolean onOuterWall(final int cx, final int cz, final Side side) {
		return wallId(cx, cz, side) >= 1000;
	}

	/** Two spawners, each in a different room and in a different wall. */
	private static List<Spawner> spawners(final boolean top, final boolean bottom, final Set<Integer> stairRooms, final int hub, final Vault vault,
		final RandomSource rng) {
		int cols = LostCastleGrid.COLS;
		List<Integer> rooms = new ArrayList<>();
		for (int room = 0; room < cols * cols; room++) {
			int cx = room % cols;
			int cz = room / cols;
			boolean entranceRoom = top && cx == cols / 2 && cz >= cols / 2;
			boolean vaultRoom = bottom && cx == vault.cx() && cz == vault.cz();
			if (!stairRooms.contains(room) && !entranceRoom && !vaultRoom && !(bottom && room == hub)) {
				rooms.add(room);
			}
		}
		Collections.shuffle(rooms, new java.util.Random(rng.nextLong()));
		List<Spawner> spawners = new ArrayList<>();
		Set<Integer> walls = new HashSet<>();
		for (int room : rooms) {
			if (spawners.size() == SPAWNERS) {
				break;
			}
			int cx = room % cols;
			int cz = room / cols;
			Side[] sides = Side.values();
			int turn = rng.nextInt(sides.length);
			for (int i = 0; i < sides.length; i++) {
				Side side = sides[(turn + i) % sides.length];
				if (walls.add(wallId(cx, cz, side))) {
					spawners.add(new Spawner(cx, cz, side));
					break;
				}
			}
		}
		return spawners;
	}

	/** The chest alcoves and the niches, on outer walls. A face holds one recess and no spawner. */
	private static List<Alcove> alcoves(final Set<Integer> stairRooms, final List<Spawner> spawners, final Vault vault, final boolean bottom,
		final RandomSource rng) {
		int cols = LostCastleGrid.COLS;
		Set<Integer> used = new HashSet<>();
		for (Spawner spawner : spawners) {
			used.add(wallId(spawner.cx(), spawner.cz(), spawner.side()));
		}
		List<Object[]> faces = new ArrayList<>();
		for (int room = 0; room < cols * cols; room++) {
			int cx = room % cols;
			int cz = room / cols;
			boolean vaultRoom = bottom && cx == vault.cx() && cz == vault.cz();
			if (stairRooms.contains(room) || vaultRoom) {
				continue;
			}
			for (Side side : Side.values()) {
				if (onOuterWall(cx, cz, side) && !used.contains(wallId(cx, cz, side))) {
					faces.add(new Object[] {cx, cz, side});
				}
			}
		}
		Collections.shuffle(faces, new java.util.Random(rng.nextLong()));
		int chests = MIN_CHESTS + rng.nextInt(MAX_CHESTS - MIN_CHESTS + 1);
		List<Alcove> alcoves = new ArrayList<>();
		for (int i = 0; i < chests + NICHES && i < faces.size(); i++) {
			Object[] face = faces.get(i);
			alcoves.add(new Alcove((Integer) face[0], (Integer) face[1], (Side) face[2], ALCOVE_FROM + rng.nextInt(2), i < chests));
		}
		return alcoves;
	}

	/** Pillars and broken wall segments in the corners of the rooms, never in a lane and never on a taken column. */
	private static void hiding(final List<Pillar> pillars, final List<Fragment> fragments, final Set<Long> taken, final boolean bottom,
		final int hub, final RandomSource rng) {
		int cols = LostCastleGrid.COLS;
		for (int room = 0; room < cols * cols; room++) {
			if (bottom && room == hub) {
				continue;
			}
			int cx = room % cols;
			int cz = room / cols;
			for (int qz = 0; qz < 2; qz++) {
				for (int qx = 0; qx < 2; qx++) {
					int x0 = LostCastleGrid.cellLow(cx) + qx * (LostCastleGrid.CELL - 5);
					int z0 = LostCastleGrid.cellLow(cz) + qz * (LostCastleGrid.CELL - 5);
					int roll = rng.nextInt(100);
					if (roll < 30) {
						int px = x0 + 1 + rng.nextInt(2);
						int pz = z0 + 1 + rng.nextInt(2);
						int height = rng.nextBoolean() ? LostCastleGrid.CLEAR : 4 + rng.nextInt(6);
						if (free(taken, px, pz, 2, 2)) {
							take(taken, px, pz, 2, 2);
							pillars.add(new Pillar(px, pz, height));
						}
					} else if (roll < 65) {
						boolean alongX = rng.nextBoolean();
						int length = 4;
						int fx = alongX ? x0 + rng.nextInt(2) : x0 + rng.nextInt(5);
						int fz = alongX ? z0 + rng.nextInt(5) : z0 + rng.nextInt(2);
						int height = 3 + rng.nextInt(4);
						if (free(taken, fx, fz, alongX ? length : 1, alongX ? 1 : length)) {
							take(taken, fx, fz, alongX ? length : 1, alongX ? 1 : length);
							fragments.add(new Fragment(fx, fz, alongX, length, height));
						}
					}
				}
			}
		}
	}

	private static boolean free(final Set<Long> taken, final int x, final int z, final int width, final int depth) {
		for (int dx = 0; dx < width; dx++) {
			for (int dz = 0; dz < depth; dz++) {
				if (taken.contains(LostCastleGrid.key(x + dx, z + dz))) {
					return false;
				}
			}
		}
		return true;
	}

	private static void take(final Set<Long> taken, final int x, final int z, final int width, final int depth) {
		for (int dx = 0; dx < width; dx++) {
			for (int dz = 0; dz < depth; dz++) {
				taken.add(LostCastleGrid.key(x + dx, z + dz));
			}
		}
	}

	/** Candles, skulls, rubble, moss, cobwebs and chains on the columns left, off every lane. */
	private static List<Scatter> scatter(final Set<Long> taken, final RandomSource rng) {
		List<int[]> spots = new ArrayList<>();
		int cols = LostCastleGrid.COLS;
		for (int cz = 0; cz < cols; cz++) {
			for (int cx = 0; cx < cols; cx++) {
				for (int[] column : roomColumns(cx, cz)) {
					boolean lane = LostCastleGrid.inLane(column[0] - LostCastleGrid.cellLow(cx)) || LostCastleGrid.inLane(column[1] - LostCastleGrid.cellLow(cz));
					if (!lane && !taken.contains(LostCastleGrid.key(column[0], column[1]))) {
						spots.add(column);
					}
				}
			}
		}
		Collections.shuffle(spots, new java.util.Random(rng.nextLong()));
		ScatterKind[] kinds = ScatterKind.values();
		int[] counts = {10, 5, 10, 14, 10, 6};
		List<Scatter> scatter = new ArrayList<>();
		int next = 0;
		for (int kind = 0; kind < kinds.length; kind++) {
			for (int i = 0; i < counts[kind] && next < spots.size(); i++) {
				int[] at = spots.get(next++);
				int arg = switch (kinds[kind]) {
					case CANDLE -> 1 + rng.nextInt(4);
					case SKULL -> rng.nextInt(16);
					case CHAIN -> 1 + rng.nextInt(3);
					default -> 0;
				};
				scatter.add(new Scatter(kinds[kind], at[0], at[1], arg));
			}
		}
		return scatter;
	}
}
