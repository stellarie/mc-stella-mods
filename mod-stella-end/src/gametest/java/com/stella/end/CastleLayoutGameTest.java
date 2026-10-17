package com.stella.end;

import java.util.ArrayDeque;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** The plan alone, over many seeds: rooms, connection, flights, and what moves with the seed and what does not. */
public class CastleLayoutGameTest {
	/** Seeds sampled for every property of the plan. */
	static final int SEEDS = 40;

	@GameTest(environment = "mod-stella-end-test:layout_rooms", maxTicks = 20)
	public void everyFloorHoldsFourToEightRoomsAndTheGreatHallThree(final GameTestHelper helper) {
		StringBuilder bad = new StringBuilder();
		int smallest = Integer.MAX_VALUE;
		int largest = 0;
		for (long seed = 0; seed < SEEDS; seed++) {
			CastleLayout layout = CastleLayout.generate(seed);
			for (int floor = 1; floor < EndCastle.FLOORS; floor++) {
				int rooms = layout.floor(floor).roomCount();
				smallest = Math.min(smallest, rooms);
				largest = Math.max(largest, rooms);
				if (rooms < 4 || rooms > 8) {
					bad.append(" seed ").append(seed).append(" floor ").append(floor).append(" has ").append(rooms);
				}
			}
			if (layout.floor(EndCastle.FLOORS).roomCount() != 3) {
				bad.append(" seed ").append(seed).append(" hall floor has ").append(layout.floor(EndCastle.FLOORS).roomCount());
			}
		}
		EndTestSupport.result("layoutRooms", "seeds=" + SEEDS + " fewest=" + smallest + " most=" + largest);
		helper.assertTrue(bad.isEmpty(), "floors with too few or too many rooms:" + bad);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:layout_connected", maxTicks = 20)
	public void everyRoomOfAFloorJoinsThroughDoorwaysAndEveryStairFootIsReachable(final GameTestHelper helper) {
		StringBuilder bad = new StringBuilder();
		for (long seed = 0; seed < SEEDS; seed++) {
			CastleLayout layout = CastleLayout.generate(seed);
			for (int floor = 1; floor <= EndCastle.FLOORS; floor++) {
				CastleLayout.FloorPlan plan = layout.floor(floor);
				boolean[] reached = flood(plan, plan.landingX(), plan.landingZ());
				int open = 0;
				int joined = 0;
				for (int x = 0; x < EndCastle.SIZE; x++) {
					for (int z = 0; z < EndCastle.SIZE; z++) {
						if (plan.kind(x, z) != CastleLayout.WALL && plan.kind(x, z) != CastleLayout.VOID) {
							open++;
							joined += reached[x * EndCastle.SIZE + z] ? 1 : 0;
						}
					}
				}
				if (open != joined) {
					bad.append(" seed ").append(seed).append(" floor ").append(floor).append(" joins ").append(joined).append(" of ").append(open);
				}
				for (CastleLayout.Flight flight : plan.ups()) {
					int footX = flight.x(-1, 1);
					int footZ = flight.z(-1, 1);
					if (!reached[footX * EndCastle.SIZE + footZ]) {
						bad.append(" seed ").append(seed).append(" floor ").append(floor).append(" stair foot ").append(footX).append(',').append(footZ).append(" sealed");
					}
				}
			}
		}
		EndTestSupport.result("layoutConnected", "seeds=" + SEEDS + " bad=" + bad.length());
		helper.assertTrue(bad.isEmpty(), "unjoined floors:" + bad);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:layout_flights", maxTicks = 20)
	public void everyFlightIsThreeWideOverOpenFloorAndArrivesInsideTheFloorAbove(final GameTestHelper helper) {
		StringBuilder bad = new StringBuilder();
		for (long seed = 0; seed < SEEDS; seed++) {
			CastleLayout layout = CastleLayout.generate(seed);
			for (int floor = 1; floor <= EndCastle.FLOORS - 1; floor++) {
				CastleLayout.FloorPlan plan = layout.floor(floor);
				CastleLayout.FloorPlan above = layout.floor(floor + 1);
				if (!plan.ups().equals(above.arrivals())) {
					bad.append(" seed ").append(seed).append(" floor ").append(floor).append(" and ").append(floor + 1).append(" disagree on their flights");
				}
				for (CastleLayout.Flight flight : plan.ups()) {
					for (int k = -1; k <= CastleLayout.Flight.STEPS; k++) {
						for (int lane = 0; lane < CastleLayout.Flight.WIDTH; lane++) {
							int x = flight.x(k, lane);
							int z = flight.z(k, lane);
							boolean below = k <= CastleLayout.Flight.HOLE_FROM - 1;
							byte onThis = plan.kind(x, z);
							byte onAbove = above.kind(x, z);
							// The run stands on this floor; from the first high step on, the cells are floor above too.
							if (k <= CastleLayout.Flight.STEPS - 1 && onThis != CastleLayout.OPEN) {
								bad.append(" seed ").append(seed).append(" floor ").append(floor).append(" step ").append(k).append(" is ").append(onThis);
							}
							if (!below && onAbove != CastleLayout.OPEN) {
								bad.append(" seed ").append(seed).append(" floor ").append(floor + 1).append(" lands on ").append(onAbove).append(" at step ").append(k);
							}
						}
					}
				}
			}
			if (layout.floor(4).ups().size() != 4) {
				bad.append(" seed ").append(seed).append(" floor 4 has ").append(layout.floor(4).ups().size()).append(" flights");
			}
		}
		EndTestSupport.result("layoutFlights", "seeds=" + SEEDS + " bad=" + bad.length());
		helper.assertTrue(bad.isEmpty(), "flights off the plan:" + bad);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:layout_seeds", maxTicks = 20)
	public void anotherSeedGivesAnotherLayoutAndOneSeedAlwaysTheSame(final GameTestHelper helper) {
		CastleLayout a = CastleLayout.generate(11L);
		CastleLayout again = CastleLayout.generate(11L);
		CastleLayout b = CastleLayout.generate(22L);
		StringBuilder bad = new StringBuilder();
		for (int floor = 1; floor <= EndCastle.FLOORS - 1; floor++) {
			if (!sameWalls(a.floor(floor), again.floor(floor))) {
				bad.append(" floor ").append(floor).append(" moved under one seed");
			}
			if (sameWalls(a.floor(floor), b.floor(floor))) {
				bad.append(" floor ").append(floor).append(" is the same under two seeds");
			}
		}
		EndTestSupport.result("layoutSeeds", "bad=" + bad.length());
		helper.assertTrue(bad.isEmpty(), bad.toString());
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:layout_vaults", maxTicks = 20)
	public void theVaultChestsFlightsAndGreatHallNeverMoveWithTheSeed(final GameTestHelper helper) {
		CastleLayout first = CastleLayout.generate(0L);
		StringBuilder bad = new StringBuilder();
		for (long seed = 1; seed < SEEDS; seed++) {
			CastleLayout other = CastleLayout.generate(seed);
			for (int floor = 1; floor <= EndCastle.FLOORS; floor++) {
				CastleLayout.Chest vault = first.floor(floor).vault();
				if (!vault.equals(other.floor(floor).vault())) {
					bad.append(" seed ").append(seed).append(" floor ").append(floor).append(" vault ").append(other.floor(floor).vault());
				}
				if (!first.floor(floor).ups().equals(other.floor(floor).ups())) {
					bad.append(" seed ").append(seed).append(" floor ").append(floor).append(" stairs moved");
				}
			}
			if (!sameWalls(first.floor(EndCastle.FLOORS), other.floor(EndCastle.FLOORS))) {
				bad.append(" seed ").append(seed).append(" the great hall moved");
			}
		}
		for (int floor = 1; floor <= EndCastle.FLOORS; floor++) {
			if (first.floor(floor).chests().stream().filter(CastleLayout.Chest::vault).count() != 1
				|| first.floor(floor).chests().stream().filter(chest -> !chest.vault()).count() != 2) {
				bad.append(" floor ").append(floor).append(" has ").append(first.floor(floor).chests().size()).append(" chests");
			}
		}
		EndTestSupport.result("layoutVaults", "seeds=" + SEEDS + " bad=" + bad.length());
		helper.assertTrue(bad.isEmpty(), "vaults or stairs that moved:" + bad);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:layout_spawn_cells", maxTicks = 20)
	public void noSpawnCellLiesInTheCourtyardOnAStairOrBesideAChest(final GameTestHelper helper) {
		StringBuilder bad = new StringBuilder();
		int cells = 0;
		for (long seed = 0; seed < SEEDS; seed++) {
			CastleLayout layout = CastleLayout.generate(seed);
			for (int floor = 1; floor <= EndCastle.FLOORS - 1; floor++) {
				CastleLayout.FloorPlan plan = layout.floor(floor);
				if (plan.spawnCells().isEmpty()) {
					bad.append(" seed ").append(seed).append(" floor ").append(floor).append(" has no spawn cell");
				}
				for (int[] cell : plan.spawnCells()) {
					cells++;
					boolean inCourtyard = floor <= 3 && CastleLayout.COURTYARD.contains(cell[0], cell[1]);
					boolean onChest = plan.chests().stream().anyMatch(chest -> (chest.x() == cell[0] && chest.z() == cell[1])
						|| (chest.partnerX() == cell[0] && chest.partnerZ() == cell[1]));
					if (inCourtyard || plan.stairAt(cell[0], cell[1]) || onChest || plan.room(cell[0], cell[1]) == 0 || plan.kind(cell[0], cell[1]) != CastleLayout.OPEN) {
						bad.append(" seed ").append(seed).append(" floor ").append(floor).append(" cell ").append(cell[0]).append(',').append(cell[1]);
					}
				}
			}
		}
		EndTestSupport.result("layoutSpawnCells", "seeds=" + SEEDS + " cells=" + cells + " bad=" + bad.length());
		helper.assertTrue(bad.isEmpty(), "spawn cells in the wrong place:" + bad);
		helper.succeed();
	}

	static boolean sameWalls(final CastleLayout.FloorPlan one, final CastleLayout.FloorPlan other) {
		for (int x = 0; x < EndCastle.SIZE; x++) {
			for (int z = 0; z < EndCastle.SIZE; z++) {
				if (one.kind(x, z) != other.kind(x, z)) {
					return false;
				}
			}
		}
		return true;
	}

	/** Cells reached by steps over open cells and doors, from a start. A void has no floor and a wall stops the walk. */
	static boolean[] flood(final CastleLayout.FloorPlan plan, final int startX, final int startZ) {
		boolean[] seen = new boolean[EndCastle.SIZE * EndCastle.SIZE];
		ArrayDeque<int[]> queue = new ArrayDeque<>(List.of(new int[] {startX, startZ}));
		seen[startX * EndCastle.SIZE + startZ] = true;
		int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!queue.isEmpty()) {
			int[] at = queue.poll();
			for (int[] step : steps) {
				int x = at[0] + step[0];
				int z = at[1] + step[1];
				if (x >= 0 && z >= 0 && x < EndCastle.SIZE && z < EndCastle.SIZE && plan.kind(x, z) != CastleLayout.WALL && plan.kind(x, z) != CastleLayout.VOID
					&& !seen[x * EndCastle.SIZE + z]) {
					seen[x * EndCastle.SIZE + z] = true;
					queue.add(new int[] {x, z});
				}
			}
		}
		return seen;
	}
}
