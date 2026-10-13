package com.stella.tower;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * The shape of one castle floor, as pure data: for each column of the {@value LostCastleGrid#SIZE} square, how many
 * blocks of ground or wall stand over the floor, and how far the ceiling hangs down. The families are the tower's
 * own layouts scaled to a castle floor: a maze ({@link TowerMaze}), a cavern ({@link TowerCavern}), a biome field
 * with hills and cover ({@link BiomeCover}), and the great hall ({@link TowerGreatHall}) on the bottom floor.
 * <p>
 * Nothing here reads a block or a clock. A layout is a function of a {@link Random} and of what the plan reserves
 * for stairs, the portal, the vault and the chests, so every chunk of one castle draws the same floor.
 */
public final class LostCastleLayout {
	private static final int S = LostCastleGrid.SIZE;
	private static final int LO = LostCastleGrid.IN_LOW;
	private static final int HI = LostCastleGrid.IN_HIGH;
	private static final int CLEAR = LostCastleGrid.CLEAR;
	private static final int RISE = LostCastleGrid.MAX_RISE;
	/** A cut-off cave smaller than this, with nothing the plan needs in it, is filled rather than joined. */
	private static final int SMALL = 30;
	private static final int[][] WAYS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	/** The layouts a floor may roll. */
	public enum Family {
		MAZE,
		CAVERN,
		FIELD,
		HALL
	}

	private LostCastleLayout() {
	}

	/** A finished floor. Equal when every column is equal, so a plan drawn twice compares equal. */
	public static final class Layout {
		private final byte[] fill;
		private final byte[] hang;
		private final boolean[] earth;

		Layout(final byte[] fill, final byte[] hang, final boolean[] earth) {
			this.fill = fill.clone();
			this.hang = hang.clone();
			this.earth = earth.clone();
		}

		/** The blocks of ground or wall over the floor in a column; the outside counts as wall. */
		public int fill(final int x, final int z) {
			return x < 0 || z < 0 || x >= S || z >= S ? CLEAR : this.fill[x * S + z];
		}

		/** The blocks the ceiling hangs down over a column. */
		public int hang(final int x, final int z) {
			return x < 0 || z < 0 || x >= S || z >= S ? 0 : this.hang[x * S + z];
		}

		/** Whether the column's ground is earth, not wall stone. */
		public boolean earth(final int x, final int z) {
			return x >= 0 && z >= 0 && x < S && z < S && this.earth[x * S + z];
		}

		/** Whether the block {@code y} over the floor in a column is stone or earth. */
		public boolean solid(final int x, final int y, final int z) {
			return y < fill(x, z) || y >= CLEAR - hang(x, z);
		}

		@Override
		public boolean equals(final Object other) {
			return other instanceof Layout layout && Arrays.equals(this.fill, layout.fill) && Arrays.equals(this.hang, layout.hang)
				&& Arrays.equals(this.earth, layout.earth);
		}

		@Override
		public int hashCode() {
			return 31 * (31 * Arrays.hashCode(this.fill) + Arrays.hashCode(this.hang)) + Arrays.hashCode(this.earth);
		}
	}

	/** A floor being drawn: the columns, and what the plan keeps clear of them. */
	static final class Work {
		final byte[] fill = new byte[S * S];
		final byte[] hang = new byte[S * S];
		final boolean[] earth = new boolean[S * S];
		/** Columns the plan needs open and level: stairs, the portal's pocket, chest fronts. */
		final boolean[] open = new boolean[S * S];
		/** Columns a body cannot stand on, though their ground is level: a stair's shaft and ramp. */
		final boolean[] hole = new boolean[S * S];
		/** Columns the plan builds itself, which no layout step may change: the vault. */
		final boolean[] locked = new boolean[S * S];
		/** Columns that cover or a piece of a family has claimed. */
		final boolean[] claimed = new boolean[S * S];
		private int towers;
		/** Floor cells a body must reach, in order; the first is the anchor of the floor's walk. */
		final List<int[]> keys = new ArrayList<>();
		final Random random;

		Work(final Random random) {
			this.random = random;
			for (int x = 0; x < S; x++) {
				for (int z = 0; z < S; z++) {
					if (!inside(x, z)) {
						this.fill[at(x, z)] = (byte) CLEAR;
					}
				}
			}
		}

		static int at(final int x, final int z) {
			return x * S + z;
		}

		static boolean inside(final int x, final int z) {
			return x >= LO && z >= LO && x <= HI && z <= HI;
		}

		int fill(final int x, final int z) {
			return x < 0 || z < 0 || x >= S || z >= S ? CLEAR : this.fill[at(x, z)];
		}

		boolean walk(final int x, final int z) {
			return inside(x, z) && this.fill[at(x, z)] <= RISE && !this.hole[at(x, z)] && !this.locked[at(x, z)];
		}

		boolean step(final int x, final int z, final int nx, final int nz) {
			return walk(nx, nz) && Math.abs(this.fill[at(x, z)] - this.fill[at(nx, nz)]) <= 1;
		}

		/** Whether the plan reserves the column, or builds on it itself. */
		boolean blocked(final int x, final int z) {
			return !inside(x, z) || this.open[at(x, z)] || this.hole[at(x, z)] || this.locked[at(x, z)];
		}

		boolean claimed(final int x, final int z) {
			return inside(x, z) && this.claimed[at(x, z)];
		}

		void claim(final int x, final int z) {
			if (inside(x, z)) {
				this.claimed[at(x, z)] = true;
			}
		}

		boolean locked(final int x, final int z) {
			return inside(x, z) && this.locked[at(x, z)];
		}

		void carve(final int x, final int z) {
			if (inside(x, z) && !this.locked[at(x, z)]) {
				this.fill[at(x, z)] = 0;
				this.hang[at(x, z)] = 0;
				this.earth[at(x, z)] = false;
			}
		}

		void wall(final int x, final int z, final int height) {
			if (inside(x, z) && !this.locked[at(x, z)]) {
				this.fill[at(x, z)] = (byte) Math.min(CLEAR, height);
				this.hang[at(x, z)] = 0;
				this.earth[at(x, z)] = false;
			}
		}

		/** Keeps a rectangle open and level, and clear of the ceiling. */
		void reserve(final int x0, final int z0, final int x1, final int z1) {
			for (int x = Math.max(LO, x0); x <= Math.min(HI, x1); x++) {
				for (int z = Math.max(LO, z0); z <= Math.min(HI, z1); z++) {
					this.open[at(x, z)] = true;
				}
			}
		}

		void hole(final int x0, final int z0, final int x1, final int z1) {
			for (int x = Math.max(LO, x0); x <= Math.min(HI, x1); x++) {
				for (int z = Math.max(LO, z0); z <= Math.min(HI, z1); z++) {
					this.hole[at(x, z)] = true;
				}
			}
		}

		void lock(final int x0, final int z0, final int x1, final int z1) {
			for (int x = Math.max(LO, x0); x <= Math.min(HI, x1); x++) {
				for (int z = Math.max(LO, z0); z <= Math.min(HI, z1); z++) {
					this.locked[at(x, z)] = true;
					this.fill[at(x, z)] = 0;
				}
			}
		}

		boolean taken(final int x0, final int z0, final int x1, final int z1) {
			for (int x = Math.max(LO, x0); x <= Math.min(HI, x1); x++) {
				for (int z = Math.max(LO, z0); z <= Math.min(HI, z1); z++) {
					if (this.open[at(x, z)] || this.hole[at(x, z)] || this.locked[at(x, z)] || this.claimed[at(x, z)]) {
						return true;
					}
				}
			}
			return false;
		}

		void key(final int x, final int z) {
			this.keys.add(new int[] {x, z});
		}

		Layout toLayout() {
			return new Layout(this.fill, this.hang, this.earth);
		}

		// ---------------------------------------------------------------- the families

		void generate(final Family family) {
			switch (family) {
				case MAZE -> maze();
				case CAVERN -> cavern();
				case FIELD -> field();
				case HALL -> hall();
			}
		}

		/** Walls full height, paths four wide between walls two thick, as the tower's maze scaled to the castle floor. */
		private void maze() {
			final int cells = 8;
			final int path = 4;
			final int wall = 2;
			final int pitch = path + wall;
			Arrays.fill(this.fill, (byte) CLEAR);
			boolean[][] seen = new boolean[cells][cells];
			for (int cx = 0; cx < cells; cx++) {
				for (int cz = 0; cz < cells; cz++) {
					rect(LO + cx * pitch, LO + cz * pitch, path, path);
				}
			}
			ArrayDeque<int[]> trail = new ArrayDeque<>();
			int[] start = {this.random.nextInt(cells), this.random.nextInt(cells)};
			seen[start[0]][start[1]] = true;
			trail.push(start);
			while (!trail.isEmpty()) {
				int[] here = trail.peek();
				List<int[]> next = new ArrayList<>();
				for (int[] way : WAYS) {
					int nx = here[0] + way[0];
					int nz = here[1] + way[1];
					if (nx >= 0 && nz >= 0 && nx < cells && nz < cells && !seen[nx][nz]) {
						next.add(new int[] {nx, nz});
					}
				}
				if (next.isEmpty()) {
					trail.pop();
					continue;
				}
				int[] to = next.get(this.random.nextInt(next.size()));
				join(here[0], here[1], to[0], to[1], path, wall);
				seen[to[0]][to[1]] = true;
				trail.push(to);
			}
			// A few extra doorways so the maze has loops, not one long way.
			for (int extra = 0; extra < 8; extra++) {
				int cx = this.random.nextInt(cells);
				int cz = this.random.nextInt(cells);
				boolean alongX = this.random.nextBoolean();
				int nx = alongX ? cx + 1 : cx;
				int nz = alongX ? cz : cz + 1;
				if (nx < cells && nz < cells) {
					join(cx, cz, nx, nz, path, wall);
				}
			}
		}

		private void join(final int ax, final int az, final int bx, final int bz, final int path, final int wall) {
			int lowX = LO + Math.min(ax, bx) * (path + wall);
			int lowZ = LO + Math.min(az, bz) * (path + wall);
			if (ax != bx) {
				rect(lowX + path, lowZ, wall, path);
			} else {
				rect(lowX, lowZ + path, path, wall);
			}
		}

		private void rect(final int x, final int z, final int width, final int depth) {
			for (int dx = 0; dx < width; dx++) {
				for (int dz = 0; dz < depth; dz++) {
					carve(x + dx, z + dz);
				}
			}
		}

		/** Rock grown by a cellular automaton round the reserved columns, thinned of spots too narrow to stand in. */
		private void cavern() {
			boolean[] rock = new boolean[S * S];
			for (int x = 0; x < S; x++) {
				for (int z = 0; z < S; z++) {
					rock[at(x, z)] = !inside(x, z) || !this.open[at(x, z)] && !this.locked[at(x, z)] && this.random.nextInt(100) < 46;
				}
			}
			for (int pass = 0; pass < 4; pass++) {
				boolean[] next = new boolean[S * S];
				for (int x = 0; x < S; x++) {
					for (int z = 0; z < S; z++) {
						int around = 0;
						for (int dx = -1; dx <= 1; dx++) {
							for (int dz = -1; dz <= 1; dz++) {
								int nx = x + dx;
								int nz = z + dz;
								if ((dx != 0 || dz != 0) && (!inside(nx, nz) || rock[at(nx, nz)])) {
									around++;
								}
							}
						}
						boolean was = rock[at(x, z)];
						next[at(x, z)] = !inside(x, z) || !this.open[at(x, z)] && !this.locked[at(x, z)] && (around >= 5 || around >= 4 && was);
					}
				}
				rock = next;
			}
			boolean changed = true;
			while (changed) {
				changed = false;
				for (int x = LO; x <= HI; x++) {
					for (int z = LO; z <= HI; z++) {
						if (!rock[at(x, z)] && !this.open[at(x, z)] && !this.locked[at(x, z)] && !inOpenSquare(rock, x, z)) {
							rock[at(x, z)] = true;
							changed = true;
						}
					}
				}
			}
			for (int x = LO; x <= HI; x++) {
				for (int z = LO; z <= HI; z++) {
					this.fill[at(x, z)] = (byte) (rock[at(x, z)] ? CLEAR : 0);
				}
			}
		}

		private boolean inOpenSquare(final boolean[] rock, final int x, final int z) {
			for (int dx = -1; dx <= 0; dx++) {
				for (int dz = -1; dz <= 0; dz++) {
					int lx = x + dx;
					int lz = z + dz;
					if (inside(lx, lz) && inside(lx + 1, lz + 1) && !rock[at(lx, lz)] && !rock[at(lx + 1, lz)] && !rock[at(lx, lz + 1)] && !rock[at(lx + 1, lz + 1)]) {
						return true;
					}
				}
			}
			return false;
		}

		/** Hills, then broken towers, ruined houses and wall runs on the level ground between them. */
		private void field() {
			int[] height = new int[S * S];
			int bumps = 6 + this.random.nextInt(5);
			for (int b = 0; b < bumps; b++) {
				int cx = LO + this.random.nextInt(HI - LO + 1);
				int cz = LO + this.random.nextInt(HI - LO + 1);
				int radius = 5 + this.random.nextInt(5);
				int peak = 1 + this.random.nextInt(RISE);
				for (int x = cx - radius; x <= cx + radius; x++) {
					for (int z = cz - radius; z <= cz + radius; z++) {
						if (inside(x, z)) {
							int v = (int) Math.round(peak * (1.0 - Math.hypot(x - cx, z - cz) / radius));
							height[at(x, z)] = Math.max(height[at(x, z)], v);
						}
					}
				}
			}
			for (int x = LO; x <= HI; x++) {
				for (int z = LO; z <= HI; z++) {
					this.fill[at(x, z)] = (byte) height[at(x, z)];
					this.earth[at(x, z)] = height[at(x, z)] > 0;
				}
			}
			int pieces = 7 + this.random.nextInt(5);
			for (int i = 0; i < pieces; i++) {
				boolean placed = false;
				for (int tries = 0; tries < 40 && !placed; tries++) {
					placed = cover(i % 3);
				}
			}
		}

		/** One try at a piece of cover; whether it found room. */
		private boolean cover(final int kind) {
			int width;
			int depth;
			switch (kind) {
				case 0 -> {
					width = 7;
					depth = 7;
				}
				case 1 -> {
					width = 6 + this.random.nextInt(3);
					depth = 7 + this.random.nextInt(3);
				}
				default -> {
					boolean alongX = this.random.nextBoolean();
					int length = 9 + this.random.nextInt(8);
					width = alongX ? length : 2;
					depth = alongX ? 2 : length;
				}
			}
			int x0 = LO + 1 + this.random.nextInt(Math.max(1, HI - LO - width - 1));
			int z0 = LO + 1 + this.random.nextInt(Math.max(1, HI - LO - depth - 1));
			if (x0 + width - 1 > HI - 1 || z0 + depth - 1 > HI - 1 || taken(x0 - 3, z0 - 3, x0 + width + 2, z0 + depth + 2)) {
				return false;
			}
			for (int x = x0; x < x0 + width; x++) {
				for (int z = z0; z < z0 + depth; z++) {
					this.claimed[at(x, z)] = true;
					carve(x, z);
				}
			}
			switch (kind) {
				case 0 -> tower(x0, z0);
				case 1 -> house(x0, z0, width, depth);
				default -> run(x0, z0, width, depth);
			}
			return true;
		}

		/** A broken tower: a ring two thick, seven blocks across, with a gap for a door. */
		private void tower(final int x0, final int z0) {
			// The first tower of a field reaches the ceiling, as the tower's tallest cover does.
			int base = this.towers++ == 0 ? CLEAR + 2 : 7 + this.random.nextInt(CLEAR - 7);
			for (int dx = 0; dx < 7; dx++) {
				for (int dz = 0; dz < 7; dz++) {
					boolean ring = dx < 2 || dx > 4 || dz < 2 || dz > 4;
					if (ring) {
						wall(x0 + dx, z0 + dz, base - this.random.nextInt(3));
					}
				}
			}
			door(x0, z0, 7, 7, 2);
		}

		/** A ruined house: a low ring one thick with a doorway and a gap broken out of it. */
		private void house(final int x0, final int z0, final int width, final int depth) {
			for (int dx = 0; dx < width; dx++) {
				for (int dz = 0; dz < depth; dz++) {
					if (dx == 0 || dz == 0 || dx == width - 1 || dz == depth - 1) {
						wall(x0 + dx, z0 + dz, 4 + this.random.nextInt(2));
					}
				}
			}
			door(x0, z0, width, depth, 1);
			door(x0, z0, width, depth, 1);
		}

		/** A run of broken wall, two thick, with a gap. */
		private void run(final int x0, final int z0, final int width, final int depth) {
			boolean alongX = width > depth;
			int length = alongX ? width : depth;
			int gap = 2 + this.random.nextInt(length - 4);
			for (int i = 0; i < length; i++) {
				boolean broken = i == gap || i == gap + 1;
				for (int w = 0; w < 2; w++) {
					int x = alongX ? x0 + i : x0 + w;
					int z = alongX ? z0 + w : z0 + i;
					if (broken) {
						carve(x, z);
					} else {
						wall(x, z, 4 + this.random.nextInt(4));
					}
				}
			}
		}

		/** Breaks a doorway two wide through a ring's wall of the given thickness, on a random side. */
		private void door(final int x0, final int z0, final int width, final int depth, final int thickness) {
			int side = this.random.nextInt(4);
			for (int t = 0; t < thickness; t++) {
				for (int w = 0; w < 2; w++) {
					int x;
					int z;
					switch (side) {
						case 0 -> {
							x = x0 + t;
							z = z0 + depth / 2 - 1 + w;
						}
						case 1 -> {
							x = x0 + width - 1 - t;
							z = z0 + depth / 2 - 1 + w;
						}
						case 2 -> {
							x = x0 + width / 2 - 1 + w;
							z = z0 + t;
						}
						default -> {
							x = x0 + width / 2 - 1 + w;
							z = z0 + depth - 1 - t;
						}
					}
					carve(x, z);
				}
			}
		}

		/**
		 * The great hall: pillars in a ring that reach the ceiling, a gallery round them with chapels along its two
		 * long walls, and low pews across the nave. The portal stands in the middle, in its reserved pocket.
		 */
		private void hall() {
			int[] places = {10, 17, 24, 31, 38};
			for (int p : places) {
				pillar(p, places[0]);
				pillar(p, places[4]);
				if (p != places[0] && p != places[4]) {
					pillar(places[0], p);
					pillar(places[4], p);
				}
			}
			int chapels = 2 + this.random.nextInt(3);
			List<int[]> slots = new ArrayList<>();
			for (int x0 : new int[] {4, 14, 24, 34}) {
				slots.add(new int[] {x0, 1});
				slots.add(new int[] {x0, 0});
			}
			java.util.Collections.shuffle(slots, this.random);
			int built = 0;
			for (int[] slot : slots) {
				if (built < chapels && chapel(slot[0], slot[1] == 1)) {
					built++;
				}
			}
			for (int x : new int[] {13, 16, 32, 35}) {
				for (int z = 13; z <= 36; z++) {
					boolean aisle = z >= 22 && z <= 26;
					if (!aisle && !taken(x, z, x, z)) {
						wall(x, z, 1);
						this.claimed[at(x, z)] = true;
					}
				}
			}
		}

		private void pillar(final int x, final int z) {
			if (taken(x - 1, z - 1, x + 2, z + 2)) {
				return;
			}
			for (int dx = 0; dx < 2; dx++) {
				for (int dz = 0; dz < 2; dz++) {
					wall(x + dx, z + dz, CLEAR);
					this.claimed[at(x + dx, z + dz)] = true;
				}
			}
		}

		/** A chapel against the north or south wall: 9 wide, 6 deep, walls two thick, a doorway two wide onto the gallery. */
		private boolean chapel(final int x0, final boolean north) {
			int z0 = north ? LO : HI - 5;
			if (taken(x0 - 1, z0 - 1, x0 + 9, z0 + 6)) {
				return false;
			}
			for (int dx = 0; dx < 9; dx++) {
				for (int dz = 0; dz < 6; dz++) {
					boolean side = dx < 2 || dx > 6;
					boolean front = north ? dz >= 4 : dz < 2;
					this.claimed[at(x0 + dx, z0 + dz)] = true;
					if (side || front) {
						boolean doorway = !side && dx >= 3 && dx <= 4;
						if (doorway) {
							carve(x0 + dx, z0 + dz);
						} else {
							wall(x0 + dx, z0 + dz, CLEAR);
						}
					}
				}
			}
			return true;
		}

		// ---------------------------------------------------------------- joining

		/** Keeps the reserved columns open and level. */
		void applyReserved() {
			for (int x = LO; x <= HI; x++) {
				for (int z = LO; z <= HI; z++) {
					if (this.open[at(x, z)]) {
						carve(x, z);
					}
					if (this.locked[at(x, z)]) {
						this.fill[at(x, z)] = 0;
					}
				}
			}
		}

		/** Lowers every earth column to within a block of its walking neighbours, and bares columns that fall to nothing. */
		void relax() {
			boolean changed = true;
			while (changed) {
				changed = false;
				for (int x = LO; x <= HI; x++) {
					for (int z = LO; z <= HI; z++) {
						int i = at(x, z);
						if (!this.earth[i] || this.locked[i]) {
							continue;
						}
						for (int[] way : WAYS) {
							int nx = x + way[0];
							int nz = z + way[1];
							if (walk(nx, nz) && this.fill[i] > this.fill[at(nx, nz)] + 1) {
								this.fill[i] = (byte) (this.fill[at(nx, nz)] + 1);
								changed = true;
							}
						}
					}
				}
			}
			for (int i = 0; i < S * S; i++) {
				if (this.fill[i] == 0) {
					this.earth[i] = false;
				}
			}
		}

		private int[] label() {
			int[] comp = new int[S * S];
			Arrays.fill(comp, -1);
			int next = 0;
			ArrayDeque<int[]> queue = new ArrayDeque<>();
			for (int x = LO; x <= HI; x++) {
				for (int z = LO; z <= HI; z++) {
					if (!walk(x, z) || comp[at(x, z)] >= 0) {
						continue;
					}
					comp[at(x, z)] = next;
					queue.add(new int[] {x, z});
					while (!queue.isEmpty()) {
						int[] here = queue.poll();
						for (int[] way : WAYS) {
							int nx = here[0] + way[0];
							int nz = here[1] + way[1];
							if (step(here[0], here[1], nx, nz) && comp[at(nx, nz)] < 0) {
								comp[at(nx, nz)] = next;
								queue.add(new int[] {nx, nz});
							}
						}
					}
					next++;
				}
			}
			return comp;
		}

		/**
		 * Joins every part of the floor a body can stand on into the anchor's: a small part holding nothing the plan
		 * needs is filled, any other is joined by the shortest tunnel, three wide.
		 */
		void repair() {
			int[] anchor = this.keys.get(0);
			carve(anchor[0], anchor[1]);
			for (int round = 0; round < 200; round++) {
				int[] comp = label();
				int main = comp[at(anchor[0], anchor[1])];
				int count = 0;
				for (int c : comp) {
					count = Math.max(count, c + 1);
				}
				int[] size = new int[count];
				boolean[] must = new boolean[count];
				for (int x = LO; x <= HI; x++) {
					for (int z = LO; z <= HI; z++) {
						int c = comp[at(x, z)];
						if (c >= 0) {
							size[c]++;
							must[c] |= this.open[at(x, z)];
						}
					}
				}
				for (int[] key : this.keys) {
					int c = comp[at(key[0], key[1])];
					if (c >= 0) {
						must[c] = true;
					}
				}
				boolean changed = false;
				boolean tunnelled = false;
				for (int c = 0; c < count; c++) {
					if (c == main) {
						continue;
					}
					if (!must[c] && size[c] < SMALL) {
						for (int x = LO; x <= HI; x++) {
							for (int z = LO; z <= HI; z++) {
								if (comp[at(x, z)] == c) {
									wall(x, z, CLEAR);
								}
							}
						}
						changed = true;
					} else if (!tunnelled) {
						tunnel(comp, c, main);
						tunnelled = true;
						changed = true;
					}
				}
				if (!changed) {
					return;
				}
			}
		}

		/** Carves the shortest way from one part to the main one through anything but the border and locked columns. */
		private void tunnel(final int[] comp, final int from, final int to) {
			int[] came = new int[S * S];
			Arrays.fill(came, -2);
			ArrayDeque<int[]> queue = new ArrayDeque<>();
			for (int x = LO; x <= HI; x++) {
				for (int z = LO; z <= HI; z++) {
					if (comp[at(x, z)] == from) {
						came[at(x, z)] = -1;
						queue.add(new int[] {x, z});
					}
				}
			}
			while (!queue.isEmpty()) {
				int[] here = queue.poll();
				if (comp[at(here[0], here[1])] == to) {
					int x = here[0];
					int z = here[1];
					while (true) {
						for (int dx = -1; dx <= 1; dx++) {
							for (int dz = -1; dz <= 1; dz++) {
								carve(x + dx, z + dz);
							}
						}
						int back = came[at(x, z)];
						if (back < 0) {
							break;
						}
						x = back / S;
						z = back % S;
					}
					return;
				}
				for (int[] way : WAYS) {
					int nx = here[0] + way[0];
					int nz = here[1] + way[1];
					if (inside(nx, nz) && !this.locked[at(nx, nz)] && came[at(nx, nz)] == -2) {
						came[at(nx, nz)] = at(here[0], here[1]);
						queue.add(new int[] {nx, nz});
					}
				}
			}
		}

		/** Columns within a block of the shortest walks between the keys, in order. */
		boolean[] route() {
			boolean[] route = new boolean[S * S];
			for (int k = 0; k + 1 < this.keys.size(); k++) {
				int[] a = this.keys.get(k);
				int[] b = this.keys.get(k + 1);
				int[] came = new int[S * S];
				Arrays.fill(came, -2);
				ArrayDeque<int[]> queue = new ArrayDeque<>();
				came[at(a[0], a[1])] = -1;
				queue.add(a);
				boolean found = false;
				while (!queue.isEmpty() && !found) {
					int[] here = queue.poll();
					for (int[] way : WAYS) {
						int nx = here[0] + way[0];
						int nz = here[1] + way[1];
						if (step(here[0], here[1], nx, nz) && came[at(nx, nz)] == -2) {
							came[at(nx, nz)] = at(here[0], here[1]);
							queue.add(new int[] {nx, nz});
							found |= nx == b[0] && nz == b[1];
						}
					}
				}
				int x = b[0];
				int z = b[1];
				while (came[at(x, z)] != -2) {
					for (int dx = -1; dx <= 1; dx++) {
						for (int dz = -1; dz <= 1; dz++) {
							if (inside(x + dx, z + dz)) {
								route[at(x + dx, z + dz)] = true;
							}
						}
					}
					int back = came[at(x, z)];
					if (back < 0) {
						break;
					}
					x = back / S;
					z = back % S;
				}
			}
			return route;
		}

		/** Hangs stalactites and raises stalagmites in a cavern, off the routes and the reserved columns. */
		void roughen(final boolean[] route) {
			for (int x = LO; x <= HI; x++) {
				for (int z = LO; z <= HI; z++) {
					int i = at(x, z);
					if (this.fill[i] != 0 || route[i] || this.open[i] || this.hole[i] || this.locked[i]) {
						continue;
					}
					int roll = this.random.nextInt(100);
					if (roll < 14) {
						this.hang[i] = (byte) (1 + this.random.nextInt(4));
					} else if (roll < 18 && openAround(x, z)) {
						this.fill[i] = (byte) (4 + this.random.nextInt(5));
					}
				}
			}
		}

		private boolean openAround(final int x, final int z) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (!walk(x + dx, z + dz) || this.open[at(x + dx, z + dz)]) {
						return false;
					}
				}
			}
			return true;
		}
	}
}
