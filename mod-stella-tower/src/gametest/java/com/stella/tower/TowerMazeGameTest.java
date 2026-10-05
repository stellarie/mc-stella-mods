package com.stella.tower;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/** Each floor is a maze of two-block paths, with a hall in the middle of an elite or boss floor. */
public class TowerMazeGameTest {
	private static final int S = TowerLayout.SIZE;

	/** Open at a spot of the room: the feet and head blocks both let a body through. */
	private static boolean[][] open(final ServerLevel tower, final int floor) {
		BlockPos o = TowerLayout.origin(floor);
		boolean[][] open = new boolean[S][S];
		for (int x = 0; x < S; x++) {
			for (int z = 0; z < S; z++) {
				BlockPos feet = o.offset(x, 0, z);
				// A chest stands against a wall, off the way: the layout is measured as if it were not there.
				open[x][z] = (tower.getBlockState(feet).getCollisionShape(tower, feet).isEmpty() || tower.getBlockState(feet).is(TowerBlocks.TOWER_CHEST))
					&& tower.getBlockState(feet.above()).getCollisionShape(tower, feet.above()).isEmpty();
			}
		}
		TowerTestSupport.openTheRaisedWay(TowerPlan.at(tower, floor), open);
		return open;
	}

	/** Steps from one spot to another over open ground, or -1 when there is no way. */
	private static int steps(final boolean[][] open, final int fromX, final int fromZ, final int toX, final int toZ) {
		int[][] seen = new int[S][S];
		for (int[] row : seen) {
			Arrays.fill(row, -1);
		}
		Deque<int[]> queue = new ArrayDeque<>();
		seen[fromX][fromZ] = 0;
		queue.add(new int[] {fromX, fromZ});
		int[][] ways = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!queue.isEmpty()) {
			int[] at = queue.poll();
			for (int[] way : ways) {
				int x = at[0] + way[0];
				int z = at[1] + way[1];
				if (x >= 0 && z >= 0 && x < S && z < S && open[x][z] && seen[x][z] < 0) {
					seen[x][z] = seen[at[0]][at[1]] + 1;
					queue.add(new int[] {x, z});
				}
			}
		}
		return seen[toX][toZ];
	}

	/** A labyrinth floor: these cases are about its lower maze's cells, paths and walls, which the other archetypes do not share. */
	private static int ordinaryFloor(final ServerLevel tower) {
		long seed = tower.getSeed();
		int floor = TowerTestSupport.freshFloorWhere(f -> TowerArchetypes.of(seed, f) == TowerArchetype.LABYRINTH);
		TowerFloors.ensure(tower, floor);
		return floor;
	}

	/** At least the walls a perfect maze of the floor's cells leaves standing, less what its clearings take. */
	@GameTest
	public void aFloorIsAMaze(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = ordinaryFloor(tower);
		boolean[][] open = open(tower, floor);
		int walls = 0;
		for (boolean[] row : open) {
			for (boolean spot : row) {
				walls += spot ? 0 : 1;
			}
		}
		int cells = TowerMaze.CELLS * TowerMaze.CELLS;
		int perfect = S * S - cells * TowerMaze.PATH * TowerMaze.PATH - (cells - 1) * TowerMaze.PATH * TowerMaze.WALL;
		// The arrival's safe zone is the one clearing: the portal's cell is a dead end, no nook is opened.
		int safe = TowerMaze.SAFE * TowerMaze.SAFE;
		TowerTestSupport.result("floorMaze", "walls=" + walls + " perfect=" + perfect + " safe=" + safe);
		helper.assertTrue(walls >= perfect - safe, "floor " + floor + " stands " + walls + " wall blocks, fewer than a maze's " + (perfect - safe));
		helper.succeed();
	}

	/** Whether an open square of this side has this spot in it. */
	private static boolean inOpenSquare(final boolean[][] open, final int x, final int z, final int side) {
		for (int ax = x - side + 1; ax <= x; ax++) {
			for (int az = z - side + 1; az <= z; az++) {
				if (ax < 0 || az < 0 || ax + side > S || az + side > S) {
					continue;
				}
				boolean whole = true;
				for (int dx = 0; dx < side && whole; dx++) {
					for (int dz = 0; dz < side && whole; dz++) {
						whole = open[ax + dx][az + dz];
					}
				}
				if (whole) {
					return true;
				}
			}
		}
		return false;
	}

	/** Stair landings use the plan's two-wide rule. */
	private static boolean nearFlight(final TowerPlan plan, final int x, final int z) {
		for (TowerFlight flight : plan.flights()) {
			for (int step = -1; step <= flight.length(); step++) {
				for (int width = 0; width < flight.width(); width++) {
					if (Math.abs(x - flight.cellX(step, width)) <= 1 && Math.abs(z - flight.cellZ(step, width)) <= 1) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** Maze paths keep their width outside the two-wide stair and its landings. */
	@GameTest
	public void everyPathIsWideEnough(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = ordinaryFloor(tower);
		boolean[][] open = open(tower, floor);
		BlockPos o = TowerLayout.origin(floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		// The portal's own slot is no path.
		java.util.Set<Long> slot = TowerLayout.portalInside(tower, floor).stream().map(pos -> ((long) (pos.getX() - o.getX()) << 32) | (pos.getZ() - o.getZ())).collect(java.util.stream.Collectors.toSet());
		int narrow = 0;
		int notFull = 0;
		int spots = 0;
		int stairSpots = 0;
		BlockPos first = null;
		for (int x = 0; x < S; x++) {
			for (int z = 0; z < S; z++) {
				// The portal's slot and the safe zones, workstations and all, are no paths.
				if (!open[x][z] || slot.contains(((long) x << 32) | z) || plan.inSafeZone(x, z)) {
					continue;
				}
				if (nearFlight(plan, x, z)) {
					stairSpots++;
					continue;
				}
				spots++;
				if (!inOpenSquare(open, x, z, TowerMaze.MIN_PATH)) {
					narrow++;
					first = first == null ? new BlockPos(x, 0, z) : first;
				}
				notFull += inOpenSquare(open, x, z, TowerMaze.PATH) ? 0 : 1;
			}
		}
		double full = 1.0 - notFull / (double) spots;
		TowerTestSupport.result("pathWidth", "floor=" + floor + " path=" + TowerMaze.PATH + " min=" + TowerMaze.MIN_PATH + " spots=" + spots
			+ " stairSpots=" + stairSpots + " narrow=" + narrow + " fullShare=" + full + " first=" + first + " flights=" + plan.flights());
		helper.assertTrue(spots < S * S, "the floor has no walls, so the case proves nothing");
		helper.assertTrue(stairSpots > 0, "the floor has no stair landing, so the exception proves nothing");
		helper.assertTrue(narrow == 0, narrow + " spots of floor " + floor + " lie on a path narrower than " + TowerMaze.MIN_PATH + ", first at " + first);
		helper.assertTrue(full >= 0.9, "only " + full + " of the open spots lie on a path " + TowerMaze.PATH + " wide");
		helper.succeed();
	}

	/** The arrival finds the portal over the floor as it stands, by a way no shorter than a straight walk. */
	@GameTest
	public void theArrivalFindsThePortalOverTheBuiltFloor(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = ordinaryFloor(tower);
		BlockPos o = TowerLayout.origin(floor);
		BlockPos arrival = BlockPos.containing(TowerLayout.arrival(tower, floor)).subtract(o);
		BlockPos portal = TowerLayout.portalInside(tower, floor).get(0).subtract(o);
		int steps = steps(open(tower, floor), arrival.getX(), arrival.getZ(), portal.getX(), portal.getZ());
		int straight = Math.abs(portal.getX() - arrival.getX()) + Math.abs(portal.getZ() - arrival.getZ());
		TowerTestSupport.result("mazeWay", "steps=" + steps + " straight=" + straight);
		helper.assertTrue(steps > 0, "no way leads from the arrival to the portal of floor " + floor);
		// A maze's way may happen to run straight between two near ends; how often it winds is measured over
		// thousands of plans in the plan property test, not asserted of one floor drawn afresh each run.
		helper.assertTrue(steps >= straight, "the way to the portal is shorter than a straight walk: " + steps + " steps");
		helper.succeed();
	}

	private static void hallAndPortal(final GameTestHelper helper, final int floor, final int side, final String name) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		TowerFloors.ensure(tower, floor);
		boolean[][] open = open(tower, floor);
		BlockPos o = TowerLayout.origin(floor);
		int low = S / 2 - side / 2;
		int shut = 0;
		for (int x = low; x < low + side; x++) {
			for (int z = low; z < low + side; z++) {
				BlockPos at = o.offset(x, -1, z);
				boolean frame = TowerLayout.portalFrame(tower, floor).stream().anyMatch(pos -> pos.getX() == at.getX() && pos.getZ() == at.getZ());
				shut += open[x][z] || frame ? 0 : 1;
			}
		}
		BlockPos portal = TowerLayout.portalInside(tower, floor).get(0).subtract(o);
		boolean middle = portal.getX() >= low && portal.getX() < low + side && portal.getZ() >= low && portal.getZ() < low + side
			&& Math.abs(portal.getX() - S / 2) <= 1 && Math.abs(portal.getZ() - S / 2) <= 1;
		BlockPos arrival = BlockPos.containing(TowerLayout.arrival(tower, floor)).subtract(o);
		int steps = steps(open, arrival.getX(), arrival.getZ(), low, low + side / 2);
		BlockPos post = TowerElites.post(tower, floor).subtract(o);
		boolean postInHall = post.getX() >= low && post.getX() < low + side && post.getZ() >= low && post.getZ() < low + side;
		TowerTestSupport.result(name, "floor=" + floor + " side=" + side + " shut=" + shut + " portal=" + portal + " middle=" + middle
			+ " steps=" + steps + " postInHall=" + postInHall);
		helper.assertTrue(shut == 0, "the " + side + " block hall of floor " + floor + " has " + shut + " walled spots");
		helper.assertTrue(middle, "the portal of floor " + floor + " stands at " + portal + ", not in the hall's middle");
		helper.assertTrue(steps > 0, "no way leads from the arrival into the hall");
		helper.assertTrue(postInHall, "the guardians' post " + post + " is outside the hall");
		helper.succeed();
	}

	@GameTest
	public void anEliteFloorHasAHallWithThePortalInItsMiddle(final GameTestHelper helper) {
		int floor = TowerTestSupport.freshFloorWhere(f -> f % TowerElites.ELITE_EVERY == 0 && f % TowerElites.BOSS_EVERY != 0);
		helper.assertTrue(TowerMaze.hall(floor) == TowerMaze.ELITE_HALL, "an elite floor's hall is " + TowerMaze.hall(floor));
		hallAndPortal(helper, floor, TowerMaze.ELITE_HALL, "eliteHall");
	}

	@GameTest
	public void aBossFloorHasAGreatHallWithThePortalInItsMiddle(final GameTestHelper helper) {
		int floor = TowerTestSupport.freshFloorWhere(f -> f % TowerElites.BOSS_EVERY == 0);
		helper.assertTrue(TowerMaze.hall(floor) == TowerMaze.BOSS_HALL, "a boss floor's hall is " + TowerMaze.hall(floor));
		hallAndPortal(helper, floor, TowerMaze.BOSS_HALL, "bossHall");
	}

	/**
	 * A boss floor's hall is walled exactly at its edge: the ring of blocks just outside it is solid but for
	 * the one door into the safe room, two wide, so no sliver of the grid's cells is left standing beside it.
	 */
	@GameTest
	public void aBossHallIsWalledExactlyAtItsEdge(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(TowerBuffs::isBossFloor);
		TowerFloors.ensure(tower, floor);
		boolean[][] open = open(tower, floor);
		int low = S / 2 - TowerMaze.BOSS_HALL / 2;
		int high = low + TowerMaze.BOSS_HALL;
		int ring = 0;
		for (int t = low; t < high; t++) {
			ring += (open[low - 1][t] ? 1 : 0) + (open[high][t] ? 1 : 0) + (open[t][low - 1] ? 1 : 0) + (open[t][high] ? 1 : 0);
		}
		// The door is the exit's cells in the hall's own wall, not those in the safe room's ring beyond it.
		TowerPlan plan = TowerPlan.at(tower, floor);
		TowerMaze.Zone room = plan.safeZones().get(1);
		long door = plan.gates().stream().filter(TowerPlan.Gate::exit).flatMap(gate -> gate.cells().stream())
			.filter(cell -> !room.contains(cell.x(), cell.z())).count();
		TowerTestSupport.result("bossHallRing", "floor=" + floor + " ringOpen=" + ring + " door=" + door);
		helper.assertTrue(door == 2, "the hall's door is " + door + " wide, not 2");
		helper.assertTrue(ring == door, ring + " blocks of the ring round boss floor " + floor + "'s hall stand open, not just its " + door + " wide door");
		helper.succeed();
	}

	@GameTest
	public void aFloorsMazeIsItsOwnAndTheSameEachTime(final GameTestHelper helper) {
		long seed = 12345L;
		boolean same = Arrays.deepEquals(TowerMaze.walls(seed, 7), TowerMaze.walls(seed, 7));
		boolean differs = !Arrays.deepEquals(TowerMaze.walls(seed, 7), TowerMaze.walls(seed, 8));
		TowerTestSupport.result("mazeOwn", "same=" + same + " differs=" + differs);
		helper.assertTrue(same && differs, "a floor's maze is not its own: same=" + same + " differs=" + differs);
		helper.succeed();
	}

	/** Every ceiling lamp of each maze story hangs over a path, where its light reaches the maze, not over a wall. */
	@GameTest
	public void everyLampLightsAPath(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = ordinaryFloor(tower);
		BlockPos o = TowerLayout.origin(floor);
		int lamps = 0;
		int overWalls = 0;
		for (TowerStory story : TowerPlan.at(tower, floor).stories()) {
			for (int x = 0; x < S; x++) {
				for (int z = 0; z < S; z++) {
					if (tower.getBlockState(o.offset(x, story.top(), z)).is(TowerBlocks.TOWER_LAMP)) {
						lamps++;
						overWalls += tower.getBlockState(o.offset(x, story.top() - 1, z)).isAir() ? 0 : 1;
					}
				}
			}
		}
		TowerTestSupport.result("mazeLamps", "lamps=" + lamps + " overWalls=" + overWalls);
		helper.assertTrue(lamps > 0, "the floor has no lamps, so the case proves nothing");
		helper.assertTrue(overWalls == 0, overWalls + " of " + lamps + " lamps hang over a wall, their light shut in");
		helper.succeed();
	}

	/** A mob broader than a block, a spider, still finds room in the maze's two-block paths. */
	@GameTest
	public void aBroadMobFindsRoomInTheMaze(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = ordinaryFloor(tower);
		net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(11L);
		int placed = 0;
		int tries = 20;
		for (int i = 0; i < tries; i++) {
			net.minecraft.world.entity.monster.spider.Spider spider = net.minecraft.world.entity.EntityTypes.SPIDER.create(tower, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			placed += TowerMobs.placeInRoom(tower, floor, spider, random) ? 1 : 0;
			spider.discard();
		}
		TowerTestSupport.result("broadMob", "placed=" + placed + "/" + tries);
		helper.assertTrue(placed * 2 >= tries, "a spider found room in the maze " + placed + " times of " + tries);
		helper.succeed();
	}

	/** A spot of the floor's maze where a wall stands, not in a clearing. */
	private static BlockPos mazeWall(final ServerLevel tower, final int floor) {
		boolean[][] walls = TowerMaze.walls(tower.getSeed(), floor);
		for (int x = 1; x < S - 1; x++) {
			for (int z = 1; z < S - 1; z++) {
				if (walls[x][z]) {
					return TowerLayout.origin(floor).offset(x, 1, z);
				}
			}
		}
		throw new IllegalStateException("floor " + floor + " has no maze wall");
	}

	/** The maze's walls can be broken, but only at four times obsidian's hardness, and they yield nothing to carry out. */
	@GameTest
	public void mazeWallsBreakAtFourTimesObsidiansHardness(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = ordinaryFloor(tower);
		BlockPos at = mazeWall(tower, floor);
		net.minecraft.world.level.block.state.BlockState state = tower.getBlockState(at);
		float hardness = state.getDestroySpeed(tower, at);
		float obsidian = net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState().getDestroySpeed(tower, at);
		net.minecraft.server.level.ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		net.minecraft.world.phys.Vec3 arrival = TowerLayout.arrival(tower, floor);
		player.teleportTo(tower, arrival.x, arrival.y, arrival.z, java.util.Set.of(), 0.0F, 0.0F, true);
		boolean handTool = player.hasCorrectToolForDrops(state);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_PICKAXE));
		boolean pickTool = player.hasCorrectToolForDrops(state);
		float progress = state.getDestroyProgress(player, tower, at);
		boolean broke = player.gameMode.destroyBlock(at);
		long drops = tower.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(at).inflate(2.0)).size();
		TowerTestSupport.result("mazeWallHardness", "block=" + state.getBlock() + " hardness=" + hardness + " obsidian=" + obsidian
			+ " hand=" + handTool + " pick=" + pickTool + " ticksWithNetherite=" + (progress > 0 ? Math.round(1.0F / progress) : -1)
			+ " broke=" + broke + " drops=" + drops);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(state.is(TowerBlocks.TOWER_MAZE_WALL), "the maze is built of " + state.getBlock());
		helper.assertTrue(hardness == obsidian * TowerBlocks.MAZE_HARDNESS_OVER_OBSIDIAN,
			"a maze wall's hardness is " + hardness + ", not " + TowerBlocks.MAZE_HARDNESS_OVER_OBSIDIAN + " times obsidian's " + obsidian);
		helper.assertTrue(pickTool && !handTool, "the right tool for a maze wall: pickaxe=" + pickTool + " hand=" + handTool);
		helper.assertTrue(broke && drops == 0, "a maze wall broke=" + broke + " and dropped " + drops);
		helper.succeed();
	}

	/** Blasts, withers and the dragon leave the maze standing; only a player's pickaxe opens it. */
	@GameTest
	public void mazeWallsStandUpToBlastsAndWithers(final GameTestHelper helper) {
		net.minecraft.world.level.block.state.BlockState wall = TowerBlocks.TOWER_MAZE_WALL.defaultBlockState();
		float resistance = TowerBlocks.TOWER_MAZE_WALL.getExplosionResistance();
		float obsidian = net.minecraft.world.level.block.Blocks.OBSIDIAN.getExplosionResistance();
		boolean wither = net.minecraft.world.entity.boss.wither.WitherBoss.canDestroy(wall);
		boolean dragon = !wall.is(net.minecraft.tags.BlockTags.DRAGON_IMMUNE);
		TowerTestSupport.result("mazeWallProof", "resistance=" + resistance + " obsidian=" + obsidian + " wither=" + wither + " dragon=" + dragon);
		helper.assertTrue(resistance >= obsidian, "a maze wall resists blasts at " + resistance + ", less than obsidian's " + obsidian);
		helper.assertFalse(wither || dragon, "a maze wall can be broken by wither=" + wither + " dragon=" + dragon);
		helper.succeed();
	}
}
