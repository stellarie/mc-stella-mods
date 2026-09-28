package com.stella.nether;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/** The raid closes once everyone is out, leaves its land and drops behind, and turns away a stale return. */
public class RaidLifecycleGameTest {
	static void leaveRaid(final GameTestHelper helper, final ServerPlayer player) {
		TeleportTransition back = NetherTestSupport.portal((ServerLevel) player.level(), player, RaidInstance.exitPortal(helper.getLevel().getServer()));
		helper.assertTrue(back != null, "the raid portal led nowhere");
		player.teleport(back);
	}

	@GameTest(environment = "mod-stella-nether-test:reset", maxTicks = RaidInstance.EMPTY_TICKS + 100)
	public void theRaidResetsOnceEveryoneHasLeft(final GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		NetherTestSupport.fresh(helper);
		ServerPlayer player = RaidModeGameTest.enter(helper, RaidTravel.Choice.RAID, RaidMode.NORMAL);
		int run = RaidInstance.state(server).run();
		leaveRaid(helper, player);
		helper.succeedWhen(() -> {
			RaidState state = RaidInstance.state(server);
			helper.assertTrue(!state.active() && state.run() == run + 1, "the empty raid is still " + state);
			NetherTestSupport.result("resetWhenEmpty", "run=" + run + " next=" + state.run() + " active=" + state.active());
			NetherTestSupport.leave(helper, player);
		});
	}

	@GameTest(environment = "mod-stella-nether-test:stays", maxTicks = RaidInstance.EMPTY_TICKS + 100)
	public void theRaidStaysOpenWhileAPlayerIsInside(final GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		NetherTestSupport.fresh(helper);
		ServerPlayer player = RaidModeGameTest.enter(helper, RaidTravel.Choice.RAID, RaidMode.NORMAL);
		int run = RaidInstance.state(server).run();
		helper.runAfterDelay(RaidInstance.EMPTY_TICKS + 40, () -> {
			RaidState state = RaidInstance.state(server);
			NetherTestSupport.result("staysOpen", "active=" + state.active() + " run=" + state.run() + " was=" + run
				+ " inside=" + player.level().dimension().equals(RaidTravel.RAID));
			NetherTestSupport.leave(helper, player);
			helper.assertTrue(state.active() && state.run() == run, "an occupied raid became " + state);
			helper.succeed();
		});
	}

	@GameTest(environment = "mod-stella-nether-test:elapsed", maxTicks = 100)
	public void theRaidCountsTheTicksItHasBeenOpen(final GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		NetherTestSupport.fresh(helper);
		ServerPlayer player = RaidModeGameTest.enter(helper, RaidTravel.Choice.RAID, RaidMode.NORMAL);
		long wait = 40L;
		helper.runAfterDelay(wait, () -> {
			long elapsed = RaidInstance.state(server).elapsed();
			NetherTestSupport.result("elapsedCounts", "elapsed=" + elapsed + " waited=" + wait);
			NetherTestSupport.leave(helper, player);
			helper.assertTrue(elapsed >= wait - 1 && elapsed <= wait + 2, "after " + wait + " ticks the raid counted " + elapsed);
			helper.succeed();
		});
	}

	@GameTest(environment = "mod-stella-nether-test:items", maxTicks = RaidInstance.EMPTY_TICKS + 100)
	public void anItemLeftInTheRaidIsLostOnReset(final GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		NetherTestSupport.fresh(helper);
		ServerPlayer player = RaidModeGameTest.enter(helper, RaidTravel.Choice.RAID, RaidMode.NORMAL);
		ServerLevel raid = (ServerLevel) player.level();
		Vec3 at = player.position().add(1.0, 0.5, 0.0);
		ItemEntity item = new ItemEntity(raid, at.x, at.y, at.z, new ItemStack(Items.DIAMOND));
		raid.addFreshEntity(item);
		int run = RaidInstance.state(server).run();
		Vec3 oldArrival = Vec3.atBottomCenterOf(RaidInstance.arrival(server));
		leaveRaid(helper, player);
		helper.succeedWhen(() -> {
			helper.assertTrue(RaidInstance.state(server).run() == run + 1, "the raid has not reset yet");
			double moved = Vec3.atBottomCenterOf(RaidInstance.arrival(server)).distanceTo(oldArrival);
			NetherTestSupport.result("itemsLost", "removed=" + item.isRemoved() + " arrivalMoved=" + moved);
			double apart = RaidInstance.RUN_STRIDE - 2.0 * RaidSites.MAX_SHIFT;
			helper.assertTrue(item.isRemoved() && moved >= apart && apart > 2 * RaidInstance.STRUCTURE_REACH,
				"the diamond removed=" + item.isRemoved() + " arrival moved " + moved + " of at least " + apart);
			NetherTestSupport.leave(helper, player);
		});
	}

	@GameTest(environment = "mod-stella-nether-test:manyitems", maxTicks = RaidInstance.EMPTY_TICKS + 100)
	public void aResetDiscardsEveryItemLeftBehind(final GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		NetherTestSupport.fresh(helper);
		ServerPlayer player = RaidModeGameTest.enter(helper, RaidTravel.Choice.RAID, RaidMode.NORMAL);
		ServerLevel raid = (ServerLevel) player.level();
		java.util.List<ItemEntity> items = new java.util.ArrayList<>();
		for (int i = 0; i < 64; i++) {
			Vec3 at = player.position().add(i % 8 - 3.5, 0.5, i / 8 - 3.5);
			ItemEntity item = new ItemEntity(raid, at.x, at.y, at.z, new ItemStack(Items.NETHERRACK));
			if (raid.addFreshEntity(item)) {
				items.add(item);
			}
		}
		int run = RaidInstance.state(server).run();
		leaveRaid(helper, player);
		helper.succeedWhen(() -> {
			helper.assertTrue(RaidInstance.state(server).run() == run + 1, "the raid has not reset yet");
			long left = items.stream().filter(item -> !item.isRemoved()).count();
			NetherTestSupport.result("everyItemLost", "items=" + items.size() + " left=" + left);
			helper.assertTrue(left == 0, left + " of " + items.size() + " items survived the reset");
			NetherTestSupport.leave(helper, player);
		});
	}

	@GameTest(environment = "mod-stella-nether-test:stale", maxTicks = 100)
	public void aPlayerReturningToAClosedRunIsSentOut(final GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		NetherTestSupport.fresh(helper);
		ServerPlayer player = RaidModeGameTest.enter(helper, RaidTravel.Choice.RAID, RaidMode.NORMAL);
		RaidInstance.reset(server, "test");
		RaidInstance.onJoin(player);
		var at = player.level().dimension();
		NetherTestSupport.result("staleSentOut", "at=" + at.identifier());
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(at.equals(Level.OVERWORLD), "a stale raider was left in " + at);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:live", maxTicks = 100)
	public void aPlayerReturningToTheLiveRunStays(final GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		NetherTestSupport.fresh(helper);
		ServerPlayer player = RaidModeGameTest.enter(helper, RaidTravel.Choice.RAID, RaidMode.NORMAL);
		RaidInstance.onJoin(player);
		var at = player.level().dimension();
		NetherTestSupport.result("liveStays", "at=" + at.identifier());
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(at.equals(RaidTravel.RAID), "a live raider was moved to " + at);
		helper.succeed();
	}
}
