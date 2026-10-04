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
	/** The room's inside width and length. */
	public static final int SIZE = 64;
	/** The room's inside height. */
	public static final int HEIGHT = 8;
	/** One grid cell: the room, its walls, and a gap, so two floors never share a chunk. */
	public static final int CELL = 128;
	/** Floors per grid row. */
	public static final int COLUMNS = 256;
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

	/** The floor whose room or walls hold this spot, or 0 for none. */
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
		boolean inside = pos.getX() >= origin.getX() - 1 && pos.getX() <= origin.getX() + SIZE
			&& pos.getZ() >= origin.getZ() - 1 && pos.getZ() <= origin.getZ() + SIZE
			&& pos.getY() >= BASE_Y - 2 && pos.getY() <= BASE_Y + HEIGHT;
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
		boolean inside = pos.getX() >= origin.getX() - 1 && pos.getX() <= origin.getX() + SIZE
			&& pos.getZ() >= origin.getZ() - 1 && pos.getZ() <= origin.getZ() + SIZE
			&& pos.getY() >= BASE_Y - 2 && pos.getY() <= BASE_Y + HEIGHT;
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
