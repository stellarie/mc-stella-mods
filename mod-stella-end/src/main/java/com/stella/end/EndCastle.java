package com.stella.end;

import net.minecraft.core.BlockPos;

/**
 * Where the castle stands. The footprint is 32 by 32 and centred on the End origin. Every height derives from
 * {@link #BASE_Y}, so a later slice moves nothing by editing one number.
 */
public final class EndCastle {
	public static final int FLOORS = 6;
	public static final int SIZE = 32;
	public static final int FLOOR_HEIGHT = 16;
	public static final int BASE_Y = 64;
	public static final int MIN_X = -16;
	public static final int MIN_Z = -16;
	public static final int REST_Y = BASE_Y + FLOOR_HEIGHT * FLOORS;
	public static final int ROOF_Y = REST_Y + FLOOR_HEIGHT;
	public static final int REST_SIZE = 12;

	private EndCastle() {
	}

	/** The block layer of a floor's slab: the floor under the feet of anyone on it. */
	public static int slabY(final int floor) {
		return BASE_Y + FLOOR_HEIGHT * (floor - 1);
	}

	public static int standY(final int floor) {
		return slabY(floor) + 1;
	}

	public static BlockPos min() {
		return new BlockPos(MIN_X, BASE_Y, MIN_Z);
	}

	/** Where the boss of floor 6 stands: the arena's centre, over the End origin. */
	public static BlockPos arenaAnchor() {
		return new BlockPos(0, standY(FLOORS), 0);
	}

	/** Whether a block lies in the castle's volume: the six floors, the rest level and the deck with its parapet. */
	public static boolean volumeContains(final BlockPos pos) {
		return pos.getX() >= MIN_X && pos.getX() < MIN_X + SIZE && pos.getZ() >= MIN_Z && pos.getZ() < MIN_Z + SIZE
			&& pos.getY() >= BASE_Y && pos.getY() <= ROOF_Y + 1;
	}

	/** The floor a height lies in, 1 to {@link #FLOORS}, or 0 above or below the six floors. */
	public static int floorAt(final double y) {
		int floor = (int) Math.floor((y - BASE_Y) / FLOOR_HEIGHT) + 1;
		return floor < 1 || floor > FLOORS ? 0 : floor;
	}
}
