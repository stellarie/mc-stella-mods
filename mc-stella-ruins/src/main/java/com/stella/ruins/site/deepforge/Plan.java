package com.stella.ruins.site.deepforge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

/**
 * Every random choice of one forge, rolled from the piece seed. The roll is a pure function of its random source, so
 * every chunk of a forge draws the same plan.
 */
record Plan(
	Geo.Slot gate,
	List<ChamberPlan> chambers,
	int route,
	int lavaWidth,
	List<SpurPlan> spurs,
	int plugA,
	int plugB,
	int crackPercent,
	List<int[]> rubble
) {
	static final int MIN_CHAMBERS = 4;
	static final int MAX_CHAMBERS = 7;
	static final int HOLDERS = 3;
	static final int LEVERS = 3;
	static final int ROUTES = 4;

	/** One chamber: its cell, its kind, its floor offset and what it holds for the objective. */
	record ChamberPlan(Geo.Slot slot, Geo.Level level, Template template, int offset, boolean holder, boolean lever) {
		int floorY() {
			return this.level.floor + this.offset;
		}
	}

	/** A two-wide side tunnel. Its far end is rubble when closed, or a cache chest when open. */
	record SpurPlan(Geo.Level level, int x0, int z0, int x1, int z1, Direction out, boolean closed) {
	}

	private record Cell(Geo.Slot slot, Geo.Level level) {
	}

	static Plan roll(final RandomSource r) {
		Geo.Slot[] gates = {Geo.Slot.E, Geo.Slot.S, Geo.Slot.W};
		Geo.Slot gate = gates[r.nextInt(gates.length)];
		int count = MIN_CHAMBERS + r.nextInt(MAX_CHAMBERS - MIN_CHAMBERS + 1);

		List<Cell> cells = new ArrayList<>();
		for (Geo.Level level : Geo.Level.values()) {
			for (Geo.Slot slot : Geo.Slot.values()) {
				boolean gateCell = level == Geo.Level.A && slot == gate;
				boolean vaultCell = level == Geo.Level.C && slot == Geo.Slot.N;
				if (!gateCell && !vaultCell) {
					cells.add(new Cell(slot, level));
				}
			}
		}
		shuffle(cells, r);
		List<Cell> chosen = new ArrayList<>(cells.subList(0, count));
		if (chosen.stream().map(Cell::level).distinct().count() < 2) {
			Geo.Level only = chosen.get(0).level();
			for (Cell other : cells.subList(count, cells.size())) {
				if (other.level() != only) {
					chosen.set(count - 1, other);
					break;
				}
			}
		}
		chosen.sort(Comparator.comparing((Cell c) -> c.level().ordinal()).thenComparing(c -> c.slot().ordinal()));

		List<Template> kinds = new ArrayList<>(List.of(Template.values()));
		shuffle(kinds, r);
		Set<Geo.Slot> stacked = new HashSet<>();
		for (int i = 0; i < chosen.size(); i++) {
			for (int j = i + 1; j < chosen.size(); j++) {
				if (chosen.get(i).slot() == chosen.get(j).slot()) {
					stacked.add(chosen.get(i).slot());
				}
			}
		}
		stacked.add(gate);

		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < chosen.size(); i++) {
			order.add(i);
		}
		List<Integer> holderOrder = new ArrayList<>(order);
		shuffle(holderOrder, r);
		Set<Integer> holders = new HashSet<>(holderOrder.subList(0, HOLDERS));
		List<Integer> plain = new ArrayList<>();
		List<Integer> held = new ArrayList<>();
		for (int i : holderOrder) {
			(holders.contains(i) ? held : plain).add(i);
		}
		Set<Integer> levers = new HashSet<>();
		for (int i : plain) {
			if (levers.size() < LEVERS) {
				levers.add(i);
			}
		}
		for (int i : held) {
			if (levers.size() < LEVERS) {
				levers.add(i);
			}
		}

		List<ChamberPlan> chambers = new ArrayList<>();
		for (int i = 0; i < chosen.size(); i++) {
			Cell cell = chosen.get(i);
			Template template = i < kinds.size() ? kinds.get(i) : Template.values()[r.nextInt(Template.values().length)];
			int[] offsets = cell.level().offsets();
			int offset = offsets[r.nextInt(offsets.length)];
			if (stacked.contains(cell.slot())) {
				offset = 0;
			}
			chambers.add(new ChamberPlan(cell.slot(), cell.level(), template, offset, holders.contains(i), levers.contains(i)));
		}

		int route = r.nextInt(ROUTES);
		int lavaWidth = 2 + r.nextInt(2);
		int crack = 15 + r.nextInt(11);
		int plugA = 3 + r.nextInt(4);
		int plugB = -2 + r.nextInt(5);

		List<SpurPlan> spurs = new ArrayList<>();
		int wanted = 1 + r.nextInt(3);
		for (int attempt = 0; attempt < 80 && spurs.size() < wanted; attempt++) {
			int which = r.nextInt(chambers.size() + 2);
			int side = r.nextBoolean() ? 1 : -1;
			int length = 4 + r.nextInt(4);
			boolean closed = r.nextBoolean();
			int shift = r.nextInt(2);
			int pick = 2 + r.nextInt(5);
			SpurPlan spur;
			if (which > chambers.size()) {
				spur = tryGateSpur(pick, side, length, closed, gate, chambers, spurs);
			} else {
				Geo.Slot slot = which == chambers.size() ? gate : chambers.get(which).slot();
				Geo.Level level = which == chambers.size() ? Geo.Level.A : chambers.get(which).level();
				int index = 3 + r.nextInt(Math.max(1, slot.tunnelCells().size() - 5));
				spur = trySpur(slot, level, index, side, length, closed, shift, gate, chambers, spurs);
			}
			if (spur != null) {
				spurs.add(spur);
			}
		}
		if (spurs.isEmpty()) {
			spurs.addAll(fallbackSpur(gate, chambers));
		}

		List<int[]> rubble = new ArrayList<>();
		int piles = 2 + r.nextInt(3);
		for (int i = 0; i < piles; i++) {
			int x = 22 + r.nextInt(19);
			int z = 22 + r.nextInt(19);
			boolean onDais = x >= 28 && x <= 34 && z >= 28 && z <= 34;
			boolean onMarker = x == 31 && z == 38 || x == 25 && z == 36 || x == 37 && z == 36;
			if (!onDais && !onMarker) {
				rubble.add(new int[] {x, z});
			}
		}
		return new Plan(gate, List.copyOf(chambers), route, lavaWidth, List.copyOf(spurs), plugA, plugB, crack, List.copyOf(rubble));
	}

	private static <T> void shuffle(final List<T> list, final RandomSource r) {
		for (int i = list.size() - 1; i > 0; i--) {
			int j = r.nextInt(i + 1);
			T keep = list.get(i);
			list.set(i, list.get(j));
			list.set(j, keep);
		}
	}

	private static List<SpurPlan> fallbackSpur(final Geo.Slot gate, final List<ChamberPlan> chambers) {
		List<SpurPlan> none = new ArrayList<>();
		for (int which = 0; which <= chambers.size(); which++) {
			Geo.Slot slot = which == chambers.size() ? gate : chambers.get(which).slot();
			Geo.Level level = which == chambers.size() ? Geo.Level.A : chambers.get(which).level();
			for (int index = 3; index < slot.tunnelCells().size() - 1; index++) {
				for (int side = -1; side <= 1; side += 2) {
					for (int length = 6; length >= 3; length--) {
						SpurPlan spur = trySpur(slot, level, index, side, length, true, 0, gate, chambers, none);
						if (spur != null) {
							return List.of(spur);
						}
					}
				}
			}
		}
		for (int a = 2; a <= 6; a++) {
			for (int side = -1; side <= 1; side += 2) {
				for (int length = 6; length >= 3; length--) {
					SpurPlan spur = tryGateSpur(a, side, length, true, gate, chambers, none);
					if (spur != null) {
						return List.of(spur);
					}
				}
			}
		}
		return stubSpur(gate, chambers);
	}

	/**
	 * The last resort: a collapsed stub of an unused slot's tunnel, three wide, from the hall wall outward. Some
	 * rolls leave no room for a side tunnel, but a forge of 19 cells and at most 7 chambers always has an unused one.
	 */
	private static List<SpurPlan> stubSpur(final Geo.Slot gate, final List<ChamberPlan> chambers) {
		for (Geo.Level level : Geo.Level.values()) {
			for (Geo.Slot slot : Geo.Slot.values()) {
				boolean gateCell = level == Geo.Level.A && slot == gate;
				boolean vaultCell = level == Geo.Level.C && slot == Geo.Slot.N;
				boolean used = chambers.stream().anyMatch(c -> c.slot() == slot && c.level() == level);
				if (gateCell || vaultCell || used) {
					continue;
				}
				List<int[]> cells = slot.tunnelCells();
				int[] far = cells.get(cells.size() - 4);
				int[] near = cells.get(cells.size() - 1);
				if (far[0] != near[0] && far[1] != near[1]) {
					continue;
				}
				boolean alongX = far[1] == near[1];
				int x0 = Math.min(far[0], near[0]) - (alongX ? 0 : 1);
				int x1 = Math.max(far[0], near[0]) + (alongX ? 0 : 1);
				int z0 = Math.min(far[1], near[1]) - (alongX ? 1 : 0);
				int z1 = Math.max(far[1], near[1]) + (alongX ? 1 : 0);
				return List.of(new SpurPlan(level, x0, z0, x1, z1, slot.tunnelEnd().getOpposite(), true));
			}
		}
		return List.of();
	}

	/** A spur off the side wall of the gate hall, two cells wide along the hall's depth. */
	private static SpurPlan tryGateSpur(
		final int a, final int side, final int length, final boolean closed, final Geo.Slot gate, final List<ChamberPlan> chambers, final List<SpurPlan> others
	) {
		int reach = Geo.CHAMBER_HALF_WIDTH + length - 1;
		int[] r = side > 0 ? gate.rect(a, Geo.CHAMBER_HALF_WIDTH, a + 1, reach) : gate.rect(a, -reach, a + 1, -Geo.CHAMBER_HALF_WIDTH);
		Direction out = side > 0 ? gate.lateral() : gate.lateral().getOpposite();
		if (r[0] < 3 || r[1] < 3 || r[2] > Geo.SIZE_X - 4 || r[3] > Geo.SIZE_Z - 4) {
			return null;
		}
		int[] probe = {r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1};
		for (int[] blocked : blockedRects(gate, gate, chambers, true)) {
			if (overlaps(probe, blocked)) {
				return null;
			}
		}
		for (SpurPlan other : others) {
			if (overlaps(probe, new int[] {other.x0() - 1, other.z0() - 1, other.x1() + 1, other.z1() + 1})) {
				return null;
			}
		}
		return new SpurPlan(Geo.Level.A, r[0], r[1], r[2], r[3], out, closed);
	}

	/** Builds a spur off a tunnel cell, or returns null when it would touch anything else or leave the box. */
	private static SpurPlan trySpur(
		final Geo.Slot slot, final Geo.Level level, final int index, final int side, final int length, final boolean closed, final int shift,
		final Geo.Slot gate, final List<ChamberPlan> chambers, final List<SpurPlan> others
	) {
		List<int[]> cells = slot.tunnelCells();
		if (index < 1 || index >= cells.size() - 1) {
			return null;
		}
		int[] c = cells.get(index);
		int[] before = cells.get(index - 1);
		int[] after = cells.get(index + 1);
		boolean horizontal = before[1] == c[1] && after[1] == c[1];
		boolean vertical = before[0] == c[0] && after[0] == c[0];
		if (!horizontal && !vertical) {
			return null;
		}
		int x0;
		int z0;
		int x1;
		int z1;
		Direction out;
		if (horizontal) {
			x0 = c[0] - shift;
			x1 = x0 + 1;
			if (side > 0) {
				z0 = c[1] + 2;
				z1 = z0 + length - 1;
				out = Direction.SOUTH;
			} else {
				z1 = c[1] - 2;
				z0 = z1 - length + 1;
				out = Direction.NORTH;
			}
		} else {
			z0 = c[1] - shift;
			z1 = z0 + 1;
			if (side > 0) {
				x0 = c[0] + 2;
				x1 = x0 + length - 1;
				out = Direction.EAST;
			} else {
				x1 = c[0] - 2;
				x0 = x1 - length + 1;
				out = Direction.WEST;
			}
		}
		if (x0 < 3 || z0 < 3 || x1 > Geo.SIZE_X - 4 || z1 > Geo.SIZE_Z - 4) {
			return null;
		}
		int[] probe = {x0 - 1, z0 - 1, x1 + 1, z1 + 1};
		for (int[] blocked : blockedRects(slot, gate, chambers, false)) {
			if (overlaps(probe, blocked)) {
				return null;
			}
		}
		for (SpurPlan other : others) {
			if (overlaps(probe, new int[] {other.x0() - 1, other.z0() - 1, other.x1() + 1, other.z1() + 1})) {
				return null;
			}
		}
		return new SpurPlan(level, x0, z0, x1, z1, out, closed);
	}

	/** Everything a spur must keep off, on any level: the hall, every chamber, the gate hall and every other tunnel. */
	private static List<int[]> blockedRects(final Geo.Slot own, final Geo.Slot gate, final List<ChamberPlan> chambers, final boolean ownGateHall) {
		List<int[]> rects = new ArrayList<>();
		rects.add(new int[] {Geo.HALL_MIN - 1, Geo.HALL_MIN - 1, Geo.HALL_MAX + 1, Geo.HALL_MAX + 1});
		rects.add(new int[] {27, 14, 35, 18});
		Set<Geo.Slot> used = new HashSet<>();
		used.add(gate);
		for (ChamberPlan chamber : chambers) {
			used.add(chamber.slot());
			int[] box = chamber.slot().rect(0, -Geo.CHAMBER_HALF_WIDTH, Geo.CHAMBER_DEPTH, Geo.CHAMBER_HALF_WIDTH);
			rects.add(new int[] {box[0] - 1, box[1] - 1, box[2] + 1, box[3] + 1});
		}
		if (!ownGateHall) {
			int[] facade = gate.rect(0, -Geo.CHAMBER_HALF_WIDTH, Geo.CHAMBER_DEPTH + 3, Geo.CHAMBER_HALF_WIDTH);
			rects.add(new int[] {facade[0] - 1, facade[1] - 1, facade[2] + 1, facade[3] + 1});
		}
		for (Geo.Slot slot : used) {
			if (slot == own) {
				continue;
			}
			for (int[] cell : slot.tunnelCells()) {
				rects.add(new int[] {cell[0] - 2, cell[1] - 2, cell[0] + 2, cell[1] + 2});
			}
		}
		return rects;
	}

	private static boolean overlaps(final int[] a, final int[] b) {
		return a[0] <= b[2] && b[0] <= a[2] && a[1] <= b[3] && b[1] <= a[3];
	}

	int holderCount() {
		return (int) this.chambers.stream().filter(ChamberPlan::holder).count();
	}

	int leverCount() {
		return (int) this.chambers.stream().filter(ChamberPlan::lever).count();
	}
}
