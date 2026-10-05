package com.stella.ruins.site.ruinedkeep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.util.RandomSource;

/**
 * The plan of one Ruined Keep, a pure function of the piece seed. Every chunk call rebuilds it, so every chunk
 * draws the same keep. Spots are local: x east, z south (the gate is on the south face), y up from the floor.
 * <p>
 * The body is 36 x 36 and holds a 3 x 3 grid of cells, each 10 x 10 inside and divided by one block of wall.
 * Floors 1 to 3 tile the grid with rooms. The throne floor is one antechamber and one throne room.
 */
final class KeepPlan {
	static final int SIZE = 48;
	static final int PITCH = 8;
	/** The walking level of floors 1 to 3 and of the throne floor. */
	static final int[] STAND = {3, 11, 19, 27};
	static final int ROOM_HEIGHT = 7;
	static final int ROOF = 40;
	static final int CROWN = 41;
	static final int BODY_MIN_X = 6;
	static final int BODY_MAX_X = 41;
	static final int BODY_MIN_Z = 4;
	static final int BODY_MAX_Z = 39;
	static final int TOWER_MIN_TOP = 30;
	static final int TOWER_MAX_TOP = 47;
	/** The most mobs of one floor besides its warden. */
	static final int FLOOR_CAP = 12;
	static final Cell GATE_CELL = new Cell(1, 2);

	enum Template {
		HALL, BARRACKS, CHAPEL, LIBRARY, ARMORY, KITCHEN
	}

	enum Corner {
		NW(0, 0, 0, 0), NE(2, 0, 39, 0), SW(0, 2, 0, 35), SE(2, 2, 39, 35);

		final int col;
		final int row;
		final int footX;
		final int footZ;

		Corner(final int col, final int row, final int footX, final int footZ) {
			this.col = col;
			this.row = row;
			this.footX = footX;
			this.footZ = footZ;
		}

		Cell cell() {
			return new Cell(this.col, this.row);
		}

		boolean north() {
			return this.row == 0;
		}

		boolean west() {
			return this.col == 0;
		}
	}

	enum Face {
		N, S, W, E
	}

	enum Kind {
		SENTINEL, KNIGHT, WARDEN, CHEST
	}

	record Cell(int col, int row) {
		int x0() {
			return 8 + 11 * this.col;
		}

		int z0() {
			return 6 + 11 * this.row;
		}

		int manhattan(final Cell other) {
			return Math.abs(this.col - other.col) + Math.abs(this.row - other.row);
		}

		Rect rect() {
			return new Rect(x0(), z0(), 10, 10);
		}
	}

	record Rect(int x, int z, int w, int h) {
		boolean contains(final int px, final int pz) {
			return px >= this.x && px < this.x + this.w && pz >= this.z && pz < this.z + this.h;
		}

		boolean intersects(final Rect o) {
			return this.x < o.x + o.w && o.x < this.x + this.w && this.z < o.z + o.h && o.z < this.z + this.h;
		}

		Rect grow(final int by) {
			return new Rect(this.x - by, this.z - by, this.w + 2 * by, this.h + 2 * by);
		}

		int maxX() {
			return this.x + this.w - 1;
		}

		int maxZ() {
			return this.z + this.h - 1;
		}
	}

	record Room(int id, Template template, List<Cell> cells, Rect rect) {
		boolean horizontal() {
			return this.rect.w >= this.rect.h;
		}

		int length() {
			return Math.max(this.rect.w, this.rect.h);
		}

		int width() {
			return Math.min(this.rect.w, this.rect.h);
		}

		/** A rect in the room's own frame: {@code u} along the long axis, {@code v} across it. */
		Rect local(final int u, final int v, final int du, final int dv) {
			return horizontal()
				? new Rect(this.rect.x + u, this.rect.z + v, du, dv)
				: new Rect(this.rect.x + v, this.rect.z + u, dv, du);
		}
	}

	/** A doorway: 2 wide, 3 high, through the wall between two rooms. */
	record Door(int roomA, int roomB, Rect rect, Cell cellA, Cell cellB, boolean loop) {
		/** The centre of the doorway, for a walk. */
		double[] centre() {
			return new double[] {this.rect.x + this.rect.w / 2.0, this.rect.z + this.rect.h / 2.0};
		}
	}

	/** A flight of 8 steps, 3 wide. {@code north} says it climbs toward local north. */
	record Stair(Cell cell, boolean west, boolean north, Rect rect) {
		static Stair of(final Cell cell, final boolean west, final boolean north) {
			return new Stair(cell, west, north, new Rect(cell.x0() + (west ? 1 : 6), cell.z0() + 1, 3, 8));
		}

		/** The block row, in the flight rect, of step {@code i}: the lowest step is 0. */
		int stepZ(final int i) {
			return this.north ? this.rect.z + this.rect.h - 1 - i : this.rect.z + i;
		}

		/** The spot where a climber leaves the top step onto the upper floor. */
		int[] stepOff() {
			int z = this.north ? this.cell.z0() : this.cell.z0() + 9;
			return new int[] {this.rect.x + 1, z};
		}

		/** The spot just before the lowest step. */
		int[] bottom() {
			int z = this.north ? this.cell.z0() + 9 : this.cell.z0();
			return new int[] {this.rect.x + 1, z};
		}
	}

	record Decor(String kind, Rect rect, boolean solid) {
	}

	record Marker(Kind kind, int x, int z) {
	}

	static final Rect NONE = new Rect(-100, -100, 0, 0);

	/** {@code arrive} is the flight that lands on this floor, or null on floor 1, where the climber enters by the gate. */
	record Floor(List<Room> rooms, Set<Cell> rubble, List<Door> doors, Stair up, Stair arrive, Cell arrivalCell, List<Decor> decor, List<Marker> markers) {
		Rect arriveRect() {
			return this.arrive == null ? NONE : this.arrive.rect;
		}

		Room roomAt(final Cell cell) {
			for (Room room : this.rooms) {
				if (room.cells.contains(cell)) {
					return room;
				}
			}
			return null;
		}

		Room hall() {
			for (Room room : this.rooms) {
				if (room.template == Template.HALL) {
					return room;
				}
			}
			throw new IllegalStateException("a floor without a key hall");
		}

		int mobCount() {
			return (int) this.markers.stream().filter(m -> m.kind == Kind.SENTINEL || m.kind == Kind.KNIGHT).count();
		}
	}

	/** A hole through the slab of floor {@code floor} (1 or 2, zero based), dropping to the floor below. */
	record Hole(int floor, Rect rect) {
	}

	/** A gap in the outer wall, 3 wide and 4 high. {@code floor} is 0 to 3, the throne floor being 3. */
	record Breach(int floor, Face face, Cell cell, Rect rect, int sill) {
	}

	record Tower(Corner corner, int top) {
	}

	/** A buttress of the outer wall: 2 wide, 2 deep, rising to {@code top}. */
	record Buttress(Face face, Rect rect, int top) {
	}

	final boolean stone;
	final int moss;
	final int crack;
	final List<Tower> towers;
	final Floor[] floors = new Floor[3];
	final List<Hole> holes = new ArrayList<>();
	final List<Breach> breaches = new ArrayList<>();
	final List<Buttress> buttresses = new ArrayList<>();
	final boolean throneStairWest;
	final int[] pillarRows;
	final List<Rect> roofGaps = new ArrayList<>();
	final Corner fallenCorner;
	final int fallenDepth;
	final List<Marker> throneMarkers;

	private KeepPlan(final RandomSource r) {
		this.stone = r.nextBoolean();
		this.moss = 10 + r.nextInt(26);
		this.crack = 5 + r.nextInt(16);
		int count = 2 + r.nextInt(3);
		List<Corner> corners = new ArrayList<>(List.of(Corner.values()));
		shuffle(corners, r);
		List<Tower> built = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			built.add(new Tower(corners.get(i), TOWER_MIN_TOP + r.nextInt(TOWER_MAX_TOP - TOWER_MIN_TOP + 1)));
		}
		built.sort((a, b) -> a.corner.compareTo(b.corner));
		this.towers = List.copyOf(built);
		Set<Cell> towerCells = new HashSet<>();
		for (Tower tower : this.towers) {
			towerCells.add(tower.corner.cell());
		}

		// The climb: each stair cell lies two or more cells from the cell where the climber arrives.
		List<Cell> all = new ArrayList<>();
		for (int c = 0; c < 3; c++) {
			for (int row = 0; row < 3; row++) {
				all.add(new Cell(c, row));
			}
		}
		Stair[] stairs = new Stair[3];
		Cell arrival = GATE_CELL;
		for (int f = 0; f < 3; f++) {
			List<Cell> candidates = new ArrayList<>();
			if (f < 2) {
				for (Cell cell : all) {
					if (cell.manhattan(arrival) >= 2 && !(f == 1 && cell.equals(GATE_CELL))) {
						candidates.add(cell);
					}
				}
			} else {
				for (Cell cell : List.of(new Cell(0, 2), new Cell(2, 2))) {
					if (cell.manhattan(arrival) >= 2) {
						candidates.add(cell);
					}
				}
			}
			Cell dep = candidates.get(r.nextInt(candidates.size()));
			boolean west;
			boolean north;
			if (f == 2) {
				west = dep.col == 0;
				north = true;
			} else {
				west = r.nextBoolean();
				north = r.nextBoolean();
			}
			stairs[f] = Stair.of(dep, west, north);
			arrival = dep;
		}
		this.throneStairWest = stairs[2].west;

		for (int f = 0; f < 3; f++) {
			Cell arrive = f == 0 ? GATE_CELL : stairs[f - 1].cell;
			this.floors[f] = tile(r, stairs[f], f == 0 ? null : stairs[f - 1], arrive, towerCells);
		}
		placeHoles(r);
		this.pillarRows = r.nextBoolean() ? new int[] {11, 21} : new int[] {10, 16, 22};
		int gaps = 3 + r.nextInt(3);
		for (int i = 0; i < gaps; i++) {
			int w = 2 + r.nextInt(3);
			int h = 2 + r.nextInt(3);
			this.roofGaps.add(new Rect(10 + r.nextInt(36 - 10 - w + 1), 8 + r.nextInt(26 - 8 - h + 1), w, h));
		}
		this.fallenCorner = Corner.values()[r.nextInt(4)];
		this.fallenDepth = 4 + r.nextInt(3);
		planButtresses(r);
		for (int f = 0; f < 3; f++) {
			furnish(this.floors[f], f, r);
		}
		List<Marker> throne = new ArrayList<>();
		throne.add(new Marker(Kind.KNIGHT, 14, 33));
		throne.add(new Marker(Kind.KNIGHT, 34, 33));
		throne.add(new Marker(Kind.SENTINEL, 17, 30));
		throne.add(new Marker(Kind.SENTINEL, 31, 30));
		this.throneMarkers = List.copyOf(throne);
	}

	static KeepPlan of(final RandomSource random) {
		return new KeepPlan(random);
	}

	static KeepPlan of(final long seed) {
		return new KeepPlan(RandomSource.create(seed));
	}

	boolean tower(final Corner corner) {
		return tower(corner, this.towers);
	}

	private static boolean tower(final Corner corner, final List<Tower> towers) {
		for (Tower t : towers) {
			if (t.corner == corner) {
				return true;
			}
		}
		return false;
	}

	/** The highest layer the plan builds, plus one: the keep's height. */
	int height() {
		int top = CROWN;
		for (Tower t : this.towers) {
			top = Math.max(top, t.top);
		}
		for (Buttress b : this.buttresses) {
			top = Math.max(top, b.top);
		}
		return top + 1;
	}

	private static <T> void shuffle(final List<T> list, final RandomSource r) {
		for (int i = list.size() - 1; i > 0; i--) {
			Collections.swap(list, i, r.nextInt(i + 1));
		}
	}

	private static List<Cell[]> adjacent() {
		List<Cell[]> pairs = new ArrayList<>();
		for (int c = 0; c < 3; c++) {
			for (int row = 0; row < 3; row++) {
				if (c < 2) {
					pairs.add(new Cell[] {new Cell(c, row), new Cell(c + 1, row)});
				}
				if (row < 2) {
					pairs.add(new Cell[] {new Cell(c, row), new Cell(c, row + 1)});
				}
			}
		}
		return pairs;
	}

	private static Rect rectOf(final List<Cell> cells) {
		int minC = 9;
		int maxC = -1;
		int minR = 9;
		int maxR = -1;
		for (Cell cell : cells) {
			minC = Math.min(minC, cell.col);
			maxC = Math.max(maxC, cell.col);
			minR = Math.min(minR, cell.row);
			maxR = Math.max(maxR, cell.row);
		}
		return new Rect(8 + 11 * minC, 6 + 11 * minR, 11 * (maxC - minC + 1) - 1, 11 * (maxR - minR + 1) - 1);
	}

	/** Door rect for the wall between two neighbouring cells. */
	static Rect doorRect(final Cell a, final Cell b) {
		Cell lo = a.col < b.col || a.row < b.row ? a : b;
		if (a.row == b.row) {
			return new Rect(lo.x0() + 10, lo.z0() + 4, 1, 2);
		}
		return new Rect(lo.x0() + 4, lo.z0() + 10, 2, 1);
	}

	private Floor tile(final RandomSource r, final Stair up, final Stair arrive, final Cell arrivalCell, final Set<Cell> towerCells) {
		List<Cell[]> pairs = adjacent();
		Set<Cell> mustRoom = new HashSet<>(List.of(arrivalCell, up.cell));
		for (int attempt = 0; attempt < 5000; attempt++) {
			Set<Cell> free = new HashSet<>();
			for (int c = 0; c < 3; c++) {
				for (int row = 0; row < 3; row++) {
					free.add(new Cell(c, row));
				}
			}
			List<Cell[]> hallCandidates = new ArrayList<>();
			for (Cell[] p : pairs) {
				if (!mustRoom.contains(p[0]) && !mustRoom.contains(p[1])) {
					hallCandidates.add(p);
				}
			}
			Cell[] hall = hallCandidates.get(r.nextInt(hallCandidates.size()));
			free.remove(hall[0]);
			free.remove(hall[1]);
			List<Room> rooms = new ArrayList<>();
			rooms.add(new Room(0, Template.HALL, List.of(hall[0], hall[1]), rectOf(List.of(hall[0], hall[1]))));
			int dominoes = 1 + r.nextInt(3);
			for (int k = 0; k < dominoes - 1; k++) {
				List<Cell[]> open = new ArrayList<>();
				for (Cell[] p : pairs) {
					if (free.contains(p[0]) && free.contains(p[1])) {
						open.add(p);
					}
				}
				if (open.isEmpty()) {
					break;
				}
				Cell[] p = open.get(r.nextInt(open.size()));
				free.remove(p[0]);
				free.remove(p[1]);
				Template t = r.nextBoolean() ? Template.BARRACKS : Template.CHAPEL;
				rooms.add(new Room(rooms.size(), t, List.of(p[0], p[1]), rectOf(List.of(p[0], p[1]))));
			}
			int rubbleCount = r.nextInt(3);
			List<Cell> rubbleCandidates = new ArrayList<>();
			for (Cell cell : sorted(free)) {
				if (!mustRoom.contains(cell) && !towerCells.contains(cell)) {
					rubbleCandidates.add(cell);
				}
			}
			shuffle(rubbleCandidates, r);
			Set<Cell> rubble = new HashSet<>(rubbleCandidates.subList(0, Math.min(rubbleCount, rubbleCandidates.size())));
			free.removeAll(rubble);
			int[] used = new int[3];
			Template[] singles = {Template.LIBRARY, Template.ARMORY, Template.KITCHEN};
			boolean ok = true;
			for (Cell cell : sorted(free)) {
				List<Integer> options = new ArrayList<>();
				for (int i = 0; i < 3; i++) {
					if (used[i] < 3) {
						options.add(i);
					}
				}
				if (options.isEmpty()) {
					ok = false;
					break;
				}
				int pick = options.get(r.nextInt(options.size()));
				used[pick]++;
				rooms.add(new Room(rooms.size(), singles[pick], List.of(cell), rectOf(List.of(cell))));
			}
			if (!ok || rooms.size() < 4 || rooms.size() > 7) {
				continue;
			}
			Floor floor = connect(r, rooms, rubble, up, arrive, arrivalCell);
			if (floor != null) {
				return floor;
			}
		}
		throw new IllegalStateException("no floor plan fits the grid");
	}

	private static List<Cell> sorted(final Set<Cell> cells) {
		List<Cell> list = new ArrayList<>(cells);
		list.sort((a, b) -> a.col != b.col ? Integer.compare(a.col, b.col) : Integer.compare(a.row, b.row));
		return list;
	}

	private Floor connect(final RandomSource r, final List<Room> rooms, final Set<Cell> rubble, final Stair up, final Stair arrive, final Cell arrival) {
		Room[] byCell = new Room[9];
		for (Room room : rooms) {
			for (Cell cell : room.cells) {
				byCell[cell.col * 3 + cell.row] = room;
			}
		}
		// Each pair of neighbouring rooms, with the cell boundaries that join them.
		List<int[]> keys = new ArrayList<>();
		List<List<Cell[]>> joins = new ArrayList<>();
		for (Cell[] p : adjacent()) {
			Room a = byCell[p[0].col * 3 + p[0].row];
			Room b = byCell[p[1].col * 3 + p[1].row];
			if (a == null || b == null || a == b) {
				continue;
			}
			int lo = Math.min(a.id, b.id);
			int hi = Math.max(a.id, b.id);
			int index = -1;
			for (int i = 0; i < keys.size(); i++) {
				if (keys.get(i)[0] == lo && keys.get(i)[1] == hi) {
					index = i;
				}
			}
			if (index < 0) {
				keys.add(new int[] {lo, hi});
				joins.add(new ArrayList<>());
				index = keys.size() - 1;
			}
			joins.get(index).add(p);
		}
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < keys.size(); i++) {
			order.add(i);
		}
		shuffle(order, r);
		int[] parent = new int[rooms.size()];
		for (int i = 0; i < parent.length; i++) {
			parent[i] = i;
		}
		List<Integer> tree = new ArrayList<>();
		List<Integer> rest = new ArrayList<>();
		for (int index : order) {
			int ra = find(parent, keys.get(index)[0]);
			int rb = find(parent, keys.get(index)[1]);
			if (ra != rb) {
				parent[ra] = rb;
				tree.add(index);
			} else {
				rest.add(index);
			}
		}
		Set<Integer> roots = new HashSet<>();
		for (int i = 0; i < parent.length; i++) {
			roots.add(find(parent, i));
		}
		if (roots.size() != 1) {
			return null;
		}
		int extra = r.nextInt(3);
		List<Door> doors = new ArrayList<>();
		for (int index : tree) {
			doors.add(door(r, keys.get(index), joins.get(index), false));
		}
		for (int i = 0; i < Math.min(extra, rest.size()); i++) {
			doors.add(door(r, keys.get(rest.get(i)), joins.get(rest.get(i)), true));
		}
		return new Floor(List.copyOf(rooms), Set.copyOf(rubble), List.copyOf(doors), up, arrive, arrival, new ArrayList<>(), new ArrayList<>());
	}

	private static Door door(final RandomSource r, final int[] key, final List<Cell[]> options, final boolean loop) {
		Cell[] join = options.get(r.nextInt(options.size()));
		return new Door(key[0], key[1], doorRect(join[0], join[1]), join[0], join[1], loop);
	}

	private static int find(final int[] parent, final int start) {
		int x = start;
		while (parent[x] != x) {
			parent[x] = parent[parent[x]];
			x = parent[x];
		}
		return x;
	}

	// ---- holes and breaches ----

	private void placeHoles(final RandomSource r) {
		int wanted = 2 + r.nextInt(4);
		int guard = 0;
		while (this.holes.size() + this.breaches.size() < wanted && guard++ < 600) {
			if (r.nextInt(100) < 55) {
				tryHole(r);
			} else {
				tryBreach(r);
			}
		}
	}

	private void tryHole(final RandomSource r) {
		int f = 1 + r.nextInt(2);
		Floor floor = this.floors[f];
		Room room = floor.rooms.get(r.nextInt(floor.rooms.size()));
		int[][] sizes = {{3, 3}, {4, 3}, {3, 4}};
		int[] size = sizes[r.nextInt(3)];
		Rect box = room.rect;
		if (box.w - 2 < size[0] || box.h - 2 < size[1]) {
			return;
		}
		int hx = box.x + 1 + r.nextInt(box.w - 1 - size[0] + 1);
		int hz = box.z + 1 + r.nextInt(box.h - 1 - size[1] + 1);
		Rect hole = new Rect(hx, hz, size[0], size[1]);
		if (!holeAllowed(f, hole)) {
			return;
		}
		this.holes.add(new Hole(f, hole));
	}

	/**
	 * A hole never lies in a stair cell or a doorway cell, above or below, and never over rubble. Its spot keeps one
	 * block of margin from the cell's walls, so no doorway opens on the landing. It cuts one slab only.
	 */
	boolean holeAllowed(final int f, final Rect hole) {
		for (int[] c : corners(hole)) {
			Cell cell = new Cell((c[0] - 8) / 11, (c[1] - 6) / 11);
			if (this.floors[f - 1].roomAt(cell) == null || this.floors[f].roomAt(cell) == null) {
				return false;
			}
			for (int level : new int[] {f - 1, f}) {
				if (this.floors[level].up.cell.equals(cell) || this.floors[level].arrivalCell.equals(cell)) {
					return false;
				}
			}
		}
		for (int level : new int[] {f - 1, f}) {
			Floor fl = this.floors[level];
			if (hole.intersects(fl.up.rect) || fl.arriveRect().intersects(hole)) {
				return false;
			}
			for (Door d : fl.doors) {
				if (hole.grow(1).intersects(d.rect.grow(1))) {
					return false;
				}
			}
		}
		for (Hole other : this.holes) {
			if (other.floor == f && other.rect.intersects(hole.grow(1))) {
				return false;
			}
		}
		return true;
	}

	private static int[][] corners(final Rect r) {
		return new int[][] {{r.x, r.z}, {r.maxX(), r.z}, {r.x, r.maxZ()}, {r.maxX(), r.maxZ()}};
	}

	private void tryBreach(final RandomSource r) {
		int f = r.nextInt(4);
		Face face = Face.values()[r.nextInt(4)];
		int idx = r.nextInt(3);
		Cell cell = switch (face) {
			case N -> new Cell(idx, 0);
			case S -> new Cell(idx, 2);
			case W -> new Cell(0, idx);
			case E -> new Cell(2, idx);
		};
		if (face == Face.S && idx == 1) {
			return;
		}
		if (f < 3 && this.floors[f].roomAt(cell) == null) {
			return;
		}
		int x0 = cell.x0();
		int z0 = cell.z0();
		Rect wall = switch (face) {
			case N -> new Rect(x0 + 3, 4, 3, 2);
			case S -> new Rect(x0 + 3, 38, 3, 2);
			case W -> new Rect(6, z0 + 3, 2, 3);
			case E -> new Rect(40, z0 + 3, 2, 3);
		};
		for (Breach b : this.breaches) {
			if (b.floor == f && b.face == face && b.cell.equals(cell)) {
				return;
			}
		}
		this.breaches.add(new Breach(f, face, cell, wall, STAND[f] + (f == 0 ? 0 : 2)));
	}

	// ---- external ruin ----

	private void planButtresses(final RandomSource r) {
		int[] along = {18, 29};
		for (int a : along) {
			this.buttresses.add(new Buttress(Face.N, new Rect(a, 2, 2, 2), 14 + r.nextInt(21)));
			this.buttresses.add(new Buttress(Face.S, new Rect(a, 40, 2, 2), 14 + r.nextInt(21)));
		}
		int[] rows = {16, 27};
		for (int a : rows) {
			this.buttresses.add(new Buttress(Face.W, new Rect(4, a, 2, 2), 14 + r.nextInt(21)));
			this.buttresses.add(new Buttress(Face.E, new Rect(42, a, 2, 2), 14 + r.nextInt(21)));
		}
	}

	// ---- furniture and markers ----

	private void furnish(final Floor floor, final int f, final RandomSource r) {
		List<Rect> solids = new ArrayList<>();
		solids.add(floor.up.rect);
		solids.add(floor.arriveRect());
		for (Hole h : this.holes) {
			if (h.floor == f || h.floor == f + 1) {
				solids.add(h.rect);
			}
		}
		List<Marker> sentinelsKnights = new ArrayList<>();
		List<Marker> rest = new ArrayList<>();
		for (Room room : floor.rooms) {
			List<Decor> decor = decorOf(room, solids);
			floor.decor.addAll(decor);
			int len = room.length();
			int wid = room.width();
			switch (room.template) {
				case HALL -> {
					pick(room, solids, decor, rest, Kind.WARDEN, 1, wardenSpots(len, wid));
					pick(room, solids, decor, sentinelsKnights, Kind.SENTINEL, 2, new int[][] {{5, 4}, {len - 7, 5}, {5, 5}, {len - 7, 4}, {8, 4}, {len - 9, 5}});
				}
				case BARRACKS -> {
					pick(room, solids, decor, sentinelsKnights, Kind.SENTINEL, 3, new int[][] {{len / 4, 4}, {len / 2, 5}, {3 * len / 4, 4}, {len / 4, 5}, {len / 2, 4}, {3 * len / 4, 5}});
					pick(room, solids, decor, rest, Kind.CHEST, 1, new int[][] {{1, 1}, {len - 2, 1}, {1, wid - 2}});
				}
				case LIBRARY -> {
					pick(room, solids, decor, sentinelsKnights, Kind.KNIGHT, 2, new int[][] {{2, 3}, {7, 6}, {3, 6}, {6, 3}, {2, 5}, {7, 4}});
					pick(room, solids, decor, rest, Kind.CHEST, 1, new int[][] {{1, 1}, {8, 1}, {1, 8}, {8, 8}});
				}
				case CHAPEL -> {
					pick(room, solids, decor, sentinelsKnights, Kind.KNIGHT, 2, new int[][] {{len - 4, 4}, {8, 5}, {len - 6, 5}, {9, 4}, {len - 8, 4}});
					pick(room, solids, decor, rest, Kind.CHEST, 1, new int[][] {{1, 1}, {1, wid - 2}, {len - 2, 1}, {len - 2, wid - 2}});
				}
				case ARMORY -> {
					pick(room, solids, decor, sentinelsKnights, Kind.SENTINEL, 2, new int[][] {{2, 4}, {7, 5}, {4, 2}, {5, 7}, {3, 6}, {6, 3}});
					pick(room, solids, decor, rest, Kind.CHEST, 1, new int[][] {{1, 1}, {8, 1}, {1, 8}, {8, 8}});
				}
				case KITCHEN -> pick(room, solids, decor, sentinelsKnights, Kind.SENTINEL, 1, new int[][] {{2, 7}, {7, 6}, {6, 2}, {3, 8}});
				default -> {
				}
			}
		}
		if (f == 2) {
			for (Tower t : this.towers) {
				sentinelsKnights.add(new Marker(Kind.KNIGHT, t.corner.footX + 4, t.corner.footZ + 4));
			}
		}
		// The floor cap: a floor spawns at most FLOOR_CAP mobs besides its warden. Drop the surplus at random.
		while (sentinelsKnights.size() > FLOOR_CAP) {
			sentinelsKnights.remove(r.nextInt(sentinelsKnights.size()));
		}
		floor.markers.addAll(sentinelsKnights);
		floor.markers.addAll(rest);
	}

	/** The hall's middle first, then every spot of the hall, nearest the middle first. */
	private static int[][] wardenSpots(final int len, final int wid) {
		List<int[]> spots = new ArrayList<>();
		for (int u = 1; u < len - 1; u++) {
			for (int v = 1; v < wid - 1; v++) {
				spots.add(new int[] {u, v});
			}
		}
		spots.sort((a, b) -> Integer.compare(Math.abs(a[0] - len / 2) * 2 + Math.abs(a[1] - 4), Math.abs(b[0] - len / 2) * 2 + Math.abs(b[1] - 4)));
		return spots.toArray(new int[0][]);
	}

	private static void pick(final Room room, final List<Rect> solids, final List<Decor> decor, final List<Marker> out, final Kind kind, final int count, final int[][] spots) {
		int placed = 0;
		for (int[] spot : spots) {
			Rect at = room.local(spot[0], spot[1], 1, 1);
			boolean blocked = false;
			for (Rect s : solids) {
				if (at.intersects(s)) {
					blocked = true;
				}
			}
			for (Decor d : decor) {
				if (d.solid && at.intersects(d.rect)) {
					blocked = true;
				}
			}
			for (Marker m : out) {
				if (m.x == at.x && m.z == at.z) {
					blocked = true;
				}
			}
			if (blocked) {
				continue;
			}
			out.add(new Marker(kind, at.x, at.z));
			if (++placed == count) {
				return;
			}
		}
	}

	private static List<Decor> decorOf(final Room room, final List<Rect> solids) {
		List<Decor> out = new ArrayList<>();
		int len = room.length();
		int wid = room.width();
		Set<Integer> doorColumns = new HashSet<>();
		for (int i = 0; i < (len + 1) / 11; i++) {
			doorColumns.add(11 * i + 4);
			doorColumns.add(11 * i + 5);
		}
		switch (room.template) {
			case HALL -> {
				out.add(new Decor("carpet", room.local(0, wid / 2 - 1, len, 2), false));
				int[] us = len > 12 ? new int[] {4, len - 6} : new int[] {4};
				for (int u : us) {
					for (int v : new int[] {2, wid - 4}) {
						addSolid(out, solids, "pillar", room.local(u, v, 2, 2));
					}
				}
			}
			case BARRACKS -> {
				wallRun(out, solids, room, 0, 2, "bunk", 1, doorColumns);
				wallRun(out, solids, room, wid - 1, 2, "bunk", 1, doorColumns);
			}
			case LIBRARY -> {
				wallRun(out, solids, room, 0, 2, "shelf", 0, doorColumns);
				wallRun(out, solids, room, wid - 1, 2, "shelf", 0, doorColumns);
				addSolid(out, solids, "table", room.local(len / 2 - 1, wid / 2 - 1, 2, 2));
			}
			case CHAPEL -> {
				addSolid(out, solids, "altar", room.local(1, wid / 2 - 2, 2, 4));
				for (int u = 5; u < len - 2; u += 3) {
					if (!doorColumns.contains(u)) {
						addSolid(out, solids, "pew", room.local(u, 1, 1, 3));
						addSolid(out, solids, "pew", room.local(u, wid - 4, 1, 3));
					}
				}
			}
			case ARMORY -> {
				wallRun(out, solids, room, 0, 2, "rack", 0, doorColumns);
				wallRun(out, solids, room, wid - 1, 2, "rack", 0, doorColumns);
				addSolid(out, solids, "stand", room.local(len / 2 - 1, wid / 2 - 1, 2, 2));
			}
			case KITCHEN -> {
				addSolid(out, solids, "hearth", room.local(6, 0, 3, 1));
				addSolid(out, solids, "table", room.local(3, 4, 3, 2));
				addSolid(out, solids, "barrel", room.local(1, 1, 1, 1));
				addSolid(out, solids, "barrel", room.local(8, wid - 2, 1, 1));
			}
			default -> {
			}
		}
		return out;
	}

	private static void addSolid(final List<Decor> out, final List<Rect> solids, final String kind, final Rect rect) {
		for (Rect s : solids) {
			if (rect.intersects(s)) {
				return;
			}
		}
		out.add(new Decor(kind, rect, true));
	}

	private static void wallRun(final List<Decor> out, final List<Rect> solids, final Room room, final int v, final int item, final String kind, final int gap, final Set<Integer> doorColumns) {
		int len = room.length();
		int u = 1;
		while (u + item <= len - 1) {
			boolean clear = true;
			for (int i = 0; i < item; i++) {
				if (doorColumns.contains(u + i)) {
					clear = false;
				}
			}
			if (clear) {
				addSolid(out, solids, kind, room.local(u, v, item, 1));
			}
			u += item + gap;
		}
	}

	// ---- reads used by the objective and the tests ----

	/** Every spot of a kind on a floor 0 to 2, or 3 for the throne floor. */
	List<Marker> markers(final int floor, final Kind kind) {
		List<Marker> out = new ArrayList<>();
		List<Marker> source = floor == 3 ? this.throneMarkers : this.floors[floor].markers;
		for (Marker m : source) {
			if (m.kind == kind) {
				out.add(m);
			}
		}
		return out;
	}
}
