package com.stella.ruins.site.fallenwatchtower;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.IntBinaryOperator;
import net.minecraft.util.RandomSource;

/**
 * Every block of one Fallen Watchtower as a function of the piece seed. The plan reads no world block, so the layout
 * draws the same tower in every chunk and a dry run recovers the same markers.
 * <p>
 * Local frame: x runs 0 to 20, z runs 0 to 20, and y runs from the ground floor block at 0 up. The door is on the low
 * z face. The shaft is 9 by 9 at x and z 6 to 14. The inside is 7 by 7. Floor k has its floor block at y = 6 k, and
 * the roof deck is floor N.
 * <p>
 * A square spiral stair climbs the 24 inner ring cells. A flight takes one side: a landing, five stair blocks, a
 * landing. The next floor has no block above steps 3 to 5, so each flight has a stairwell hole.
 */
public final class WatchtowerPlan {
	public static final int SIZE_X = 21;
	public static final int SIZE_Y = 44;
	public static final int SIZE_Z = 21;
	public static final int FLOOR_HEIGHT = 6;
	public static final int MIN_FLOORS = 4;
	public static final int MAX_CROWN = 4;
	/** The most garrison mobs a tower holds. */
	public static final int GARRISON_CAP = 16;
	/** The shaft's wall ring and its inside, in local x and z. */
	public static final int WALL_LOW = 6;
	public static final int WALL_HIGH = 14;
	public static final int IN_LOW = 7;
	public static final int IN_HIGH = 13;
	public static final int CENTER = 10;
	/** The outer ring, one block out from the wall: the plinth below and the lookout platform above. */
	public static final int OUTER_LOW = 5;
	public static final int OUTER_HIGH = 15;

	public static final byte AIR = 0;
	public static final byte WALL = 1;
	public static final byte WALL_CRACKED = 2;
	public static final byte WALL_MOSSY = 3;
	public static final byte FLOOR = 4;
	public static final byte STONE_FLOOR = 5;
	public static final byte STAIR = 6;
	public static final byte PLINTH = 7;
	public static final byte RUBBLE_COBBLE = 8;
	public static final byte RUBBLE_MOSSY = 9;
	public static final byte RUBBLE_CRACKED = 10;
	public static final byte RUBBLE_SLAB = 11;
	public static final byte BED_FOOT = 12;
	public static final byte BED_HEAD = 13;
	public static final byte BARREL = 14;
	public static final byte HAY = 15;
	public static final byte FLETCHING = 16;
	public static final byte GRINDSTONE = 17;
	public static final byte RACK = 18;
	public static final byte WEB = 19;
	public static final byte TABLE = 20;
	public static final byte CORE = 21;
	public static final byte VAULT = 22;
	public static final byte WATCH_FIRE = 23;
	public static final byte DAIS = 24;
	public static final byte CORBEL = 25;
	public static final byte CHEST = 26;

	public static final String ARCHER = "archer";
	public static final String CROSSBOW = "crossbow";
	public static final String CAPTAIN = "captain";

	public static final int BUNKS = 0;
	public static final int ARMORY = 1;
	public static final int LOOKOUT = 2;
	public static final int STORAGE = 3;
	/** The ground floor has its own fixed furniture. */
	public static final int GATE = -1;

	/** Local direction codes: 0 is +x, 1 is +z, 2 is -x, 3 is -z. */
	public static final int[] DX = {1, 0, -1, 0};
	public static final int[] DZ = {0, 1, 0, -1};

	private static final long SALT_MIX = 0x9E3779B97F4A7C15L;
	private static final int[][] SLOTS = {{9, 9}, {11, 9}, {9, 11}, {11, 11}};
	private static final int[][] ORTHO = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	/** A named spot: a marker of the layout. Y is the standing level of a mob. */
	public record Marker(String name, int x, int y, int z) {
	}

	/** One flight of the stair, in the direction it was drawn. */
	public record Flight(int floor, int[] start, int[] direction, int[][] steps, int[] end, String side) {
	}

	public final long seed;
	public final int floors;
	public final int crown;
	public final boolean counterClockwise;
	/** The side of the breach as the sketch names it: 0 back, 1 right, 2 front (the door side), 3 left. */
	public final int side;
	public final int depth;
	public final int breachFloor;
	public final int halfWidth;
	public final int weathering;
	public final int[] template;
	public final int[] turn;
	public final List<Flight> flights = new ArrayList<>();
	public final List<Marker> markers = new ArrayList<>();
	public final List<int[]> route = new ArrayList<>();
	public int removedWall;
	public int removedFloor;
	public int rubble;

	private final byte[] cells = new byte[SIZE_X * SIZE_Y * SIZE_Z];
	private final byte[] dirs = new byte[SIZE_X * SIZE_Y * SIZE_Z];
	private final boolean[] holes = new boolean[SIZE_X * SIZE_Y * SIZE_Z];
	private final byte[] gone = new byte[SIZE_X * SIZE_Y * SIZE_Z];
	private final boolean[] protectedCells = new boolean[SIZE_X * SIZE_Y * SIZE_Z];
	private final boolean[] clear = new boolean[SIZE_X * SIZE_Y * SIZE_Z];
	private final List<int[]> removed = new ArrayList<>();
	private final List<int[]> chestSpots = new ArrayList<>();
	private int[][] deckPath = new int[0][];
	private int[] core;
	private int[] vault;
	private int[] captainCell;

	private WatchtowerPlan(final long seed) {
		this.seed = seed;
		RandomSource roll = stream(seed, 0);
		this.floors = MIN_FLOORS + roll.nextInt(3);
		this.crown = roll.nextInt(MAX_CROWN + 1);
		this.counterClockwise = roll.nextBoolean();
		this.side = roll.nextInt(4);
		this.depth = 2 + roll.nextInt(3);
		this.breachFloor = 1 + roll.nextInt(this.floors - 2);
		this.halfWidth = 2 + roll.nextInt(3);
		this.weathering = 15 + roll.nextInt(26);
		this.template = new int[this.floors];
		this.turn = new int[this.floors];
		this.template[0] = GATE;
		for (int k = 1; k < this.floors; k++) {
			int t;
			do {
				t = roll.nextInt(4);
			} while (k > 1 && t == this.template[k - 1]);
			this.template[k] = t;
			this.turn[k] = roll.nextInt(4);
		}
	}

	/** A random stream for one part of the plan, salted from the piece seed the way {@code LayoutContext.random(salt)} is. */
	public static RandomSource stream(final long seed, final long salt) {
		return RandomSource.create(seed ^ (salt + 1L) * SALT_MIX);
	}

	public static WatchtowerPlan derive(final long seed) {
		WatchtowerPlan plan = new WatchtowerPlan(seed);
		plan.build();
		return plan;
	}

	/** The design height: 6 N + c, 24 to 40. The breach side stands lower. */
	public int height() {
		return FLOOR_HEIGHT * this.floors + this.crown;
	}

	public int floorY(final int floor) {
		return FLOOR_HEIGHT * floor;
	}

	public static boolean inBox(final int x, final int y, final int z) {
		return x >= 0 && x < SIZE_X && y >= 0 && y < SIZE_Y && z >= 0 && z < SIZE_Z;
	}

	private static int idx(final int x, final int y, final int z) {
		return (y * SIZE_Z + z) * SIZE_X + x;
	}

	public byte cell(final int x, final int y, final int z) {
		return inBox(x, y, z) ? this.cells[idx(x, y, z)] : AIR;
	}

	public int dir(final int x, final int y, final int z) {
		return inBox(x, y, z) ? this.dirs[idx(x, y, z)] : 0;
	}

	/** True where a floor block fell in the collapse. */
	public boolean fell(final int x, final int y, final int z) {
		return inBox(x, y, z) && this.gone[idx(x, y, z)] == 2;
	}

	/** True where a wall block fell in the collapse. */
	public boolean wallFell(final int x, final int y, final int z) {
		return inBox(x, y, z) && this.gone[idx(x, y, z)] == 1;
	}

	/** True where a stairwell hole was cut, or a floor block fell. */
	public boolean hole(final int x, final int y, final int z) {
		return inBox(x, y, z) && this.holes[idx(x, y, z)];
	}

	public int[] core() {
		return this.core;
	}

	public int[] vault() {
		return this.vault;
	}

	public int[] captainCell() {
		return this.captainCell;
	}

	public List<Marker> markers(final String name) {
		List<Marker> out = new ArrayList<>();
		for (Marker marker : this.markers) {
			if (marker.name().equals(name)) {
				out.add(marker);
			}
		}
		return out;
	}

	public static boolean isWall(final byte id) {
		return id == WALL || id == WALL_CRACKED || id == WALL_MOSSY;
	}

	public static boolean isRubble(final byte id) {
		return id >= RUBBLE_COBBLE && id <= RUBBLE_SLAB;
	}

	private void put(final int x, final int y, final int z, final byte id) {
		if (inBox(x, y, z)) {
			this.cells[idx(x, y, z)] = id;
		}
	}

	private void protect(final int x, final int y, final int z) {
		if (inBox(x, y, z)) {
			this.protectedCells[idx(x, y, z)] = true;
		}
	}

	private boolean isProtected(final int x, final int y, final int z) {
		return inBox(x, y, z) && this.protectedCells[idx(x, y, z)];
	}

	private void markClear(final int x, final int feet, final int z) {
		for (int i = 0; i < 3; i++) {
			if (inBox(x, feet + i, z)) {
				this.clear[idx(x, feet + i, z)] = true;
			}
		}
	}

	private boolean isClear(final int x, final int y, final int z) {
		return inBox(x, y, z) && this.clear[idx(x, y, z)];
	}

	private static boolean ring(final int x, final int z) {
		return x >= WALL_LOW && x <= WALL_HIGH && z >= WALL_LOW && z <= WALL_HIGH
			&& (x == WALL_LOW || x == WALL_HIGH || z == WALL_LOW || z == WALL_HIGH);
	}

	private static boolean outerRing(final int x, final int z) {
		return x >= OUTER_LOW && x <= OUTER_HIGH && z >= OUTER_LOW && z <= OUTER_HIGH
			&& (x == OUTER_LOW || x == OUTER_HIGH || z == OUTER_LOW || z == OUTER_HIGH);
	}

	/** The wall ring rises to this many blocks above the roof deck at a ring spot. */
	public static int crownAt(final int x, final int z, final int crown) {
		int cx = Math.min(x - WALL_LOW, WALL_HIGH - x);
		int cz = Math.min(z - WALL_LOW, WALL_HIGH - z);
		if (cx + cz <= 1) {
			return crown;
		}
		return ((x + z) & 1) == 0 ? Math.max(0, crown - 1) : Math.max(0, crown - 2);
	}

	private static int dirCode(final int dx, final int dz) {
		return dx == 1 ? 0 : dz == 1 ? 1 : dx == -1 ? 2 : 3;
	}

	/** The flight of a floor. A counter-clockwise tower mirrors every flight in x. */
	public static Flight flight(final int floor, final boolean counterClockwise) {
		int[][] starts = {{7, 7}, {7, 13}, {13, 13}, {13, 7}};
		int[][] dirs = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
		int[] a = starts[floor % 4].clone();
		int[] u = dirs[floor % 4].clone();
		int[][] steps = new int[5][];
		for (int i = 1; i <= 5; i++) {
			steps[i - 1] = new int[] {a[0] + u[0] * i, a[1] + u[1] * i};
		}
		int[] b = {a[0] + u[0] * 6, a[1] + u[1] * 6};
		if (counterClockwise) {
			a = new int[] {20 - a[0], a[1]};
			u = new int[] {-u[0], u[1]};
			for (int i = 0; i < 5; i++) {
				steps[i] = new int[] {20 - steps[i][0], steps[i][1]};
			}
			b = new int[] {20 - b[0], b[1]};
		}
		int[] c = steps[0];
		String name = c[0] == IN_LOW ? "west" : c[0] == IN_HIGH ? "east" : c[1] == IN_HIGH ? "north" : "south";
		return new Flight(floor, a, u, steps, b, name);
	}

	/** An inside cell at a lateral spot and a depth from the wall of one side. */
	public static int[] insideCell(final int side, final int lateral, final int depth) {
		return switch (side) {
			case 0 -> new int[] {lateral, IN_HIGH - depth};
			case 1 -> new int[] {IN_HIGH - depth, lateral};
			case 2 -> new int[] {lateral, IN_LOW + depth};
			default -> new int[] {IN_LOW + depth, lateral};
		};
	}

	public static int depthFrom(final int side, final int x, final int z) {
		return switch (side) {
			case 0 -> IN_HIGH - z;
			case 1 -> IN_HIGH - x;
			case 2 -> z - IN_LOW;
			default -> x - IN_LOW;
		};
	}

	private static int lateralOf(final int side, final int x, final int z) {
		return side == 0 || side == 2 ? x : z;
	}

	private static int[] wallCell(final int side, final int lateral, final boolean outer) {
		int low = outer ? OUTER_LOW : WALL_LOW;
		int high = outer ? OUTER_HIGH : WALL_HIGH;
		return switch (side) {
			case 0 -> new int[] {lateral, high};
			case 1 -> new int[] {high, lateral};
			case 2 -> new int[] {lateral, low};
			default -> new int[] {low, lateral};
		};
	}

	private static int[] wallOfSide(final String side, final int lateral) {
		return switch (side) {
			case "south" -> new int[] {lateral, WALL_LOW};
			case "north" -> new int[] {lateral, WALL_HIGH};
			case "west" -> new int[] {WALL_LOW, lateral};
			default -> new int[] {WALL_HIGH, lateral};
		};
	}

	/** The slots a mob may stand on, far from the breach first. */
	public int[][] farSlots() {
		Integer[] order = {0, 1, 2, 3};
		Arrays.sort(order, (a, b) -> {
			int ra = depthFrom(this.side, SLOTS[a][0], SLOTS[a][1]);
			int rb = depthFrom(this.side, SLOTS[b][0], SLOTS[b][1]);
			return rb != ra ? Integer.compare(rb, ra) : Integer.compare(a, b);
		});
		int[][] out = new int[4][];
		for (int i = 0; i < 4; i++) {
			out[i] = SLOTS[order[i]];
		}
		return out;
	}

	private int[][] deckTargets() {
		return new int[][] {insideCell(this.side, 10, 4), insideCell(this.side, 8, 5), insideCell(this.side, 12, 5)};
	}

	private static List<int[]> dijkstra(final int[] src, final int[] dst, final BiPredicate<Integer, Integer> ok, final IntBinaryOperator cost) {
		int n = 49;
		int[] dist = new int[n];
		int[] prev = new int[n];
		boolean[] done = new boolean[n];
		Arrays.fill(dist, Integer.MAX_VALUE);
		Arrays.fill(prev, -1);
		int s = (src[0] - IN_LOW) + (src[1] - IN_LOW) * 7;
		int t = (dst[0] - IN_LOW) + (dst[1] - IN_LOW) * 7;
		dist[s] = 0;
		while (true) {
			int best = -1;
			for (int i = 0; i < n; i++) {
				if (!done[i] && dist[i] != Integer.MAX_VALUE && (best < 0 || dist[i] < dist[best])) {
					best = i;
				}
			}
			if (best < 0) {
				break;
			}
			done[best] = true;
			if (best == t) {
				break;
			}
			int cx = best % 7 + IN_LOW;
			int cz = best / 7 + IN_LOW;
			for (int[] d : ORTHO) {
				int nx = cx + d[0];
				int nz = cz + d[1];
				if (nx < IN_LOW || nx > IN_HIGH || nz < IN_LOW || nz > IN_HIGH || !ok.test(nx, nz)) {
					continue;
				}
				int ni = (nx - IN_LOW) + (nz - IN_LOW) * 7;
				if (done[ni]) {
					continue;
				}
				int nd = dist[best] + cost.applyAsInt(nx, nz);
				if (nd < dist[ni]) {
					dist[ni] = nd;
					prev[ni] = best;
				}
			}
		}
		List<int[]> path = new ArrayList<>();
		if (dist[t] == Integer.MAX_VALUE) {
			return path;
		}
		for (int c = t; c >= 0; c = prev[c]) {
			path.add(0, new int[] {c % 7 + IN_LOW, c / 7 + IN_LOW});
		}
		return path;
	}

	/** Keeps the wall block beside a protected cell standing, three blocks high: a rail for the climber. */
	private void protectWallBeside(final int x, final int y, final int z) {
		for (int[] d : ORTHO) {
			int nx = x + d[0];
			int nz = z + d[1];
			if (ring(nx, nz)) {
				protect(nx, y, nz);
				protect(nx, y + 1, nz);
				protect(nx, y + 2, nz);
			}
		}
	}

	private void build() {
		final int n = this.floors;
		final long seed = this.seed;
		RandomSource weather = stream(seed, 1);
		RandomSource collapse = stream(seed, 2);
		RandomSource rubbleRandom = stream(seed, 3);
		int x;
		int y;
		int z;
		// The plinth ring and the corner buttresses.
		for (z = OUTER_LOW; z <= OUTER_HIGH; z++) {
			for (x = OUTER_LOW; x <= OUTER_HIGH; x++) {
				if (outerRing(x, z)) {
					put(x, 0, z, PLINTH);
				}
			}
		}
		for (x = 9; x <= 11; x++) {
			put(x, 0, 4, STAIR);
			this.dirs[idx(x, 0, 4)] = 1;
		}
		for (int[] b : new int[][] {{5, 5}, {15, 5}, {5, 15}, {15, 15}}) {
			for (y = 0; y <= 5; y++) {
				put(b[0], y, b[1], WALL);
			}
		}
		// The wall ring, with its crown.
		for (z = WALL_LOW; z <= WALL_HIGH; z++) {
			for (x = WALL_LOW; x <= WALL_HIGH; x++) {
				if (ring(x, z)) {
					int top = FLOOR_HEIGHT * n + crownAt(x, z, this.crown);
					for (y = 0; y <= top; y++) {
						put(x, y, z, WALL);
					}
				}
			}
		}
		// The lookout platform: a ring one block out at deck height, on corbels, with merlons and two watch fires.
		int deck = FLOOR_HEIGHT * n;
		for (z = OUTER_LOW; z <= OUTER_HIGH; z++) {
			for (x = OUTER_LOW; x <= OUTER_HIGH; x++) {
				if (!outerRing(x, z)) {
					continue;
				}
				put(x, deck, z, WALL);
				boolean corner = (x == OUTER_LOW || x == OUTER_HIGH) && (z == OUTER_LOW || z == OUTER_HIGH);
				if (!corner) {
					put(x, deck - 1, z, CORBEL);
					int toward = x == OUTER_LOW ? 0 : x == OUTER_HIGH ? 2 : z == OUTER_LOW ? 1 : 3;
					this.dirs[idx(x, deck - 1, z)] = (byte) toward;
				}
				if (this.crown >= 1 && ((x + z) & 1) == 0) {
					put(x, deck + 1, z, WALL);
				}
			}
		}
		put(OUTER_LOW, deck + 1, OUTER_LOW, WATCH_FIRE);
		put(OUTER_HIGH, deck + 1, OUTER_HIGH, WATCH_FIRE);
		// The floors.
		for (z = IN_LOW; z <= IN_HIGH; z++) {
			for (x = IN_LOW; x <= IN_HIGH; x++) {
				put(x, 0, z, STONE_FLOOR);
				for (int k = 1; k <= n; k++) {
					put(x, FLOOR_HEIGHT * k, z, k == n ? STONE_FLOOR : FLOOR);
				}
			}
		}
		// The door.
		put(CENTER, 1, WALL_LOW, AIR);
		put(CENTER, 2, WALL_LOW, AIR);
		// The stair, the stairwell holes, the landings.
		Flight first = flight(0, this.counterClockwise);
		protect(first.start()[0], 0, first.start()[1]);
		protectWallBeside(first.start()[0], 0, first.start()[1]);
		for (int k = 0; k < n; k++) {
			Flight f = flight(k, this.counterClockwise);
			this.flights.add(f);
			for (int i = 0; i < 5; i++) {
				int[] c = f.steps()[i];
				int sy = FLOOR_HEIGHT * k + i + 1;
				put(c[0], sy, c[1], STAIR);
				this.dirs[idx(c[0], sy, c[1])] = (byte) dirCode(f.direction()[0], f.direction()[1]);
				protect(c[0], sy, c[1]);
				protectWallBeside(c[0], sy, c[1]);
			}
			for (int i = 2; i <= 4; i++) {
				int[] h = f.steps()[i];
				put(h[0], FLOOR_HEIGHT * (k + 1), h[1], AIR);
				this.holes[idx(h[0], FLOOR_HEIGHT * (k + 1), h[1])] = true;
			}
			protect(f.end()[0], FLOOR_HEIGHT * (k + 1), f.end()[1]);
			protectWallBeside(f.end()[0], FLOOR_HEIGHT * (k + 1), f.end()[1]);
		}
		// Walkways above the breach floor, kept whole so every floor stays reachable.
		for (int k = this.breachFloor + 1; k <= n; k++) {
			final int ys = FLOOR_HEIGHT * k;
			final int[] arrival = this.flights.get(k - 1).end();
			final boolean[][] blocked = new boolean[21][21];
			if (k < n) {
				int[][] steps = this.flights.get(k).steps();
				for (int i = 3; i < 5; i++) {
					blocked[steps[i][0]][steps[i][1]] = true;
				}
			}
			BiPredicate<Integer, Integer> ok = (px, pz) -> this.cells[idx(px, ys, pz)] != AIR && !blocked[px][pz];
			IntBinaryOperator cost = (px, pz) -> 1 + (depthFrom(this.side, px, pz) < this.depth ? 6 : 0);
			int[][] targets;
			if (k == n) {
				targets = deckTargets();
			} else {
				int[][] far = farSlots();
				targets = new int[][] {far[0], far[1]};
			}
			Map<Long, int[]> walk = new HashMap<>();
			for (int ti = 0; ti < targets.length; ti++) {
				List<int[]> path = dijkstra(arrival, targets[ti], ok, cost);
				if (k == n && ti == 0) {
					this.deckPath = path.toArray(new int[0][]);
				}
				for (int[] c : path) {
					walk.put((long) c[0] * 100 + c[1], c);
				}
			}
			for (int[] c : walk.values()) {
				protect(c[0], ys, c[1]);
				protectWallBeside(c[0], ys, c[1]);
				markClear(c[0], ys + 1, c[1]);
			}
		}
		// Slits and windows.
		for (int k = 0; k < n; k++) {
			String skip = this.flights.get(k).side();
			boolean lookout = this.template[k] == LOOKOUT;
			int ys = FLOOR_HEIGHT * k;
			for (String s : new String[] {"south", "east", "north", "west"}) {
				if (s.equals(skip) || (k == 0 && s.equals("south"))) {
					continue;
				}
				int lo = lookout ? 9 : 10;
				int hi = lookout ? 11 : 10;
				int yl = lookout ? 2 : 3;
				for (int l = lo; l <= hi; l++) {
					int[] cc = wallOfSide(s, l);
					for (y = ys + yl; y <= ys + 4; y++) {
						put(cc[0], y, cc[1], AIR);
					}
				}
			}
		}
		// Weathering: cosmetic, and never a removal.
		for (y = 0; y < SIZE_Y; y++) {
			for (z = 0; z < SIZE_Z; z++) {
				for (x = 0; x < SIZE_X; x++) {
					if (this.cells[idx(x, y, z)] != WALL) {
						continue;
					}
					int a = weather.nextInt(100);
					boolean mossy = weather.nextFloat() < (y < 12 ? 0.6F : 0.3F);
					if (a < this.weathering) {
						this.cells[idx(x, y, z)] = mossy ? WALL_MOSSY : WALL_CRACKED;
					}
				}
			}
		}
		// Collapse: the wall, in a V-notch.
		int[] jitter = new int[2 * (this.halfWidth + 1) + 1];
		for (int i = 0; i < jitter.length; i++) {
			jitter[i] = collapse.nextInt(2);
		}
		int topMax = FLOOR_HEIGHT * n + MAX_CROWN;
		for (int u = -(this.halfWidth + 1); u <= this.halfWidth + 1; u++) {
			int lateral = CENTER + u;
			int keep = FLOOR_HEIGHT * this.breachFloor + 1 + (int) Math.floor(1.6 * Math.abs(u)) + jitter[u + this.halfWidth + 1]
				+ (Math.abs(u) == this.halfWidth + 1 ? 2 : 0);
			for (int outer = 0; outer < 2; outer++) {
				if (outer == 0 && (lateral < WALL_LOW || lateral > WALL_HIGH)) {
					continue;
				}
				int[] wc = wallCell(this.side, lateral, outer == 1);
				for (y = keep + 1; y <= topMax; y++) {
					byte id = cell(wc[0], y, wc[1]);
					if (!(isWall(id) || id == CORBEL || id == WATCH_FIRE) || isProtected(wc[0], y, wc[1])) {
						continue;
					}
					put(wc[0], y, wc[1], AIR);
					this.gone[idx(wc[0], y, wc[1])] = 1;
					this.removed.add(new int[] {wc[0], y, wc[1], 0});
					this.removedWall++;
				}
				if (outer == 1) {
					int below = deck - 1;
					if (below > keep && cell(wc[0], below, wc[1]) == CORBEL) {
						put(wc[0], below, wc[1], AIR);
						this.gone[idx(wc[0], below, wc[1])] = 1;
						this.removed.add(new int[] {wc[0], below, wc[1], 0});
						this.removedWall++;
					}
				}
			}
		}
		// The route, laid before anything falls so that nothing lands on it.
		layRoute();
		for (int k = 1; k < n; k++) {
			if (this.template[k] == STORAGE) {
				int[] spot = chestSpot(k);
				this.chestSpots.add(new int[] {spot[0], FLOOR_HEIGHT * k + 1, spot[1]});
			}
		}
		// Collapse: the floors. A block falls only when the block under it stands.
		for (int k = this.breachFloor + 1; k <= n; k++) {
			int ys = FLOOR_HEIGHT * k;
			int yb = FLOOR_HEIGHT * (k - 1);
			for (int r = 0; r < this.depth; r++) {
				for (int u = -(this.halfWidth + 1); u <= this.halfWidth + 1; u++) {
					int lateral = CENTER + u;
					if (lateral < IN_LOW || lateral > IN_HIGH) {
						continue;
					}
					int[] cc = insideCell(this.side, lateral, r);
					float roll = collapse.nextFloat();
					float pick = collapse.nextFloat();
					if (roll >= 0.85F) {
						continue;
					}
					int fi = idx(cc[0], ys, cc[1]);
					if (this.cells[fi] == AIR || this.protectedCells[fi]) {
						continue;
					}
					if (this.cells[idx(cc[0], yb, cc[1])] == AIR) {
						continue;
					}
					this.cells[fi] = AIR;
					this.gone[fi] = 2;
					this.holes[fi] = true;
					this.removed.add(new int[] {cc[0], ys, cc[1], 1});
					this.removedFloor++;
					int ri = idx(cc[0], yb + 1, cc[1]);
					boolean slot = cc[0] >= 9 && cc[0] <= 11 && cc[1] >= 9 && cc[1] <= 11;
					boolean chest = false;
					for (int[] spot : this.chestSpots) {
						chest |= spot[0] == cc[0] && spot[1] == yb + 1 && spot[2] == cc[1];
					}
					if (this.cells[ri] == AIR && !this.clear[ri] && !slot && !chest) {
						this.cells[ri] = rubbleType(pick);
					}
				}
			}
		}
		furnish();
		crownTop();
		rubbleField(rubbleRandom);
	}

	private static byte rubbleType(final float pick) {
		return pick < 0.45F ? RUBBLE_COBBLE : pick < 0.75F ? RUBBLE_MOSSY : pick < 0.92F ? RUBBLE_CRACKED : RUBBLE_SLAB;
	}

	private void layRoute() {
		List<int[]> route = this.route;
		for (int z = 0; z <= 3; z++) {
			route.add(new int[] {CENTER, z, 0});
		}
		route.add(new int[] {CENTER, 4, 1});
		route.add(new int[] {CENTER, 5, 1});
		route.add(new int[] {CENTER, 6, 1});
		route.add(new int[] {CENTER, 7, 1});
		int[] a0 = this.flights.get(0).start();
		int dx = a0[0] < CENTER ? -1 : 1;
		for (int x = CENTER + dx; ; x += dx) {
			route.add(new int[] {x, 7, 1});
			if (x == a0[0]) {
				break;
			}
		}
		for (int k = 0; k < this.floors; k++) {
			Flight f = this.flights.get(k);
			for (int i = 0; i < 5; i++) {
				route.add(new int[] {f.steps()[i][0], f.steps()[i][1], FLOOR_HEIGHT * k + i + 2});
			}
			route.add(new int[] {f.end()[0], f.end()[1], FLOOR_HEIGHT * (k + 1) + 1});
		}
		for (int j = 1; j < this.deckPath.length; j++) {
			route.add(new int[] {this.deckPath[j][0], this.deckPath[j][1], FLOOR_HEIGHT * this.floors + 1});
		}
		for (int[] p : route) {
			markClear(p[0], p[2], p[1]);
		}
	}

	/** The cell inside the ring from a step of a flight. */
	private static int[] insideOf(final Flight flight, final int step) {
		int[] c = flight.steps()[step];
		return new int[] {c[0] + (flight.side().equals("west") ? 1 : flight.side().equals("east") ? -1 : 0),
			c[1] + (flight.side().equals("south") ? 1 : flight.side().equals("north") ? -1 : 0)};
	}

	/** The cell inside the ring from a step of a flight, and the next one toward the centre. */
	private static int[][] corridor(final Flight flight) {
		int[] step = flight.steps()[2];
		int dx = flight.side().equals("west") ? 1 : flight.side().equals("east") ? -1 : 0;
		int dz = flight.side().equals("south") ? 1 : flight.side().equals("north") ? -1 : 0;
		return new int[][] {{step[0] + dx, step[1] + dz}, {step[0] + 2 * dx, step[1] + 2 * dz}};
	}

	private void bedPair(final int[] a, final int[] b, final int turn, final int ys, final int k) {
		int[][] cells2 = {a, b};
		int[][] world = new int[2][];
		for (int i = 0; i < 2; i++) {
			int dx = cells2[i][0];
			int dz = cells2[i][1];
			for (int q = 0; q < turn; q++) {
				int t = dx;
				dx = -dz;
				dz = t;
			}
			world[i] = new int[] {CENTER + dx, CENTER + dz};
		}
		int yf = ys + 1;
		for (int[] w : world) {
			int gi = idx(w[0], yf, w[1]);
			if (this.cells[idx(w[0], ys, w[1])] == AIR || this.cells[gi] != AIR || this.clear[gi] || isSlot(w[0], w[1])) {
				return;
			}
		}
		int code = dirCode(world[1][0] - world[0][0], world[1][1] - world[0][1]);
		put(world[0][0], yf, world[0][1], BED_FOOT);
		put(world[1][0], yf, world[1][1], BED_HEAD);
		this.dirs[idx(world[0][0], yf, world[0][1])] = (byte) code;
		this.dirs[idx(world[1][0], yf, world[1][1])] = (byte) code;
	}

	/** Where the chest of a storage floor stands, as x, z and the direction it faces, toward the centre of the room. */
	private int[] chestSpot(final int k) {
		int dx = 1;
		int dz = -2;
		for (int q = 0; q < this.turn[k]; q++) {
			int tmp = dx;
			dx = -dz;
			dz = tmp;
		}
		int face = dirCode(Math.abs(dx) == 2 ? -Integer.signum(dx) : 0, Math.abs(dz) == 2 ? -Integer.signum(dz) : 0);
		return new int[] {CENTER + dx, CENTER + dz, face};
	}

	private boolean isSlot(final int x, final int z) {
		for (int[] s : SLOTS) {
			if (s[0] == x && s[1] == z) {
				return true;
			}
		}
		return false;
	}

	private void furnish() {
		List<Marker> mobs = new ArrayList<>();
		List<int[]> trimmable = new ArrayList<>();
		for (int k = 0; k < this.floors; k++) {
			int ys = FLOOR_HEIGHT * k;
			int yf = ys + 1;
			int t = this.template[k];
			// A way off the stair into the room that no furniture may close.
			for (int[] c : corridor(this.flights.get(k))) {
				markClear(c[0], yf, c[1]);
			}
			int turn = k == 0 ? 0 : this.turn[k];
			boolean lookout = t == LOOKOUT;
			// Each entry is dx, dz, block.
			int[][] list;
			if (t == GATE) {
				list = new int[][] {{-2, -2, BARREL}, {2, -2, BARREL}, {-2, 2, RACK}, {2, 2, RACK}, {0, 2, TABLE}};
			} else if (t == BUNKS) {
				list = new int[][] {{0, 0, TABLE}};
			} else if (t == ARMORY) {
				list = new int[][] {{-2, -2, FLETCHING}, {2, -2, GRINDSTONE}, {-2, 2, BARREL}, {-1, 2, BARREL}, {1, 2, BARREL}, {2, 2, BARREL}, {-1, -2, RACK}, {1, -2, RACK}};
			} else if (t == LOOKOUT) {
				list = new int[][] {{-2, 0, HAY}, {2, 0, HAY}, {0, -2, HAY}, {0, 2, HAY}, {-2, -2, WATCH_FIRE}};
			} else {
				list = new int[][] {{-2, -2, BARREL}, {-1, -2, BARREL}, {-2, -1, BARREL}, {2, 2, BARREL}, {1, 2, BARREL}, {2, 1, BARREL}, {2, -2, WEB}, {-2, 2, WEB}};
			}
			for (int[] it : list) {
				int dx = it[0];
				int dz = it[1];
				for (int q = 0; q < turn; q++) {
					int tmp = dx;
					dx = -dz;
					dz = tmp;
				}
				int px = CENTER + dx;
				int pz = CENTER + dz;
				if (isSlot(px, pz) || (lookout && px == CENTER && pz == CENTER)) {
					continue;
				}
				int gi = idx(px, yf, pz);
				if (this.cells[idx(px, ys, pz)] == AIR || this.cells[gi] != AIR || this.clear[gi]) {
					continue;
				}
				put(px, yf, pz, (byte) it[2]);
				if (it[2] == BARREL && t == STORAGE && ((px + pz) & 1) == 0) {
					put(px, yf + 1, pz, BARREL);
				}
			}
			if (t == BUNKS) {
				bedPair(new int[] {-2, -2}, new int[] {-2, -1}, turn, ys, k);
				bedPair(new int[] {-2, 1}, new int[] {-2, 2}, turn, ys, k);
				bedPair(new int[] {2, -2}, new int[] {2, -1}, turn, ys, k);
				bedPair(new int[] {2, 1}, new int[] {2, 2}, turn, ys, k);
			}
			if (t == STORAGE) {
				int[] spot = chestSpot(k);
				int gi = idx(spot[0], yf, spot[1]);
				if (this.cells[idx(spot[0], ys, spot[1])] != AIR && this.cells[gi] == AIR && !this.clear[gi]) {
					put(spot[0], yf, spot[1], CHEST);
					this.dirs[gi] = (byte) spot[2];
				}
			}
			// Flood from the arrival landing, over the cells a mob can stand on.
			int[] arrival = k == 0 ? new int[] {CENTER, 7} : this.flights.get(k - 1).end();
			boolean[][] seen = new boolean[21][21];
			List<int[]> queue = new ArrayList<>();
			seen[arrival[0]][arrival[1]] = true;
			queue.add(arrival);
			final int fy = yf;
			final int fs = ys;
			if (k < this.floors) {
				for (int i = 0; i < 3; i++) {
					int[] off = insideOf(this.flights.get(k), i);
					if (!seen[off[0]][off[1]] && standable(off[0], off[1], fs, fy)) {
						seen[off[0]][off[1]] = true;
						queue.add(off);
					}
				}
			}
			while (!queue.isEmpty()) {
				int[] c = queue.remove(queue.size() - 1);
				for (int[] d : ORTHO) {
					int nx = c[0] + d[0];
					int nz = c[1] + d[1];
					if (nx < IN_LOW || nx > IN_HIGH || nz < IN_LOW || nz > IN_HIGH || seen[nx][nz] || !standable(nx, nz, fs, fy)) {
						continue;
					}
					seen[nx][nz] = true;
					queue.add(new int[] {nx, nz});
				}
			}
			List<int[]> cand = new ArrayList<>(Arrays.asList(farSlots()));
			if (lookout) {
				cand.add(new int[] {CENTER, CENTER});
			}
			List<int[]> valid = new ArrayList<>();
			for (int[] s : cand) {
				if (seen[s[0]][s[1]] && standable(s[0], s[1], fs, fy)) {
					valid.add(s);
				}
			}
			String[] kinds = t == GATE ? new String[] {CROSSBOW, CROSSBOW}
				: t == BUNKS ? new String[] {ARCHER, CROSSBOW}
				: t == ARMORY ? new String[] {CROSSBOW, CROSSBOW}
				: t == LOOKOUT ? new String[] {ARCHER, ARCHER, CROSSBOW}
				: new String[] {ARCHER, ARCHER};
			for (int j = 0; j < kinds.length && j < valid.size(); j++) {
				if (t == LOOKOUT && j == 2) {
					trimmable.add(new int[] {mobs.size(), k});
				}
				mobs.add(new Marker(kinds[j], valid.get(j)[0], yf, valid.get(j)[1]));
			}
		}
		// Roof: the captain, and two archers.
		int ysR = FLOOR_HEIGHT * this.floors;
		int yR = ysR + 1;
		this.captainCell = insideCell(this.side, 10, 4);
		mobs.add(new Marker(CAPTAIN, this.captainCell[0], yR, this.captainCell[1]));
		for (int lat : new int[] {8, 12}) {
			int[] a = insideCell(this.side, lat, 5);
			if (this.cells[idx(a[0], ysR, a[1])] != AIR) {
				mobs.add(new Marker(ARCHER, a[0], yR, a[1]));
			}
		}
		// Cap: drop the third mob of the lowest lookout floors first.
		int over = mobs.size() - GARRISON_CAP;
		List<Integer> drop = new ArrayList<>();
		for (int i = 0; i < trimmable.size() && over > 0; i++, over--) {
			drop.add(trimmable.get(i)[0]);
		}
		drop.sort((a, b) -> b - a);
		for (int d : drop) {
			mobs.remove(d);
		}
		this.markers.addAll(mobs);
	}

	private boolean standable(final int x, final int z, final int ys, final int yf) {
		if (x < IN_LOW || x > IN_HIGH || z < IN_LOW || z > IN_HIGH) {
			return false;
		}
		if (this.cells[idx(x, ys, z)] == AIR) {
			return false;
		}
		byte a1 = this.cells[idx(x, yf, z)];
		byte a2 = this.cells[idx(x, yf + 1, z)];
		return (a1 == AIR || a1 == WEB || isRubble(a1)) && (a2 == AIR || a2 == WEB);
	}

	/** The vault stands on the wall row behind the core, or beside that spot when the last stairwell hole is there. */
	private int vaultLateral() {
		Flight last = this.flights.get(this.floors - 1);
		for (int lat : new int[] {10, 9, 11, 8, 12}) {
			int[] c = insideCell(this.side, lat, 6);
			boolean hole = false;
			for (int i = 2; i <= 4; i++) {
				hole |= last.steps()[i][0] == c[0] && last.steps()[i][1] == c[1];
			}
			if (!hole && !(last.end()[0] == c[0] && last.end()[1] == c[1])) {
				return lat;
			}
		}
		return 10;
	}

	private void crownTop() {
		int ysR = FLOOR_HEIGHT * this.floors;
		int yR = ysR + 1;
		for (int lat = 9; lat <= 11; lat++) {
			for (int r = 4; r <= 6; r++) {
				int[] dc = insideCell(this.side, lat, r);
				if (this.cells[idx(dc[0], ysR, dc[1])] != AIR) {
					put(dc[0], ysR, dc[1], DAIS);
				}
			}
		}
		this.core = new int[] {insideCell(this.side, 10, 5)[0], yR, insideCell(this.side, 10, 5)[1]};
		int[] vaultCell = insideCell(this.side, vaultLateral(), 6);
		this.vault = new int[] {vaultCell[0], yR, vaultCell[1]};
		put(this.core[0], yR, this.core[2], CORE);
		put(this.vault[0], yR, this.vault[2], VAULT);
		for (int lat : new int[] {7, 13}) {
			int[] b = insideCell(this.side, lat, 6);
			int bi = idx(b[0], yR, b[1]);
			if (this.cells[bi] == AIR && !this.clear[bi] && this.cells[idx(b[0], ysR, b[1])] != AIR) {
				put(b[0], yR, b[1], WATCH_FIRE);
			}
		}
	}

	private void rubbleField(final RandomSource random) {
		Map<Long, Integer> heights = new HashMap<>();
		for (int[] b : this.removed) {
			float p = b[3] == 0 ? 0.6F : 0.3F;
			float chance = random.nextFloat();
			int jitter = random.nextInt(5) - 2;
			float outFactor = random.nextFloat();
			float kind = random.nextFloat();
			if (chance >= p) {
				continue;
			}
			int lateral = Math.max(4, Math.min(16, lateralOf(this.side, b[0], b[2]) + jitter));
			int out = 1 + (int) (outFactor * (1.5F + b[1] / 6.0F));
			if (out > 6) {
				out = 6;
			}
			int[] c = switch (this.side) {
				case 0 -> new int[] {lateral, WALL_HIGH + out};
				case 1 -> new int[] {WALL_HIGH + out, lateral};
				case 2 -> new int[] {lateral, WALL_LOW - out};
				default -> new int[] {WALL_LOW - out, lateral};
			};
			if (c[0] >= 9 && c[0] <= 11 && c[1] <= 5) {
				continue;
			}
			long key = (long) c[0] * 100 + c[1];
			int h = heights.getOrDefault(key, 0);
			if (h >= 5) {
				continue;
			}
			boolean plinth = c[0] == OUTER_LOW || c[0] == OUTER_HIGH || c[1] == OUTER_LOW || c[1] == OUTER_HIGH;
			int yb = (plinth ? 1 : 0) + h;
			if (!inBox(c[0], yb, c[1]) || this.cells[idx(c[0], yb, c[1])] != AIR) {
				continue;
			}
			put(c[0], yb, c[1], kind < 0.4F ? RUBBLE_COBBLE : kind < 0.7F ? RUBBLE_MOSSY : kind < 0.9F ? RUBBLE_CRACKED : RUBBLE_SLAB);
			heights.put(key, h + 1);
			this.rubble++;
		}
	}
}
