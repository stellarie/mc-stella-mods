package com.stella.ruins.site.deepforge;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;

/**
 * Fixed numbers of the forge, all in the site's local frame. Local north is -z, local x runs east and local y runs
 * up from the floor of the box. The hall stands in the middle. Seven slots ring it, and each slot holds one chamber
 * per level at most.
 */
final class Geo {
	static final int SIZE_X = 64;
	static final int SIZE_Y = 40;
	static final int SIZE_Z = 64;

	/** The hall's outer wall, inclusive, and its inside. */
	static final int HALL_MIN = 18;
	static final int HALL_MAX = 44;
	static final int IN_MIN = 19;
	static final int IN_MAX = 43;
	static final int CENTER = 31;
	/** The hall's roof block. The rock cap runs from here to the top of the box. */
	static final int ROOF = 36;
	static final int TOP = SIZE_Y - 1;

	/** The stair tower fills the north-east corner of the hall. */
	static final int TOWER_X0 = 37;
	static final int TOWER_X1 = 43;
	static final int TOWER_Z0 = 19;
	static final int TOWER_Z1 = 25;

	/** A chamber is eleven deep and thirteen wide in its outer wall, six high inside. */
	static final int CHAMBER_DEPTH = 10;
	static final int CHAMBER_HALF_WIDTH = 6;
	static final int CHAMBER_HEIGHT = 6;
	static final int TUNNEL_HEIGHT = 4;

	private Geo() {
	}

	/** One of the three floors. The value is the y of the floor block. */
	enum Level {
		A(28),
		B(17),
		C(6);

		final int floor;

		Level(final int floor) {
			this.floor = floor;
		}

		/** The offsets a chamber floor may take against the level floor. */
		int[] offsets() {
			return switch (this) {
				case A -> new int[] {-3, 0};
				case B -> new int[] {-3, 0, 3};
				case C -> new int[] {0, 3};
			};
		}
	}

	static boolean inHall(final int x, final int z) {
		return x >= IN_MIN && x <= IN_MAX && z >= IN_MIN && z <= IN_MAX;
	}

	static boolean inTower(final int x, final int z) {
		return x >= TOWER_X0 && x <= TOWER_X1 && z >= TOWER_Z0 && z <= TOWER_Z1;
	}

	/** True for a cell of a gallery ring: the three-wide margin of the hall, less the tower. */
	static boolean isGallery(final int x, final int z) {
		return inHall(x, z) && !inTower(x, z) && (x <= IN_MIN + 2 || x >= IN_MAX - 2 || z <= IN_MIN + 2 || z >= IN_MAX - 2);
	}

	/**
	 * A chamber slot. The door cell is the middle of the door wall. A chamber's own frame has {@code a} running
	 * from the door wall inward and {@code b} running sideways, so every template draws once and turns to any slot.
	 */
	enum Slot {
		NW(16, 9, Direction.EAST, new int[][] {{17, 9}, {22, 9}, {22, 17}}, new int[] {21, 19, 23, 21}),
		N(31, 13, Direction.SOUTH, new int[][] {{31, 14}, {31, 17}}, new int[] {30, 19, 32, 21}),
		W(13, 31, Direction.EAST, new int[][] {{14, 31}, {17, 31}}, new int[] {19, 30, 21, 32}),
		E(49, 31, Direction.WEST, new int[][] {{48, 31}, {45, 31}}, new int[] {41, 30, 43, 32}),
		SW(16, 53, Direction.EAST, new int[][] {{17, 53}, {22, 53}, {22, 45}}, new int[] {21, 41, 23, 43}),
		S(31, 49, Direction.NORTH, new int[][] {{31, 48}, {31, 45}}, new int[] {30, 41, 32, 43}),
		SE(46, 53, Direction.WEST, new int[][] {{45, 53}, {40, 53}, {40, 45}}, new int[] {39, 41, 41, 43});

		final int doorX;
		final int doorZ;
		/** The way the door faces, out of the chamber toward the hall. */
		final Direction door;
		final int[][] path;
		/** The hall cells the tunnel opens onto: minX, minZ, maxX, maxZ. */
		final int[] ext;

		Slot(final int doorX, final int doorZ, final Direction door, final int[][] path, final int[] ext) {
			this.doorX = doorX;
			this.doorZ = doorZ;
			this.door = door;
			this.path = path;
			this.ext = ext;
		}

		/** Local x and z of the chamber cell at depth {@code a} and side {@code b}. */
		int[] cell(final int a, final int b) {
			return switch (this.door) {
				case EAST -> new int[] {this.doorX - a, this.doorZ + b};
				case WEST -> new int[] {this.doorX + a, this.doorZ + b};
				case SOUTH -> new int[] {this.doorX + b, this.doorZ - a};
				default -> new int[] {this.doorX + b, this.doorZ + a};
			};
		}

		/** The box of a chamber-frame rectangle as minX, minZ, maxX, maxZ. */
		int[] rect(final int a0, final int b0, final int a1, final int b1) {
			int[] p = cell(a0, b0);
			int[] q = cell(a1, b1);
			return new int[] {Math.min(p[0], q[0]), Math.min(p[1], q[1]), Math.max(p[0], q[0]), Math.max(p[1], q[1])};
		}

		/** The way the chamber extends, from its door wall to its back wall. */
		Direction back() {
			return this.door.getOpposite();
		}

		/** The way {@code b} grows. */
		Direction lateral() {
			return this.door == Direction.EAST || this.door == Direction.WEST ? Direction.SOUTH : Direction.EAST;
		}

		/** Centre cells of the tunnel from the chamber's door to the hall, with one cell more through the hall wall. */
		List<int[]> tunnelCells() {
			List<int[]> cells = new ArrayList<>();
			cells.add(new int[] {this.path[0][0], this.path[0][1]});
			for (int i = 1; i < this.path.length; i++) {
				int dx = Integer.signum(this.path[i][0] - this.path[i - 1][0]);
				int dz = Integer.signum(this.path[i][1] - this.path[i - 1][1]);
				int x = this.path[i - 1][0];
				int z = this.path[i - 1][1];
				while (x != this.path[i][0] || z != this.path[i][1]) {
					x += dx;
					z += dz;
					cells.add(new int[] {x, z});
				}
			}
			int[] last = this.path[this.path.length - 1];
			int[] before = this.path[this.path.length - 2];
			cells.add(new int[] {last[0] + Integer.signum(last[0] - before[0]), last[1] + Integer.signum(last[1] - before[1])});
			return cells;
		}

		/** The way the first leg of the tunnel runs, from the chamber toward the hall. */
		Direction tunnelStart() {
			int dx = Integer.signum(this.path[1][0] - this.path[0][0]);
			int dz = Integer.signum(this.path[1][1] - this.path[0][1]);
			return dirOf(dx, dz);
		}

		/** The way the last leg runs, into the hall wall. */
		Direction tunnelEnd() {
			int[] last = this.path[this.path.length - 1];
			int[] before = this.path[this.path.length - 2];
			return dirOf(Integer.signum(last[0] - before[0]), Integer.signum(last[1] - before[1]));
		}
	}

	/** The horizontal direction of a unit step in x or z. */
	static Direction dirOf(final int dx, final int dz) {
		if (dx > 0) {
			return Direction.EAST;
		}
		if (dx < 0) {
			return Direction.WEST;
		}
		return dz > 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/** The 24 cells of the stair tower's ring, in walking order from the top. */
	static int[][] ringCells() {
		int[][] cells = new int[24][];
		int i = 0;
		for (int x = TOWER_X0; x <= TOWER_X1; x++) {
			cells[i++] = new int[] {x, TOWER_Z0};
		}
		for (int z = TOWER_Z0 + 1; z <= TOWER_Z1; z++) {
			cells[i++] = new int[] {TOWER_X1, z};
		}
		for (int x = TOWER_X1 - 1; x >= TOWER_X0; x--) {
			cells[i++] = new int[] {x, TOWER_Z1};
		}
		for (int z = TOWER_Z1 - 1; z > TOWER_Z0; z--) {
			cells[i++] = new int[] {TOWER_X0, z};
		}
		return cells;
	}

	/** The y of ring cell {@code i}: a stair block, or the floor block of the landing. */
	static int ringY(final int i) {
		if (i <= 10) {
			return 28 - i;
		}
		if (i <= 12) {
			return 17;
		}
		return 30 - i;
	}

	/** True for the two flat cells of the landing. */
	static boolean ringIsLanding(final int i) {
		return i == 11 || i == 12;
	}
}
