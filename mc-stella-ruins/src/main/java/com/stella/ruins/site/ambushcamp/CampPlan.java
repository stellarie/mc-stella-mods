package com.stella.ruins.site.ambushcamp;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;

/**
 * Everything random in the ambush camp, and every block of it, as a function of one random source. The plan reads
 * no world block. The layout and the tests both read it.
 * <p>
 * The plan is a grid of 31 x 10 x 31 cells in the site's local frame: x east, z south, y up from the floor course.
 * A draw is rejected when the tents do not fit, or when a flood fill from the trail cannot reach the core, the vault,
 * every tent door and every tower ladder. The next draw then continues the same random stream, so the plan stays a
 * function of the seed.
 * <p>
 * Roll order: gaps, trail gap, towers, palisade heights, fire, seats, tent count, colours, tent spots, bed sides,
 * supply piles, rack, hay, patrol markers, ground, lanterns.
 */
public final class CampPlan {
	public static final int SIZE_X = 31;
	public static final int SIZE_Y = 10;
	public static final int SIZE_Z = 31;
	public static final int CENTER = 15;
	public static final int MIN_TENTS = 3;
	public static final int MAX_TENTS = 6;
	public static final int MIN_GAPS = 2;
	public static final int MAX_GAPS = 4;
	/** Bandits in the roster. Decoys in the tents count against it, the rest wait on patrol markers. */
	public static final int BANDITS = 8;
	public static final int LOOKOUTS = 2;
	public static final int TENT_HALF = 2;
	/** The draws before the plan gives up on connectivity. */
	public static final int MAX_DRAWS = 200;

	public static final int AIR = 0;
	public static final int GRASS = 1;
	public static final int COARSE = 2;
	public static final int PATH = 3;
	public static final int COBBLE = 4;
	public static final int LOG = 5;
	public static final int LANTERN = 6;
	public static final int LANTERN_HANGING = 7;
	public static final int WOOL = 8;
	public static final int BED_FOOT = 9;
	public static final int BED_HEAD = 10;
	public static final int BARREL = 11;
	public static final int HAY = 12;
	public static final int SEAT = 13;
	public static final int FIRE = 14;
	public static final int CORE = 15;
	public static final int VAULT = 16;
	public static final int FENCE = 17;
	public static final int GRIND = 18;
	public static final int PLANK = 19;
	public static final int TLOG = 20;
	public static final int LADDER = 21;
	/** Red wool of the tower banner. */
	public static final int BANNER = 22;

	/** Horizontal facings as local steps: north is -z. */
	public static final int[][] STEPS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
	public static final int NORTH = 0;
	public static final int EAST = 1;
	public static final int SOUTH = 2;
	public static final int WEST = 3;
	/** The wool colour of the banner, an index of the 16 dyes. */
	public static final int RED = 14;

	private static final int[][] SEAT_SPOTS = {{2, 0}, {-2, 0}, {0, 2}, {2, 2}, {-2, 2}, {2, -2}, {-2, -2}};
	private static final String[] SIDES = {"N", "E", "S", "W"};

	/** A cell column on the floor, as local x and z. */
	public record Cell(int x, int z) {
	}

	/** One tent: its centre, its door side, its colour and its marker cells. {@code dx} and {@code dz} point at the fire. */
	public record Tent(int id, int cx, int cz, int dx, int dz, int color, int bed, Cell marker, Cell door, Cell step) {
	}

	/** One palisade gap. The main gap carries the worn trail. */
	public record Gap(String side, int start, int width, List<Cell> cells, boolean main) {
	}

	/** A lookout tower. {@code ldx} and {@code ldz} point from the tower to the fire, the ladder side. */
	public record Tower(String corner, int cx, int cz, int ldx, int ldz) {
	}

	private final byte[] kinds = new byte[SIZE_X * SIZE_Y * SIZE_Z];
	private final byte[] data = new byte[SIZE_X * SIZE_Y * SIZE_Z];
	private List<Tent> tents = List.of();
	private List<Gap> gaps = List.of();
	private List<Tower> towers = List.of();
	private List<Cell> patrol = List.of();
	private List<Cell> trail = List.of();
	private List<List<Cell>> piles = List.of();
	private List<Cell> rack = List.of();
	private List<Cell> hay = List.of();
	private List<Cell> seats = List.of();
	private Cell trailStart;
	private Cell hub;
	private Cell core;
	private Cell vault;
	private int draws;
	private boolean connected;

	private CampPlan() {
	}

	/** Draws the plan from a random source. The same source state gives the same plan. */
	public static CampPlan derive(final RandomSource random) {
		CampPlan fallback = null;
		for (int draw = 1; draw <= MAX_DRAWS; draw++) {
			CampPlan plan = new CampPlan();
			if (!plan.tryDraw(random)) {
				continue;
			}
			plan.draws = draw;
			if (plan.floodsEverything()) {
				plan.connected = true;
				return plan;
			}
			fallback = plan;
		}
		if (fallback == null) {
			throw new IllegalStateException("no ambush camp plan fits after " + MAX_DRAWS + " draws");
		}
		return fallback;
	}

	private static int index(final int x, final int y, final int z) {
		return (y * SIZE_Z + z) * SIZE_X + x;
	}

	private static boolean in(final int x, final int y, final int z) {
		return x >= 0 && z >= 0 && y >= 0 && x < SIZE_X && z < SIZE_Z && y < SIZE_Y;
	}

	public int kind(final int x, final int y, final int z) {
		return in(x, y, z) ? this.kinds[index(x, y, z)] : AIR;
	}

	/** The colour index of a wool or bed cell, or the facing of a ladder, bed or grindstone cell. */
	public int data(final int x, final int y, final int z) {
		return in(x, y, z) ? this.data[index(x, y, z)] : 0;
	}

	private void set(final int x, final int y, final int z, final int kind, final int value) {
		if (in(x, y, z)) {
			this.kinds[index(x, y, z)] = (byte) kind;
			this.data[index(x, y, z)] = (byte) value;
		}
	}

	private void set(final int x, final int y, final int z, final int kind) {
		set(x, y, z, kind, 0);
	}

	public List<Tent> tents() {
		return this.tents;
	}

	public List<Gap> gaps() {
		return this.gaps;
	}

	public List<Tower> towers() {
		return this.towers;
	}

	/** The patrol marker cells. */
	public List<Cell> patrol() {
		return this.patrol;
	}

	public List<Cell> trail() {
		return this.trail;
	}

	public List<List<Cell>> piles() {
		return this.piles;
	}

	public List<Cell> rack() {
		return this.rack;
	}

	public List<Cell> hay() {
		return this.hay;
	}

	public List<Cell> seats() {
		return this.seats;
	}

	/** The cell at the bounds edge where the trail begins. */
	public Cell trailStart() {
		return this.trailStart;
	}

	public Cell hub() {
		return this.hub;
	}

	public Cell core() {
		return this.core;
	}

	public Cell vault() {
		return this.vault;
	}

	/** How many draws this plan took, 1 for the first. */
	public int draws() {
		return this.draws;
	}

	/** Whether the flood fill reached everything. A false value means the draws ran out. */
	public boolean isConnected() {
		return this.connected;
	}

	public int tentCount() {
		return this.tents.size();
	}

	public Gap mainGap() {
		for (Gap gap : this.gaps) {
			if (gap.main()) {
				return gap;
			}
		}
		throw new IllegalStateException("no main gap");
	}

	/** The cell in front of a tower ladder, where a player steps on. */
	public Cell ladderFoot(final Tower tower) {
		return new Cell(tower.cx() + 3 * tower.ldx(), tower.cz() + 3 * tower.ldz());
	}

	/** Patrol marker count for the tent count: the bandits that are not decoys. */
	public static int patrolFor(final int tents) {
		return BANDITS - (tents - 1);
	}

	/** A short hash of the plan, for variety checks. */
	public String signature() {
		long hash = 17L;
		for (int i = 0; i < this.kinds.length; i++) {
			hash = hash * 31L + this.kinds[i];
			hash = hash * 31L + this.data[i];
		}
		return Long.toHexString(hash);
	}

	// ----- the draw -----

	private static boolean inShape(final int x, final int z) {
		return x >= 0 && z >= 0 && x < SIZE_X && z < SIZE_Z && Math.abs(x - CENTER) <= 12 && Math.abs(z - CENTER) <= 12
			&& Math.abs(x - CENTER) + Math.abs(z - CENTER) <= 20;
	}

	private static int roll(final RandomSource random, final int low, final int high) {
		return low + random.nextInt(high - low + 1);
	}

	private static <T> List<T> shuffle(final List<T> list, final RandomSource random) {
		List<T> copy = new ArrayList<>(list);
		for (int i = copy.size() - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			T swap = copy.get(i);
			copy.set(i, copy.get(j));
			copy.set(j, swap);
		}
		return copy;
	}

	private static boolean[][] grid(final boolean value) {
		boolean[][] g = new boolean[SIZE_Z][SIZE_X];
		if (value) {
			for (boolean[] row : g) {
				java.util.Arrays.fill(row, true);
			}
		}
		return g;
	}

	private static int facingOf(final int dx, final int dz) {
		for (int i = 0; i < STEPS.length; i++) {
			if (STEPS[i][0] == dx && STEPS[i][1] == dz) {
				return i;
			}
		}
		throw new IllegalArgumentException("no facing for " + dx + "," + dz);
	}

	private boolean tryDraw(final RandomSource random) {
		java.util.Arrays.fill(this.kinds, (byte) 0);
		java.util.Arrays.fill(this.data, (byte) 0);
		boolean[][] ring = grid(false);
		boolean[][] solid = grid(true);
		boolean[][] keep = grid(false);
		boolean[][] gapMap = grid(false);
		for (int z = 0; z < SIZE_Z; z++) {
			for (int x = 0; x < SIZE_X; x++) {
				set(x, 0, z, GRASS);
				if (!inShape(x, z)) {
					continue;
				}
				boolean out = false;
				for (int dz = -1; dz <= 1; dz++) {
					for (int dx = -1; dx <= 1; dx++) {
						if ((dx != 0 || dz != 0) && !inShape(x + dx, z + dz)) {
							out = true;
						}
					}
				}
				if (out) {
					ring[z][x] = true;
				} else {
					solid[z][x] = false;
				}
			}
		}
		// gaps
		int gapCount = roll(random, MIN_GAPS, MAX_GAPS);
		List<int[]> halves = new ArrayList<>();
		for (int side = 0; side < 4; side++) {
			halves.add(new int[] {side, 9, 14});
			halves.add(new int[] {side, 16, 21});
		}
		List<Gap> drawnGaps = new ArrayList<>();
		List<int[]> picked = shuffle(halves, random).subList(0, gapCount);
		List<Object[]> rawGaps = new ArrayList<>();
		for (int[] half : picked) {
			int width = roll(random, 2, 3);
			int start = roll(random, half[1], half[2] - width + 1);
			List<Cell> cells = new ArrayList<>();
			for (int i = 0; i < width; i++) {
				int p = start + i;
				cells.add(switch (half[0]) {
					case 0 -> new Cell(p, 3);
					case 1 -> new Cell(27, p);
					case 2 -> new Cell(p, 27);
					default -> new Cell(3, p);
				});
			}
			rawGaps.add(new Object[] {SIDES[half[0]], start, width, cells});
		}
		for (Object[] raw : rawGaps) {
			@SuppressWarnings("unchecked")
			List<Cell> cells = (List<Cell>) raw[3];
			for (Cell cell : cells) {
				ring[cell.z()][cell.x()] = false;
				gapMap[cell.z()][cell.x()] = true;
				set(cell.x(), 0, cell.z(), PATH);
			}
		}
		int mainIndex = random.nextInt(rawGaps.size());
		for (int i = 0; i < rawGaps.size(); i++) {
			Object[] raw = rawGaps.get(i);
			@SuppressWarnings("unchecked")
			List<Cell> cells = (List<Cell>) raw[3];
			drawnGaps.add(new Gap((String) raw[0], (Integer) raw[1], (Integer) raw[2], List.copyOf(cells), i == mainIndex));
		}
		this.gaps = List.copyOf(drawnGaps);
		Gap main = mainGap();
		Cell mid = main.cells().get(main.width() / 2);
		List<Cell> trailCells = new ArrayList<>();
		for (Cell cell : main.cells()) {
			for (int d = 1; d <= 3; d++) {
				int tx = main.side().equals("W") ? 3 - d : main.side().equals("E") ? 27 + d : cell.x();
				int tz = main.side().equals("N") ? 3 - d : main.side().equals("S") ? 27 + d : cell.z();
				set(tx, 0, tz, PATH);
				trailCells.add(new Cell(tx, tz));
			}
		}
		this.trail = List.copyOf(trailCells);
		this.trailStart = switch (main.side()) {
			case "N" -> new Cell(mid.x(), 0);
			case "S" -> new Cell(mid.x(), 30);
			case "W" -> new Cell(0, mid.z());
			default -> new Cell(30, mid.z());
		};
		// towers
		String[] cornerNames = {"NE", "NW", "SE", "SW"};
		int[][] cornerSpots = {{23, 7}, {7, 7}, {23, 23}, {7, 23}};
		List<Integer> order = shuffle(List.of(0, 1, 2, 3), random);
		List<int[]> towerSpots = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			towerSpots.add(new int[] {order.get(i), cornerSpots[order.get(i)][0], cornerSpots[order.get(i)][1]});
		}
		// palisade heights
		int[][] height = new int[SIZE_Z][SIZE_X];
		List<Cell> ringCells = new ArrayList<>();
		for (int z = 0; z < SIZE_Z; z++) {
			for (int x = 0; x < SIZE_X; x++) {
				if (!ring[z][x]) {
					continue;
				}
				double r = random.nextDouble();
				double r2 = random.nextDouble();
				int h = r < 0.12 ? 2 : new int[] {3, 4, 4, 5}[(int) Math.floor(r2 * 4)];
				if (nearGap(gapMap, x, z)) {
					h = Math.min(h, r2 < 0.5 ? 2 : 3);
				}
				for (int[] spot : towerSpots) {
					if (Math.max(Math.abs(x - spot[1]), Math.abs(z - spot[2])) <= 3) {
						h = Math.min(h, 4);
					}
				}
				height[z][x] = h;
				ringCells.add(new Cell(x, z));
				for (int y = 1; y <= h; y++) {
					set(x, y, z, LOG);
				}
			}
		}
		// fire
		int hx = roll(random, 12, 18);
		int hz = roll(random, 12, 18);
		this.hub = new Cell(hx, hz);
		List<Tower> placedTowers = new ArrayList<>();
		for (int[] spot : towerSpots) {
			int dx = hx - spot[1];
			int dz = hz - spot[2];
			int ldx;
			int ldz;
			if (Math.abs(dx) >= Math.abs(dz)) {
				ldx = Integer.signum(dx);
				ldz = 0;
			} else {
				ldx = 0;
				ldz = Integer.signum(dz);
			}
			placedTowers.add(new Tower(cornerNames[spot[0]], spot[1], spot[2], ldx, ldz));
			placeTower(spot[1], spot[2], ldx, ldz);
			for (int dz2 = -2; dz2 <= 2; dz2++) {
				for (int dx2 = -2; dx2 <= 2; dx2++) {
					solid[spot[2] + dz2][spot[1] + dx2] = true;
				}
			}
		}
		this.towers = List.copyOf(placedTowers);
		for (int dz = -2; dz <= 2; dz++) {
			for (int dx = -2; dx <= 2; dx++) {
				solid[hz + dz][hx + dx] = true;
			}
		}
		set(hx, 1, hz, FIRE);
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				if (dx != 0 || dz != 0) {
					set(hx + dx, 0, hz + dz, COBBLE);
				}
			}
		}
		List<int[]> seatList = new ArrayList<>();
		for (int[] spot : SEAT_SPOTS) {
			seatList.add(spot);
		}
		List<Cell> seatCells = new ArrayList<>();
		for (int[] spot : shuffle(seatList, random).subList(0, 4)) {
			seatCells.add(new Cell(hx + spot[0], hz + spot[1]));
			set(hx + spot[0], 1, hz + spot[1], SEAT);
		}
		this.seats = List.copyOf(seatCells);
		this.core = new Cell(hx, hz - 2);
		set(hx, 1, hz - 2, CORE);
		set(hx, 2, hz - 2, FENCE);
		set(hx, 3, hz - 2, FENCE);
		set(hx, 4, hz - 2, LANTERN);
		// tents
		int tentCount = roll(random, MIN_TENTS, MAX_TENTS);
		List<Integer> dyes = new ArrayList<>();
		for (int i = 0; i < 16; i++) {
			dyes.add(i);
		}
		dyes = shuffle(dyes, random);
		List<Cell> centres = new ArrayList<>();
		for (int attempt = 0; attempt < 40 && centres.size() < tentCount; attempt++) {
			centres = new ArrayList<>();
			boolean[][] tmp = new boolean[SIZE_Z][];
			for (int z = 0; z < SIZE_Z; z++) {
				tmp[z] = solid[z].clone();
			}
			for (int i = 0; i < tentCount; i++) {
				boolean placed = false;
				for (int t = 0; t < 200 && !placed; t++) {
					int cx = roll(random, 5, 25);
					int cz = roll(random, 5, 25);
					if (!boxFree(tmp, cx, cz)) {
						continue;
					}
					for (int dz = -TENT_HALF; dz <= TENT_HALF; dz++) {
						for (int dx = -TENT_HALF; dx <= TENT_HALF; dx++) {
							tmp[cz + dz][cx + dx] = true;
						}
					}
					centres.add(new Cell(cx, cz));
					placed = true;
				}
				if (!placed) {
					break;
				}
			}
		}
		if (centres.size() < tentCount) {
			return false;
		}
		centres.sort((a, b) -> a.z() != b.z() ? Integer.compare(a.z(), b.z()) : Integer.compare(a.x(), b.x()));
		List<Tent> built = new ArrayList<>();
		for (int i = 0; i < centres.size(); i++) {
			Cell c = centres.get(i);
			int dx = hx - c.x();
			int dz = hz - c.z();
			int tdx;
			int tdz;
			if (Math.abs(dx) >= Math.abs(dz)) {
				tdx = Integer.signum(dx) == 0 ? 1 : Integer.signum(dx);
				tdz = 0;
			} else {
				tdx = 0;
				tdz = Integer.signum(dz);
			}
			int bed = random.nextDouble() < 0.5 ? -1 : 1;
			Tent tent = buildTent(i + 1, c.x(), c.z(), tdx, tdz, dyes.get(i), bed);
			built.add(tent);
			for (int dz2 = -TENT_HALF; dz2 <= TENT_HALF; dz2++) {
				for (int dx2 = -TENT_HALF; dx2 <= TENT_HALF; dx2++) {
					solid[c.z() + dz2][c.x() + dx2] = true;
				}
			}
			for (int u = 3; u <= 4; u++) {
				for (int b = -1; b <= 1; b++) {
					int[] w = world(tent, u, b);
					if (w[0] >= 0 && w[1] >= 0 && w[0] < SIZE_X && w[1] < SIZE_Z) {
						keep[w[1]][w[0]] = true;
					}
				}
			}
		}
		this.tents = List.copyOf(built);
		// supply piles
		int pileCount = roll(random, 2, 3);
		List<List<Cell>> pileList = new ArrayList<>();
		Cell vaultCell = null;
		for (int i = 0; i < pileCount; i++) {
			boolean done = false;
			for (int t = 0; t < 300 && !done; t++) {
				int ax = roll(random, 4, 25);
				int az = roll(random, 4, 25);
				List<Cell> cells = List.of(new Cell(ax, az), new Cell(ax + 1, az), new Cell(ax, az + 1), new Cell(ax + 1, az + 1));
				if (!cells.stream().allMatch(c -> free(solid, keep, c.x(), c.z()))) {
					continue;
				}
				int n = roll(random, 2, 3);
				List<Cell> pick = shuffle(cells, random).subList(0, n);
				for (int j = 0; j < pick.size(); j++) {
					Cell c = pick.get(j);
					solid[c.z()][c.x()] = true;
					if (i == 0 && j == 1) {
						set(c.x(), 1, c.z(), VAULT);
						vaultCell = c;
					} else {
						set(c.x(), 1, c.z(), BARREL);
						if (j == 0) {
							set(c.x(), 2, c.z(), BARREL);
						}
					}
				}
				pileList.add(List.copyOf(pick));
				done = true;
			}
			if (!done && i < 2) {
				return false;
			}
		}
		if (vaultCell == null) {
			return false;
		}
		this.piles = List.copyOf(pileList);
		this.vault = vaultCell;
		// rack
		List<Cell> rackCells = null;
		for (int t = 0; t < 300 && rackCells == null; t++) {
			boolean alongX = random.nextDouble() < 0.5;
			int ax = roll(random, 4, 25);
			int az = roll(random, 4, 25);
			List<Cell> cells = new ArrayList<>();
			for (int i = 0; i < 3; i++) {
				cells.add(alongX ? new Cell(ax + i, az) : new Cell(ax, az + i));
			}
			if (!cells.stream().allMatch(c -> free(solid, keep, c.x(), c.z()))) {
				continue;
			}
			for (int i = 0; i < 3; i++) {
				Cell c = cells.get(i);
				solid[c.z()][c.x()] = true;
				if (i == 1) {
					set(c.x(), 1, c.z(), GRIND, alongX ? NORTH : EAST);
					set(c.x(), 2, c.z(), FENCE);
				} else {
					set(c.x(), 1, c.z(), FENCE);
					set(c.x(), 2, c.z(), FENCE);
				}
			}
			rackCells = cells;
		}
		if (rackCells == null) {
			return false;
		}
		this.rack = List.copyOf(rackCells);
		// hay
		List<Cell> hayCells = new ArrayList<>();
		int bales = roll(random, 2, 3);
		for (int i = 0; i < bales; i++) {
			for (int t = 0; t < 200; t++) {
				int x = roll(random, 4, 26);
				int z = roll(random, 4, 26);
				if (!free(solid, keep, x, z)) {
					continue;
				}
				solid[z][x] = true;
				set(x, 1, z, HAY);
				if (random.nextDouble() < 0.4) {
					set(x, 2, z, HAY);
				}
				hayCells.add(new Cell(x, z));
				break;
			}
		}
		this.hay = List.copyOf(hayCells);
		// patrol markers
		int wanted = patrolFor(tentCount);
		List<Cell> candidates = new ArrayList<>();
		for (int z = 0; z < SIZE_Z; z++) {
			for (int x = 0; x < SIZE_X; x++) {
				if (free(solid, keep, x, z) && kind(x, 1, z) == AIR) {
					candidates.add(new Cell(x, z));
				}
			}
		}
		candidates = shuffle(candidates, random);
		List<Cell> patrolCells = new ArrayList<>();
		for (int spacing = 4; spacing >= 1 && patrolCells.size() < wanted; spacing--) {
			for (Cell c : candidates) {
				if (patrolCells.size() >= wanted) {
					break;
				}
				final int gap = spacing;
				if (patrolCells.stream().allMatch(p -> Math.abs(p.x() - c.x()) + Math.abs(p.z() - c.z()) >= gap)) {
					patrolCells.add(c);
				}
			}
		}
		if (patrolCells.size() < wanted) {
			return false;
		}
		this.patrol = List.copyOf(patrolCells);
		// ground
		for (int z = 0; z < SIZE_Z; z++) {
			for (int x = 0; x < SIZE_X; x++) {
				if (!inShape(x, z) || ring[z][x] || gapMap[z][x] || kind(x, 0, z) != GRASS) {
					continue;
				}
				if (keep[z][x] && !solid[z][x]) {
					set(x, 0, z, PATH);
					continue;
				}
				int d = Math.max(Math.abs(x - hx), Math.abs(z - hz));
				if (random.nextDouble() < Math.max(0.06, 0.55 - 0.07 * d)) {
					set(x, 0, z, COARSE);
				}
			}
		}
		// lanterns on three palisade posts
		List<Cell> lampable = new ArrayList<>();
		for (Cell c : ringCells) {
			boolean nearTower = false;
			for (int[] spot : towerSpots) {
				if (Math.max(Math.abs(c.x() - spot[1]), Math.abs(c.z() - spot[2])) <= 3) {
					nearTower = true;
				}
			}
			if (height[c.z()][c.x()] >= 3 && !nearTower) {
				lampable.add(c);
			}
		}
		for (Cell c : shuffle(lampable, random).subList(0, 3)) {
			set(c.x(), height[c.z()][c.x()] + 1, c.z(), LANTERN);
		}
		return true;
	}

	private static boolean nearGap(final boolean[][] gapMap, final int x, final int z) {
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				int xx = x + dx;
				int zz = z + dz;
				if (xx >= 0 && zz >= 0 && xx < SIZE_X && zz < SIZE_Z && gapMap[zz][xx]) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean boxFree(final boolean[][] tmp, final int cx, final int cz) {
		for (int dz = -3; dz <= 3; dz++) {
			for (int dx = -3; dx <= 3; dx++) {
				int x = cx + dx;
				int z = cz + dz;
				if (x < 0 || z < 0 || x >= SIZE_X || z >= SIZE_Z || tmp[z][x]) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean free(final boolean[][] solid, final boolean[][] keep, final int x, final int z) {
		return x >= 0 && z >= 0 && x < SIZE_X && z < SIZE_Z && !solid[z][x] && !keep[z][x];
	}

	/** The world cell of a tent's frame: {@code u} runs from the back wall to the door, {@code b} across the ridge. */
	private static int[] world(final Tent tent, final int u, final int b) {
		return tent.dx() != 0 ? new int[] {tent.cx() + u * tent.dx(), tent.cz() + b} : new int[] {tent.cx() + b, tent.cz() + u * tent.dz()};
	}

	private Tent buildTent(final int id, final int cx, final int cz, final int dx, final int dz, final int color, final int bed) {
		Tent shell = new Tent(id, cx, cz, dx, dz, color, bed, null, null, null);
		for (int u = -2; u <= 2; u++) {
			for (int b = -2; b <= 2; b++) {
				int ab = Math.abs(b);
				int[] w = world(shell, u, b);
				if (Math.abs(u) == 2) {
					int top = ab == 2 ? 2 : ab == 1 ? 3 : 4;
					for (int y = 1; y <= top; y++) {
						set(w[0], y, w[1], WOOL, color);
					}
				} else if (ab == 2) {
					set(w[0], 1, w[1], WOOL, color);
					set(w[0], 2, w[1], WOOL, color);
				} else if (ab == 1) {
					set(w[0], 3, w[1], WOOL, color);
				} else {
					set(w[0], 4, w[1], WOOL, color);
				}
			}
		}
		int[] doorCell = world(shell, 2, 0);
		set(doorCell[0], 1, doorCell[1], AIR);
		set(doorCell[0], 2, doorCell[1], AIR);
		int backFacing = facingOf(-dx, -dz);
		int[] foot = world(shell, 0, bed);
		int[] head = world(shell, -1, bed);
		set(foot[0], 1, foot[1], BED_FOOT, backFacing);
		set(head[0], 1, head[1], BED_HEAD, backFacing);
		int[] barrel = world(shell, -1, -bed);
		set(barrel[0], 1, barrel[1], BARREL);
		int[] lamp = world(shell, -1, 0);
		set(lamp[0], 3, lamp[1], LANTERN_HANGING);
		int[] centre = world(shell, 0, 0);
		int[] step = world(shell, 3, 0);
		return new Tent(id, cx, cz, dx, dz, color, bed, new Cell(centre[0], centre[1]), new Cell(doorCell[0], doorCell[1]), new Cell(step[0], step[1]));
	}

	private void placeTower(final int cx, final int cz, final int ldx, final int ldz) {
		int qx = Math.abs(ldz);
		int qz = Math.abs(ldx);
		for (int a = -1; a <= 1; a += 2) {
			for (int b = -1; b <= 1; b += 2) {
				for (int y = 1; y <= 4; y++) {
					set(cx + a, y, cz + b, TLOG);
				}
			}
		}
		for (int y = 1; y <= 4; y++) {
			set(cx + ldx, y, cz + ldz, PLANK);
		}
		for (int a = -1; a <= 1; a++) {
			for (int b = -1; b <= 1; b++) {
				set(cx + a, 5, cz + b, PLANK);
			}
		}
		for (int a = -1; a <= 1; a++) {
			for (int b = -1; b <= 1; b++) {
				if (Math.max(Math.abs(a), Math.abs(b)) == 1 && !(a == ldx && b == ldz)) {
					set(cx + a, 6, cz + b, FENCE);
				}
			}
		}
		int facing = facingOf(ldx, ldz);
		for (int y = 1; y <= 5; y++) {
			set(cx + 2 * ldx, y, cz + 2 * ldz, LADDER, facing);
		}
		int px = cx - ldx + qx;
		int pz = cz - ldz + qz;
		for (int y = 7; y <= 9; y++) {
			set(px, y, pz, FENCE);
		}
		set(cx - ldx, 8, cz - ldz, BANNER, RED);
		set(cx - ldx, 9, cz - ldz, BANNER, RED);
	}

	// ----- reading the plan -----

	private static boolean isSolidAtY1(final int kind) {
		return kind == LOG || kind == WOOL || kind == BED_FOOT || kind == BED_HEAD || kind == BARREL || kind == HAY || kind == SEAT
			|| kind == FIRE || kind == CORE || kind == VAULT || kind == FENCE || kind == GRIND || kind == PLANK || kind == TLOG
			|| kind == LADDER || kind == BANNER;
	}

	/** Whether a player can stand in this column at y1. */
	public boolean walkable(final int x, final int z) {
		return in(x, 1, z) && !isSolidAtY1(kind(x, 1, z));
	}

	/**
	 * The shortest walk over y1 from a cell to the first cell that {@code goal} accepts, or null. The path holds both
	 * ends.
	 */
	public List<Cell> walk(final Cell from, final java.util.function.Predicate<Cell> goal) {
		Cell[][] prev = new Cell[SIZE_Z][SIZE_X];
		boolean[][] seen = new boolean[SIZE_Z][SIZE_X];
		List<Cell> queue = new ArrayList<>();
		queue.add(from);
		seen[from.z()][from.x()] = true;
		for (int i = 0; i < queue.size(); i++) {
			Cell cell = queue.get(i);
			if (goal.test(cell)) {
				List<Cell> path = new ArrayList<>();
				for (Cell c = cell; c != null; c = prev[c.z()][c.x()]) {
					path.add(0, c);
				}
				return path;
			}
			for (int[] step : STEPS) {
				int nx = cell.x() + step[0];
				int nz = cell.z() + step[1];
				if (nx < 0 || nz < 0 || nx >= SIZE_X || nz >= SIZE_Z || seen[nz][nx] || !walkable(nx, nz)) {
					continue;
				}
				seen[nz][nx] = true;
				prev[nz][nx] = cell;
				queue.add(new Cell(nx, nz));
			}
		}
		return null;
	}

	private static boolean touches(final Cell a, final Cell b) {
		return Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z()) == 1;
	}

	/** Whether a walk from the trail reaches the core, the vault, every tent door step and every tower ladder foot. */
	public boolean floodsEverything() {
		if (walk(this.trailStart, c -> touches(c, this.core)) == null || walk(this.trailStart, c -> touches(c, this.vault)) == null) {
			return false;
		}
		for (Tent tent : this.tents) {
			if (walk(this.trailStart, c -> c.equals(tent.step())) == null) {
				return false;
			}
		}
		for (Tower tower : this.towers) {
			Cell foot = ladderFoot(tower);
			if (walk(this.trailStart, c -> c.equals(foot)) == null) {
				return false;
			}
		}
		return true;
	}

	/** The marker cell of the lookout on a tower, on the platform top. */
	public static int lookoutY() {
		return 6;
	}
}
