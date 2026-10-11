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
		NetherTestSupport.leaveByPortal(helper, player, RaidMode.NORMAL);
	}

	@GameTest(environment = "mod-stella-nether-test:reset", maxTicks = RaidInstance.EMPTY_TICKS + 100)
	public void theRaidResetsOnceEveryoneHasLeft(final GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		NetherTestSupport.fresh(helper);
		ServerPlayer player = RaidModeGameTest.enter(helper, RaidMode.NORMAL);
		int run = RaidInstance.state(server, RaidMode.NORMAL).run();
		leaveRaid(helper, player);
		helper.succeedWhen(() -> {
			RaidState state = RaidInstance.state(server, RaidMode.NORMAL);
			helper.assertTrue(!state.active() && state.run() == run + 1, "the empty raid is still " + state);
			NetherTestSupport.result("resetWhenEmpty", "run=" + run + " next=" + state.run() + " active=" + state.active());
			NetherTestSupport.leave(helper, player);
		});
	}

	@GameTest(environment = "mod-stella-nether-test:stays", maxTicks = RaidInstance.EMPTY_TICKS + 100)
	public void theRaidStaysOpenWhileAPlayerIsInside(final GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		NetherTestSupport.fresh(helper);
		ServerPlayer player = RaidModeGameTest.enter(helper, RaidMode.NORMAL);
		int run = RaidInstance.state(server, RaidMode.NORMAL).run();
		helper.runAfterDelay(RaidInstance.EMPTY_TICKS + 40, () -> {
			RaidState state = RaidInstance.state(server, RaidMode.NORMAL);
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
		ServerPlayer player = RaidModeGameTest.enter(helper, RaidMode.NORMAL);
		long wait = 40L;
		helper.runAfterDelay(wait, () -> {
			long elapsed = RaidInstance.state(server, RaidMode.NORMAL).elapsed();
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
		ServerPlayer player = RaidModeGameTest.enter(helper, RaidMode.NORMAL);
		ServerLevel raid = (ServerLevel) player.level();
		Vec3 at = player.position().add(1.0, 0.5, 0.0);
		ItemEntity item = new ItemEntity(raid, at.x, at.y, at.z, new ItemStack(Items.DIAMOND));
		raid.addFreshEntity(item);
		int run = RaidInstance.state(server, RaidMode.NORMAL).run();
		Vec3 oldArrival = Vec3.atBottomCenterOf(RaidInstance.arrival(server, RaidMode.NORMAL));
		leaveRaid(helper, player);
		helper.succeedWhen(() -> {
			helper.assertTrue(RaidInstance.state(server, RaidMode.NORMAL).run() == run + 1, "the raid has not reset yet");
			double moved = Vec3.atBottomCenterOf(RaidInstance.arrival(server, RaidMode.NORMAL)).distanceTo(oldArrival);
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
		ServerPlayer player = RaidModeGameTest.enter(helper, RaidMode.NORMAL);
		ServerLevel raid = (ServerLevel) player.level();
		// The grid can straddle a chunk edge; both sides are held, or a side the level hides is out of the reset's sight.
		BlazeSovereignGameTest.hold(raid, player.blockPosition().offset(-4, 0, -4));
		BlazeSovereignGameTest.hold(raid, player.blockPosition().offset(4, 0, 4));
		java.util.List<ItemEntity> items = new java.util.ArrayList<>();
		for (int i = 0; i < 64; i++) {
			Vec3 at = player.position().add(i % 8 - 3.5, 0.5, i / 8 - 3.5);
			ItemEntity item = new ItemEntity(raid, at.x, at.y, at.z, new ItemStack(Items.NETHERRACK));
			if (raid.addFreshEntity(item)) {
				items.add(item);
			}
		}
		int run = RaidInstance.state(server, RaidMode.NORMAL).run();
		leaveRaid(helper, player);
		helper.succeedWhen(() -> {
			helper.assertTrue(RaidInstance.state(server, RaidMode.NORMAL).run() == run + 1, "the raid has not reset yet");
			long left = items.stream().filter(item -> !item.isRemoved()).count();
			String where = items.stream().filter(item -> !item.isRemoved()).map(item -> item.blockPosition().toShortString() + (raid.isLoaded(item.blockPosition()) ? "L" : "u")
				+ "/" + raid.getEntity(item.getUUID())).limit(3).toList().toString();
			NetherTestSupport.result("everyItemLost", "items=" + items.size() + " left=" + left + " survivors=" + where);
			helper.assertTrue(left == 0, left + " of " + items.size() + " items survived the reset");
			NetherTestSupport.leave(helper, player);
		});
	}

	@GameTest(environment = "mod-stella-nether-test:stale", maxTicks = 100)
	public void aPlayerReturningToAClosedRunIsSentOut(final GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		NetherTestSupport.fresh(helper);
		ServerPlayer player = RaidModeGameTest.enter(helper, RaidMode.NORMAL);
		RaidInstance.reset(server, RaidMode.NORMAL, "test");
		RaidInstance.onJoin(player);
		var at = player.level().dimension();
		NetherTestSupport.result("staleSentOut", "at=" + at.identifier());
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(at.equals(Level.NETHER), "a stale raider was left in " + at);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:live", maxTicks = 100)
	public void aPlayerReconnectingToALiveRunIsSentToTheirGuardianAndMayUseItToJoinAgain(final GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		NetherTestSupport.fresh(helper);
		BlazeGuardian guardian = NetherTestSupport.hub(helper);
		ServerPlayer player = NetherTestSupport.enterAt(helper, NetherTestSupport.atGuardian(helper, guardian), guardian, RaidMode.NORMAL);
		RaidTravel.Entry entry = player.getAttached(RaidTravel.ENTRY);
		int run = RaidInstance.state(server, RaidMode.NORMAL).run();
		RaidInstance.onJoin(player);
		var sentTo = player.level().dimension();
		double fromEntry = entry == null ? -1.0 : player.position().distanceTo(entry.position());
		// The run still lives, so the guardian takes them in again.
		NetherTestSupport.enterAt(helper, player, guardian, RaidMode.NORMAL);
		boolean rejoined = player.level().dimension().equals(RaidTravel.RAID) && RaidInstance.state(server, RaidMode.NORMAL).active() && RaidInstance.state(server, RaidMode.NORMAL).run() == run;
		NetherTestSupport.result("reconnectSentOut", "sentTo=" + sentTo.identifier() + " fromEntry=" + String.format("%.2f", fromEntry) + " rejoined=" + rejoined);
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(sentTo.equals(Level.NETHER) && fromEntry >= 0.0 && fromEntry < 0.01 && rejoined,
			"a reconnecting raider was sent to " + sentTo.identifier() + ", " + fromEntry + " from their entry, and rejoined=" + rejoined);
		helper.succeed();
	}
}
