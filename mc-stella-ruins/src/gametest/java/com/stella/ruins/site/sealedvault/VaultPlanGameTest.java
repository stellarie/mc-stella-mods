package com.stella.ruins.site.sealedvault;

import com.stella.ruins.site.sealedvault.VaultPlan.Cell;
import com.stella.ruins.site.sealedvault.VaultPlan.Trap;
import com.stella.ruins.site.sealedvault.VaultPlan.TrapKind;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Items 1 and 2 at the plan level: every cell reaches the core, and the random elements vary. */
public class VaultPlanGameTest {
	static final int VARIETY_SEEDS = 200;
	static final int WALK_SEEDS = 12;

	static long seedOf(final int n) {
		return 0x5EA1ED00L + n * 7919L;
	}

	@GameTest
	public void everyCellReachesTheCoreOverTwelveSeeds(final GameTestHelper helper) {
		for (int n = 0; n < WALK_SEEDS; n++) {
			VaultPlan plan = VaultTestSupport.plan(seedOf(n));
			List<Cell> reach = plan.reachable(new Cell(1, plan.entrance()));
			helper.assertTrue(reach.size() == 2 * VaultPlan.CELLS, "seed " + n + " reaches " + reach.size() + " of " + 2 * VaultPlan.CELLS + " cells");
			helper.assertTrue(reach.contains(new Cell(1, VaultPlan.CENTER_CELL)) && reach.contains(new Cell(0, VaultPlan.CENTER_CELL)), "seed " + n + " misses the core or the vault");
		}
		VaultTestSupport.result("planReach", "seeds=" + WALK_SEEDS + " cells=" + 2 * VaultPlan.CELLS + " all reached");
		helper.succeed();
	}

	@GameTest
	public void theMazeAndTheSealRoomsDifferOverTwoHundredSeeds(final GameTestHelper helper) {
		Set<String> mazes = new HashSet<>();
		Set<String> sealSets = new HashSet<>();
		Set<String> choices = new HashSet<>();
		for (int n = 0; n < VARIETY_SEEDS; n++) {
			VaultPlan plan = VaultTestSupport.plan(seedOf(n));
			mazes.add(plan.signature());
			StringBuilder cells = new StringBuilder();
			plan.sealCells().stream().sorted((a, b) -> Integer.compare(a.level() * 100 + a.cell(), b.level() * 100 + b.cell()))
				.forEach(cell -> cells.append(cell.level()).append(':').append(cell.cell()).append(' '));
			sealSets.add(cells.toString());
			choices.add(java.util.Arrays.toString(plan.chosen()));
		}
		VaultTestSupport.result("planVariety", "seeds=" + VARIETY_SEEDS + " mazes=" + mazes.size() + " sealSets=" + sealSets.size() + " choices=" + choices.size());
		helper.assertTrue(mazes.size() == VARIETY_SEEDS, "only " + mazes.size() + " different mazes over " + VARIETY_SEEDS + " seeds");
		helper.assertTrue(sealSets.size() >= VARIETY_SEEDS * 9 / 10, "only " + sealSets.size() + " different seal sets");
		helper.assertTrue(choices.size() == 18, "the 3-of-6 choice drew " + choices.size() + " of its 18 combinations");
		helper.succeed();
	}

	@GameTest
	public void threeOfSixCandidatesHoldASealWithAtLeastOnePerLevel(final GameTestHelper helper) {
		for (int n = 0; n < VARIETY_SEEDS; n++) {
			VaultPlan plan = VaultTestSupport.plan(seedOf(n));
			List<Cell> candidates = plan.candidates();
			helper.assertTrue(candidates.size() == 6 && new HashSet<>(candidates).size() == 6, "seed " + n + " has " + candidates.size() + " candidates");
			int[] chosen = plan.chosen();
			helper.assertTrue(chosen.length == VaultPlan.SEALS && new HashSet<>(List.of(chosen[0], chosen[1], chosen[2])).size() == 3, "seed " + n + " chose " + java.util.Arrays.toString(chosen));
			long levelOne = plan.sealCells().stream().filter(cell -> cell.level() == 1).count();
			helper.assertTrue(levelOne >= 1 && levelOne <= 2, "seed " + n + " put " + levelOne + " seals on level 1");
			for (Cell cell : candidates) {
				helper.assertTrue(plan.isOpen(cell.level(), cell.cell()) && cell.cell() != VaultPlan.CENTER_CELL && !plan.isStair(cell.cell()), "seed " + n + " has a bad candidate " + cell);
				helper.assertTrue(cell.level() != 1 || cell.cell() != plan.entrance(), "seed " + n + " has the entrance as a candidate");
				helper.assertTrue(cell.level() != 0 || cell.cell() != plan.lair(), "seed " + n + " has the lair as a candidate");
			}
		}
		helper.succeed();
	}

	@GameTest
	public void twoToFourTrapCorridorsAppearOverTwoHundredSeeds(final GameTestHelper helper) {
		int[] counts = new int[VaultPlan.MAX_TRAPS + 1];
		EnumSet<TrapKind> kinds = EnumSet.noneOf(TrapKind.class);
		int drops = 0;
		for (int n = 0; n < VARIETY_SEEDS; n++) {
			VaultPlan plan = VaultTestSupport.plan(seedOf(n));
			List<Trap> traps = plan.traps();
			helper.assertTrue(traps.size() >= VaultPlan.MIN_TRAPS && traps.size() <= VaultPlan.MAX_TRAPS, "seed " + n + " has " + traps.size() + " traps");
			counts[traps.size()]++;
			int[] reach0 = plan.distancesFromStairs(0);
			Set<Integer> cells = new HashSet<>();
			for (Trap trap : traps) {
				helper.assertTrue(cells.add(trap.cell()), "seed " + n + " has two traps in cell " + trap.cell());
				kinds.add(trap.kind());
				boolean east = plan.hasLink(trap.level(), trap.cell(), 0);
				boolean west = plan.hasLink(trap.level(), trap.cell(), 2);
				boolean north = plan.hasLink(trap.level(), trap.cell(), 1);
				boolean south = plan.hasLink(trap.level(), trap.cell(), 3);
				boolean straight = trap.alongX() ? east && west && !north && !south : north && south && !east && !west;
				helper.assertTrue(straight, "seed " + n + " trap " + trap + " is not a straight corridor");
				helper.assertTrue(trap.cell() != VaultPlan.CENTER_CELL && !plan.isStair(trap.cell()), "seed " + n + " has a trap in a stair or the core");
				if (trap.kind() == TrapKind.DROP) {
					drops++;
					helper.assertTrue(trap.level() == 1, "seed " + n + " has a drop on level 0");
					int below = trap.cell();
					helper.assertTrue(plan.isOpen(0, below) && reach0[below] >= 0 && reach0[below] <= VaultPlan.DROP_STAIR_REACH,
						"seed " + n + " drop lands " + reach0[below] + " cells from a stair, not within " + VaultPlan.DROP_STAIR_REACH);
					int links = 0;
					for (int dir = 0; dir < 4; dir++) {
						links += plan.hasLink(0, below, dir) ? 1 : 0;
					}
					boolean alongAxis = trap.alongX() ? plan.hasLink(0, below, 0) || plan.hasLink(0, below, 2) : plan.hasLink(0, below, 1) || plan.hasLink(0, below, 3);
					helper.assertTrue(links == 1 && alongAxis, "seed " + n + " drop lands in a room that is not a dead end along the pit axis");
				}
			}
		}
		VaultTestSupport.result("planTraps", "seeds=" + VARIETY_SEEDS + " two=" + counts[2] + " three=" + counts[3] + " four=" + counts[4] + " drops=" + drops);
		helper.assertTrue(counts[2] > 0 && counts[3] > 0 && counts[4] > 0, "the trap count did not cover 2, 3 and 4");
		helper.assertTrue(kinds.size() == 2, "the traps did not cover both kinds: " + kinds);
		helper.succeed();
	}

	@GameTest
	public void twoStairsStandFarApartWithAClosedWestWall(final GameTestHelper helper) {
		for (int n = 0; n < VARIETY_SEEDS; n++) {
			VaultPlan plan = VaultTestSupport.plan(seedOf(n));
			int[] stairs = plan.stairs();
			helper.assertTrue(stairs[0] != stairs[1], "seed " + n + " has one stair twice");
			for (int stair : stairs) {
				helper.assertTrue(!plan.hasLink(0, stair, 2) && !plan.hasLink(1, stair, 2), "seed " + n + " stair " + stair + " has an open west wall");
				helper.assertTrue(stair != VaultPlan.CENTER_CELL && stair != plan.entrance(), "seed " + n + " stair in the core or the entrance cell");
			}
			int apart = Math.abs(stairs[0] % VaultPlan.GRID - stairs[1] % VaultPlan.GRID) + Math.abs(stairs[0] / VaultPlan.GRID - stairs[1] / VaultPlan.GRID);
			helper.assertTrue(apart >= 3, "seed " + n + " stairs only " + apart + " cells apart");
		}
		helper.succeed();
	}

	@GameTest
	public void theLoopsAreTenPercentOfTheRemainingWalls(final GameTestHelper helper) {
		for (int n = 0; n < 40; n++) {
			VaultPlan plan = VaultTestSupport.plan(seedOf(n));
			helper.assertTrue(plan.loops(1) == 4 && plan.loops(0) == 3, "seed " + n + " loops " + plan.loops(1) + "/" + plan.loops(0));
			helper.assertTrue(plan.linkCount(1) == VaultPlan.CELLS - 1 + plan.loops(1), "seed " + n + " level 1 has " + plan.linkCount(1) + " passages");
			helper.assertTrue(plan.linkCount(0) == VaultPlan.CELLS - 2 + plan.loops(0), "seed " + n + " level 0 has " + plan.linkCount(0) + " passages");
		}
		helper.succeed();
	}

	@GameTest
	public void sixteenGuardiansStandInFreeCellsAndTheLairIsFarthestFromTheStairs(final GameTestHelper helper) {
		for (int n = 0; n < VARIETY_SEEDS; n++) {
			VaultPlan plan = VaultTestSupport.plan(seedOf(n));
			List<Cell> guardians = plan.guardians();
			helper.assertTrue(guardians.size() == VaultPlan.GUARDIAN_ROOMS && new HashSet<>(guardians).size() == VaultPlan.GUARDIAN_ROOMS, "seed " + n + " has " + guardians.size() + " guardian cells");
			Set<Cell> blocked = new HashSet<>(plan.candidates());
			blocked.add(plan.archer());
			for (Trap trap : plan.traps()) {
				blocked.add(trap.at());
				if (trap.kind() == TrapKind.DROP) {
					blocked.add(new Cell(0, trap.cell()));
				}
			}
			for (Cell guardian : guardians) {
				helper.assertTrue(plan.isOpen(guardian.level(), guardian.cell()) && !blocked.contains(guardian) && !plan.isStair(guardian.cell())
					&& guardian.cell() != VaultPlan.CENTER_CELL && !(guardian.level() == 0 && guardian.cell() == plan.lair()) && !(guardian.level() == 1 && guardian.cell() == plan.entrance()),
					"seed " + n + " has a guardian in " + guardian);
			}
			int[] reach0 = plan.distancesFromStairs(0);
			int best = 0;
			for (int c = 0; c < VaultPlan.CELLS; c++) {
				if (plan.isOpen(0, c) && !plan.isStair(c)) {
					best = Math.max(best, reach0[c]);
				}
			}
			helper.assertTrue(reach0[plan.lair()] >= best - 1, "seed " + n + " lair is " + reach0[plan.lair()] + " from the stairs, the farthest is " + best);
		}
		helper.succeed();
	}

	@GameTest
	public void theArcherTheTreasureRoomsAndTheInnerVaultDoorFollowThePlan(final GameTestHelper helper) {
		for (int n = 0; n < VARIETY_SEEDS; n++) {
			VaultPlan plan = VaultTestSupport.plan(seedOf(n));
			Cell archer = plan.archer();
			helper.assertTrue(archer.level() == 1 && plan.isOpen(1, archer.cell()) && !plan.isStair(archer.cell()) && archer.cell() != VaultPlan.CENTER_CELL && archer.cell() != plan.entrance()
				&& !plan.candidates().contains(archer) && plan.traps().stream().noneMatch(trap -> trap.at().equals(archer)), "seed " + n + " archer room " + archer);
			List<Cell> treasures = plan.treasures();
			helper.assertTrue(new HashSet<>(treasures).size() == treasures.size(), "seed " + n + " has a treasure room twice");
			helper.assertTrue(treasures.size() >= 5 && treasures.size() <= 5 + VaultPlan.TREASURE_DEAD_ENDS, "seed " + n + " has " + treasures.size() + " treasure rooms");
			helper.assertTrue(treasures.contains(archer) && treasures.contains(new Cell(0, plan.lair())), "seed " + n + " has no treasure in the lair or the archer's room");
			for (int k = 0; k < plan.candidates().size(); k++) {
				helper.assertTrue(plan.holdsSeal(k) != treasures.contains(plan.candidates().get(k)), "seed " + n + " candidate " + k + " holds both or neither of a seal and a treasure");
			}
			for (Trap trap : plan.traps()) {
				helper.assertTrue(!treasures.contains(trap.at()) && !(trap.kind() == TrapKind.DROP && treasures.contains(new Cell(0, trap.cell()))), "seed " + n + " has a treasure in a trap room");
			}
			int door = plan.centerDoorCell();
			helper.assertTrue(plan.isOpen(0, door) && door != VaultPlan.CENTER_CELL && plan.centerDoor() >= 0 && plan.centerDoor() < 4, "seed " + n + " inner vault door leads to " + door);
			helper.assertTrue(plan.neighbors(new Cell(0, VaultPlan.CENTER_CELL)).contains(new Cell(0, door)) && plan.neighbors(new Cell(0, door)).contains(new Cell(0, VaultPlan.CENTER_CELL)), "seed " + n + " the door is not a step of the plan");
		}
		helper.succeed();
	}
}
