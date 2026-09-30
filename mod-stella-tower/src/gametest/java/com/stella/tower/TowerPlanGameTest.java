package com.stella.tower;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** The floor plan: random ends with a guaranteed path, the plan as built, and the old layout kept. */
public class TowerPlanGameTest {
	private static final int S = TowerLayout.SIZE;

	/** Seeds and floors the property test draws: at least two thousand pairs. */
	private static final int SEEDS = 40;
	private static final int FLOORS = 60;

	private static long seedAt(final int index) {
		return 1_000_003L * index + 17L;
	}

	/**
	 * Fingerprints of the classic walls, taken from the code as it stood before the plans existed: a hash of
	 * the whole wall array for (seed, floor). A legacy plan must give exactly these.
	 */
	private static final long[][] OLD_WALLS = {
		{12345L, 7L, 8391411071748500074L},
		{12345L, 1L, -3231825072507713880L},
		{12345L, 5L, 1654065078783224120L},
		{12345L, 10L, 3510796471186295787L},
		{12345L, 20L, -563737311668365414L},
		{12345L, 3L, 5002486067388449886L},
		{-987654321L, 7L, 4695082212232137879L},
		{-987654321L, 1L, -4362123326015061068L},
		{-987654321L, 5L, 1520498405519526906L},
		{-987654321L, 10L, -5012008666906391412L},
		{-987654321L, 20L, 5724359630228979283L},
		{-987654321L, 3L, 8842875750910476780L},
	};

	private static long fingerprint(final boolean[][] walls) {
		long hash = 1125899906842597L;
		for (int x = 0; x < S; x++) {
			for (int z = 0; z < S; z++) {
				hash = 31 * hash + (walls[x][z] ? 1 : 0);
			}
		}
		return hash;
	}

	/**
	 * Steps from the arrival to the portal over the plan's open spots, the frame's posts in the way, or -1.
	 * Written apart from the plan's own walk, so the plan is not checked by itself.
	 */
	private static int walk(final TowerPlan plan) {
		BlockPos o = plan.origin();
		Set<Long> posts = new HashSet<>();
		for (BlockPos pos : plan.portalFrame()) {
			// The two posts either side of the portal, on the floor of the maze: not the frame's bottom row under it.
			if (plan.isFramePost(pos.getX() - o.getX(), pos.getZ() - o.getZ())) {
				posts.add(((long) (pos.getX() - o.getX()) << 32) | (pos.getZ() - o.getZ()));
			}
		}
		int[][] seen = new int[S][S];
		for (int[] row : seen) {
			java.util.Arrays.fill(row, -1);
		}
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		seen[plan.arrivalX()][plan.arrivalZ()] = 0;
		queue.add(new int[] {plan.arrivalX(), plan.arrivalZ()});
		int[][] ways = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!queue.isEmpty()) {
			int[] at = queue.poll();
			if (at[0] == plan.portalX() && at[1] >= plan.portalZ() && at[1] < plan.portalZ() + TowerLayout.PORTAL_INNER_WIDTH) {
				return seen[at[0]][at[1]];
			}
			for (int[] way : ways) {
				int x = at[0] + way[0];
				int z = at[1] + way[1];
				if (x >= 0 && z >= 0 && x < S && z < S && seen[x][z] < 0 && !plan.walls()[x][z] && !posts.contains(((long) x << 32) | z)) {
					seen[x][z] = seen[at[0]][at[1]] + 1;
					queue.add(new int[] {x, z});
				}
			}
		}
		return -1;
	}

	/** How many distinct arrival spots an ordinary floor's rule allows: the outer cells of a half, on either axis. */
	private static int possibleEnds() {
		int along = 2 * TowerPlan.END_CELLS;
		return 2 * along * TowerMaze.CELLS - along * along;
	}

	/**
	 * Every one of thousands of (seed, floor) plans has a walk from its arrival to its portal, and the ends
	 * really vary: the arrivals and the portals take every spot the rule allows.
	 */
	@GameTest
	public void everyPlanHasAWalkFromItsArrivalToItsPortalAndTheEndsVary(final GameTestHelper helper) {
		Set<String> arrivals = new HashSet<>();
		Set<String> portals = new HashSet<>();
		int pairs = 0;
		int noWalk = 0;
		int walled = 0;
		int fallbacks = 0;
		int reseeded = 0;
		int ordinary = 0;
		int opposite = 0;
		int unzoned = 0;
		int hallOff = 0;
		int winding = 0;
		String first = "";
		for (int i = 0; i < SEEDS; i++) {
			long seed = seedAt(i);
			for (int floor = 1; floor <= FLOORS; floor++) {
				TowerPlan plan = TowerPlan.of(seed, floor);
				pairs++;
				int steps = walk(plan);
				if (steps < 0) {
					noWalk++;
					first = first.isEmpty() ? seed + "/" + floor : first;
				}
				walled += plan.walls()[plan.arrivalX()][plan.arrivalZ()] || plan.walls()[plan.portalX()][plan.portalZ()] ? 1 : 0;
				fallbacks += plan.attempt() == TowerPlan.FALLBACK ? 1 : 0;
				reseeded += plan.attempt() > 0 ? 1 : 0;
				unzoned += plan.inSafeZone(plan.arrivalX(), plan.arrivalZ()) ? 0 : 1;
				arrivals.add(plan.arrivalX() + "," + plan.arrivalZ());
				portals.add(plan.portalX() + "," + plan.portalZ());
				if (plan.hall() == 0) {
					ordinary++;
					boolean acrossX = plan.arrivalX() < S / 2 != plan.portalX() < S / 2;
					boolean acrossZ = plan.arrivalZ() < S / 2 != plan.portalZ() < S / 2;
					opposite += acrossX || acrossZ ? 1 : 0;
					int straight = Math.abs(plan.portalX() - plan.arrivalX()) + Math.abs(plan.portalZ() - plan.arrivalZ());
					winding += steps > straight ? 1 : 0;
				} else {
					hallOff += plan.portalX() == S / 2 && plan.portalZ() == TowerPlan.CLASSIC_MIDDLE_Z - 1
						&& plan.portalX() >= plan.hallLow() && plan.portalX() < plan.hallLow() + plan.hall() ? 0 : 1;
				}
			}
		}
		TowerTestSupport.result("planProperty", "pairs=" + pairs + " noWalk=" + noWalk + " endsInWalls=" + walled + " fallbacks=" + fallbacks
			+ " reseeded=" + reseeded + " ordinary=" + ordinary + " oppositeHalves=" + opposite + " hallPortalsOff=" + hallOff
			+ " arrivalsOutsideZone=" + unzoned + " distinctArrivals=" + arrivals.size() + " distinctPortals=" + portals.size()
			+ " windingShare=" + (winding / (double) ordinary));
		helper.assertTrue(pairs >= 2000, "only " + pairs + " plans were drawn");
		helper.assertTrue(noWalk == 0, noWalk + " plans have no walk from arrival to portal, first " + first);
		helper.assertTrue(walled == 0, walled + " plans put an end inside a wall");
		helper.assertTrue(fallbacks == 0, fallbacks + " plans fell back to the plain maze");
		helper.assertTrue(unzoned == 0, unzoned + " arrivals lie outside every safe zone");
		helper.assertTrue(opposite == ordinary, (ordinary - opposite) + " ordinary plans have their ends on the same half");
		helper.assertTrue(hallOff == 0, hallOff + " hall floors have their portal outside the hall's middle");
		helper.assertTrue(arrivals.size() >= possibleEnds(), "only " + arrivals.size() + " distinct arrivals of the " + possibleEnds() + " the rule allows");
		helper.assertTrue(portals.size() >= possibleEnds(), "only " + portals.size() + " distinct portals of the " + possibleEnds() + " the rule allows");
		helper.assertTrue(winding > ordinary / 2, "only " + winding + " of " + ordinary + " ways wind: the ends are too near");
		helper.succeed();
	}

	/** A plan is pure: drawn twice it is the same, and it is its own for each seed and floor. */
	@GameTest
	public void aPlanIsPureAndItsOwn(final GameTestHelper helper) {
		TowerPlan plan = TowerPlan.of(555L, 42);
		TowerPlan again = TowerPlan.generate(555L, 42, drawn -> false);
		boolean same = plan.arrivalX() == again.arrivalX() && plan.arrivalZ() == again.arrivalZ() && plan.portalX() == again.portalX()
			&& plan.portalZ() == again.portalZ() && java.util.Arrays.deepEquals(plan.walls(), again.walls()) && plan.safeZones().equals(again.safeZones());
		boolean cached = TowerPlan.of(555L, 42) == plan;
		boolean otherFloor = !java.util.Arrays.deepEquals(plan.walls(), TowerPlan.of(555L, 43).walls());
		boolean otherSeed = !java.util.Arrays.deepEquals(plan.walls(), TowerPlan.of(556L, 42).walls());
		TowerTestSupport.result("planPure", "same=" + same + " cached=" + cached + " otherFloor=" + otherFloor + " otherSeed=" + otherSeed
			+ " version=" + plan.version());
		helper.assertTrue(same, "a plan drawn twice is not the same");
		helper.assertTrue(cached, "the plan is not cached");
		helper.assertTrue(otherFloor && otherSeed, "a plan is not its own: otherFloor=" + otherFloor + " otherSeed=" + otherSeed);
		helper.assertTrue(plan.version() == TowerPlan.VERSION && plan.variation() == TowerPlan.Variation.MAZE, "the plan is version " + plan.version());
		helper.succeed();
	}

	/** A draw with no walk is drawn again, up to the shipped number of times, and then the floor falls back. */
	@GameTest
	public void aPlanWithNoWalkIsDrawnAgainThenFallsBackToThePlainMaze(final GameTestHelper helper) {
		int[] calls = {0};
		Set<String> tried = new HashSet<>();
		TowerPlan fallback = TowerPlan.generate(777L, 12, drawn -> {
			calls[0]++;
			tried.add(drawn.arrivalX() + "," + drawn.arrivalZ() + "," + drawn.portalX() + "," + drawn.portalZ());
			return true;
		});
		int[] refused = {0};
		TowerPlan third = TowerPlan.generate(777L, 12, drawn -> refused[0]++ < 3);
		boolean classic = fallback.arrivalX() == TowerPlan.CLASSIC_ARRIVAL_X && fallback.arrivalZ() == TowerPlan.CLASSIC_MIDDLE_Z
			&& fallback.portalX() == TowerPlan.CLASSIC_PORTAL_X && fallback.arrivalYaw() == -90.0F;
		TowerTestSupport.result("planReseed", "draws=" + calls[0] + " distinct=" + tried.size() + " fallbackAttempt=" + fallback.attempt()
			+ " fallbackClassic=" + classic + " fallbackVersion=" + fallback.version() + " thirdAttempt=" + third.attempt() + " walk=" + walk(fallback));
		helper.assertTrue(calls[0] == TowerPlan.ATTEMPTS, "the plan was drawn " + calls[0] + " times, not " + TowerPlan.ATTEMPTS);
		helper.assertTrue(tried.size() == TowerPlan.ATTEMPTS, "the draws were not each their own: " + tried.size() + " distinct");
		helper.assertTrue(fallback.attempt() == TowerPlan.FALLBACK && classic, "the plan did not fall back to the plain maze: attempt " + fallback.attempt());
		helper.assertTrue(fallback.version() == TowerPlan.VERSION && walk(fallback) > 0, "the fallback is not a plan with a walk");
		helper.assertTrue(third.attempt() == 3, "a plan refused three times came from draw " + third.attempt());
		helper.succeed();
	}

	private static int blocksThatDisagree(final ServerLevel tower, final int floor, final TowerPlan plan) {
		BlockPos o = TowerLayout.origin(floor);
		java.util.Set<BlockPos> posts = new HashSet<>();
		for (BlockPos pos : plan.portalFrame()) {
			posts.add(new BlockPos(pos.getX(), TowerLayout.BASE_Y, pos.getZ()));
		}
		java.util.Set<BlockPos> stations = TowerFloors.workstations(plan).keySet();
		int wrong = 0;
		for (int x = 0; x < S; x++) {
			for (int z = 0; z < S; z++) {
				BlockPos at = o.offset(x, 0, z);
				if (posts.contains(at) || stations.contains(at)) {
					continue;
				}
				boolean standing = tower.getBlockState(at).is(TowerBlocks.TOWER_MAZE_WALL);
				wrong += standing == plan.walls()[x][z] ? 0 : 1;
			}
		}
		return wrong;
	}

	private static void builtMatches(final GameTestHelper helper, final String name, final int floor) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		TowerFloors.ensure(tower, floor);
		TowerPlan plan = TowerPlan.of(tower.getSeed(), floor);
		BlockPos o = TowerLayout.origin(floor);
		Vec3 arrival = TowerLayout.arrival(tower, floor);
		BlockPos feet = BlockPos.containing(arrival);
		boolean arrivalAt = arrival.equals(plan.arrivalPos()) && feet.equals(o.offset(plan.arrivalX(), 0, plan.arrivalZ()));
		boolean arrivalClear = tower.getBlockState(feet).isAir() && tower.getBlockState(feet.above()).isAir();
		boolean arrivalZone = tower.getBlockState(feet.below()).is(TowerFloors.SAFE_FLOOR);
		int frame = 0;
		for (BlockPos pos : plan.portalFrame()) {
			frame += tower.getBlockState(pos).is(TowerBlocks.TOWER_FRAME) ? 1 : 0;
		}
		int inside = 0;
		for (BlockPos pos : plan.portalInside()) {
			BlockState state = tower.getBlockState(pos);
			inside += state.getCollisionShape(tower, pos).isEmpty() ? 1 : 0;
		}
		boolean portalAt = TowerLayout.portalInside(tower, floor).equals(plan.portalInside())
			&& TowerLayout.portalX(tower, floor) == plan.portalX() && plan.portalInside().get(0).equals(o.offset(plan.portalX(), plan.lift(), plan.portalZ()));
		int wrong = blocksThatDisagree(tower, floor, plan);
		boolean planned = TowerFloors.isPlanned(tower, floor) && TowerPlan.at(tower, floor).version() == TowerPlan.VERSION;
		TowerTestSupport.result(name, "floor=" + floor + " arrival=" + plan.arrivalX() + "," + plan.arrivalZ() + " portal=" + plan.portalX() + "," + plan.portalZ()
			+ " turns=" + plan.rotation() + " arrivalAt=" + arrivalAt + " arrivalClear=" + arrivalClear + " arrivalZone=" + arrivalZone
			+ " frame=" + frame + "/" + plan.portalFrame().size() + " insideClear=" + inside + "/" + plan.portalInside().size()
			+ " portalAt=" + portalAt + " wallsDisagree=" + wrong + " planned=" + planned);
		helper.assertTrue(planned, "floor " + floor + " was not built to the current plan");
		helper.assertTrue(arrivalAt, "the arrival " + arrival + " is not where the plan puts it");
		helper.assertTrue(arrivalClear && arrivalZone, "the arrival is not clear (" + arrivalClear + ") on a safe zone's floor (" + arrivalZone + ")");
		helper.assertTrue(frame == plan.portalFrame().size(), "the built frame has " + frame + " of the plan's " + plan.portalFrame().size() + " blocks");
		helper.assertTrue(inside == plan.portalInside().size() && portalAt, "the portal's slot is not open at the plan's spot");
		helper.assertTrue(wrong == 0, wrong + " blocks of floor " + floor + " disagree with the plan's walls");
		helper.succeed();
	}

	@GameTest
	public void anOrdinaryFloorIsBuiltToItsPlan(final GameTestHelper helper) {
		builtMatches(helper, "builtOrdinary", TowerTestSupport.freshFloorWhere(f -> TowerMaze.hall(f) == 0));
	}

	@GameTest
	public void anEliteFloorIsBuiltToItsPlan(final GameTestHelper helper) {
		builtMatches(helper, "builtElite", TowerTestSupport.freshFloorWhere(f -> TowerMaze.hall(f) == TowerMaze.ELITE_HALL));
	}

	@GameTest
	public void aBossFloorIsBuiltToItsPlan(final GameTestHelper helper) {
		builtMatches(helper, "builtBoss", TowerTestSupport.freshFloorWhere(f -> TowerMaze.hall(f) == TowerMaze.BOSS_HALL));
	}

	/** A hall floor's arrival and safe room are the canonical ones turned by quarter turns, all four of them turns. */
	@GameTest
	public void aHallFloorTurnsItsArrivalAndSafeRoomByQuarterTurns(final GameTestHelper helper) {
		Set<Integer> turns = new java.util.TreeSet<>();
		int wrong = 0;
		int checked = 0;
		for (int i = 0; i < SEEDS; i++) {
			for (int floor = TowerElites.BOSS_EVERY; floor <= FLOORS; floor += TowerElites.BOSS_EVERY) {
				TowerPlan plan = TowerPlan.of(seedAt(i), floor);
				turns.add(plan.rotation());
				int[] arrival = {TowerPlan.CLASSIC_ARRIVAL_X, TowerPlan.CLASSIC_MIDDLE_Z};
				TowerMaze.Zone room = TowerMaze.classicZones(floor).get(1);
				for (int t = 0; t < plan.rotation(); t++) {
					arrival = TowerPlan.turn(arrival[0], arrival[1]);
					room = TowerPlan.turn(room);
				}
				checked++;
				wrong += arrival[0] == plan.arrivalX() && arrival[1] == plan.arrivalZ() && plan.safeZones().get(1).equals(room)
					&& plan.safeZones().get(0).contains(plan.arrivalX(), plan.arrivalZ()) ? 0 : 1;
			}
		}
		TowerTestSupport.result("hallTurns", "checked=" + checked + " turns=" + turns + " wrong=" + wrong);
		helper.assertTrue(turns.size() == 4, "the hall floors used only the turns " + turns);
		helper.assertTrue(wrong == 0, wrong + " of " + checked + " hall plans are not the canonical layout turned");
		helper.succeed();
	}

	private static void legacyKeeps(final GameTestHelper helper, final String name, final int floor) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		TowerFloors.ensure(tower, floor, TowerPlan.legacy(tower.getSeed(), floor));
		TowerPlan plan = TowerPlan.at(tower, floor);
		BlockPos o = TowerLayout.origin(floor);
		boolean marked = TowerFloors.isPlanned(tower, floor);
		Vec3 arrival = TowerLayout.arrival(tower, floor);
		boolean oldArrival = arrival.equals(new Vec3(o.getX() + TowerPlan.CLASSIC_ARRIVAL_X + 0.5, TowerLayout.BASE_Y, o.getZ() + TowerPlan.CLASSIC_MIDDLE_Z + 0.5))
			&& TowerLayout.arrivalYaw(tower, floor) == -90.0F;
		int oldPortalX = TowerMaze.hall(floor) > 0 ? S / 2 : TowerPlan.CLASSIC_PORTAL_X;
		boolean oldPortal = TowerLayout.portalX(tower, floor) == oldPortalX
			&& TowerLayout.portalInside(tower, floor).get(0).equals(o.offset(oldPortalX, 0, TowerPlan.CLASSIC_MIDDLE_Z - 1));
		int frame = 0;
		for (BlockPos pos : TowerLayout.portalFrame(tower, floor)) {
			frame += tower.getBlockState(pos).is(TowerBlocks.TOWER_FRAME) ? 1 : 0;
		}
		int wrong = blocksThatDisagree(tower, floor, plan);
		boolean oldWalls = java.util.Arrays.deepEquals(plan.walls(), TowerMaze.legacyWalls(tower.getSeed(), floor));
		boolean newPlanDiffers = !java.util.Arrays.deepEquals(plan.walls(), TowerPlan.of(tower.getSeed(), floor).walls());
		TowerTestSupport.result(name, "floor=" + floor + " planMarker=" + marked + " version=" + plan.version() + " oldArrival=" + oldArrival
			+ " oldPortal=" + oldPortal + " frame=" + frame + "/" + plan.portalFrame().size() + " wallsDisagree=" + wrong
			+ " legacyWalls=" + oldWalls + " newPlanWouldDiffer=" + newPlanDiffers);
		helper.assertFalse(marked, "a floor built the old way carries a plan marker");
		helper.assertTrue(plan.version() == 0, "the floor has the version " + plan.version() + " plan, not the legacy one");
		helper.assertTrue(oldArrival && oldPortal, "the old floor's ends moved: arrival " + arrival + " oldArrival=" + oldArrival + " oldPortal=" + oldPortal);
		helper.assertTrue(frame == plan.portalFrame().size() && oldWalls, "the old floor's frame or walls are not the old layout");
		helper.assertTrue(wrong == 0, wrong + " blocks of the old floor disagree with the old layout");
		helper.assertTrue(newPlanDiffers, "the current plan matches the old layout, so the case proves nothing");
		helper.succeed();
	}

	@GameTest
	public void aLegacyMarkedOrdinaryFloorKeepsTheOldLayout(final GameTestHelper helper) {
		legacyKeeps(helper, "legacyOrdinary", TowerTestSupport.freshFloorWhere(f -> TowerMaze.hall(f) == 0));
	}

	@GameTest
	public void aLegacyMarkedEliteFloorKeepsTheOldLayout(final GameTestHelper helper) {
		legacyKeeps(helper, "legacyElite", TowerTestSupport.freshFloorWhere(f -> TowerMaze.hall(f) == TowerMaze.ELITE_HALL));
	}

	/** The legacy plan is the walls the code drew before there were plans, to the last block. */
	@GameTest
	public void theLegacyPlanIsTheOldMazeToTheLastBlock(final GameTestHelper helper) {
		int wrong = 0;
		for (long[] old : OLD_WALLS) {
			TowerPlan plan = TowerPlan.legacy(old[0], (int) old[1]);
			wrong += fingerprint(plan.walls()) == old[2] ? 0 : 1;
		}
		TowerTestSupport.result("legacyFingerprints", "checked=" + OLD_WALLS.length + " wrong=" + wrong);
		helper.assertTrue(wrong == 0, wrong + " of " + OLD_WALLS.length + " legacy mazes differ from the old code's");
		helper.succeed();
	}
}
