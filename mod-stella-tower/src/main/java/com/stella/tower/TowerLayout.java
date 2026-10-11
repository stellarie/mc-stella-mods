package com.stella.tower;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Where each floor sits. Floors are laid out on a grid in the tower dimension, one cell each, because the
 * world is not tall enough to stack them. Floor {@code n} fills a {@link #SIZE} by {@link #SIZE} room,
 * {@link #HEIGHT} blocks high, whose inner corner is {@link #origin(int)}.
 */
public final class TowerLayout {
	/** The base room's inside width and length: the size of a boss floor, a lost room and every floor of the older generators. */
	public static final int SIZE = 64;
	/** The most a biome floor's room is wide or deep inside its walls ({@link TowerSizes}). */
	public static final int MAX_SIZE = 128;
	/** The room's inside height of the plans drawn before the biome floors, and of a lost room. */
	public static final int HEIGHT = 24;
	/** The room's inside height of a biome floor, and so the most any floor reaches. */
	public static final int ROOM_HEIGHT = 32;
	/** The lowest block of a biome floor, below the base: its bedrock. */
	public static final int ROOM_LOW = -6;
	/** One grid cell: the largest room, its walls, and a gap of a chunk and more, so two floors never share a chunk. */
	public static final int CELL = 192;
	/** Floors per grid row: floor numbers in the tens of millions still lie inside the world's limit of thirty million blocks. */
	public static final int COLUMNS = 384;
	/** The y of the lowest air block in every room. */
	public static final int BASE_Y = 64;
	/** The portal frame: two blocks wide and three high inside, standing on the floor layer. */
	public static final int PORTAL_INNER_WIDTH = 2;
	public static final int PORTAL_INNER_HEIGHT = 3;

	private TowerLayout() {
	}

	/**
	 * The room's inner corner, at {@link #BASE_Y}. A negative number is a lost room's instance
	 * ({@link LostRooms}): its cells lie west of the floors, where {@link #floorAt} sees nothing.
	 */
	public static BlockPos origin(final int floor) {
		if (floor < 0) {
			int slot = -floor - 1;
			return new BlockPos(-((slot % COLUMNS) + 1) * CELL, BASE_Y, (slot / COLUMNS) * CELL);
		}
		int index = floor - 1;
		return new BlockPos((index % COLUMNS) * CELL, BASE_Y, (index / COLUMNS) * CELL);
	}

	/**
	 * The floor whose room or walls hold this spot, or 0 for none. A spot is in a floor's cell up to the most a room is
	 * wide ({@link #MAX_SIZE}), whatever the floor's own size: a smaller room has only void past its walls.
	 */
	public static int floorAt(final BlockPos pos) {
		if (pos.getX() < -1 || pos.getZ() < -1) {
			return 0;
		}
		int column = Math.floorDiv(pos.getX() + 1, CELL);
		int row = Math.floorDiv(pos.getZ() + 1, CELL);
		if (column >= COLUMNS) {
			return 0;
		}
		int floor = row * COLUMNS + column + 1;
		BlockPos origin = origin(floor);
		boolean inside = pos.getX() >= origin.getX() - 1 && pos.getX() <= origin.getX() + MAX_SIZE
			&& pos.getZ() >= origin.getZ() - 1 && pos.getZ() <= origin.getZ() + MAX_SIZE
			&& pos.getY() >= BASE_Y + ROOM_LOW && pos.getY() <= BASE_Y + ROOM_HEIGHT;
		return inside ? floor : 0;
	}

	/** The floor or the lost room whose room or walls hold this spot: positive, negative, or 0 for none. */
	public static int roomAt(final BlockPos pos) {
		int floor = floorAt(pos);
		if (floor != 0 || pos.getX() >= -1 || pos.getZ() < -1) {
			return floor;
		}
		int column = -Math.floorDiv(pos.getX() + 1, CELL) - 1;
		int row = Math.floorDiv(pos.getZ() + 1, CELL);
		if (column >= COLUMNS) {
			return 0;
		}
		int id = -(row * COLUMNS + column + 1);
		BlockPos origin = origin(id);
		boolean inside = pos.getX() >= origin.getX() - 1 && pos.getX() <= origin.getX() + MAX_SIZE
			&& pos.getZ() >= origin.getZ() - 1 && pos.getZ() <= origin.getZ() + MAX_SIZE
			&& pos.getY() >= BASE_Y + ROOM_LOW && pos.getY() <= BASE_Y + ROOM_HEIGHT;
		return inside ? id : 0;
	}

	/**
	 * Where a player lands on a floor, as the floor was built: its {@link TowerPlan}'s arrival. The spot
	 * moves from floor to floor, so every reader asks the plan, and the plan needs the tower's seed.
	 */
	public static Vec3 arrival(final ServerLevel tower, final int floor) {
		return TowerPlan.at(tower, floor).arrivalPos();
	}

	/** The yaw that faces the next floor's portal from the arrival spot. */
	public static float arrivalYaw(final ServerLevel tower, final int floor) {
		return TowerPlan.at(tower, floor).arrivalYaw();
	}

	/** The portal's line across the room: the column its frame stands in. */
	public static int portalX(final ServerLevel tower, final int floor) {
		return TowerPlan.at(tower, floor).portalX();
	}

	/** The portal frame's inside: the blocks the portal fills once the frame is lit. */
	public static List<BlockPos> portalInside(final ServerLevel tower, final int floor) {
		return TowerPlan.at(tower, floor).portalInside();
	}

	/** The frame around {@link #portalInside(ServerLevel, int)}, corners included. */
	public static List<BlockPos> portalFrame(final ServerLevel tower, final int floor) {
		return TowerPlan.at(tower, floor).portalFrame();
	}
}
