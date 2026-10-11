package com.stella.nether;

import io.netty.buffer.Unpooled;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The guardian's raid window: using the guardian opens it with a row for each raid and its level, Cancel
 * changes nothing, and a pick that cannot be honoured sends no one.
 */
public class GuardianWindowGameTest {
	@GameTest(environment = "mod-stella-nether-test:window_open", maxTicks = 100)
	public void usingTheGuardianOpensTheWindowWithTwoRaidsAndTheirLevels(final GameTestHelper helper) {
		NetherTestSupport.fresh(helper);
		BlazeGuardian guardian = NetherTestSupport.hub(helper);
		ServerPlayer player = NetherTestSupport.atGuardian(helper, guardian);
		List<GuardianWindow.Open> windows = NetherTestSupport.use(player, guardian);
		boolean open = GuardianHub.windowOpen(player);
		GuardianWindow.Open sent = windows.isEmpty() ? null : windows.get(0);
		StringBuilder rows = new StringBuilder();
		int wrong = 0;
		for (GuardianWindow.Option option : GuardianWindow.Option.values()) {
			GuardianWindow.Row row = sent == null || sent.rows().size() != GuardianWindow.Option.values().length ? null : sent.rows().get(option.ordinal());
			rows.append(' ').append(option).append('=').append(row);
			wrong += row != null && row.raidMode() == option.mode() && row.level() == RaidInstance.minimumLevel(option.mode()) && !row.running() ? 0 : 1;
		}
		NetherTestSupport.result("windowOpens", "windows=" + windows.size() + " open=" + open + " rows=" + rows);
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(windows.size() == 1 && open, "using the guardian sent " + windows.size() + " windows, open=" + open);
		helper.assertTrue(wrong == 0 && sent.rows().size() == 2, wrong + " rows are off the raids' minimum levels:" + rows);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:window_levels", maxTicks = 100)
	public void aRunningRaidIsShownAtItsBestPlayersLevelAndAsRunning(final GameTestHelper helper) {
		NetherTestSupport.fresh(helper);
		BlazeGuardian guardian = NetherTestSupport.hub(helper);
		ServerPlayer host = NetherTestSupport.enterAt(helper, NetherTestSupport.atGuardian(helper, guardian), guardian, RaidMode.NORMAL);
		host.experienceLevel = 80;
		ServerPlayer next = NetherTestSupport.atGuardian(helper, guardian);
		List<GuardianWindow.Open> windows = NetherTestSupport.use(next, guardian);
		GuardianWindow.Open sent = windows.isEmpty() ? null : windows.get(0);
		GuardianWindow.Row normal = sent == null ? null : sent.rows().get(GuardianWindow.Option.RAID_NORMAL.ordinal());
		GuardianWindow.Row hard = sent == null ? null : sent.rows().get(GuardianWindow.Option.RAID_HARD.ordinal());
		NetherTestSupport.result("windowLevels", "normal=" + normal + " hard=" + hard);
		NetherTestSupport.leave(helper, host);
		NetherTestSupport.leave(helper, next);
		helper.assertTrue(normal != null && normal.running() && normal.level() == 80, "the running normal raid is shown as " + normal);
		helper.assertTrue(hard != null && !hard.running() && hard.level() == RaidInstance.MIN_LEVEL_HARD, "the idle hard raid is shown as " + hard);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:window_cancel", maxTicks = 100)
	public void cancellingTheWindowChangesNothingAndASpentWindowSendsNoOne(final GameTestHelper helper) {
		NetherTestSupport.fresh(helper);
		BlazeGuardian guardian = NetherTestSupport.hub(helper);
		ServerPlayer player = NetherTestSupport.atGuardian(helper, guardian);
		Vec3 before = player.position();
		NetherTestSupport.use(player, guardian);
		boolean cancelled = GuardianHub.cancel(player);
		boolean closed = !GuardianHub.windowOpen(player);
		// A pick that arrives after the cancel has no window behind it.
		boolean sent = GuardianHub.choose(player, GuardianWindow.Option.RAID_NORMAL);
		boolean stayed = player.level().dimension().equals(Level.NETHER) && player.position().equals(before);
		boolean quiet = !RaidInstance.state(helper.getLevel().getServer(), RaidMode.NORMAL).active() && !RaidInstance.state(helper.getLevel().getServer(), RaidMode.HARD).active();
		NetherTestSupport.result("windowCancel", "cancelled=" + cancelled + " closed=" + closed + " lateSent=" + sent + " stayed=" + stayed + " quiet=" + quiet + " guardianAlive=" + guardian.isAlive());
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(cancelled && closed && !sent, "cancel=" + cancelled + " closed=" + closed + ", a late pick sent=" + sent);
		helper.assertTrue(stayed && quiet && guardian.isAlive(), "after cancel the player stayed=" + stayed + ", no raid open=" + quiet + ", guardian alive=" + guardian.isAlive());
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:window_far", maxTicks = 100)
	public void aPickFromFarAwayFromTheGuardianSendsNoOne(final GameTestHelper helper) {
		NetherTestSupport.fresh(helper);
		BlazeGuardian guardian = NetherTestSupport.hub(helper);
		ServerPlayer player = NetherTestSupport.atGuardian(helper, guardian);
		NetherTestSupport.use(player, guardian);
		// The player walks off while the window is open; the pick arrives from beyond the guardian's reach.
		Vec3 far = player.position().add(GuardianHub.USE_RANGE + 8.0, 0.0, 0.0);
		player.teleportTo(NetherTestSupport.nether(helper), far.x, far.y, far.z, java.util.Set.of(), 0.0F, 0.0F, true);
		boolean sent = GuardianHub.choose(player, GuardianWindow.Option.RAID_HARD);
		boolean stayed = player.level().dimension().equals(Level.NETHER);
		boolean quiet = !RaidInstance.state(helper.getLevel().getServer(), RaidMode.HARD).active();
		NetherTestSupport.result("windowFar", "sent=" + sent + " stayed=" + stayed + " quiet=" + quiet);
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(!sent && stayed && quiet, "a far pick sent=" + sent + " stayed=" + stayed + " no hard raid opened=" + quiet);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:window_codec", maxTicks = 20)
	public void theWindowsMessagesCrossTheWireIntact(final GameTestHelper helper) {
		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		GuardianWindow.Open open = new GuardianWindow.Open(List.of(new GuardianWindow.Row(RaidMode.NORMAL.ordinal(), 80, true), new GuardianWindow.Row(RaidMode.HARD.ordinal(), 45, false)));
		GuardianWindow.Chosen chosen = new GuardianWindow.Chosen(GuardianWindow.Option.RAID_HARD.ordinal());
		GuardianWindow.Open.CODEC.encode(buffer, open);
		GuardianWindow.Chosen.CODEC.encode(buffer, chosen);
		GuardianWindow.Open openBack = GuardianWindow.Open.CODEC.decode(buffer);
		GuardianWindow.Chosen chosenBack = GuardianWindow.Chosen.CODEC.decode(buffer);
		NetherTestSupport.result("windowCodec", "open=" + openBack + " chosen=" + chosenBack);
		helper.assertTrue(open.equals(openBack) && chosen.equals(chosenBack) && openBack.rows().get(0).raidMode() == RaidMode.NORMAL && openBack.rows().get(1).level() == 45,
			"the window's messages came back as " + openBack + " and " + chosenBack);
		helper.succeed();
	}

	/**
	 * Tagged, not required: the window, its Cancel button and its labels are drawn and clicked by the client, and a
	 * server game test cannot open a screen or click it. It fails every run, by design, until a person has checked
	 * it: use the guardian, see two raid buttons with their levels and a Cancel button, and press Cancel.
	 */
	@GameTest(environment = "mod-stella-nether-test:window_client", maxTicks = 20, required = false)
	public void theGuardianWindowIsDrawnAndClickedRequiresManualTest(final GameTestHelper helper) {
		helper.fail("the guardian's window is drawn and clicked on the client; check it by hand");
	}
}
