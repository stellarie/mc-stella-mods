package com.stella.ruins.site.ruinedkeep;

import com.stella.ruins.site.ruinedkeep.KeepPlan.Cell;
import com.stella.ruins.site.ruinedkeep.KeepPlan.Door;
import com.stella.ruins.site.ruinedkeep.KeepPlan.Floor;
import com.stella.ruins.site.ruinedkeep.KeepPlan.Hole;
import com.stella.ruins.site.ruinedkeep.KeepPlan.Kind;
import com.stella.ruins.site.ruinedkeep.KeepPlan.Rect;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Acceptance item 2 and the rules of the plan: what varies with the seed, and what never does. */
public class KeepPlanGameTest {
	static final int SEEDS = 200;
	static final int RULE_SEEDS = 400;

	@GameTest(maxTicks = 200)
	public void floorPlansRoomGraphsCollapseHolesAndTowersVaryOverTwoHundredSeeds(final GameTestHelper helper) {
		Set<String> tilings = new HashSet<>();
		Set<String> graphs = new HashSet<>();
		Set<String> holes = new HashSet<>();
		Set<String> towerCorners = new HashSet<>();
		Set<String> towerHeights = new HashSet<>();
		Set<String> stairs = new HashSet<>();
		for (long seed = 1; seed <= SEEDS; seed++) {
			KeepPlan plan = KeepPlan.of(seed);
			StringBuilder tiling = new StringBuilder();
			StringBuilder graph = new StringBuilder();
			StringBuilder stair = new StringBuilder();
			for (Floor f : plan.floors) {
				for (KeepPlan.Room r : f.rooms()) {
					tiling.append(r.template()).append(r.cells()).append(';');
				}
				for (Door d : f.doors()) {
					graph.append(d.rect()).append(';');
				}
				stair.append(f.up().cell()).append(f.up().west()).append(f.up().north());
			}
			StringBuilder h = new StringBuilder();
			for (Hole hole : plan.holes) {
				h.append(hole.floor()).append(hole.rect());
			}
			for (KeepPlan.Breach b : plan.breaches) {
				h.append('B').append(b.floor()).append(b.rect());
			}
			StringBuilder corners = new StringBuilder();
			StringBuilder heights = new StringBuilder();
			for (KeepPlan.Tower t : plan.towers) {
				corners.append(t.corner());
				heights.append(t.corner()).append(t.top());
			}
			tilings.add(tiling.toString());
			graphs.add(graph.toString());
			holes.add(h.toString());
			towerCorners.add(corners.toString());
			towerHeights.add(heights.toString());
			stairs.add(stair.toString());
		}
		KeepTestSupport.result("planVariety", "seeds=" + SEEDS + " tilings=" + tilings.size() + " doorGraphs=" + graphs.size() + " holeSets=" + holes.size()
			+ " towerCornerSets=" + towerCorners.size() + " towerHeightSets=" + towerHeights.size() + " stairChains=" + stairs.size());
		helper.assertTrue(tilings.size() > SEEDS / 2, "only " + tilings.size() + " distinct floor plans in " + SEEDS + " seeds");
		helper.assertTrue(graphs.size() > SEEDS / 2, "only " + graphs.size() + " distinct room graphs in " + SEEDS + " seeds");
		helper.assertTrue(holes.size() > SEEDS / 2, "only " + holes.size() + " distinct collapse sets in " + SEEDS + " seeds");
		helper.assertTrue(towerCorners.size() == 11, "the towers took " + towerCorners.size() + " of the 11 corner sets");
		helper.assertTrue(towerHeights.size() > SEEDS / 2, "only " + towerHeights.size() + " distinct tower sets in " + SEEDS + " seeds");
		helper.assertTrue(stairs.size() > 20, "only " + stairs.size() + " distinct stair chains");
		helper.succeed();
	}

	@GameTest(maxTicks = 400)
	public void everyPlanKeepsTheClimbValid(final GameTestHelper helper) {
		int hallsChecked = 0;
		for (long seed = 1; seed <= RULE_SEEDS; seed++) {
			KeepPlan plan = KeepPlan.of(seed);
			String where = "seed " + seed;
			helper.assertTrue(plan.towers.size() >= 2 && plan.towers.size() <= 4, where + ": " + plan.towers.size() + " towers");
			helper.assertTrue(plan.height() >= 42 && plan.height() <= 48, where + ": height " + plan.height());
			int extra = plan.holes.size() + plan.breaches.size();
			helper.assertTrue(extra >= 2 && extra <= 5, where + ": " + extra + " holes and breaches");
			Cell arrival = KeepPlan.GATE_CELL;
			for (int f = 0; f < 3; f++) {
				Floor floor = plan.floors[f];
				helper.assertTrue(floor.rooms().size() >= 4 && floor.rooms().size() <= 7, where + ": floor " + (f + 1) + " has " + floor.rooms().size() + " rooms");
				helper.assertTrue(floor.arrivalCell().equals(arrival), where + ": floor " + (f + 1) + " arrival mismatch");
				helper.assertTrue(floor.up().cell().manhattan(arrival) >= 2, where + ": floor " + (f + 1) + " stair is " + floor.up().cell().manhattan(arrival) + " cells from its arrival");
				long halls = floor.rooms().stream().filter(r -> r.template() == KeepPlan.Template.HALL).count();
				helper.assertTrue(halls == 1, where + ": floor " + (f + 1) + " has " + halls + " key halls");
				KeepPlan.Room hall = floor.hall();
				helper.assertTrue(!hall.cells().contains(floor.up().cell()) && !hall.cells().contains(arrival), where + ": the key hall of floor " + (f + 1) + " holds a stair");
				helper.assertTrue(floor.roomAt(floor.up().cell()) != null && floor.roomAt(arrival) != null, where + ": a stair cell is rubble");
				helper.assertTrue(floor.markers().stream().filter(m -> m.kind() == Kind.WARDEN).count() == 1, where + ": floor " + (f + 1) + " has no single warden");
				helper.assertTrue(floor.mobCount() <= KeepPlan.FLOOR_CAP, where + ": floor " + (f + 1) + " holds " + floor.mobCount() + " mobs");
				helper.assertTrue(connected(floor), where + ": floor " + (f + 1) + " has a room no door reaches");
				for (KeepPlan.Tower t : plan.towers) {
					helper.assertTrue(floor.roomAt(t.corner().cell()) != null, where + ": a tower corner is rubble on floor " + (f + 1));
				}
				hallsChecked++;
				arrival = floor.up().cell();
			}
			helper.assertTrue(plan.floors[2].up().cell().equals(new Cell(0, 2)) || plan.floors[2].up().cell().equals(new Cell(2, 2)), where + ": the throne stair is not in a south corner cell");
			helper.assertTrue(plan.floors[2].up().north(), where + ": the throne stair does not climb north");
			helper.assertTrue(plan.throneStairWest == plan.floors[2].up().west(), where + ": the throne stair side is off");
		}
		KeepTestSupport.result("climbValid", "seeds=" + RULE_SEEDS + " floorsChecked=" + hallsChecked);
		helper.succeed();
	}

	static boolean connected(final Floor floor) {
		Set<Integer> seen = new HashSet<>();
		List<Integer> stack = new ArrayList<>(List.of(0));
		seen.add(0);
		while (!stack.isEmpty()) {
			int at = stack.remove(stack.size() - 1);
			for (Door d : floor.doors()) {
				int other = d.roomA() == at ? d.roomB() : d.roomB() == at ? d.roomA() : -1;
				if (other >= 0 && seen.add(other)) {
					stack.add(other);
				}
			}
		}
		return seen.size() == floor.rooms().size();
	}

	@GameTest(maxTicks = 400)
	public void aHoleNeverLiesInAStairCellOrBesideADoorwayAndDropsOneFloor(final GameTestHelper helper) {
		int holes = 0;
		for (long seed = 1; seed <= RULE_SEEDS; seed++) {
			KeepPlan plan = KeepPlan.of(seed);
			for (Hole h : plan.holes) {
				holes++;
				helper.assertTrue(h.floor() == 1 || h.floor() == 2, "seed " + seed + ": a hole in slab " + h.floor());
				Rect r = h.rect();
				for (int level : new int[] {h.floor() - 1, h.floor()}) {
					Floor floor = plan.floors[level];
					helper.assertTrue(!r.intersects(floor.up().rect()) && !r.intersects(floor.arriveRect()), "seed " + seed + ": a hole cuts a stair");
					for (int x = r.x(); x <= r.maxX(); x++) {
						for (int z = r.z(); z <= r.maxZ(); z++) {
							Cell cell = new Cell((x - 8) / 11, (z - 6) / 11);
							helper.assertTrue(!cell.equals(floor.up().cell()) && !cell.equals(floor.arrivalCell()), "seed " + seed + ": a hole lies in the stair cell " + cell + " of floor " + (level + 1));
							helper.assertTrue(floor.roomAt(cell) != null, "seed " + seed + ": a hole lies over rubble");
						}
					}
					for (Door d : floor.doors()) {
						helper.assertTrue(!r.grow(1).intersects(d.rect()), "seed " + seed + ": a hole touches a doorway of floor " + (level + 1));
					}
				}
			}
		}
		KeepTestSupport.result("holesClearOfStairsAndDoors", "seeds=" + RULE_SEEDS + " holes=" + holes);
		helper.assertTrue(holes > 200, "only " + holes + " holes in " + RULE_SEEDS + " seeds");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void everyFloorSpawnsAtMostTwelveMobsAndOneWarden(final GameTestHelper helper) {
		int most = 0;
		for (long seed = 1; seed <= RULE_SEEDS; seed++) {
			KeepPlan plan = KeepPlan.of(seed);
			for (int f = 0; f < 4; f++) {
				int mobs = plan.markers(f, Kind.SENTINEL).size() + plan.markers(f, Kind.KNIGHT).size();
				most = Math.max(most, mobs);
				helper.assertTrue(mobs <= KeepPlan.FLOOR_CAP, "seed " + seed + ": floor " + (f + 1) + " spawns " + mobs + " mobs");
				helper.assertTrue(mobs >= 1, "seed " + seed + ": floor " + (f + 1) + " spawns nobody");
			}
		}
		KeepTestSupport.result("floorCap", "cap=" + KeepPlan.FLOOR_CAP + " mostSeen=" + most + " seeds=" + RULE_SEEDS);
		helper.succeed();
	}
}
