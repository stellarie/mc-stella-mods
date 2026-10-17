package com.stella.end;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/** The shell of the keep: the slabs, the walls with their doorways, and the flights between the floors. */
final class CastleKeep {
	private static final int SIZE = EndCastle.SIZE;
	private static final int TOP = EndCastle.FLOOR_HEIGHT - 1;

	private CastleKeep() {
	}

	static void build(final CastleSite site) {
		slabs(site);
		for (int floor = 1; floor <= EndCastle.FLOORS; floor++) {
			walls(site, site.layout.floor(floor));
		}
		for (int floor = 1; floor <= EndCastle.FLOORS; floor++) {
			for (CastleLayout.Flight flight : site.layout.floor(floor).ups()) {
				flight(site, flight, EndCastle.slabY(floor));
			}
		}
	}

	static boolean onEdge(final int x, final int z) {
		return x == 0 || z == 0 || x == SIZE - 1 || z == SIZE - 1;
	}

	static boolean onCorner(final int x, final int z) {
		return (x == 0 || x == SIZE - 1) && (z == 0 || z == SIZE - 1);
	}

	/** A cell of one of the four corner pillars that run from the floor 5 slab to the top of the towers. */
	static boolean towerCell(final int x, final int z) {
		return (x <= 2 || x >= SIZE - 3) && (z <= 2 || z >= SIZE - 3);
	}

	/** Every slab, the rest level's too: whole, then opened where a courtyard or a stair head passes through. */
	private static void slabs(final CastleSite site) {
		for (int floor = 1; floor <= EndCastle.FLOORS; floor++) {
			site.fill(0, EndCastle.slabY(floor), 0, SIZE - 1, EndCastle.slabY(floor), SIZE - 1, CastleBlocks.BRICKS);
		}
		site.fill(0, EndCastle.REST_Y, 0, SIZE - 1, EndCastle.REST_Y, SIZE - 1, CastleBlocks.BRICKS);
		for (int floor = 1; floor <= EndCastle.FLOORS; floor++) {
			CastleLayout.FloorPlan plan = site.layout.floor(floor);
			int slab = EndCastle.slabY(floor);
			for (int x = 0; x < SIZE; x++) {
				for (int z = 0; z < SIZE; z++) {
					if (plan.kind(x, z) == CastleLayout.VOID) {
						site.put(x, slab, z, CastleBlocks.AIR);
					}
				}
			}
			for (CastleLayout.Flight arrival : plan.arrivals()) {
				opening(site, arrival, slab);
			}
		}
		opening(site, CastleLayout.APSE, EndCastle.REST_Y);
	}

	/** Opens the slab over the high steps of a flight. */
	static void opening(final CastleSite site, final CastleLayout.Flight flight, final int slab) {
		for (int k = 0; k < CastleLayout.Flight.STEPS; k++) {
			if (flight.hole(k)) {
				for (int lane = 0; lane < CastleLayout.Flight.WIDTH; lane++) {
					site.put(flight.x(k, lane), slab, flight.z(k, lane), CastleBlocks.AIR);
				}
			}
		}
	}

	private static BlockState wallState(final int floor, final int x, final int z) {
		if (floor == EndCastle.FLOORS) {
			boolean hall = x == CastleLayout.HALL.x0() || x == CastleLayout.HALL.x1() || z == CastleLayout.HALL.z0() || z == CastleLayout.HALL.z1();
			return hall ? CastleBlocks.BRICKS : CastleBlocks.PURPUR;
		}
		if (onCorner(x, z) && floor <= 4) {
			return CastleBlocks.PILLAR;
		}
		return onEdge(x, z) ? CastleBlocks.BRICKS : CastleBlocks.PURPUR;
	}

	/** Every wall and doorway of a floor, a lintel over each doorway and a barrier in the sealed one. */
	private static void walls(final CastleSite site, final CastleLayout.FloorPlan plan) {
		int slab = EndCastle.slabY(plan.number());
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				byte kind = plan.kind(x, z);
				if ((kind != CastleLayout.WALL && kind != CastleLayout.DOOR) || (plan.number() >= 5 && towerCell(x, z))) {
					continue;
				}
				BlockState wall = wallState(plan.number(), x, z);
				int from = kind == CastleLayout.DOOR ? slab + CastleLayout.DOOR_HEIGHT + 1 : slab + 1;
				for (int y = from; y <= slab + TOP; y++) {
					site.put(x, y, z, wall);
				}
				if (kind == CastleLayout.DOOR && isBarrier(plan, x, z)) {
					for (int y = slab + 1; y <= slab + CastleLayout.DOOR_HEIGHT; y++) {
						site.put(x, y, z, CastleBlocks.BARRIER);
					}
				}
			}
		}
	}

	private static boolean isBarrier(final CastleLayout.FloorPlan plan, final int x, final int z) {
		return plan.doors().stream().anyMatch(door -> door.barrier() && door.contains(x, z));
	}

	/** A straight flight of purpur stairs from a slab to the next: the last step takes the cell of the slab above. */
	static void flight(final CastleSite site, final CastleLayout.Flight flight, final int slab) {
		BlockState step = CastleBlocks.stair(Blocks.PURPUR_STAIRS, flight.direction(), Half.BOTTOM);
		for (int k = 0; k < CastleLayout.Flight.STEPS; k++) {
			for (int lane = 0; lane < CastleLayout.Flight.WIDTH; lane++) {
				site.put(flight.x(k, lane), slab + 1 + k, flight.z(k, lane), step);
			}
		}
	}
}
