package com.stella.tower;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/** A portal from outside leads a player back to the floor they last stood on. */
public class TowerLastFloorGameTest {
	@GameTest(maxTicks = 100)
	public void enteringAgainReturnsToTheLastFloor(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloor();
		TowerFloors.ensure(tower, floor);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		// The server's own pulse records the floor; two pulses pass before the player leaves.
		helper.runAfterDelay(45L, () -> {
			Vec3 out = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
			player.teleportTo(helper.getLevel(), out.x, out.y, out.z, java.util.Set.of(), 0.0F, 0.0F, true);
			TeleportTransition again = TowerTravel.destination(helper.getLevel(), player, helper.absolutePos(new BlockPos(2, 2, 2)));
			Vec3 want = TowerPlan.at(tower, floor).arrivalPos();
			TowerTestSupport.result("lastFloor", "recorded=" + TowerTravel.lastFloor(player) + " floor=" + floor
				+ " leadsTo=" + (again == null ? "nothing" : again.position().toString()) + " want=" + want);
			boolean right = again != null && again.newLevel() == tower && again.position().equals(want);
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(right, "a portal led a player who last stood on floor " + floor + " somewhere else");
			helper.succeed();
		});
	}

	@GameTest
	public void aNewPlayerStartsOnTheFirstFloor(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		TeleportTransition first = TowerTravel.destination(helper.getLevel(), player, helper.absolutePos(new BlockPos(2, 2, 2)));
		Vec3 want = TowerPlan.at(tower, 1).arrivalPos();
		TowerTestSupport.result("firstFloor", "last=" + TowerTravel.lastFloor(player) + " leadsTo=" + (first == null ? "nothing" : first.position().toString()));
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(first != null && first.position().equals(want), "a player with no visit did not start on floor 1");
		helper.succeed();
	}

	@GameTest
	public void theLastFloorIsSavedWithThePlayer(final GameTestHelper helper) {
		boolean saved = TowerTravel.LAST_FLOOR.isPersistent();
		TowerTestSupport.result("lastFloorSaved", "persistent=" + saved);
		helper.assertTrue(saved, "the last floor is not saved with the player");
		helper.succeed();
	}
}
