package com.stella.ruins.site.sealedvault;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.util.RandomSource;

/**
 * The plan of the Sealed Vault: two levels of a 7 x 7 maze, the core room above the inner vault, six candidate seal
 * rooms of which three hold a seal, trap corridors, two stairs, the Warden's lair, and the cells of the wraiths.
 * <p>
 * The plan is a pure function of one random source, so every chunk draws the same vault. It holds cell numbers only.
 * {@link VaultLayout} turns it into blocks.
 * <p>
 * A cell is {@code j * 7 + i}. Local x grows with i and local z grows with j. The entrance is on level 1, in the
 * row {@code j = 0}.
 */
public final class VaultPlan {
	public static final int GRID = 7;
	public static final int CELLS = GRID * GRID;
	/** Blocks from one cell to the next: 6 open and 1 wall. */
	public static final int PITCH = 7;
	public static final int CELL = 6;
	/** The outer shell on the sides and the back. */
	public static final int OFFSET = 3;
	/** The tunnel between the south face and the grid. */
	public static final int EXTENSION = 8;
	public static final int SIZE_X = OFFSET * 2 + PITCH * GRID + 1;
	public static final int SIZE_Y = 24;
	public static final int SIZE_Z = EXTENSION + SIZE_X;
	public static final int CENTER_CELL = 3 * GRID + 3;
	public static final int LEVELS = 2;
	/** Candidate seal rooms on each level. */
	public static final int CANDIDATES_PER_LEVEL = 3;
	public static final int SEALS = 3;
	public static final int WRAITHS = 6;
	public static final int STAIRS = 2;
	public static final int MIN_TRAPS = 2;
	public static final int MAX_TRAPS = 4;
	/** A void drop lands in a level 0 cell at most this many cells from a stair. */
	public static final int DROP_STAIR_REACH = 8;
	/** The share of the remaining walls that open as loops. */
	public static final double LOOP_SHARE = 0.10;
	/** The share of drop-capable corridors that become a void drop. Such corridors are rare, so most take it. */
	public static final double DROP_SHARE = 0.75;

	private static final int[] DI = {1, 0, -1, 0};
	private static final int[] DJ = {0, 1, 0, -1};

	/** The kind of a trap corridor. */
	public enum TrapKind {
		ARROW,
		DROP
	}

	/** One cell of one level. */
	public record Cell(int level, int cell) {
		public int i() {
			return this.cell % GRID;
		}

		public int j() {
			return this.cell / GRID;
		}
	}

	/** A trap corridor. {@code alongX} is true when the corridor runs east and west. */
	public record Trap(int level, int cell, TrapKind kind, boolean alongX) {
		public Cell at() {
			return new Cell(this.level, this.cell);
		}
	}

	private final boolean[][] open = new boolean[LEVELS][CELLS];
	private final boolean[][] linkEast = new boolean[LEVELS][CELLS];
	private final boolean[][] linkNorth = new boolean[LEVELS][CELLS];
	private int entrance;
	private final int[] stairs = new int[STAIRS];
	private int lair;
	private final List<Cell> candidates = new ArrayList<>();
	private final int[] chosen = new int[SEALS];
	private final List<Trap> traps = new ArrayList<>();
	private final List<Cell> wraiths = new ArrayList<>();
	private final int[] loops = new int[LEVELS];

	private VaultPlan() {
	}

	public static int cellMinX(final int i) {
		return OFFSET + PITCH * i + 1;
	}

	public static int cellMinZ(final int j) {
		return EXTENSION + OFFSET + PITCH * j + 1;
	}

	/** Standing height of a level: the first air layer above its floor. */
	public static int stand(final int level) {
		return level == 0 ? 2 : 9;
	}

	/** Draws a plan from a random source. The same source state gives the same plan. */
	public static VaultPlan derive(final RandomSource random) {
		VaultPlan plan = new VaultPlan();
		Arrays.fill(plan.open[0], true);
		Arrays.fill(plan.open[1], true);
		plan.open[0][CENTER_CELL] = false;
		plan.entrance = random.nextInt(GRID);
		plan.carve(1, plan.entrance, random);
		List<Integer> level0 = new ArrayList<>();
		for (int c = 0; c < CELLS; c++) {
			if (plan.open[0][c]) {
				level0.add(c);
			}
		}
		plan.carve(0, level0.get(random.nextInt(level0.size())), random);
		plan.loops[0] = plan.addLoops(0, random);
		plan.loops[1] = plan.addLoops(1, random);
		plan.placeStairs(random);
		int[] reach0 = plan.distances(0, plan.stairs);
		int[] reach1 = plan.distances(1, new int[] {plan.entrance});
		plan.placeLair(reach0, random);
		boolean[] reserved1 = new boolean[CELLS];
		boolean[] reserved0 = new boolean[CELLS];
		reserved1[CENTER_CELL] = true;
		reserved0[CENTER_CELL] = true;
		for (int stair : plan.stairs) {
			reserved1[stair] = true;
			reserved0[stair] = true;
		}
		reserved1[plan.entrance] = true;
		reserved0[plan.lair] = true;
		List<Integer> first = plan.pickCandidates(1, reserved1, reach1, random);
		List<Integer> second = plan.pickCandidates(0, reserved0, reach0, random);
		first.forEach(c -> plan.candidates.add(new Cell(1, c)));
		second.forEach(c -> plan.candidates.add(new Cell(0, c)));
		plan.chooseSeals(random);
		plan.placeTraps(reach0, random);
		plan.placeWraiths(random);
		return plan;
	}

	// ---- the maze ----

	private boolean linked(final int level, final int cell, final int dir) {
		int i = cell % GRID;
		int j = cell / GRID;
		return switch (dir) {
			case 0 -> this.linkEast[level][cell];
			case 1 -> this.linkNorth[level][cell];
			case 2 -> i > 0 && this.linkEast[level][cell - 1];
			default -> j > 0 && this.linkNorth[level][cell - GRID];
		};
	}

	private void link(final int level, final int cell, final int dir) {
		switch (dir) {
			case 0 -> this.linkEast[level][cell] = true;
			case 1 -> this.linkNorth[level][cell] = true;
			case 2 -> this.linkEast[level][cell - 1] = true;
			default -> this.linkNorth[level][cell - GRID] = true;
		}
	}

	private static int neighbor(final int cell, final int dir) {
		int i = cell % GRID + DI[dir];
		int j = cell / GRID + DJ[dir];
		return i < 0 || i >= GRID || j < 0 || j >= GRID ? -1 : j * GRID + i;
	}

	private void carve(final int level, final int start, final RandomSource random) {
		boolean[] seen = new boolean[CELLS];
		List<Integer> stack = new ArrayList<>();
		stack.add(start);
		seen[start] = true;
		while (!stack.isEmpty()) {
			int cell = stack.get(stack.size() - 1);
			List<int[]> next = new ArrayList<>();
			for (int dir = 0; dir < 4; dir++) {
				int to = neighbor(cell, dir);
				if (to >= 0 && this.open[level][to] && !seen[to]) {
					next.add(new int[] {to, dir});
				}
			}
			if (next.isEmpty()) {
				stack.remove(stack.size() - 1);
				continue;
			}
			int[] pick = next.get(random.nextInt(next.size()));
			link(level, cell, pick[1]);
			seen[pick[0]] = true;
			stack.add(pick[0]);
		}
	}

	private int addLoops(final int level, final RandomSource random) {
		List<int[]> walls = new ArrayList<>();
		for (int cell = 0; cell < CELLS; cell++) {
			for (int dir = 0; dir < 2; dir++) {
				int to = neighbor(cell, dir);
				if (to >= 0 && this.open[level][cell] && this.open[level][to] && !linked(level, cell, dir)) {
					walls.add(new int[] {cell, dir});
				}
			}
		}
		shuffle(walls, random);
		int count = (int) Math.round(walls.size() * LOOP_SHARE);
		for (int k = 0; k < count; k++) {
			link(level, walls.get(k)[0], walls.get(k)[1]);
		}
		return count;
	}

	private static <T> void shuffle(final List<T> list, final RandomSource random) {
		for (int i = list.size() - 1; i > 0; i--) {
			Collections.swap(list, i, random.nextInt(i + 1));
		}
	}

	/** Steps through the maze of one level from the start cells. A cell the starts cannot reach has -1. */
	int[] distances(final int level, final int[] starts) {
		int[] dist = new int[CELLS];
		Arrays.fill(dist, -1);
		List<Integer> queue = new ArrayList<>();
		for (int start : starts) {
			dist[start] = 0;
			queue.add(start);
		}
		for (int head = 0; head < queue.size(); head++) {
			int cell = queue.get(head);
			for (int dir = 0; dir < 4; dir++) {
				int to = neighbor(cell, dir);
				if (to >= 0 && this.open[level][to] && dist[to] < 0 && linked(level, cell, dir)) {
					dist[to] = dist[cell] + 1;
					queue.add(to);
				}
			}
		}
		return dist;
	}

	/**
	 * A stair cell needs its west wall closed on both levels, because the flight runs along the west lane and no
	 * doorway may open onto it. Two such cells stand at least 6 cells apart.
	 */
	private void placeStairs(final RandomSource random) {
		List<Integer> pool = new ArrayList<>();
		for (int c = 0; c < CELLS; c++) {
			if (c != CENTER_CELL && c != this.entrance && !hasLink(0, c, 2) && !hasLink(1, c, 2)) {
				pool.add(c);
			}
		}
		int first = pool.get(random.nextInt(pool.size()));
		List<Integer> far = new ArrayList<>();
		for (int c : pool) {
			if (Math.abs(c % GRID - first % GRID) + Math.abs(c / GRID - first / GRID) >= 6) {
				far.add(c);
			}
		}
		if (far.isEmpty()) {
			int best = -1;
			for (int c : pool) {
				int away = Math.abs(c % GRID - first % GRID) + Math.abs(c / GRID - first / GRID);
				if (c != first && (best < 0 || away > Math.abs(best % GRID - first % GRID) + Math.abs(best / GRID - first / GRID))) {
					best = c;
				}
			}
			far.add(best);
		}
		this.stairs[0] = first;
		this.stairs[1] = far.get(random.nextInt(far.size()));
	}

	private void placeLair(final int[] reach0, final RandomSource random) {
		double best = -1.0;
		int lair = -1;
		for (int c = 0; c < CELLS; c++) {
			if (!this.open[0][c] || c == this.stairs[0] || c == this.stairs[1]) {
				continue;
			}
			double score = reach0[c] + random.nextDouble() * 0.4;
			if (score > best) {
				best = score;
				lair = c;
			}
		}
		this.lair = lair;
	}

	private int degree(final int level, final int cell) {
		int count = 0;
		for (int dir = 0; dir < 4; dir++) {
			if (neighbor(cell, dir) >= 0 && linked(level, cell, dir)) {
				count++;
			}
		}
		return count;
	}

	private List<Integer> pickCandidates(final int level, final boolean[] reserved, final int[] reach, final RandomSource random) {
		List<double[]> scored = new ArrayList<>();
		for (int c = 0; c < CELLS; c++) {
			if (!this.open[level][c] || reserved[c]) {
				continue;
			}
			scored.add(new double[] {c, (degree(level, c) == 1 ? 1000 : 0) + reach[c] + random.nextDouble() * 0.5});
		}
		scored.sort((a, b) -> Double.compare(b[1], a[1]));
		List<Integer> picked = new ArrayList<>();
		for (int k = 0; k < CANDIDATES_PER_LEVEL; k++) {
			picked.add((int) scored.get(k)[0]);
		}
		return picked;
	}

	private void chooseSeals(final RandomSource random) {
		for (;;) {
			List<Integer> all = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5));
			shuffle(all, random);
			List<Integer> pick = new ArrayList<>(all.subList(0, SEALS));
			Collections.sort(pick);
			long onLevelOne = pick.stream().filter(k -> k < CANDIDATES_PER_LEVEL).count();
			if (onLevelOne >= 1 && onLevelOne <= SEALS - 1) {
				for (int k = 0; k < SEALS; k++) {
					this.chosen[k] = pick.get(k);
				}
				return;
			}
		}
	}

	private boolean isCandidate(final int level, final int cell) {
		for (Cell candidate : this.candidates) {
			if (candidate.level() == level && candidate.cell() == cell) {
				return true;
			}
		}
		return false;
	}

	private boolean reserved(final int level, final int cell) {
		return cell == CENTER_CELL || cell == this.stairs[0] || cell == this.stairs[1] || isCandidate(level, cell)
			|| (level == 1 && cell == this.entrance) || (level == 0 && cell == this.lair);
	}

	/** The axis of a straight corridor cell: true for east and west, false for north and south, null for any other. */
	private Boolean straightAxis(final int level, final int cell) {
		boolean east = linked(level, cell, 0);
		boolean north = linked(level, cell, 1);
		boolean west = linked(level, cell, 2);
		boolean south = linked(level, cell, 3);
		if (east && west && !north && !south) {
			return Boolean.TRUE;
		}
		if (north && south && !east && !west) {
			return Boolean.FALSE;
		}
		return null;
	}

	/**
	 * A void drop needs a level 1 corridor over a level 0 dead end that opens along the same axis, within
	 * {@link #DROP_STAIR_REACH} cells of a stair. The catch dais fills that dead end.
	 */
	private boolean dropFits(final int cell, final boolean alongX, final int[] reach0) {
		if (!this.open[0][cell] || reserved(0, cell) || degree(0, cell) != 1 || reach0[cell] < 0 || reach0[cell] > DROP_STAIR_REACH) {
			return false;
		}
		return alongX ? linked(0, cell, 0) || linked(0, cell, 2) : linked(0, cell, 1) || linked(0, cell, 3);
	}

	private void placeTraps(final int[] reach0, final RandomSource random) {
		int wanted = MIN_TRAPS + random.nextInt(MAX_TRAPS - MIN_TRAPS + 1);
		List<int[]> pool = new ArrayList<>();
		for (int level = 0; level < LEVELS; level++) {
			for (int c = 0; c < CELLS; c++) {
				if (!this.open[level][c] || reserved(level, c)) {
					continue;
				}
				Boolean axis = straightAxis(level, c);
				if (axis != null) {
					pool.add(new int[] {level, c, axis ? 1 : 0});
				}
			}
		}
		shuffle(pool, random);
		pool.sort((a, b) -> Boolean.compare(canDrop(b, reach0), canDrop(a, reach0)));
		boolean[] taken = new boolean[CELLS];
		for (int[] entry : pool) {
			if (this.traps.size() >= wanted) {
				break;
			}
			if (taken[entry[1]]) {
				continue;
			}
			boolean alongX = entry[2] == 1;
			TrapKind kind = canDrop(entry, reach0) && random.nextDouble() < DROP_SHARE ? TrapKind.DROP : TrapKind.ARROW;
			taken[entry[1]] = true;
			this.traps.add(new Trap(entry[0], entry[1], kind, alongX));
		}
	}

	private boolean canDrop(final int[] entry, final int[] reach0) {
		return entry[0] == 1 && dropFits(entry[1], entry[2] == 1, reach0);
	}

	private void placeWraiths(final RandomSource random) {
		List<Cell> free = new ArrayList<>();
		for (int level = 0; level < LEVELS; level++) {
			for (int c = 0; c < CELLS; c++) {
				if (this.open[level][c] && !reserved(level, c) && !trapAt(level, c) && !(level == 0 && droppedOn(c))) {
					free.add(new Cell(level, c));
				}
			}
		}
		shuffle(free, random);
		this.wraiths.addAll(free.subList(0, WRAITHS));
	}

	private boolean trapAt(final int level, final int cell) {
		for (Trap trap : this.traps) {
			if (trap.level() == level && trap.cell() == cell) {
				return true;
			}
		}
		return false;
	}

	/** True when a void drop on level 1 lands in this level 0 cell. */
	private boolean droppedOn(final int cell) {
		for (Trap trap : this.traps) {
			if (trap.level() == 1 && trap.cell() == cell && trap.kind() == TrapKind.DROP) {
				return true;
			}
		}
		return false;
	}

	// ---- reading the plan ----

	public int entrance() {
		return this.entrance;
	}

	public boolean isOpen(final int level, final int cell) {
		return this.open[level][cell];
	}

	/** True when a passage joins this cell to its neighbour in a direction: 0 east, 1 north (+z), 2 west, 3 south. */
	public boolean hasLink(final int level, final int cell, final int dir) {
		return neighbor(cell, dir) >= 0 && linked(level, cell, dir);
	}

	public int linkCount(final int level) {
		int count = 0;
		for (int cell = 0; cell < CELLS; cell++) {
			for (int dir = 0; dir < 2; dir++) {
				if (neighbor(cell, dir) >= 0 && linked(level, cell, dir)) {
					count++;
				}
			}
		}
		return count;
	}

	public int loops(final int level) {
		return this.loops[level];
	}

	public int[] stairs() {
		return this.stairs.clone();
	}

	public boolean isStair(final int cell) {
		return cell == this.stairs[0] || cell == this.stairs[1];
	}

	public int lair() {
		return this.lair;
	}

	public List<Cell> candidates() {
		return List.copyOf(this.candidates);
	}

	/** The indexes into {@link #candidates()} of the three cells that hold a seal, ascending. */
	public int[] chosen() {
		return this.chosen.clone();
	}

	public boolean holdsSeal(final int candidateIndex) {
		for (int k : this.chosen) {
			if (k == candidateIndex) {
				return true;
			}
		}
		return false;
	}

	public List<Cell> sealCells() {
		List<Cell> cells = new ArrayList<>();
		for (int k : this.chosen) {
			cells.add(this.candidates.get(k));
		}
		return cells;
	}

	public List<Trap> traps() {
		return List.copyOf(this.traps);
	}

	public List<Cell> wraiths() {
		return List.copyOf(this.wraiths);
	}

	/** Steps through one level's maze from the stairs. */
	public int[] distancesFromStairs(final int level) {
		return distances(level, this.stairs);
	}

	/** The cells a walker can step to from a cell on a level, through passages, stairs and the core stair. */
	public List<Cell> neighbors(final Cell from) {
		List<Cell> out = new ArrayList<>();
		for (int dir = 0; dir < 4; dir++) {
			int to = neighbor(from.cell(), dir);
			if (to >= 0 && linked(from.level(), from.cell(), dir)) {
				out.add(new Cell(from.level(), to));
			}
		}
		if (isStair(from.cell())) {
			out.add(new Cell(1 - from.level(), from.cell()));
		}
		if (from.cell() == CENTER_CELL) {
			out.add(new Cell(1 - from.level(), CENTER_CELL));
		}
		return out;
	}

	/** Every cell a walker reaches from a cell, the cell included. */
	public List<Cell> reachable(final Cell from) {
		List<Cell> seen = new ArrayList<>();
		boolean[][] mark = new boolean[LEVELS][CELLS];
		mark[from.level()][from.cell()] = true;
		seen.add(from);
		for (int head = 0; head < seen.size(); head++) {
			for (Cell next : neighbors(seen.get(head))) {
				if (!mark[next.level()][next.cell()]) {
					mark[next.level()][next.cell()] = true;
					seen.add(next);
				}
			}
		}
		return seen;
	}

	/** A signature of the maze and the entrance, for comparing seeds. */
	public String signature() {
		StringBuilder text = new StringBuilder().append(this.entrance);
		for (int level = 0; level < LEVELS; level++) {
			for (int c = 0; c < CELLS; c++) {
				text.append(this.linkEast[level][c] ? '1' : '0').append(this.linkNorth[level][c] ? '1' : '0');
			}
		}
		return text.toString();
	}
}
