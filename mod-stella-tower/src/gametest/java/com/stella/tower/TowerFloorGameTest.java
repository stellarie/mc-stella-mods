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
		int floor = 3;
		TowerFloors.ensure(tower, floor);
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
		BlockPos arrival = BlockPos.containing(TowerLayout.arrival(floor));
		for (int y = 0; y < TowerLayout.HEIGHT; y++) {
			room += tower.getBlockState(arrival.above(y)).isAir() ? 1 : 0;
		}
		float wallHardness = TowerBlocks.TOWER_WALL.defaultBlockState().getDestroySpeed(tower, o.offset(-1, 3, 20));
		TowerTestSupport.result("floorShape", "bedrock=" + bedrock + " walls=" + walls + " ceiling=" + ceiling + " room=" + room
			+ " wallHardness=" + wallHardness + " floorY=" + base);
		helper.assertTrue(bedrock, "the floor has no bedrock under it");
		helper.assertTrue(walls && ceiling, "the floor is not closed by tower walls");
		helper.assertTrue(room == TowerLayout.HEIGHT, "the room is " + room + " blocks high at the arrival spot");
		helper.assertTrue(wallHardness < 0.0F, "a tower wall can be mined: hardness " + wallHardness);
		helper.succeed();
	}

	@GameTest
	public void aFloorIsBuiltOnceAndKeepsItsChanges(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = 4;
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
		List<TowerFloors.Piece> five = TowerFloors.layout(seed, 5);
		boolean same = five.equals(TowerFloors.layout(seed, 5));
		boolean differs = !five.equals(TowerFloors.layout(seed, 6));
		TowerTestSupport.result("floorPlaces", "distinct=" + origins.size() + " wrong=" + wrong + " same=" + same + " differs=" + differs);
		helper.assertTrue(origins.size() == 600 && wrong == 0, "floors share places or are not found: distinct=" + origins.size() + " wrong=" + wrong);
		helper.assertTrue(same && differs, "a floor's layout is not its own: same=" + same + " differs=" + differs);
		helper.succeed();
	}

	/** The room may be dug, but not the frame that leads up. */
	@GameTest
	public void theWayUpCannotBeMinedButTheRoomCan(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = 5;
		TowerFloors.ensure(tower, floor);
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		Vec3 arrival = TowerLayout.arrival(floor);
		player.teleportTo(tower, arrival.x, arrival.y, arrival.z, Set.of(), 0.0F, 0.0F, true);
		BlockPos frame = TowerLayout.portalFrame(floor).get(0);
		BlockPos ground = TowerLayout.origin(floor).offset(30, -1, 12);
		boolean frameBroke = player.gameMode.destroyBlock(frame);
		boolean groundBroke = player.gameMode.destroyBlock(ground);
		TowerTestSupport.result("frameProtected", "frameBroke=" + frameBroke + " groundBroke=" + groundBroke);
		TowerTestSupport.leave(helper, player);
		helper.assertFalse(frameBroke, "the portal frame was mined");
		helper.assertTrue(groundBroke, "the room's floor could not be dug, so the guard stops too much");
		helper.succeed();
	}
}
