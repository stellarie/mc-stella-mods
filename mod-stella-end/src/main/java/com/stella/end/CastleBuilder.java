package com.stella.end;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Half;

/** Writes a castle plan into a level as blocks. The only code that places castle blocks. */
public final class CastleBuilder {
	/** Bulk placement skips neighbour shape updates: the plan already holds every shape it wants. */
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	private static final int SIZE = EndCastle.SIZE;
	private static final int TOP = EndCastle.FLOOR_HEIGHT - 1;
	/** A light every this many cells, and in every room at least one. */
	private static final int LIGHT_SPACING = 8;

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState BRICKS = Blocks.END_STONE_BRICKS.defaultBlockState();
	private static final BlockState PURPUR = Blocks.PURPUR_BLOCK.defaultBlockState();
	private static final BlockState PILLAR = Blocks.PURPUR_PILLAR.defaultBlockState();
	private static final BlockState LIGHT = Blocks.SEA_LANTERN.defaultBlockState();
	private static final BlockState BARRIER = Blocks.BARRIER.defaultBlockState();
	private static final BlockState PARAPET = Blocks.END_STONE_BRICK_WALL.defaultBlockState();

	private CastleBuilder() {
	}

	/** Clears the castle's volume at {@code min}, the footprint corner at the base of floor 1, and builds the plan there. */
	public static void build(final ServerLevel level, final CastleLayout layout, final BlockPos min) {
		for (int cx = min.getX() >> 4; cx <= (min.getX() + SIZE - 1) >> 4; cx++) {
			for (int cz = min.getZ() >> 4; cz <= (min.getZ() + SIZE - 1) >> 4; cz++) {
				level.getChunk(cx, cz);
			}
		}
		for (int y = EndCastle.BASE_Y; y <= EndCastle.ROOF_Y + 3; y++) {
			for (int x = 0; x < SIZE; x++) {
				for (int z = 0; z < SIZE; z++) {
					put(level, min, x, y, z, AIR);
				}
			}
		}
		for (int y = EndCastle.BASE_Y; y <= EndCastle.ROOF_Y; y += EndCastle.FLOOR_HEIGHT) {
			slab(level, min, y);
		}
		for (int floor = 1; floor <= EndCastle.FLOORS; floor++) {
			walls(level, min, layout.floor(floor));
		}
		perimeter(level, min, EndCastle.REST_Y);
		restRoom(level, min, layout.rest());
		for (int floor = 1; floor <= EndCastle.FLOORS; floor++) {
			shaft(level, min, layout.floor(floor).up(), EndCastle.slabY(floor));
		}
		shaft(level, min, layout.rest().up(), EndCastle.REST_Y);
		furnish(level, min, layout.rest());
		roof(level, min);
		for (int floor = 1; floor <= EndCastle.FLOORS; floor++) {
			lights(level, min, layout.floor(floor));
		}
		for (int[] cell : new int[][] {{5, 5}, {4, 9}, {9, 4}}) {
			put(level, min, cell[0], EndCastle.REST_Y, cell[1], LIGHT);
		}
		for (int[] cell : new int[][] {{11, 11}, {11, 21}, {21, 11}, {21, 21}}) {
			put(level, min, cell[0], EndCastle.ROOF_Y, cell[1], LIGHT);
		}
		CastleTreasure.place(level, layout, min);
	}

	/** Shuts or opens the barrier door that bars the way out of floor 6. */
	public static void setExitDoor(final ServerLevel level, final CastleLayout layout, final BlockPos min, final boolean open) {
		int slab = EndCastle.slabY(EndCastle.FLOORS);
		for (CastleLayout.Door door : layout.floor(EndCastle.FLOORS).doors()) {
			if (!door.barrier()) {
				continue;
			}
			for (int x = door.x0(); x <= door.x1(); x++) {
				for (int z = door.z0(); z <= door.z1(); z++) {
					for (int y = slab + 1; y <= slab + CastleLayout.DOOR_HEIGHT; y++) {
						put(level, min, x, y, z, open ? AIR : BARRIER);
					}
				}
			}
		}
	}

	// -- Pieces ------------------------------------------------------------------------------------------------

	private static void slab(final ServerLevel level, final BlockPos min, final int y) {
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				put(level, min, x, y, z, BRICKS);
			}
		}
	}

	private static boolean onEdge(final int x, final int z) {
		return x == 0 || z == 0 || x == SIZE - 1 || z == SIZE - 1;
	}

	private static boolean onCorner(final int x, final int z) {
		return (x == 0 || x == SIZE - 1) && (z == 0 || z == SIZE - 1);
	}

	/** Every wall and doorway of a floor: the outer wall in brick, the dividing walls in purpur, a lintel over each door. */
	private static void walls(final ServerLevel level, final BlockPos min, final CastleLayout.FloorPlan plan) {
		int slab = EndCastle.slabY(plan.number());
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				byte kind = plan.kind(x, z);
				if (kind == CastleLayout.OPEN) {
					continue;
				}
				BlockState wall = onCorner(x, z) ? PILLAR : onEdge(x, z) ? BRICKS : PURPUR;
				int from = kind == CastleLayout.DOOR ? slab + CastleLayout.DOOR_HEIGHT + 1 : slab + 1;
				for (int y = from; y <= slab + TOP; y++) {
					put(level, min, x, y, z, wall);
				}
				if (kind == CastleLayout.DOOR && isBarrier(plan, x, z)) {
					for (int y = slab + 1; y <= slab + CastleLayout.DOOR_HEIGHT; y++) {
						put(level, min, x, y, z, BARRIER);
					}
				}
			}
		}
	}

	private static boolean isBarrier(final CastleLayout.FloorPlan plan, final int x, final int z) {
		return plan.doors().stream().anyMatch(door -> door.barrier() && door.contains(x, z));
	}

	/** The outer wall of a level that holds no floor plan of its own. */
	private static void perimeter(final ServerLevel level, final BlockPos min, final int slab) {
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				if (onEdge(x, z)) {
					for (int y = slab + 1; y <= slab + TOP; y++) {
						put(level, min, x, y, z, onCorner(x, z) ? PILLAR : BRICKS);
					}
				}
			}
		}
	}

	/** The rest room's two inner walls: its other two sides are the castle's own outer wall. */
	private static void restRoom(final ServerLevel level, final BlockPos min, final CastleLayout.RestPlan rest) {
		int last = EndCastle.REST_SIZE - 1;
		for (int i = 1; i <= last; i++) {
			for (int y = EndCastle.REST_Y + 1; y <= EndCastle.REST_Y + TOP; y++) {
				put(level, min, last, y, i, PURPUR);
				put(level, min, i, y, last, PURPUR);
			}
		}
	}

	/**
	 * A spiral stair of {@link CastleLayout.Shaft#STEPS} steps from a slab to the next. The ceiling over the last
	 * steps opens for a climber's head, and the top step takes the cell of the ceiling slab it climbs into.
	 */
	private static void shaft(final ServerLevel level, final BlockPos min, final CastleLayout.Shaft shaft, final int slab) {
		for (int k = 0; k < CastleLayout.Shaft.STEPS; k++) {
			if (shaft.overheadOpen(k)) {
				put(level, min, shaft.stepX(k), slab + EndCastle.FLOOR_HEIGHT, shaft.stepZ(k), AIR);
			}
		}
		for (int y = slab + 1; y <= slab + TOP; y++) {
			put(level, min, shaft.pillarX(), y, shaft.pillarZ(), PILLAR);
		}
		for (int k = 0; k < CastleLayout.Shaft.STEPS; k++) {
			BlockState step = Blocks.PURPUR_STAIRS.defaultBlockState()
				.setValue(StairBlock.FACING, shaft.stepFacing(k))
				.setValue(StairBlock.HALF, Half.BOTTOM);
			put(level, min, shaft.stepX(k), slab + 1 + k, shaft.stepZ(k), step);
		}
	}

	private static void furnish(final ServerLevel level, final BlockPos min, final CastleLayout.RestPlan rest) {
		int y = EndCastle.REST_Y + 1;
		BlockState bed = Blocks.BED.purple().defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
		put(level, min, rest.bedX(), y, rest.bedZ(), bed.setValue(BedBlock.PART, BedPart.FOOT));
		put(level, min, rest.bedX(), y, rest.bedZ() + 1, bed.setValue(BedBlock.PART, BedPart.HEAD));
		put(level, min, rest.tableX(), y, rest.tableZ(), Blocks.CRAFTING_TABLE.defaultBlockState());
		put(level, min, rest.anvilX(), y, rest.anvilZ(), Blocks.ANVIL.defaultBlockState());
		put(level, min, rest.chestX(), y, rest.chestZ(), Blocks.ENDER_CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.EnderChestBlock.FACING, Direction.WEST));
	}

	/** The rooftop: an open deck with a low parapet all round. The exit portal belongs to its centre. */
	private static void roof(final ServerLevel level, final BlockPos min) {
		int y = EndCastle.ROOF_Y + 1;
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				if (onEdge(x, z)) {
					level.setBlock(new BlockPos(min.getX() + x, y, min.getZ() + z), PARAPET, Block.UPDATE_ALL);
				}
			}
		}
	}

	/**
	 * Sea lanterns set into the floor slab: a grid of them, and one in any room the grid misses. They sit in the floor,
	 * not the high ceiling, so the light reaches the ground where players and mobs walk.
	 */
	private static void lights(final ServerLevel level, final BlockPos min, final CastleLayout.FloorPlan plan) {
		int slab = EndCastle.slabY(plan.number());
		for (int[] cell : lightCells(plan)) {
			put(level, min, cell[0], slab, cell[1], LIGHT);
		}
	}

	static List<int[]> lightCells(final CastleLayout.FloorPlan plan) {
		List<int[]> lights = new ArrayList<>();
		int[] lit = new int[plan.roomCount() + 1];
		for (int x = 3; x < SIZE; x += LIGHT_SPACING) {
			for (int z = 3; z < SIZE; z += LIGHT_SPACING) {
				if (lightable(plan, x, z)) {
					lights.add(new int[] {x, z});
					lit[plan.room(x, z)]++;
				}
			}
		}
		for (int room = 1; room <= plan.roomCount(); room++) {
			if (lit[room] > 0) {
				continue;
			}
			long sumX = 0;
			long sumZ = 0;
			int count = 0;
			for (int x = 0; x < SIZE; x++) {
				for (int z = 0; z < SIZE; z++) {
					if (plan.room(x, z) == room) {
						sumX += x;
						sumZ += z;
						count++;
					}
				}
			}
			if (count == 0) {
				continue;
			}
			double centreX = (double) sumX / count;
			double centreZ = (double) sumZ / count;
			int[] best = null;
			double bestDistance = Double.MAX_VALUE;
			for (int x = 0; x < SIZE; x++) {
				for (int z = 0; z < SIZE; z++) {
					double distance = Math.hypot(x - centreX, z - centreZ);
					if (plan.room(x, z) == room && lightable(plan, x, z) && distance < bestDistance) {
						best = new int[] {x, z};
						bestDistance = distance;
					}
				}
			}
			if (best != null) {
				lights.add(best);
			}
		}
		return lights;
	}

	/** A floor cell may hold a light when it lies in an open room and no stairwell climbs through it. */
	private static boolean lightable(final CastleLayout.FloorPlan plan, final int x, final int z) {
		return plan.kind(x, z) == CastleLayout.OPEN && plan.room(x, z) > 0
			&& !(plan.up() != null && plan.up().contains(x, z)) && !(plan.arrival() != null && plan.arrival().contains(x, z));
	}

	private static void put(final ServerLevel level, final BlockPos min, final int x, final int y, final int z, final BlockState state) {
		level.setBlock(new BlockPos(min.getX() + x, y, min.getZ() + z), state, FLAGS);
	}
}
