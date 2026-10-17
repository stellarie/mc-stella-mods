package com.stella.end;

import net.minecraft.core.BlockPos;

/**
 * Where the castle stands. The keep is 32 by 32 and centred on the End origin, inside a 48 by 48 outer zone. Every
 * height derives from {@link #BASE_Y}, so a later slice moves nothing by editing one number.
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
	/** The outer zone round the keep: yard, gate, stall and camp. It is 8 blocks wider than the keep on every side. */
	public static final int ZONE_SIZE = 48;
	public static final int ZONE_MARGIN = (ZONE_SIZE - SIZE) / 2;
	public static final int ZONE_MIN_X = MIN_X - ZONE_MARGIN;
	public static final int ZONE_MIN_Z = MIN_Z - ZONE_MARGIN;
	/** The rooftop disc is this many blocks from its middle block to its rim; 46 across. */
	public static final int DISC_RADIUS = 23;
	/** The tallest tower stands this far over the deck; nothing of the castle rises higher. */
	public static final int TOWER_HEIGHT = 14;
	/** The foundation reaches down at most this far when no island lies under the zone. */
	public static final int MAX_FOUNDATION = 80;

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

	/** Whether a block lies in the castle's volume: the whole outer zone, from the ground to the top of the towers. */
	public static boolean volumeContains(final BlockPos pos) {
		return pos.getX() >= ZONE_MIN_X && pos.getX() < ZONE_MIN_X + ZONE_SIZE && pos.getZ() >= ZONE_MIN_Z && pos.getZ() < ZONE_MIN_Z + ZONE_SIZE
			&& pos.getY() >= BASE_Y && pos.getY() <= ROOF_Y + TOWER_HEIGHT;
	}

	/** The floor a height lies in, 1 to {@link #FLOORS}, or 0 above or below the six floors. */
	public static int floorAt(final double y) {
		int floor = (int) Math.floor((y - BASE_Y) / FLOOR_HEIGHT) + 1;
		return floor < 1 || floor > FLOORS ? 0 : floor;
	}

	/** Whether a block column of the world lies in the rooftop disc, which is centred on the End origin block. */
	public static boolean inDisc(final int x, final int z) {
		return x * x + z * z <= DISC_RADIUS * DISC_RADIUS + DISC_RADIUS;
	}
}
