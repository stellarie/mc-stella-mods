package com.stella.tower;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * The raised portals: a wall block under the frame on an ordinary and an elite floor, a stepped pyramid on a
 * boss floor, a way up that a body can walk, and bosses that roam the hall untethered.
 */
public class TowerRaisedPortalGameTest {
	private static int ordinary() {
		return TowerTestSupport.freshFloorWhere(f -> TowerMaze.hall(f) == 0);
	}

	private static int elite() {
		return TowerTestSupport.freshFloorWhere(f -> TowerMaze.hall(f) == TowerMaze.ELITE_HALL);
	}

	private static int boss() {
		return TowerTestSupport.freshFloorWhere(f -> TowerMaze.hall(f) == TowerMaze.BOSS_HALL);
	}

	private static void raised(final GameTestHelper helper, final String name, final int floor, final int lift) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		TowerFloors.ensure(tower, floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		int insideY = plan.portalInside().get(0).getY();
		int lowFrame = plan.portalFrame().stream().mapToInt(BlockPos::getY).min().getAsInt();
		int onWall = 0;
		int bottomRow = 0;
		for (BlockPos pos : plan.portalFrame()) {
			if (pos.getY() == lowFrame) {
				bottomRow++;
				onWall += tower.getBlockState(pos.below()).is(TowerBlocks.TOWER_WALL) ? 1 : 0;
			}
		}
		int open = 0;
		for (BlockPos pos : plan.portalInside()) {
			open += tower.getBlockState(pos).getCollisionShape(tower, pos).isEmpty() ? 1 : 0;
		}
		TowerTestSupport.result(name, "floor=" + floor + " insideY=" + insideY + " expected=" + (TowerLayout.BASE_Y + lift) + " frameOnWall=" + onWall + "/" + bottomRow
			+ " insideOpen=" + open + "/" + plan.portalInside().size());
		helper.assertTrue(plan.lift() == lift, "the plan lifts the portal " + plan.lift() + ", not " + lift);
		helper.assertTrue(insideY == TowerLayout.BASE_Y + lift, "the portal's inside is at y " + insideY + ", not " + (TowerLayout.BASE_Y + lift));
		helper.assertTrue(onWall == bottomRow && bottomRow > 0, "only " + onWall + " of the frame's " + bottomRow + " bottom blocks stand on tower wall");
		helper.assertTrue(open == plan.portalInside().size(), "the raised portal's slot is not open: " + open + " of " + plan.portalInside().size());
		helper.succeed();
	}

	@GameTest
	public void anOrdinaryFloorsPortalStandsOnAPlinth(final GameTestHelper helper) {
		raised(helper, "raisedOrdinary", ordinary(), TowerPlan.LIFT);
	}

	@GameTest
	public void anEliteFloorsPortalStandsOnAPlinth(final GameTestHelper helper) {
		raised(helper, "raisedElite", elite(), TowerPlan.LIFT);
	}

	@GameTest
	public void aBossFloorsPortalStandsOnThePyramid(final GameTestHelper helper) {
		raised(helper, "raisedBoss", boss(), TowerPlan.BOSS_LIFT);
	}

	@GameTest
	public void theBossPyramidHasThreeSteppedLayersUnderTheCeiling(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = boss();
		TowerFloors.ensure(tower, floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		Map<Integer, Integer> perLayer = new java.util.TreeMap<>();
		int built = 0;
		for (BlockPos pos : plan.plinth()) {
			perLayer.merge(pos.getY(), 1, Integer::sum);
			built += tower.getBlockState(pos).is(TowerBlocks.TOWER_WALL) ? 1 : 0;
		}
		boolean narrowing = true;
		int previous = Integer.MAX_VALUE;
		for (int count : perLayer.values()) {
			narrowing &= count < previous;
			previous = count;
		}
		int frameTop = plan.portalFrame().stream().mapToInt(BlockPos::getY).max().getAsInt();
		int frameLow = plan.portalFrame().stream().mapToInt(BlockPos::getY).min().getAsInt();
		TowerTestSupport.result("bossPyramid", "floor=" + floor + " layers=" + perLayer + " built=" + built + "/" + plan.plinth().size()
			+ " frameLow=" + frameLow + " frameTop=" + frameTop + " ceiling=" + (TowerLayout.BASE_Y + TowerLayout.HEIGHT));
		helper.assertTrue(perLayer.size() == TowerPlan.PYRAMID_LAYERS, "the pyramid has " + perLayer.size() + " layers: " + perLayer);
		helper.assertTrue(perLayer.keySet().iterator().next() == TowerLayout.BASE_Y, "the pyramid's first layer is not on the hall floor");
		helper.assertTrue(built == plan.plinth().size() && built > 0, "only " + built + " of the pyramid's " + plan.plinth().size() + " blocks are built");
		helper.assertTrue(narrowing, "the layers do not narrow going up: " + perLayer);
		helper.assertTrue(frameLow == TowerLayout.BASE_Y + TowerPlan.PYRAMID_LAYERS, "the frame sits at y " + frameLow + ", not on the top layer");
		helper.assertTrue(frameTop < TowerLayout.BASE_Y + TowerLayout.HEIGHT, "the frame reaches y " + frameTop + ", into the ceiling");
		helper.succeed();
	}

	/**
	 * A walk over the built blocks, one block up at most per step, with room to stand: from the hall floor to
	 * the portal's inside. Written apart from the plan, so the pyramid is judged by what stands in the world.
	 */
	private static int climb(final ServerLevel tower, final BlockPos from, final Set<BlockPos> goals) {
		return climb(new Ground() {
			@Override
			public boolean free(final BlockPos pos) {
				return TowerRaisedPortalGameTest.free(tower, pos);
			}

			@Override
			public boolean standable(final BlockPos pos) {
				return tower.getBlockState(pos).isFaceSturdy(tower, pos, net.minecraft.core.Direction.UP);
			}
		}, from, goals);
	}

	/** What a walk asks of the ground: room to stand in a spot, and something to stand on. */
	private interface Ground {
		boolean free(BlockPos pos);

		boolean standable(BlockPos pos);
	}

	private static int climb(final Ground ground, final BlockPos from, final Set<BlockPos> goals) {
		Map<BlockPos, Integer> seen = new HashMap<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		seen.put(from, 0);
		queue.add(from);
		while (!queue.isEmpty()) {
			BlockPos at = queue.poll();
			int walked = seen.get(at);
			if (goals.contains(at)) {
				return walked;
			}
			for (net.minecraft.core.Direction way : net.minecraft.core.Direction.Plane.HORIZONTAL) {
				for (int rise = 1; rise >= -1; rise--) {
					BlockPos next = at.relative(way).above(rise);
					if (seen.containsKey(next) || TowerLayout.floorAt(next) != TowerLayout.floorAt(from)) {
						continue;
					}
					boolean room = ground.free(next) && ground.free(next.above());
					boolean floor = ground.standable(next.below());
					// A step up needs room to jump: the block over the head of the spot it is taken from.
					boolean jump = rise <= 0 || ground.free(at.above(2));
					// A step down or across passes through the cell beside, at the same height.
					boolean clear = rise >= 0 || ground.free(at.relative(way)) && ground.free(at.relative(way).above());
					if (room && floor && jump && clear) {
						seen.put(next, walked + 1);
						queue.add(next);
					}
				}
			}
		}
		return -1;
	}

	/**
	 * The blocks a plan stands as, for a walk over hundreds of plans that could not each be built: the maze's walls
	 * from floor to ceiling, the plinth, the frame's blocks, the workstations, the safe room's lintels over its
	 * openings. Solid floor under the room, solid outside it.
	 */
	private static Ground modelOf(final TowerPlan plan) {
		BlockPos o = plan.origin();
		Set<BlockPos> solid = new HashSet<>(plan.plinth());
		solid.addAll(plan.portalFrame());
		solid.addAll(TowerFloors.workstations(plan).keySet());
		for (TowerPlan.Gate gate : plan.gates()) {
			for (TowerPlan.Spot cell : gate.cells()) {
				for (int y = TowerFloors.GATE_HEIGHT; y < TowerLayout.HEIGHT; y++) {
					solid.add(o.offset(cell.x(), y, cell.z()));
				}
			}
		}
		return new Ground() {
			private boolean solid(final BlockPos pos) {
				int x = pos.getX() - o.getX();
				int y = pos.getY() - o.getY();
				int z = pos.getZ() - o.getZ();
				if (x < 0 || z < 0 || x >= TowerLayout.SIZE || z >= TowerLayout.SIZE || y < 0 || y >= TowerLayout.HEIGHT) {
					return true;
				}
				return plan.walls()[x][z] || solid.contains(pos);
			}

			@Override
			public boolean free(final BlockPos pos) {
				return !solid(pos);
			}

			@Override
			public boolean standable(final BlockPos pos) {
				return solid(pos);
			}
		};
	}

	private static boolean free(final ServerLevel tower, final BlockPos pos) {
		return tower.getBlockState(pos).getCollisionShape(tower, pos).isEmpty();
	}

	@GameTest
	public void thePyramidCanBeWalkedUpOneBlockAtATime(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = boss();
		TowerFloors.ensure(tower, floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		BlockPos start = TowerElites.post(plan);
		Set<BlockPos> goals = new HashSet<>(plan.portalInside());
		int steps = climb(tower, start, goals);
		// The walk is not a cheat: with the pyramid's middle layer gone the same walk finds no way.
		Set<BlockPos> removed = new HashSet<>();
		for (BlockPos pos : plan.plinth()) {
			if (pos.getY() == TowerLayout.BASE_Y + 1) {
				removed.add(pos);
				tower.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			}
		}
		int without = climb(tower, start, goals);
		for (BlockPos pos : removed) {
			tower.setBlock(pos, TowerBlocks.TOWER_WALL.defaultBlockState(), 2);
		}
		int restored = climb(tower, start, goals);
		TowerTestSupport.result("pyramidWalk", "floor=" + floor + " from=" + start + " steps=" + steps + " withoutMiddleLayer=" + without + " restored=" + restored
			+ " layers=" + TowerPlan.PYRAMID_LAYERS);
		helper.assertTrue(steps >= TowerPlan.PYRAMID_LAYERS + 1, "the walk to the portal takes " + steps + " steps, fewer than the " + (TowerPlan.PYRAMID_LAYERS + 1) + " climbs it needs");
		helper.assertTrue(without < 0, "the walk still finds a way with a layer missing, so it proves nothing");
		helper.assertTrue(restored == steps, "the pyramid was not put back");
		helper.succeed();
	}

	private static final int WALK_SEEDS = 25;
	private static final int WALK_FLOORS = 30;

	/**
	 * The whole way on foot, from the arrival to the raised portal, over hundreds of plans of every kind: both sides the
	 * portal is entered from, the portal in the first and the last cell column, the elite and boss halls at every turn,
	 * and through the boss room's two-high openings.
	 */
	@GameTest
	public void everyPlansPortalCanBeWalkedToFromTheArrivalOnFoot(final GameTestHelper helper) {
		int plans = 0;
		int stuck = 0;
		int[] byFacing = new int[2];
		int edgeLow = 0;
		int edgeHigh = 0;
		int elites = 0;
		int bosses = 0;
		Set<Integer> turns = new java.util.TreeSet<>();
		String first = "";
		for (int i = 0; i < WALK_SEEDS; i++) {
			long seed = 7_919L * i + 3L;
			for (int floor = 1; floor <= WALK_FLOORS; floor++) {
				TowerPlan plan = TowerPlan.of(seed, floor);
				plans++;
				BlockPos start = BlockPos.containing(plan.arrivalPos());
				int steps = climb(modelOf(plan), start, new HashSet<>(plan.portalInside()));
				if (steps < 0) {
					stuck++;
					first = first.isEmpty() ? seed + "/" + floor : first;
				}
				byFacing[plan.portalFacing() < 0 ? 0 : 1] += plan.hall() == 0 ? 1 : 0;
				edgeLow += plan.hall() == 0 && plan.portalCell()[0] == 0 ? 1 : 0;
				edgeHigh += plan.hall() == 0 && plan.portalCell()[0] == TowerMaze.CELLS - 1 ? 1 : 0;
				elites += plan.hall() == TowerMaze.ELITE_HALL ? 1 : 0;
				if (plan.hall() == TowerMaze.BOSS_HALL) {
					bosses++;
					turns.add(plan.rotation());
				}
			}
		}
		TowerTestSupport.result("planWalks", "plans=" + plans + " stuck=" + stuck + " facingLow=" + byFacing[0] + " facingHigh=" + byFacing[1] + " firstColumn=" + edgeLow
			+ " lastColumn=" + edgeHigh + " elites=" + elites + " bosses=" + bosses + " turns=" + turns + " first=" + first);
		helper.assertTrue(byFacing[0] >= 100 && byFacing[1] >= 100 && edgeLow >= 20 && edgeHigh >= 20 && elites >= 50 && bosses >= 50 && turns.size() == 4,
			"the case proves little: " + byFacing[0] + "/" + byFacing[1] + " facings, edges " + edgeLow + "/" + edgeHigh + ", " + elites + " elites, " + bosses + " bosses at turns " + turns);
		helper.assertTrue(stuck == 0, stuck + " of " + plans + " plans have no walk from arrival to portal on foot, first " + first);
		helper.succeed();
	}

	/**
	 * The same walk over floors the real builder built: two for each of the four ends an ordinary portal can have
	 * (either side it is entered from, and the first and last cell column), and four elite and four boss floors. The
	 * model of a plan's blocks must agree with the world, step for step.
	 */
	@GameTest(maxTicks = 100)
	public void builtFloorsPortalsCanBeWalkedToFromTheArrivalOnFoot(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		long seed = tower.getSeed();
		int start = TowerTestSupport.freshBlock(400);
		int[] wanted = {2, 2, 2, 2, 4, 4};
		int[] got = new int[6];
		List<Integer> floors = new java.util.ArrayList<>();
		for (int floor = start; floor < start + 400 - 3; floor++) {
			TowerPlan plan = TowerPlan.of(seed, floor);
			int kind;
			if (plan.hall() == TowerMaze.BOSS_HALL) {
				kind = 5;
			} else if (plan.hall() == TowerMaze.ELITE_HALL) {
				kind = 4;
			} else if (plan.portalCell()[0] == 0) {
				kind = 2;
			} else if (plan.portalCell()[0] == TowerMaze.CELLS - 1) {
				kind = 3;
			} else {
				kind = plan.portalFacing() < 0 ? 0 : 1;
			}
			if (got[kind] < wanted[kind]) {
				got[kind]++;
				floors.add(floor);
			}
		}
		int stuck = 0;
		int disagree = 0;
		int shortest = Integer.MAX_VALUE;
		for (int floor : floors) {
			TowerFloors.ensure(tower, floor);
			TowerPlan plan = TowerPlan.at(tower, floor);
			BlockPos from = BlockPos.containing(plan.arrivalPos());
			Set<BlockPos> goals = new HashSet<>(plan.portalInside());
			int built = climb(tower, from, goals);
			int model = climb(modelOf(plan), from, goals);
			stuck += built < 0 ? 1 : 0;
			disagree += built != model ? 1 : 0;
			shortest = Math.min(shortest, built);
		}
		TowerTestSupport.result("builtWalks", "floors=" + floors.size() + " stuck=" + stuck + " modelDisagrees=" + disagree + " shortest=" + shortest + " kinds=" + java.util.Arrays.toString(got));
		helper.assertTrue(floors.size() == java.util.Arrays.stream(wanted).sum(), "the block held only " + java.util.Arrays.toString(got) + " of " + java.util.Arrays.toString(wanted));
		helper.assertTrue(stuck == 0, stuck + " built floors have no walk from arrival to portal on foot");
		helper.assertTrue(disagree == 0, "the model of a plan's blocks disagrees with the built world on " + disagree + " floors");
		helper.succeed();
	}

	@GameTest
	public void anOrdinaryFloorsRaisedPortalIsReachableByWalking(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = ordinary();
		TowerFloors.ensure(tower, floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		BlockPos start = plan.portalInside().get(0).offset(plan.portalFacing() * 2, -plan.lift(), 0);
		int steps = climb(tower, start, new HashSet<>(plan.portalInside()));
		TowerTestSupport.result("ordinaryWalk", "floor=" + floor + " from=" + start + " steps=" + steps);
		helper.assertTrue(steps > 0, "no walk from " + start + " to the raised portal");
		helper.succeed();
	}

	private static void lights(final GameTestHelper helper, final String name, final int floor) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		TowerFloors.ensure(tower, floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		BlockPos first = plan.portalInside().get(0);
		boolean found = TowerPortalShape.find(tower, first).isPresent();
		tower.setBlock(first, Blocks.FIRE.defaultBlockState(), 3);
		int lit = 0;
		for (BlockPos pos : plan.portalInside()) {
			lit += tower.getBlockState(pos).is(TowerBlocks.TOWER_PORTAL) ? 1 : 0;
		}
		TowerTestSupport.result(name, "floor=" + floor + " insideY=" + first.getY() + " shapeFound=" + found + " lit=" + lit + "/" + plan.portalInside().size());
		helper.assertTrue(found, "the shape finder does not see the raised frame");
		helper.assertTrue(lit == plan.portalInside().size(), "the raised frame lit " + lit + " of " + plan.portalInside().size() + " portal blocks");
		helper.succeed();
	}

	@GameTest
	public void anOrdinaryRaisedFrameLights(final GameTestHelper helper) {
		lights(helper, "litOrdinary", ordinary());
	}

	@GameTest
	public void anEliteRaisedFrameLights(final GameTestHelper helper) {
		lights(helper, "litElite", elite());
	}

	@GameTest
	public void aBossRaisedFrameLights(final GameTestHelper helper) {
		lights(helper, "litBoss", boss());
	}

	@GameTest
	public void aPlayerInTheBossPortalClimbsToTheNextFloor(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = boss();
		TowerFloors.ensure(tower, floor);
		BlockPos inside = TowerLayout.portalInside(tower, floor).get(0);
		tower.setBlock(inside, Blocks.FIRE.defaultBlockState(), 3);
		ServerPlayer climber = TowerTestSupport.join(helper, 1.5, 1.5);
		climber.teleportTo(tower, inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
		TeleportTransition next = TowerBlocks.TOWER_PORTAL.getPortalDestination(tower, climber, inside);
		TowerTestSupport.leave(helper, climber);
		double off = next == null ? -1.0 : next.position().distanceTo(TowerLayout.arrival(tower, floor + 1));
		TowerTestSupport.result("bossPortalClimbs", "floor=" + floor + " insideY=" + inside.getY() + " off=" + off);
		helper.assertTrue(next != null && off < 0.01 && next.newLevel() == tower, "the raised portal leads " + off + " from floor " + (floor + 1) + "'s arrival");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void theBossPostAndItsRiseAreOutsideThePyramid(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = boss();
		TowerFloors.ensure(tower, floor);
		TowerElites.pinGuardianKind(floor, EntityTypes.ZOMBIE);
		TowerPlan plan = TowerPlan.at(tower, floor);
		Set<BlockPos> pyramid = new HashSet<>(plan.plinth());
		BlockPos post = TowerElites.post(plan);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		int[] tries = {0};
		helper.succeedWhen(() -> {
			Mob boss = TowerElites.guardian(tower, floor, TowerRank.BOSS);
			helper.assertTrue(boss != null, "no boss rose, so the case proves nothing");
			BlockPos feet = boss.blockPosition();
			boolean inside = false;
			for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(boss.getBoundingBox().minX, boss.getBoundingBox().minY, boss.getBoundingBox().minZ),
				BlockPos.containing(boss.getBoundingBox().maxX - 1.0E-4, boss.getBoundingBox().maxY - 1.0E-4, boss.getBoundingBox().maxZ - 1.0E-4))) {
				inside |= pyramid.contains(pos);
			}
			TowerElites.pinGuardianKind(floor, null);
			TowerTestSupport.result("bossPostClear", "floor=" + floor + " post=" + post + " postY=" + post.getY() + " boss=" + feet + " insidePyramid=" + inside
				+ " postInPyramid=" + pyramid.contains(post));
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(post.getY() == TowerLayout.BASE_Y, "the post is at y " + post.getY() + ", not on the hall floor");
			helper.assertFalse(pyramid.contains(post) || pyramid.contains(post.above()), "the post is inside the pyramid");
			helper.assertFalse(inside, "the boss spawned inside the pyramid at " + feet);
		});
	}

	/**
	 * A boss moved far from its post is not walked or snapped back. It has no AI, so nothing but the tether
	 * could move it or send it walking; the window is two checks of the tether's cadence and more.
	 */
	@GameTest(maxTicks = 300)
	public void aBossFarFromItsPostIsNotPulledBack(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = boss();
		TowerFloors.ensure(tower, floor);
		TowerElites.pinGuardianKind(floor, EntityTypes.ZOMBIE);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		BlockPos post = TowerElites.post(tower, floor);
		int window = TowerElites.CHECK_TICKS * 3;
		int far = TowerElites.GUARD_RADIUS + 6;
		long[] movedAt = {-1};
		Vec3[] spot = {null};
		helper.succeedWhen(() -> {
			Mob boss = TowerElites.guardian(tower, floor, TowerRank.BOSS);
			helper.assertTrue(boss != null, "no boss rose, so the case proves nothing");
			if (movedAt[0] < 0) {
				movedAt[0] = helper.getTick();
				boss.setNoAi(true);
				spot[0] = Vec3.atBottomCenterOf(post.offset(0, 0, far));
				boss.snapTo(spot[0].x, spot[0].y, spot[0].z, 0.0F, 0.0F);
				boss.getNavigation().stop();
			}
			helper.assertTrue(helper.getTick() - movedAt[0] >= window, "the window of " + window + " ticks has not run");
			double drift = boss.position().distanceTo(spot[0]);
			boolean idle = boss.getNavigation().isDone();
			double fromPost = boss.position().distanceTo(Vec3.atBottomCenterOf(post));
			TowerElites.pinGuardianKind(floor, null);
			TowerTestSupport.result("bossRoams", "floor=" + floor + " window=" + window + " fromPost=" + fromPost + " drift=" + drift + " navigationIdle=" + idle);
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(fromPost > TowerElites.GUARD_RADIUS, "the boss was pulled to " + fromPost + " blocks from its post");
			helper.assertTrue(drift == 0.0, "the boss was snapped " + drift + " blocks");
			helper.assertTrue(idle, "the boss was sent walking back to its post");
		});
	}

	private static void legacyStaysFlat(final GameTestHelper helper, final String name, final int floor) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		TowerFloors.ensure(tower, floor, TowerPlan.legacy(tower.getSeed(), floor));
		TowerPlan plan = TowerPlan.at(tower, floor);
		BlockPos inside = plan.portalInside().get(0);
		int walls = 0;
		for (int dx = -TowerPlan.PYRAMID_LAYERS; dx <= TowerPlan.PYRAMID_LAYERS; dx++) {
			for (int dz = -2; dz <= 3; dz++) {
				BlockPos at = inside.offset(dx, 0, dz);
				walls += tower.getBlockState(at).is(TowerBlocks.TOWER_WALL) ? 1 : 0;
			}
		}
		boolean flat = tower.getBlockState(inside.below()).is(TowerBlocks.TOWER_FRAME);
		TowerTestSupport.result(name, "floor=" + floor + " version=" + plan.version() + " lift=" + plan.lift() + " insideY=" + inside.getY()
			+ " plinth=" + plan.plinth().size() + " wallBlocksAround=" + walls + " frameFlush=" + flat + " postY=" + TowerElites.post(tower, floor).getY());
		helper.assertTrue(plan.version() == 0 && plan.lift() == 0 && plan.plinth().isEmpty(), "a legacy floor's portal was lifted");
		helper.assertTrue(inside.getY() == TowerLayout.BASE_Y && flat, "the legacy portal is not flush with the floor");
		helper.assertTrue(walls == 0, walls + " wall blocks stand around the legacy portal");
		helper.assertTrue(TowerElites.post(tower, floor).getY() == TowerLayout.BASE_Y, "the legacy post moved");
		helper.succeed();
	}

	@GameTest
	public void aLegacyOrdinaryFloorKeepsItsFlatPortal(final GameTestHelper helper) {
		legacyStaysFlat(helper, "legacyFlatOrdinary", ordinary());
	}

	@GameTest
	public void aLegacyBossFloorKeepsItsFlatPortalAndNoPyramid(final GameTestHelper helper) {
		legacyStaysFlat(helper, "legacyFlatBoss", boss());
	}
}
