package com.stella.end;

import java.util.ArrayDeque;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** The plan alone, over many seeds: rooms, connection, stairs, and what moves with the seed and what does not. */
public class CastleLayoutGameTest {
	/** Seeds sampled for every property of the plan. */
	static final int SEEDS = 40;

	@GameTest(environment = "mod-stella-end-test:layout_rooms", maxTicks = 20)
	public void everyFloorHoldsAtLeastFourRooms(final GameTestHelper helper) {
		StringBuilder bad = new StringBuilder();
		int smallest = Integer.MAX_VALUE;
		int largest = 0;
		for (long seed = 0; seed < SEEDS; seed++) {
			CastleLayout layout = CastleLayout.generate(seed);
			for (int floor = 1; floor <= EndCastle.FLOORS; floor++) {
				int rooms = layout.floor(floor).roomCount();
				smallest = Math.min(smallest, rooms);
				largest = Math.max(largest, rooms);
				if (rooms < 4) {
					bad.append(" seed ").append(seed).append(" floor ").append(floor).append(" has ").append(rooms);
				}
				if (floor < EndCastle.FLOORS && rooms > 8) {
					bad.append(" seed ").append(seed).append(" floor ").append(floor).append(" has ").append(rooms);
				}
			}
		}
		EndTestSupport.result("layoutRooms", "seeds=" + SEEDS + " fewest=" + smallest + " most=" + largest);
		helper.assertTrue(bad.isEmpty(), "floors with too few or too many rooms:" + bad);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:layout_connected", maxTicks = 20)
	public void everyRoomOfAFloorJoinsThroughDoorwaysAndTheStairsAreNeverSealedIn(final GameTestHelper helper) {
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
						if (plan.kind(x, z) != CastleLayout.WALL) {
							open++;
							joined += reached[x * EndCastle.SIZE + z] ? 1 : 0;
						}
					}
				}
				if (open != joined) {
					bad.append(" seed ").append(seed).append(" floor ").append(floor).append(" joins ").append(joined).append(" of ").append(open);
				}
				CastleLayout.Shaft up = plan.up();
				if (up == null || !touches(plan, reached, up.topX(), up.topZ())) {
					bad.append(" seed ").append(seed).append(" floor ").append(floor).append(" stairs sealed");
				}
			}
		}
		EndTestSupport.result("layoutConnected", "seeds=" + SEEDS + " bad=" + bad.length());
		helper.assertTrue(bad.isEmpty(), "unjoined floors:" + bad);
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
	public void theVaultChestsAndStairCornersNeverMoveWithTheSeed(final GameTestHelper helper) {
		CastleLayout first = CastleLayout.generate(0L);
		StringBuilder bad = new StringBuilder();
		for (long seed = 1; seed < SEEDS; seed++) {
			CastleLayout other = CastleLayout.generate(seed);
			for (int floor = 1; floor <= EndCastle.FLOORS; floor++) {
				CastleLayout.Chest vault = first.floor(floor).vault();
				if (!vault.equals(other.floor(floor).vault())) {
					bad.append(" seed ").append(seed).append(" floor ").append(floor).append(" vault ").append(other.floor(floor).vault());
				}
				if (!java.util.Objects.equals(first.floor(floor).up(), other.floor(floor).up())) {
					bad.append(" seed ").append(seed).append(" floor ").append(floor).append(" stairs moved");
				}
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

	/** Cells reached by steps over open cells and doors, from a start. */
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
				if (x >= 0 && z >= 0 && x < EndCastle.SIZE && z < EndCastle.SIZE && plan.kind(x, z) != CastleLayout.WALL && !seen[x * EndCastle.SIZE + z]) {
					seen[x * EndCastle.SIZE + z] = true;
					queue.add(new int[] {x, z});
				}
			}
		}
		return seen;
	}

	/** Whether a cell, or a neighbour of it, was reached. */
	static boolean touches(final CastleLayout.FloorPlan plan, final boolean[] reached, final int x, final int z) {
		int[][] steps = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] step : steps) {
			int nx = x + step[0];
			int nz = z + step[1];
			if (nx >= 0 && nz >= 0 && nx < EndCastle.SIZE && nz < EndCastle.SIZE && reached[nx * EndCastle.SIZE + nz] && plan.kind(nx, nz) != CastleLayout.WALL) {
				return true;
			}
		}
		return false;
	}
}
