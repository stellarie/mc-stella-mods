package com.stella.tower;

import net.minecraft.core.Direction;

/**
 * The shape of a lost castle as pure geometry. Nothing here reads or writes a block, so the shape can be
 * proved on its own; {@link LostCastleBuilder} lays the castle to it.
 * <p>
 * A castle goes down into the ground. Its footprint is a {@value #SIZE} by {@value #SIZE} square inside an
 * outer wall {@value #OUTER} blocks thick. Each floor is a small tower floor, {@value #FLOOR_HEIGHT} blocks from
 * its floor to the floor above it, and {@link LostCastleLayout} draws what it holds. Floor 0 is the top floor,
 * just under the ground. A ring of ruins stands over the middle of the footprint, and a stair 2 wide goes down
 * from the ring's middle to floor 0.
 * <p>
 * Heights are in the piece's own frame, from the slab under the lowest floor, which is 0.
 */
public final class LostCastleGrid {
	public static final int SIZE = 50;
	/** The thickness of the outer wall. */
	public static final int OUTER = 2;
	/** The first and last column of a floor's inside, along either axis. */
	public static final int IN_LOW = OUTER;
	public static final int IN_HIGH = SIZE - OUTER - 1;
	/** From one floor's walking level to the next: a floor and the slab over it. */
	public static final int FLOOR_HEIGHT = 16;
	/** The open height of a floor, from the block over its slab to the block under the next slab. */
	public static final int CLEAR = FLOOR_HEIGHT - 1;
	/** The most a floor's ground may rise over its level before a column counts as a wall, not ground. */
	public static final int MAX_RISE = 3;
	public static final int MIN_FLOORS = 3;
	public static final int MAX_FLOORS = 6;
	/** A stair between floors is this wide, and its steps fall one block a column over this many columns. */
	public static final int STAIR_WIDTH = 2;
	public static final int STAIR_RUN = FLOOR_HEIGHT;
	/** A floor is split into this many zones along each axis, and each zone holds one spawner. */
	public static final int ZONES = 3;
	/** Ground over the top floor's ceiling, at the lowest ground under the footprint. */
	public static final int COVER = 2;
	/** The drop from the ring's floor to floor 0 at the least: flat ground. */
	public static final int MIN_DROP = CLEAR + COVER + 1;
	/** The most the ground may rise across the footprint; the entrance stair must land inside the top floor. */
	public static final int MAX_SLOPE = 3;
	public static final int MAX_DROP = MIN_DROP + MAX_SLOPE;
	/** The lower of the two middle columns: the ring's middle lies between it and the next. */
	public static final int MID = SIZE / 2 - 1;
	/** The row of the entrance stair's top step. */
	public static final int ENTRY_Z = MID + 1;
	/** The ring of ruins: how far it stands over the ring's floor, and the radii of its parts. */
	public static final int RING_HEIGHT = 8;
	public static final double ARENA_RADIUS = 6.5;
	public static final double BENCH_RADIUS = 8.0;
	public static final double WALL_RADIUS = 9.6;

	/** A face of a room, in the piece's own frame. */
	public enum Side {
		LOW_X(-1, 0),
		HIGH_X(1, 0),
		LOW_Z(0, -1),
		HIGH_Z(0, 1);

		private final int dx;
		private final int dz;

		Side(final int dx, final int dz) {
			this.dx = dx;
			this.dz = dz;
		}

		/** The step from the room's wall into the room, along x. */
		public int inX() {
			return -this.dx;
		}

		/** The step from the room's wall into the room, along z. */
		public int inZ() {
			return -this.dz;
		}

		public boolean alongX() {
			return this.dz != 0;
		}
	}

	private LostCastleGrid() {
	}

	/** The side whose step from the wall into the open is {@code (dx, dz)}. */
	public static Side facing(final int dx, final int dz) {
		for (Side side : Side.values()) {
			if (side.inX() == dx && side.inZ() == dz) {
				return side;
			}
		}
		throw new IllegalArgumentException("not a step: " + dx + "," + dz);
	}

	/** The first column of a zone along an axis. */
	public static int zoneLow(final int zone) {
		return IN_LOW + zone * zoneSpan();
	}

	/** The last column of a zone along an axis; the last zone takes the remainder. */
	public static int zoneHigh(final int zone) {
		return zone == ZONES - 1 ? IN_HIGH : zoneLow(zone) + zoneSpan() - 1;
	}

	private static int zoneSpan() {
		return (IN_HIGH - IN_LOW + 1) / ZONES;
	}

	/** The zone an axis column belongs to; the outer wall belongs to the nearest zone. */
	public static int zoneOf(final int at) {
		return Math.min(ZONES - 1, Math.max(0, (at - IN_LOW) / zoneSpan()));
	}

	/** The slab a floor stands on. Index 0 is the top floor. */
	public static int slabY(final int floors, final int index) {
		return (floors - 1 - index) * FLOOR_HEIGHT;
	}

	/** The y a body stands at on a floor. */
	public static int feet(final int floors, final int index) {
		return slabY(floors, index) + 1;
	}

	/** The last open block of a floor. */
	public static int head(final int floors, final int index) {
		return feet(floors, index) + CLEAR - 1;
	}

	/** The slab over the top floor. */
	public static int topY(final int floors) {
		return floors * FLOOR_HEIGHT;
	}

	/** The y a body stands at on the ring's floor. */
	public static int surface(final int floors, final int drop) {
		return feet(floors, 0) + drop;
	}

	/** The height of a castle's box. */
	public static int height(final int floors, final int drop) {
		return surface(floors, drop) + RING_HEIGHT;
	}

	/** The drop for ground that lies between two heights: the least drop that keeps the cover over the lowest ground. */
	public static int dropFor(final int lowestGround, final int highestGround) {
		return highestGround - lowestGround + MIN_DROP;
	}

	/** The y of the slab under the lowest floor, for ground whose lowest top block is at {@code lowestGround}. */
	public static int bottomFor(final int floors, final int lowestGround) {
		return lowestGround - COVER - topY(floors);
	}

	/** The direction a step names in the frame of a north-facing piece, where +z is north. */
	public static Direction label(final int dx, final int dz) {
		if (dz != 0) {
			return dz > 0 ? Direction.NORTH : Direction.SOUTH;
		}
		return dx > 0 ? Direction.EAST : Direction.WEST;
	}

	/** A packed column, for sets. */
	public static long key(final int x, final int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}
}
