package com.stella.tower;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Each floor: its room, its walls, its own place, and its permanence. */
public class TowerFloorGameTest {
	@GameTest
	public void aFloorHasABedrockBaseUnbreakableWallsAndEightBlocksOfRoom(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloor();
		long started = System.nanoTime();
		TowerFloors.ensure(tower, floor);
		long buildMillis = (System.nanoTime() - started) / 1_000_000L;
		BlockPos o = TowerLayout.origin(floor);
		int s = TowerLayout.SIZE;
		int base = TowerLayout.BASE_Y;
		boolean bedrock = tower.getBlockState(o.offset(10, -2, 10)).is(Blocks.BEDROCK)
			&& tower.getBlockState(o.offset(s - 1, -2, s - 1)).is(Blocks.BEDROCK);
		boolean walls = tower.getBlockState(o.offset(-1, 3, 20)).is(TowerBlocks.TOWER_WALL)
			&& tower.getBlockState(o.offset(s, 3, 20)).is(TowerBlocks.TOWER_WALL)
			&& tower.getBlockState(o.offset(20, 3, -1)).is(TowerBlocks.TOWER_WALL)
			&& tower.getBlockState(o.offset(20, 3, s)).is(TowerBlocks.TOWER_WALL);
		boolean ceiling = tower.getBlockState(o.offset(20, TowerLayout.HEIGHT, 21)).is(TowerBlocks.TOWER_WALL)
			|| tower.getBlockState(o.offset(20, TowerLayout.HEIGHT, 21)).is(TowerBlocks.TOWER_LAMP);
		int room = 0;
		BlockPos arrival = BlockPos.containing(TowerLayout.arrival(tower, floor));
		for (int y = 0; y < TowerLayout.HEIGHT; y++) {
			room += tower.getBlockState(arrival.above(y)).isAir() ? 1 : 0;
		}
		float wallHardness = TowerBlocks.TOWER_WALL.defaultBlockState().getDestroySpeed(tower, o.offset(-1, 3, 20));
		TowerTestSupport.result("floorShape", "bedrock=" + bedrock + " walls=" + walls + " ceiling=" + ceiling + " room=" + room
			+ " wallHardness=" + wallHardness + " floorY=" + base + " buildMillis=" + buildMillis);
		helper.assertTrue(bedrock, "the floor has no bedrock under it");
		helper.assertTrue(walls && ceiling, "the floor is not closed by tower walls");
		helper.assertTrue(room == TowerLayout.HEIGHT, "the room is " + room + " blocks high at the arrival spot");
		helper.assertTrue(wallHardness < 0.0F, "a tower wall can be mined: hardness " + wallHardness);
		helper.succeed();
	}

	@GameTest
	public void aFloorIsBuiltOnceAndKeepsItsChanges(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloor();
		TowerFloors.ensure(tower, floor);
		BlockPos dug = TowerLayout.origin(floor).offset(30, -1, 10);
		tower.setBlock(dug, Blocks.AIR.defaultBlockState(), 3);
		boolean rebuilt = TowerFloors.ensure(tower, floor);
		boolean kept = tower.getBlockState(dug).isAir();
		TowerTestSupport.result("floorPermanence", "rebuilt=" + rebuilt + " kept=" + kept);
		helper.assertFalse(rebuilt, "a built floor was built again");
		helper.assertTrue(kept, "a floor did not keep the block dug from it");
		helper.succeed();
	}

	@GameTest
	public void everyFloorHasItsOwnPlaceAndItsOwnLayout(final GameTestHelper helper) {
		Set<BlockPos> origins = new HashSet<>();
		int wrong = 0;
		for (int floor = 1; floor <= 600; floor++) {
			BlockPos o = TowerLayout.origin(floor);
			origins.add(o);
			wrong += TowerLayout.floorAt(o) == floor && TowerLayout.floorAt(o.offset(TowerLayout.SIZE - 1, 0, TowerLayout.SIZE - 1)) == floor ? 0 : 1;
		}
		long seed = 12345L;
		boolean same = java.util.Arrays.deepEquals(TowerMaze.walls(seed, 5), TowerMaze.walls(seed, 5));
		boolean differs = !java.util.Arrays.deepEquals(TowerMaze.walls(seed, 5), TowerMaze.walls(seed, 6));
		TowerTestSupport.result("floorPlaces", "distinct=" + origins.size() + " wrong=" + wrong + " same=" + same + " differs=" + differs);
		helper.assertTrue(origins.size() == 600 && wrong == 0, "floors share places or are not found: distinct=" + origins.size() + " wrong=" + wrong);
		helper.assertTrue(same && differs, "a floor's layout is not its own: same=" + same + " differs=" + differs);
		helper.succeed();
	}

	/** A queued floor is built a little each tick, not at once, and stands complete once done. */
	@GameTest(maxTicks = 200)
	public void aQueuedFloorIsBuiltInTheBackground(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloor();
		TowerFloors.prepare(tower, floor);
		boolean builtAtOnce = TowerFloors.isBuilt(tower, floor);
		boolean queued = TowerFloors.isQueued(floor);
		helper.succeedWhen(() -> {
			helper.assertFalse(builtAtOnce, "prepare built the whole floor at once");
			helper.assertTrue(queued, "the floor was never queued");
			helper.assertTrue(TowerFloors.isBuilt(tower, floor), "the queued floor is not built yet");
			boolean frame = tower.getBlockState(TowerLayout.portalFrame(tower, floor).get(0)).is(TowerBlocks.TOWER_FRAME);
			helper.assertTrue(frame, "the background floor has no frame");
			TowerTestSupport.result("backgroundBuild", "tick=" + helper.getTick() + " longestStepMillis=" + TowerFloors.longestStepMillis());
		});
	}

	/** Arriving on a floor starts building the one above, so the climb waits on nothing. */
	@GameTest(maxTicks = 200)
	public void arrivingOnAFloorStartsTheOneAbove(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloor();
		TowerFloors.ensure(tower, floor);
		BlockPos inside = TowerLayout.portalInside(tower, floor).get(0);
		// Only players climb, so a player takes the portal.
		ServerPlayer climber = TowerTestSupport.join(helper, 1.5, 1.5);
		climber.teleportTo(tower, inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
		climber.teleport(TowerBlocks.TOWER_PORTAL.getPortalDestination(tower, climber, inside));
		TowerTestSupport.leave(helper, climber);
		int twoUp = floor + 2;
		boolean started = TowerFloors.isQueued(twoUp) || TowerFloors.isBuilt(tower, twoUp);
		helper.succeedWhen(() -> {
			helper.assertTrue(started, "arriving on floor " + (floor + 1) + " did not start floor " + twoUp);
			helper.assertTrue(TowerFloors.isBuilt(tower, twoUp), "floor " + twoUp + " is not built yet");
			TowerTestSupport.result("buildAhead", "started=" + started + " tick=" + helper.getTick());
		});
	}

	/** The room may be dug, but not the frame that leads up. */
	@GameTest
	public void theWayUpCannotBeMinedButTheRoomCan(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloor();
		TowerFloors.ensure(tower, floor);
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		Vec3 arrival = TowerLayout.arrival(tower, floor);
		player.teleportTo(tower, arrival.x, arrival.y, arrival.z, Set.of(), 0.0F, 0.0F, true);
		BlockPos frame = TowerLayout.portalFrame(tower, floor).get(0);
		BlockPos ground = TowerLayout.origin(floor).offset(30, -1, 12);
		float frameHardness = tower.getBlockState(frame).getDestroySpeed(tower, frame);
		boolean groundBroke = player.gameMode.destroyBlock(ground);
		TowerTestSupport.result("frameProtected", "frameHardness=" + frameHardness + " groundBroke=" + groundBroke);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(frameHardness < 0.0F, "the portal frame can be mined: hardness " + frameHardness);
		helper.assertTrue(groundBroke, "the room's floor could not be dug, so the guard stops too much");
		helper.succeed();
	}
}
