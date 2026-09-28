package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
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
	/**
	 * The arrival spot and the next floor's portal sit across the room from each other, on its middle line;
	 * an elite or boss floor has its portal in the middle of its hall.
	 */
	public static final int ARRIVAL_X = 4;
	public static final int PORTAL_X = 60;
	public static final int MIDDLE_Z = 32;
	/** The portal frame: two blocks wide and three high inside, standing on the floor layer. */
	public static final int PORTAL_INNER_WIDTH = 2;
	public static final int PORTAL_INNER_HEIGHT = 3;

	private TowerLayout() {
	}

	/** The room's inner corner, at {@link #BASE_Y}. */
	public static BlockPos origin(final int floor) {
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

	/** Where a player lands on a floor, facing the portal across the room. */
	public static Vec3 arrival(final int floor) {
		BlockPos origin = origin(floor);
		return new Vec3(origin.getX() + ARRIVAL_X + 0.5, BASE_Y, origin.getZ() + MIDDLE_Z + 0.5);
	}

	/** The yaw that faces the next floor's portal from the arrival spot. */
	public static float arrivalYaw() {
		return -90.0F;
	}

	/** The portal's line across the room: at the far side, or in the middle of a hall. */
	public static int portalX(final int floor) {
		return TowerMaze.hall(floor) > 0 ? SIZE / 2 : PORTAL_X;
	}

	/** The portal frame's inside: the blocks the portal fills once the frame is lit. */
	public static List<BlockPos> portalInside(final int floor) {
		BlockPos origin = origin(floor);
		List<BlockPos> inside = new ArrayList<>();
		for (int dz = 0; dz < PORTAL_INNER_WIDTH; dz++) {
			for (int dy = 0; dy < PORTAL_INNER_HEIGHT; dy++) {
				inside.add(new BlockPos(origin.getX() + portalX(floor), BASE_Y + dy, origin.getZ() + MIDDLE_Z - 1 + dz));
			}
		}
		return inside;
	}

	/** The frame around {@link #portalInside(int)}, corners included. */
	public static List<BlockPos> portalFrame(final int floor) {
		BlockPos origin = origin(floor);
		int x = origin.getX() + portalX(floor);
		int zLow = origin.getZ() + MIDDLE_Z - 2;
		int zHigh = origin.getZ() + MIDDLE_Z + PORTAL_INNER_WIDTH - 1;
		int yLow = BASE_Y - 1;
		int yHigh = BASE_Y + PORTAL_INNER_HEIGHT;
		List<BlockPos> frame = new ArrayList<>();
		for (int z = zLow; z <= zHigh; z++) {
			for (int y = yLow; y <= yHigh; y++) {
				if (z == zLow || z == zHigh || y == yLow || y == yHigh) {
					frame.add(new BlockPos(x, y, z));
				}
			}
		}
		return frame;
	}
}
