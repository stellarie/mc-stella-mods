package com.stella.tower;

import com.stella.tower.LostCastleGrid.Side;
import com.stella.tower.LostCastleLayout.Family;
import com.stella.tower.LostCastleLayout.Layout;
import com.stella.tower.LostCastleLayout.Work;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * What a lost castle holds, decided from its seed alone. The plan never reads a block, so every chunk of
 * one castle draws the same plan and the castle comes out whole however it is cut into chunks. Spots are
 * in the piece's own frame; {@link LostCastleBuilder} paints the plan.
 * <p>
 * Floors are numbered from the top, 0, down. Every floor but the bottom one rolls a layout family; the bottom
 * floor is the great hall. The stair from floor {@code k} to the floor below it stands on one side of the castle,
 * and the stair from floor {@code k + 1} stands on the other side. Floor 0's stair stands on the side opposite
 * the entrance stair, which lands on the south side.
 */
public record LostCastlePlan(int floors, int drop, List<Level> levels, Vault vault) {
	private static final long SALT = 0x464C4F52L;
	/** The double chests a floor holds, at least and at most. */
	public static final int MIN_CHESTS = 1;
	public static final int MAX_CHESTS = 2;
	/** A chest alcove is cut into the outer wall this wide and this high. */
	public static final int ALCOVE_WIDTH = 3;
	public static final int ALCOVE_HEIGHT = 4;
	/** The spawners of a floor: one in each zone. The bottom floor has none in the portal's zone. */
	public static final int ZONE_SPAWNERS = LostCastleGrid.ZONES * LostCastleGrid.ZONES;
	/** The zone that holds the portal, in the middle of the bottom floor. */
	public static final int PORTAL_ZONE = LostCastleGrid.ZONES / 2;
	/** The portal's pocket on the bottom floor, which holds no wall, prop or spawner. */
	static final int[] POCKET = {19, 20, 29, 31};

	/** A column of a floor. */
	public record Cell(int x, int z) {
	}

	/**
	 * A stair down from one floor to the next, along an outer wall: {@code north} is the low-z wall, and it
	 * descends toward higher x when {@code eastward}. Column {@code j} of the run lies {@code j} blocks
	 * lower than column 0; column 0 is on the upper floor and column {@value LostCastleGrid#STAIR_RUN} is
	 * on the lower floor.
	 */
	public record Stair(boolean north, int topX, boolean eastward) {
		/** The open columns kept before the run on the upper floor, and after it on the lower floor. */
		public static final int BEFORE = 2;
		public static final int AFTER = 3;

		public int step() {
			return this.eastward ? 1 : -1;
		}

		public int xAt(final int j) {
			return this.topX + step() * j;
		}

		/** The first of the two rows the run lies on. */
		public int z0() {
			return this.north ? LostCastleGrid.OUTER : LostCastleGrid.SIZE - LostCastleGrid.OUTER - LostCastleGrid.STAIR_WIDTH;
		}

		/** The row beside the run, on the room's side, where the low barrier stands on the upper floor. */
		public int barrierZ() {
			return this.north ? z0() + LostCastleGrid.STAIR_WIDTH : z0() - 1;
		}

		/** The rows of the run and of its barrier. */
		public int rowLow() {
			return this.north ? z0() : barrierZ();
		}

		public int rowHigh() {
			return this.north ? barrierZ() : z0() + LostCastleGrid.STAIR_WIDTH - 1;
		}

		public int minX() {
			return Math.min(xAt(-BEFORE), xAt(LostCastleGrid.STAIR_RUN + AFTER));
		}

		public int maxX() {
			return Math.max(xAt(-BEFORE), xAt(LostCastleGrid.STAIR_RUN + AFTER));
		}

		public Side wall() {
			return this.north ? Side.LOW_Z : Side.HIGH_Z;
		}
	}

	/** A spawner set in the solid block at {@code (x, z)}, its one open face toward {@code side}'s room. */
	public record Spawner(int x, int z, Side side) {
	}

	/** A recess in an outer wall holding a double chest: {@code first} is the first column along the wall. */
	public record Chest(Side side, int first) {
		/** The recess's column {@code w} along the wall, in the wall layer beside the floor. */
		public Cell cell(final int w) {
			return switch (this.side) {
				case LOW_X -> new Cell(LostCastleGrid.OUTER - 1, this.first + w);
				case HIGH_X -> new Cell(LostCastleGrid.SIZE - LostCastleGrid.OUTER, this.first + w);
				case LOW_Z -> new Cell(this.first + w, LostCastleGrid.OUTER - 1);
				case HIGH_Z -> new Cell(this.first + w, LostCastleGrid.SIZE - LostCastleGrid.OUTER);
			};
		}

		/** The floor column in front of the recess's middle. */
		public Cell front() {
			Cell middle = cell(1);
			return new Cell(middle.x() + this.side.inX(), middle.z() + this.side.inZ());
		}
	}

	/** The loose ruin on a floor. */
	public enum PropKind {
		CANDLE,
		SKULL,
		BARREL,
		ANVIL,
		GRINDSTONE,
		BONE,
		FLOOR_COBWEB,
		RUBBLE,
		MOSS,
		WALL_SKULL,
		WALL_COBWEB,
		WALL_CHAIN,
		CEILING_CHAIN,
		CEILING_COBWEB;

		public boolean onFloor() {
			return this.ordinal() >= SKULL.ordinal() && this.ordinal() <= MOSS.ordinal();
		}

		public boolean onWall() {
			return this == WALL_SKULL || this == WALL_COBWEB || this == WALL_CHAIN;
		}

		public boolean onCeiling() {
			return this == CEILING_CHAIN || this == CEILING_COBWEB;
		}
	}

	/** A prop {@code y} blocks over the floor; {@code arg} is a count, a turn, a length or a side's ordinal. */
	public record Prop(PropKind kind, int x, int y, int z, int arg) {
	}

	/** One floor. {@code exit} is null on the bottom floor, {@code arrival} on the top floor. */
	public record Level(int index, LostFloorKind kind, Family family, Layout layout, @Nullable Stair exit, @Nullable Stair arrival,
		List<Spawner> spawners, List<Chest> chests, List<Prop> props, List<Cell> keys) {
	}

	/**
	 * The hidden vault in a corner of the bottom floor. It fills the corner: four by four inside, walled by the
	 * outer wall on two sides and by two partitions on the others. One partition is cracked stone brick.
	 */
	public record Vault(boolean lowX, boolean lowZ, boolean crackedAlongX) {
		public static final int INSIDE = 4;

		/** The inside's first and last column along x. */
		public int x0() {
			return this.lowX ? LostCastleGrid.IN_LOW : LostCastleGrid.IN_HIGH - INSIDE + 1;
		}

		public int x1() {
			return x0() + INSIDE - 1;
		}

		public int z0() {
			return this.lowZ ? LostCastleGrid.IN_LOW : LostCastleGrid.IN_HIGH - INSIDE + 1;
		}

		public int z1() {
			return z0() + INSIDE - 1;
		}

		/** The column of the partition that closes the inside along x, and the row of the one along z. */
		public int wallX() {
			return this.lowX ? x1() + 1 : x0() - 1;
		}

		public int wallZ() {
			return this.lowZ ? z1() + 1 : z0() - 1;
		}

		/** The chest stands in the inside's corner, against the outer wall. */
		public int chestX() {
			return this.lowX ? x0() : x1();
		}

		public int chestZ() {
			return this.lowZ ? z0() : z1();
		}

		/** The box the vault takes, partitions included. */
		public int[] box() {
			return new int[] {Math.min(x0(), wallX()), Math.min(z0(), wallZ()), Math.max(x1(), wallX()), Math.max(z1(), wallZ())};
		}

		/** The floor column in front of the cracked partition. */
		public Cell front() {
			return this.crackedAlongX
				? new Cell(wallX() + (this.lowX ? 1 : -1), z0() + 1)
				: new Cell(x0() + 1, wallZ() + (this.lowZ ? 1 : -1));
		}
	}

	/** The number of floors a castle draws: 3 to 6, each as often as another. */
	public static int floorCount(final RandomSource random) {
		return LostCastleGrid.MIN_FLOORS + random.nextInt(LostCastleGrid.MAX_FLOORS - LostCastleGrid.MIN_FLOORS + 1);
	}

	/** The plan a seed gives for a castle of {@code floors} floors with a ring {@code drop} blocks over floor 0. */
	public static LostCastlePlan of(final long seed, final int floors, final int drop) {
		RandomSource rng = RandomSource.create(seed ^ SALT);
		LostFloorKind[] kinds = kinds(floors, rng);
		Stair[] exits = new Stair[floors - 1];
		for (int k = 0; k < floors - 1; k++) {
			// The entrance lands on the south side, so floor 0's stair is on the north side, and the sides alternate.
			boolean eastward = rng.nextBoolean();
			int topX = eastward ? 4 + rng.nextInt(25) : 21 + rng.nextInt(25);
			exits[k] = new Stair(k % 2 == 0, topX, eastward);
		}
		Vault vault = vault(exits[floors - 2], rng);
		Family[] rolls = {Family.MAZE, Family.CAVERN, Family.FIELD};
		List<Level> levels = new ArrayList<>();
		for (int k = 0; k < floors; k++) {
			Family family = k == floors - 1 ? Family.HALL : rolls[rng.nextInt(rolls.length)];
			levels.add(level(k, floors, drop, kinds[k], family, k < floors - 1 ? exits[k] : null, k > 0 ? exits[k - 1] : null, vault, rng));
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

	private static boolean overlaps(final int[] a, final int[] b, final int margin) {
		return a[0] - margin <= b[2] && a[2] + margin >= b[0] && a[1] - margin <= b[3] && a[3] + margin >= b[1];
	}

	/** A corner of the bottom floor clear of the stair that lands there. */
	private static Vault vault(final Stair last, final RandomSource rng) {
		int[] band = {last.minX(), last.rowLow(), last.maxX(), last.rowHigh()};
		List<Vault> corners = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			Vault corner = new Vault(i % 2 == 0, i / 2 == 0, false);
			if (!overlaps(corner.box(), band, 3)) {
				corners.add(corner);
			}
		}
		Vault corner = corners.get(rng.nextInt(corners.size()));
		return new Vault(corner.lowX(), corner.lowZ(), rng.nextBoolean());
	}

	/** The stair's band is open on the floor above and the floor below; its run is a shaft above and a ramp below. */
	private static void band(final Work w, final Stair stair, final boolean upper) {
		w.reserve(stair.minX(), stair.rowLow(), stair.maxX(), stair.rowHigh());
		int from = Math.min(stair.xAt(upper ? 2 : 1), stair.xAt(LostCastleGrid.STAIR_RUN));
		int to = Math.max(stair.xAt(upper ? 2 : 1), stair.xAt(LostCastleGrid.STAIR_RUN));
		if (upper) {
			w.hole(from, stair.rowLow(), to, stair.rowHigh());
		} else {
			w.hole(from, stair.z0(), to, stair.z0() + LostCastleGrid.STAIR_WIDTH - 1);
		}
	}

	private static Level level(final int index, final int floors, final int drop, final LostFloorKind kind, final Family family,
		final @Nullable Stair exit, final @Nullable Stair arrival, final Vault vault, final RandomSource rng) {
		boolean top = index == 0;
		boolean bottom = index == floors - 1;
		Work w = new Work(new Random(rng.nextLong()));
		if (top) {
			// The entrance stair lands on the south side; the strip it climbs along is a ramp, not floor.
			int landing = Math.min(LostCastleGrid.IN_HIGH, LostCastleGrid.ENTRY_Z + drop + 1);
			w.reserve(LostCastleGrid.MID - 1, LostCastleGrid.ENTRY_Z + drop - 16, LostCastleGrid.MID + 2, Math.min(LostCastleGrid.IN_HIGH, landing + 2));
			w.hole(LostCastleGrid.MID, LostCastleGrid.ENTRY_Z + drop - 14, LostCastleGrid.MID + 1, LostCastleGrid.ENTRY_Z + drop - 1);
			w.key(LostCastleGrid.MID, landing);
		}
		if (arrival != null) {
			band(w, arrival, false);
			w.key(arrival.xAt(LostCastleGrid.STAIR_RUN + 1), arrival.z0());
		}
		if (exit != null) {
			band(w, exit, true);
			w.key(exit.xAt(0), exit.z0());
		}
		if (bottom) {
			w.reserve(POCKET[0], POCKET[1], POCKET[2], POCKET[3]);
			int[] box = vault.box();
			w.reserve(box[0] - 2, box[1] - 2, box[2] + 2, box[3] + 2);
			w.lock(box[0], box[1], box[2], box[3]);
			w.key(LostCastleBuilder.PORTAL_X, LostCastleBuilder.PORTAL_Z + 5);
			w.key(vault.front().x(), vault.front().z());
		}
		List<Chest> chests = chests(w, rng);
		for (Chest chest : chests) {
			w.key(chest.front().x(), chest.front().z());
		}
		w.generate(family);
		w.applyReserved();
		w.relax();
		w.repair();
		List<Spawner> spawners = LostCastleDress.spawners(w, chests, bottom);
		w.relax();
		w.repair();
		boolean[] route = w.route();
		if (family == Family.CAVERN) {
			w.roughen(route);
		}
		List<Prop> props = LostCastleDress.props(w, route, chests, spawners);
		List<Cell> keys = new ArrayList<>();
		for (int[] key : w.keys) {
			keys.add(new Cell(key[0], key[1]));
		}
		return new Level(index, kind, family, w.toLayout(), exit, arrival, List.copyOf(spawners), List.copyOf(chests), List.copyOf(props), List.copyOf(keys));
	}

	/** One or two chest alcoves on outer walls, each with the floor in front of it kept open. */
	private static List<Chest> chests(final Work w, final RandomSource rng) {
		Random random = new Random(rng.nextLong());
		int want = MIN_CHESTS + random.nextInt(MAX_CHESTS - MIN_CHESTS + 1);
		List<Chest> chests = new ArrayList<>();
		for (int tries = 0; tries < 600 && chests.size() < want; tries++) {
			Side side = Side.values()[random.nextInt(4)];
			int first = LostCastleGrid.IN_LOW + 5 + random.nextInt(LostCastleGrid.IN_HIGH - LostCastleGrid.IN_LOW - 10 - ALCOVE_WIDTH);
			int[] front = frontRect(side, first);
			if (w.taken(front[0] - 1, front[1] - 1, front[2] + 1, front[3] + 1)) {
				continue;
			}
			w.reserve(front[0], front[1], front[2], front[3]);
			chests.add(new Chest(side, first));
		}
		return chests;
	}

	/** The floor in front of an alcove: two deep, the alcove's width and a column more each side. */
	private static int[] frontRect(final Side side, final int first) {
		return switch (side) {
			case LOW_X -> new int[] {LostCastleGrid.IN_LOW, first - 1, LostCastleGrid.IN_LOW + 1, first + ALCOVE_WIDTH};
			case HIGH_X -> new int[] {LostCastleGrid.IN_HIGH - 1, first - 1, LostCastleGrid.IN_HIGH, first + ALCOVE_WIDTH};
			case LOW_Z -> new int[] {first - 1, LostCastleGrid.IN_LOW, first + ALCOVE_WIDTH, LostCastleGrid.IN_LOW + 1};
			case HIGH_Z -> new int[] {first - 1, LostCastleGrid.IN_HIGH - 1, first + ALCOVE_WIDTH, LostCastleGrid.IN_HIGH};
		};
	}

	/** Shuffles a list with a plain random, so the order is the same on every machine. */
	static <T> void shuffle(final List<T> list, final Random random) {
		Collections.shuffle(list, random);
	}
}
