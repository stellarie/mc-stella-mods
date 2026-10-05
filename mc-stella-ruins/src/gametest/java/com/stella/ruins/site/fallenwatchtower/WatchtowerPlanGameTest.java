package com.stella.ruins.site.fallenwatchtower;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Acceptance items 1 and 2 on the plan itself: the tower stands with a climbable stair, and height, collapse side,
 * floors, and stair direction vary. The plan reads no world, so these tests run over many seeds in one tick.
 */
public class WatchtowerPlanGameTest {
	static final int SEEDS = 200;
	static final long BASE = 0x7A11F0A1L;

	static long seed(final int i) {
		return BASE + i * 7919L;
	}

	private static boolean standableBlock(final byte id) {
		return WatchtowerPlan.isWall(id) || id == WatchtowerPlan.FLOOR || id == WatchtowerPlan.STONE_FLOOR || id == WatchtowerPlan.STAIR
			|| id == WatchtowerPlan.PLINTH || WatchtowerPlan.isRubble(id) || id == WatchtowerPlan.DAIS;
	}

	private static boolean solid(final WatchtowerPlan plan, final int x, final int y, final int z) {
		byte id = plan.cell(x, y, z);
		return id != WatchtowerPlan.AIR && id != WatchtowerPlan.WEB;
	}

	private static boolean standable(final WatchtowerPlan plan, final int x, final int y, final int z) {
		if (!WatchtowerPlan.inBox(x, y, z)) {
			return false;
		}
		boolean ground = y == 0 || standableBlock(plan.cell(x, y - 1, z));
		return ground && !solid(plan, x, y, z) && !solid(plan, x, y + 1, z);
	}

	/** Every standing spot a player can reach from the ground in front of the door: climb 1, fall 3, 2 blocks of head room. */
	static boolean[][][] reach(final WatchtowerPlan plan) {
		boolean[][][] seen = new boolean[WatchtowerPlan.SIZE_X][WatchtowerPlan.SIZE_Y][WatchtowerPlan.SIZE_Z];
		java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
		seen[WatchtowerPlan.CENTER][0][0] = true;
		queue.add(new int[] {WatchtowerPlan.CENTER, 0, 0});
		int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!queue.isEmpty()) {
			int[] c = queue.poll();
			for (int[] d : around) {
				int nx = c[0] + d[0];
				int nz = c[2] + d[1];
				if (nx < 0 || nx >= WatchtowerPlan.SIZE_X || nz < 0 || nz >= WatchtowerPlan.SIZE_Z) {
					continue;
				}
				for (int dy = 1; dy >= -3; dy--) {
					int ny = c[1] + dy;
					if (!standable(plan, nx, ny, nz)) {
						continue;
					}
					if (dy == 1 && solid(plan, c[0], c[1] + 2, c[2])) {
						continue;
					}
					if (dy < 0) {
						boolean blocked = false;
						for (int y = ny + 2; y <= c[1] + 1; y++) {
							blocked |= solid(plan, nx, y, nz);
						}
						if (blocked) {
							continue;
						}
					}
					if (!seen[nx][ny][nz]) {
						seen[nx][ny][nz] = true;
						queue.add(new int[] {nx, ny, nz});
					}
					break;
				}
			}
		}
		return seen;
	}

	@GameTest
	public void heightCollapseSideFloorsAndStairDirectionVaryOver200Seeds(final GameTestHelper helper) {
		Set<Integer> heights = new HashSet<>();
		Set<Integer> sides = new HashSet<>();
		Set<Boolean> directions = new HashSet<>();
		Set<Integer> floors = new HashSet<>();
		Set<Integer> templates = new HashSet<>();
		Set<Integer> depths = new HashSet<>();
		Set<Integer> halves = new HashSet<>();
		Set<Integer> breaches = new HashSet<>();
		Set<String> layouts = new HashSet<>();
		int low = Integer.MAX_VALUE;
		int high = 0;
		int fewest = Integer.MAX_VALUE;
		for (int i = 0; i < SEEDS; i++) {
			WatchtowerPlan plan = WatchtowerPlan.derive(seed(i));
			heights.add(plan.height());
			low = Math.min(low, plan.height());
			high = Math.max(high, plan.height());
			sides.add(plan.side);
			directions.add(plan.counterClockwise);
			floors.add(plan.floors);
			depths.add(plan.depth);
			halves.add(plan.halfWidth);
			breaches.add(plan.breachFloor);
			for (int k = 1; k < plan.floors; k++) {
				templates.add(plan.template[k]);
			}
			fewest = Math.min(fewest, plan.rubble);
			layouts.add(plan.side + ":" + plan.counterClockwise + ":" + plan.height() + ":" + java.util.Arrays.toString(plan.template) + ":" + plan.rubble + ":" + plan.removedWall);
		}
		TowerTestSupport.result("planVariety", "heights=" + low + ".." + high + " distinct=" + heights.size() + " sides=" + sides + " floors=" + floors
			+ " templates=" + templates + " depths=" + depths + " halves=" + halves + " layouts=" + layouts.size() + " fewestRubble=" + fewest);
		helper.assertTrue(low == WatchtowerPlan.FLOOR_HEIGHT * WatchtowerPlan.MIN_FLOORS && high == WatchtowerPlan.FLOOR_HEIGHT * (WatchtowerPlan.MIN_FLOORS + 2) + WatchtowerPlan.MAX_CROWN,
			"the height runs " + low + " to " + high);
		helper.assertTrue(heights.size() >= 12, "only " + heights.size() + " distinct heights");
		helper.assertTrue(sides.size() == 4, "collapse sides " + sides);
		helper.assertTrue(directions.size() == 2, "stair directions " + directions);
		helper.assertTrue(floors.size() == 3, "floor counts " + floors);
		helper.assertTrue(templates.size() == 4, "floor templates " + templates);
		helper.assertTrue(depths.size() == 3 && halves.size() == 3 && breaches.size() >= 3, "depths " + depths + " halves " + halves + " breach floors " + breaches);
		helper.assertTrue(layouts.size() >= SEEDS * 9 / 10, "only " + layouts.size() + " different plans in " + SEEDS + " seeds");
		helper.succeed();
	}

	@GameTest
	public void aRubbleFieldLiesOnTheCollapseSideOnly(final GameTestHelper helper) {
		int most = 0;
		for (int i = 0; i < SEEDS; i++) {
			WatchtowerPlan plan = WatchtowerPlan.derive(seed(i));
			int found = 0;
			for (int y = 0; y < WatchtowerPlan.SIZE_Y; y++) {
				for (int z = 0; z < WatchtowerPlan.SIZE_Z; z++) {
					for (int x = 0; x < WatchtowerPlan.SIZE_X; x++) {
						if (!WatchtowerPlan.isRubble(plan.cell(x, y, z)) || (x >= 6 && x <= 14 && z >= 6 && z <= 14)) {
							continue;
						}
						found++;
						boolean onSide = switch (plan.side) {
							case 0 -> z >= 14;
							case 1 -> x >= 14;
							case 2 -> z <= 6;
							default -> x <= 6;
						};
						helper.assertTrue(onSide, "seed " + i + ": rubble at " + x + "," + y + "," + z + " is off the collapse side " + plan.side);
						helper.assertTrue(y <= 5, "seed " + i + ": rubble " + y + " blocks high");
					}
				}
			}
			helper.assertTrue(found > 0 && found == plan.rubble, "seed " + i + ": " + found + " rubble blocks, plan counted " + plan.rubble);
			most = Math.max(most, found);
		}
		TowerTestSupport.result("rubbleField", "most=" + most);
		helper.succeed();
	}

	@GameTest
	public void theDoorStripStaysClearOfRubbleAndTheDoorOpensTwoBlocksHigh(final GameTestHelper helper) {
		for (int i = 0; i < SEEDS; i++) {
			WatchtowerPlan plan = WatchtowerPlan.derive(seed(i));
			for (int z = 0; z <= 5; z++) {
				for (int x = 9; x <= 11; x++) {
					for (int y = 1; y <= 3; y++) {
						helper.assertTrue(plan.cell(x, y, z) == WatchtowerPlan.AIR, "seed " + i + ": a block stands in the door strip at " + x + "," + y + "," + z);
					}
					helper.assertTrue(!WatchtowerPlan.isRubble(plan.cell(x, 0, z)), "seed " + i + ": rubble lies in the door strip at " + x + "," + z);
				}
			}
			helper.assertTrue(plan.cell(WatchtowerPlan.CENTER, 1, WatchtowerPlan.WALL_LOW) == WatchtowerPlan.AIR && plan.cell(WatchtowerPlan.CENTER, 2, WatchtowerPlan.WALL_LOW) == WatchtowerPlan.AIR
				&& plan.cell(WatchtowerPlan.CENTER, 3, WatchtowerPlan.WALL_LOW) != WatchtowerPlan.AIR, "seed " + i + ": the door is not 2 high");
		}
		helper.succeed();
	}

	@GameTest
	public void theStairClimbsFromTheDoorToTheCaptainOver200Seeds(final GameTestHelper helper) {
		for (int i = 0; i < SEEDS; i++) {
			WatchtowerPlan plan = WatchtowerPlan.derive(seed(i));
			boolean[][][] seen = reach(plan);
			int[] cap = plan.captainCell();
			int roof = plan.floorY(plan.floors) + 1;
			helper.assertTrue(seen[cap[0]][roof][cap[1]], "seed " + i + " (" + plan.floors + " floors, " + (plan.counterClockwise ? "ccw" : "cw") + ", breach side " + plan.side
				+ "): the climb does not reach the captain at " + cap[0] + "," + roof + "," + cap[1]);
			for (WatchtowerPlan.Marker marker : plan.markers) {
				helper.assertTrue(seen[marker.x()][marker.y()][marker.z()], "seed " + i + ": no way to the " + marker.name() + " marker at " + marker.x() + "," + marker.y() + "," + marker.z());
			}
		}
		TowerTestSupport.result("planClimb", "seeds=" + SEEDS + " all reach the captain and every marker");
		helper.succeed();
	}

	@GameTest
	public void everyFloorHoldsTwoOrThreeMobsAndTheGarrisonStaysUnderTheCap(final GameTestHelper helper) {
		int most = 0;
		int least = Integer.MAX_VALUE;
		for (int i = 0; i < SEEDS; i++) {
			WatchtowerPlan plan = WatchtowerPlan.derive(seed(i));
			int[] perFloor = new int[plan.floors + 1];
			for (WatchtowerPlan.Marker marker : plan.markers) {
				perFloor[(marker.y() - 1) / WatchtowerPlan.FLOOR_HEIGHT]++;
			}
			for (int k = 0; k < plan.floors; k++) {
				helper.assertTrue(perFloor[k] >= 2 && perFloor[k] <= 3, "seed " + i + ": floor " + k + " holds " + perFloor[k] + " mobs");
			}
			helper.assertTrue(perFloor[plan.floors] == 3, "seed " + i + ": the roof holds " + perFloor[plan.floors] + " mobs");
			helper.assertTrue(plan.markers.size() <= WatchtowerPlan.GARRISON_CAP, "seed " + i + ": " + plan.markers.size() + " mobs");
			helper.assertTrue(plan.markers(WatchtowerPlan.CAPTAIN).size() == 1, "seed " + i + ": " + plan.markers(WatchtowerPlan.CAPTAIN).size() + " captains");
			most = Math.max(most, plan.markers.size());
			least = Math.min(least, plan.markers.size());
		}
		TowerTestSupport.result("garrisonSize", "least=" + least + " most=" + most + " cap=" + WatchtowerPlan.GARRISON_CAP);
		helper.assertTrue(most == WatchtowerPlan.GARRISON_CAP, "no tower reached the cap, so the cap is untested: most " + most);
		helper.succeed();
	}

	@GameTest
	public void theWallBesideEveryStairBlockStandsTwoBlocksAboveTheStep(final GameTestHelper helper) {
		for (int i = 0; i < SEEDS; i++) {
			WatchtowerPlan plan = WatchtowerPlan.derive(seed(i));
			for (WatchtowerPlan.Flight flight : plan.flights) {
				for (int s = 0; s < 5; s++) {
					int sy = WatchtowerPlan.FLOOR_HEIGHT * flight.floor() + s + 1;
					int x = flight.steps()[s][0];
					int z = flight.steps()[s][1];
					helper.assertTrue(plan.cell(x, sy, z) == WatchtowerPlan.STAIR, "seed " + i + ": no stair block at " + x + "," + sy + "," + z);
					int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
					for (int[] d : around) {
						int wx = x + d[0];
						int wz = z + d[1];
						if (wx != 6 && wx != 14 && wz != 6 && wz != 14) {
							continue;
						}
						int top = WatchtowerPlan.FLOOR_HEIGHT * plan.floors + WatchtowerPlan.crownAt(wx, wz, plan.crown);
						helper.assertTrue(WatchtowerPlan.isWall(plan.cell(wx, sy + 1, wz)), "seed " + i + ": no rail at " + wx + "," + (sy + 1) + "," + wz);
						if (sy + 2 <= top) {
							helper.assertTrue(WatchtowerPlan.isWall(plan.cell(wx, sy + 2, wz)), "seed " + i + ": rail is one block high at " + wx + "," + (sy + 2) + "," + wz);
						}
					}
				}
			}
		}
		helper.succeed();
	}

	@GameTest
	public void everyFlightHasItsStairwellHoleAndNoFallenFloorSitsOverAnother(final GameTestHelper helper) {
		for (int i = 0; i < SEEDS; i++) {
			WatchtowerPlan plan = WatchtowerPlan.derive(seed(i));
			for (WatchtowerPlan.Flight flight : plan.flights) {
				int slab = WatchtowerPlan.FLOOR_HEIGHT * (flight.floor() + 1);
				for (int s = 2; s <= 4; s++) {
					helper.assertTrue(plan.cell(flight.steps()[s][0], slab, flight.steps()[s][1]) == WatchtowerPlan.AIR, "seed " + i + ": no hole over step " + (s + 1) + " of floor " + flight.floor());
				}
				helper.assertTrue(plan.cell(flight.end()[0], slab, flight.end()[1]) != WatchtowerPlan.AIR, "seed " + i + ": no landing at the end of floor " + flight.floor());
			}
			for (int k = 2; k <= plan.floors; k++) {
				for (int z = 7; z <= 13; z++) {
					for (int x = 7; x <= 13; x++) {
						helper.assertTrue(!(plan.fell(x, WatchtowerPlan.FLOOR_HEIGHT * k, z) && plan.fell(x, WatchtowerPlan.FLOOR_HEIGHT * (k - 1), z)),
							"seed " + i + ": floors " + (k - 1) + " and " + k + " both fell at " + x + "," + z);
					}
				}
			}
		}
		helper.succeed();
	}

	@GameTest
	public void theCoreAndTheVaultSitOnTheRoofBehindTheCaptain(final GameTestHelper helper) {
		for (int i = 0; i < SEEDS; i++) {
			WatchtowerPlan plan = WatchtowerPlan.derive(seed(i));
			int roof = plan.floorY(plan.floors);
			helper.assertTrue(plan.core()[1] == roof + 1 && plan.vault()[1] == roof + 1, "seed " + i + ": core y " + plan.core()[1] + " vault y " + plan.vault()[1] + " roof " + roof);
			helper.assertTrue(plan.cell(plan.core()[0], plan.core()[1], plan.core()[2]) == WatchtowerPlan.CORE
				&& plan.cell(plan.vault()[0], plan.vault()[1], plan.vault()[2]) == WatchtowerPlan.VAULT, "seed " + i + ": core or vault missing");
			helper.assertTrue(plan.cell(plan.core()[0], roof, plan.core()[2]) != WatchtowerPlan.AIR && plan.cell(plan.vault()[0], roof, plan.vault()[2]) != WatchtowerPlan.AIR,
				"seed " + i + ": the core or the vault has no floor under it");
			int[] cap = plan.captainCell();
			int farFromBreach = WatchtowerPlan.depthFrom(plan.side, plan.core()[0], plan.core()[2]);
			helper.assertTrue(farFromBreach == 5 && WatchtowerPlan.depthFrom(plan.side, cap[0], cap[1]) == 4, "seed " + i + ": the roof order is wrong");
		}
		helper.succeed();
	}

	@GameTest
	public void theLookoutPlatformOverhangsOnCorbelsWithMerlonsAndAWatchFire(final GameTestHelper helper) {
		int merlons = 0;
		int corbels = 0;
		for (int i = 0; i < SEEDS; i++) {
			WatchtowerPlan plan = WatchtowerPlan.derive(seed(i));
			int deck = plan.floorY(plan.floors);
			// Corner (15,15) and (5,5) sit away from a breach on the front or the left, or the back or the right.
			int[][] corners = {{5, 5}, {15, 15}};
			for (int[] c : corners) {
				boolean broken = plan.cell(c[0], deck, c[1]) == WatchtowerPlan.AIR || plan.wallFell(c[0], deck + 1, c[1]);
				if (broken) {
					continue;
				}
				helper.assertTrue(plan.cell(c[0], deck + 1, c[1]) == WatchtowerPlan.WATCH_FIRE, "seed " + i + ": no watch fire at the corner " + c[0] + "," + c[1]);
			}
			int standing = 0;
			for (int z = 5; z <= 15; z++) {
				for (int x = 5; x <= 15; x++) {
					boolean outer = x == 5 || x == 15 || z == 5 || z == 15;
					if (!outer || plan.cell(x, deck, z) == WatchtowerPlan.AIR) {
						continue;
					}
					standing++;
					if (plan.cell(x, deck - 1, z) == WatchtowerPlan.CORBEL) {
						corbels++;
						int toward = plan.dir(x, deck - 1, z);
						int tx = x + WatchtowerPlan.DX[toward];
						int tz = z + WatchtowerPlan.DZ[toward];
						helper.assertTrue(WatchtowerPlan.isWall(plan.cell(tx, deck - 1, tz)) || plan.wallFell(tx, deck - 1, tz), "seed " + i + ": the corbel at " + x + "," + z + " has no wall at its back");
					}
					if (plan.crown >= 1 && WatchtowerPlan.isWall(plan.cell(x, deck + 1, z))) {
						merlons++;
					}
				}
			}
			helper.assertTrue(standing >= 10, "seed " + i + ": the platform stands on " + standing + " blocks");
		}
		helper.assertTrue(merlons > 0 && corbels > 0, "merlons " + merlons + " corbels " + corbels);
		TowerTestSupport.result("platform", "merlons=" + merlons + " corbels=" + corbels);
		helper.succeed();
	}

	@GameTest
	public void everyStorageFloorHoldsOneChestWhereItsFloorStands(final GameTestHelper helper) {
		int storage = 0;
		int chests = 0;
		for (int i = 0; i < SEEDS; i++) {
			WatchtowerPlan plan = WatchtowerPlan.derive(seed(i));
			for (int k = 0; k < plan.floors; k++) {
				int ys = plan.floorY(k);
				int found = 0;
				for (int z = 7; z <= 13; z++) {
					for (int x = 7; x <= 13; x++) {
						if (plan.cell(x, ys + 1, z) == WatchtowerPlan.CHEST) {
							found++;
							helper.assertTrue(plan.template[k] == WatchtowerPlan.STORAGE, "seed " + i + ": a chest on floor " + k + ", which is no storage floor");
						}
					}
				}
				if (plan.template[k] == WatchtowerPlan.STORAGE) {
					storage++;
					chests += found;
					helper.assertTrue(found <= 1, "seed " + i + ": " + found + " chests on one storage floor");
					if (k <= plan.breachFloor) {
						helper.assertTrue(found == 1, "seed " + i + ": the intact storage floor " + k + " has " + found + " chests");
					}
				}
			}
		}
		TowerTestSupport.result("storageChests", "storageFloors=" + storage + " chests=" + chests);
		helper.assertTrue(storage > 50 && chests > 40, "storage floors " + storage + " chests " + chests);
		helper.succeed();
	}

	@GameTest
	public void theSameSeedDrawsTheSamePlanTwiceAndTheRollsFollowTheSeed(final GameTestHelper helper) {
		WatchtowerPlan a = WatchtowerPlan.derive(seed(3));
		WatchtowerPlan b = WatchtowerPlan.derive(seed(3));
		List<WatchtowerPlan.Marker> ma = a.markers;
		helper.assertTrue(ma.equals(b.markers) && a.rubble == b.rubble && a.removedWall == b.removedWall && a.removedFloor == b.removedFloor, "the same seed drew two plans");
		boolean same = true;
		for (int y = 0; y < WatchtowerPlan.SIZE_Y && same; y++) {
			for (int z = 0; z < WatchtowerPlan.SIZE_Z && same; z++) {
				for (int x = 0; x < WatchtowerPlan.SIZE_X; x++) {
					same &= a.cell(x, y, z) == b.cell(x, y, z);
				}
			}
		}
		helper.assertTrue(same, "the same seed drew different blocks");
		helper.succeed();
	}
}
