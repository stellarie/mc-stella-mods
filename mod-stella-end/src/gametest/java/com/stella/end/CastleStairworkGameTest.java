package com.stella.end;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** The flights between the floors: rails along both sides, a place against a wall, and nothing of the first flight in sight of the way in. */
public class CastleStairworkGameTest {
	/** The last step whose rail, two blocks over the step, still stands under the slab above. */
	private static final int LAST_RAIL_STEP = CastleLayout.Flight.HOLE_FROM + 1;
	/** A flight lies against a wall when one is this many cells from its outer lane, on one whole side. */
	private static final int WALL_REACH = 3;
	/** The eye of a standing player over the floor under the feet. */
	private static final double EYE = 1.62;

	@GameTest(environment = "mod-stella-end-test:stairwork_rails", maxTicks = 40)
	public void everyFlightHasALowWallOnBothSidesOfEveryStepThatAWallADoorwayOrAnotherRunDoesNotCover(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		CastleLayout layout = EndCastles.layout(end);
		StringBuilder bad = new StringBuilder();
		int rails = 0;
		int flights = 0;
		for (int floor = 1; floor <= EndCastle.FLOORS; floor++) {
			CastleLayout.FloorPlan plan = layout.floor(floor);
			int slab = EndCastle.slabY(floor);
			for (CastleLayout.Flight flight : plan.ups()) {
				flights++;
				for (int k = 0; k <= LAST_RAIL_STEP; k++) {
					for (int rail : new int[] {CastleLayout.Flight.RAIL_LOW, CastleLayout.Flight.RAIL_HIGH}) {
						int x = flight.x(k, rail);
						int z = flight.z(k, rail);
						if (plan.kind(x, z) != CastleLayout.OPEN || plan.walkedByAnother(flight, x, z)) {
							continue;
						}
						rails++;
						BlockState state = EndTestSupport.state(end, EndCastle.min(), x, slab + 2 + k, z);
						if (!state.is(Blocks.END_STONE_BRICK_WALL)) {
							bad.append(" floor ").append(floor).append(" step ").append(k).append(" side ").append(rail).append(" is ").append(state);
						}
					}
				}
			}
		}
		EndTestSupport.result("stairworkRails", "flights=" + flights + " rails=" + rails + " bad=" + bad.length());
		helper.assertTrue(flights == 9, "the plan holds " + flights + " flights");
		helper.assertTrue(rails > 100, "only " + rails + " rail cells were checked");
		helper.assertTrue(bad.isEmpty(), "steps without a rail:" + bad);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:stairwork_wall", maxTicks = 40)
	public void everyFlightLiesAgainstAWallAlongOneWholeSideForEverySeed(final GameTestHelper helper) {
		StringBuilder bad = new StringBuilder();
		int seeds = 100;
		for (long seed = 0; seed < seeds; seed++) {
			CastleLayout layout = CastleLayout.generate(seed);
			for (int floor = 1; floor <= EndCastle.FLOORS; floor++) {
				CastleLayout.FloorPlan plan = layout.floor(floor);
				for (CastleLayout.Flight flight : plan.ups()) {
					boolean low = backed(plan, flight, -1, -1);
					boolean high = backed(plan, flight, CastleLayout.Flight.WIDTH, 1);
					if (!low && !high) {
						bad.append(" seed ").append(seed).append(" floor ").append(floor).append(" flight at ").append(flight.startX()).append(',').append(flight.startZ()).append(" stands mid-room");
					}
				}
			}
		}
		EndTestSupport.result("stairworkWall", "seeds=" + seeds + " bad=" + bad.length());
		helper.assertTrue(bad.isEmpty(), "flights away from every wall:" + bad);
		helper.succeed();
	}

	/** Whether, at every step of the run, a wall or a doorway line lies within {@link #WALL_REACH} cells outward on one side. */
	private static boolean backed(final CastleLayout.FloorPlan plan, final CastleLayout.Flight flight, final int firstLane, final int outward) {
		for (int k = 0; k < CastleLayout.Flight.STEPS; k++) {
			boolean wall = false;
			for (int away = 0; away < WALL_REACH && !wall; away++) {
				int lane = firstLane + outward * away;
				int x = flight.x(k, lane);
				int z = flight.z(k, lane);
				wall = x < 0 || z < 0 || x >= EndCastle.SIZE || z >= EndCastle.SIZE || plan.kind(x, z) != CastleLayout.OPEN;
			}
			if (!wall) {
				return false;
			}
		}
		return true;
	}

	@GameTest(environment = "mod-stella-end-test:stairwork_hidden_plan", maxTicks = 60)
	public void noCellOfTheFirstFlightShowsFromTheGateOrThePortalLandingForEverySeed(final GameTestHelper helper) {
		StringBuilder bad = new StringBuilder();
		int seeds = 300;
		int looks = 0;
		for (long seed = 0; seed < seeds; seed++) {
			CastleLayout.FloorPlan plan = CastleLayout.generate(seed).floor(1);
			for (double[] from : CastleLayout.entrances()) {
				for (CastleLayout.Flight flight : plan.ups()) {
					looks++;
					if (CastleLayout.shows(plan, flight, from[0], from[1])) {
						bad.append(" seed ").append(seed).append(" shows from ").append(from[0]).append(',').append(from[1]);
					}
				}
			}
		}
		EndTestSupport.result("stairworkHiddenPlan", "seeds=" + seeds + " looks=" + looks + " bad=" + bad.length());
		helper.assertTrue(bad.isEmpty(), "the first flight shows:" + bad);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:stairwork_hidden_built", maxTicks = 80)
	public void noStepOfTheFirstFlightIsInSightFromTheGateOrThePortalLandingInTheBuiltCastles(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		CastleLayout other = CastleBuildGameTest.other(helper);
		StringBuilder bad = new StringBuilder();
		int looks = 0;
		int clear = 0;
		for (int castle = 0; castle < 2; castle++) {
			CastleLayout layout = castle == 0 ? EndCastles.layout(end) : other;
			BlockPos min = castle == 0 ? EndCastle.min() : CastleBuildGameTest.OTHER_MIN;
			for (double[] from : CastleLayout.entrances()) {
				Vec3 eye = new Vec3(min.getX() + from[0], EndCastle.standY(1) + EYE, min.getZ() + from[1]);
				for (CastleLayout.Flight flight : layout.floor(1).ups()) {
					for (int k = 0; k < CastleLayout.Flight.STEPS; k++) {
						for (int lane = 0; lane < CastleLayout.Flight.WIDTH; lane++) {
							Vec3 step = new Vec3(min.getX() + flight.x(k, lane) + 0.5, EndCastle.slabY(1) + 2 + k, min.getZ() + flight.z(k, lane) + 0.5);
							looks++;
							if (sees(end, eye, step)) {
								clear++;
								bad.append(" castle ").append(castle).append(" from ").append(from[0]).append(',').append(from[1]).append(" sees step ").append(k).append(" lane ").append(lane);
							}
						}
					}
				}
			}
		}
		EndTestSupport.result("stairworkHiddenBuilt", "looks=" + looks + " clear=" + clear);
		EndTestSupport.fresh(helper);
		helper.assertTrue(looks > 0, "nothing was looked at");
		helper.assertTrue(bad.isEmpty(), "steps in sight:" + bad);
		helper.succeed();
	}

	/** A sight line that no opaque block cuts, tested every tenth of a block; the block that holds the target and the one that holds the eye never cut it. */
	static boolean sees(final ServerLevel end, final Vec3 from, final Vec3 to) {
		Vec3 line = to.subtract(from);
		int steps = (int) Math.ceil(line.length() / 0.1);
		BlockPos start = BlockPos.containing(from);
		BlockPos goal = BlockPos.containing(to);
		for (int i = 1; i < steps; i++) {
			BlockPos at = BlockPos.containing(from.add(line.scale(i / (double) steps)));
			if (at.equals(start) || at.equals(goal)) {
				continue;
			}
			if (end.getBlockState(at).isSolidRender()) {
				return false;
			}
		}
		return true;
	}
}
