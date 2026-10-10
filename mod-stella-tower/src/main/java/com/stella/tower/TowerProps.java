package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The things the dead left in a lost room: skulls, empty barrels, broken gear and bones on the floor at the foot of its
 * walls, and wall skulls and cobwebs high on them. The pass runs last, after the room's traps, stations and chests, and
 * reads the plan and the volume without changing either: a prop takes only a cell the plan leaves empty, and the room's
 * design, traps and levels stay as they were. The same room draws the same props whenever it is built.
 */
final class TowerProps {
	private static final long SALT = 0x50524F50L;
	/** The floor props and the wall props a room draws, fewest and most. */
	static final int FLOOR_MIN = 60;
	static final int FLOOR_SPREAD = 31;
	static final int WALL_MIN = 40;
	static final int WALL_SPREAD = 31;
	/** How near a prop may lie to a trap, a chest, a station or a gate, in blocks. */
	static final int KEEP_OFF = 2;
	/** How near the portal's approach and the arrival it keeps off, in blocks. */
	static final int PORTAL_CLEAR = 9;
	static final int ARRIVAL_CLEAR = 3;
	private static final int BUILD_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	private static final byte[] FLOOR = {BiomeBlocks.SKULL, BiomeBlocks.SKULL_R4, BiomeBlocks.SKULL_R8, BiomeBlocks.SKULL_R12, BiomeBlocks.BARREL,
		BiomeBlocks.ANVIL, BiomeBlocks.GRINDSTONE, BiomeBlocks.BONE_BLOCK, BiomeBlocks.CANDLE, BiomeBlocks.COBWEB};
	private static final int[][] WAYS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
	private static final Direction[] FACES = {Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};

	private TowerProps() {
	}

	/** Sets a lost room's props. */
	static void scatterRoom(final ServerLevel tower, final int room, final TowerPlan plan, final List<TowerTraps.Trap> traps) {
		TowerVolume volume = TowerVolume.of(plan);
		BlockPos origin = TowerLayout.origin(room);
		Random random = new Random(TowerPlan.mix(tower.getSeed() ^ SALT ^ (room * 0x9E3779B97F4A7C15L)));
		List<int[]> avoid = new ArrayList<>();
		for (TowerTraps.Trap trap : traps) {
			avoid.add(new int[] {trap.x(), trap.z()});
		}
		for (TowerPlan.ChestSpot spot : plan.chestSpots()) {
			for (TowerPlan.Spot cell : spot.cells()) {
				avoid.add(new int[] {cell.x(), cell.z()});
			}
		}
		for (TowerPlan.Station station : plan.stations()) {
			avoid.add(new int[] {station.x(), station.z()});
		}
		for (TowerPlan.Gate gate : plan.gates()) {
			for (TowerPlan.Spot cell : gate.cells()) {
				avoid.add(new int[] {cell.x(), cell.z()});
			}
		}
		List<int[]> floor = new ArrayList<>();
		for (int x = 1; x < TowerLayout.SIZE - 1; x++) {
			for (int z = 1; z < TowerLayout.SIZE - 1; z++) {
				if (clear(plan, volume, avoid, x, z) && volume.standsFlat(x, 0, z) && atTheFootOfAWall(volume, x, z)) {
					floor.add(new int[] {x, z});
				}
			}
		}
		java.util.Collections.shuffle(floor, random);
		int barrels = 0;
		int want = FLOOR_MIN + random.nextInt(FLOOR_SPREAD);
		for (int i = 0; i < Math.min(want, floor.size()); i++) {
			byte code = FLOOR[random.nextInt(FLOOR.length)];
			if (code == BiomeBlocks.BARREL && barrels++ >= BiomeProps.BARRELS) {
				code = BiomeBlocks.BONE_BLOCK;
			}
			set(tower, origin.offset(floor.get(i)[0], 0, floor.get(i)[1]), BiomeBlocks.state(code));
		}
		int hung = 0;
		int walls = WALL_MIN + random.nextInt(WALL_SPREAD);
		for (int tries = 0; hung < walls && tries < walls * 80; tries++) {
			int x = 1 + random.nextInt(TowerLayout.SIZE - 2);
			int z = 1 + random.nextInt(TowerLayout.SIZE - 2);
			int y = 3 + random.nextInt(4);
			int way = random.nextInt(4);
			int nx = x + WAYS[way][0];
			int nz = z + WAYS[way][1];
			byte beside = volume.kind(nx, y, nz);
			boolean onAWall = beside == TowerVolume.WALL || beside == TowerVolume.PILLAR;
			if (volume.kind(x, y, z) == TowerVolume.AIR && onAWall && clear(plan, volume, avoid, x, z)) {
				BlockState state = BiomeBlocks.state(random.nextBoolean() && y >= 4 ? BiomeBlocks.wallSkull(FACES[way]) : BiomeBlocks.COBWEB);
				if (set(tower, origin.offset(x, y, z), state)) {
					hung++;
				}
			}
		}
	}

	/** Whether the column is clear of what a prop keeps off: the room's fixtures, its portal's approach, its arrival and its safe zones. */
	private static boolean clear(final TowerPlan plan, final TowerVolume volume, final List<int[]> avoid, final int x, final int z) {
		if (plan.inSafeZone(x, z) || volume.isWay(x, 0, z)) {
			return false;
		}
		if (Math.max(Math.abs(x - plan.portalX()), Math.abs(z - plan.portalZ())) <= PORTAL_CLEAR
			|| Math.max(Math.abs(x - plan.arrivalX()), Math.abs(z - plan.arrivalZ())) <= ARRIVAL_CLEAR) {
			return false;
		}
		if (plan.hall() > 0) {
			int low = plan.hallLow() - 2 - KEEP_OFF;
			int high = plan.hallLow() + plan.hall() + 1 + KEEP_OFF;
			if (x >= low && x <= high && z >= low && z <= high) {
				return false;
			}
		}
		for (int[] fixture : avoid) {
			if (Math.max(Math.abs(x - fixture[0]), Math.abs(z - fixture[1])) <= KEEP_OFF) {
				return false;
			}
		}
		return true;
	}

	private static boolean atTheFootOfAWall(final TowerVolume volume, final int x, final int z) {
		for (int[] way : WAYS) {
			if (volume.at(x + way[0], 0, z + way[1]) == TowerWalk.SOLID && volume.at(x + way[0], 1, z + way[1]) == TowerWalk.SOLID) {
				return true;
			}
		}
		return false;
	}

	private static boolean set(final ServerLevel tower, final BlockPos at, final BlockState state) {
		if (!tower.getBlockState(at).isAir()) {
			return false;
		}
		tower.setBlock(at, state, BUILD_FLAGS);
		return true;
	}
}
