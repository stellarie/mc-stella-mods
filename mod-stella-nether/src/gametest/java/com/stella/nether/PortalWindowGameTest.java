package com.stella.nether;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** The portal's window: an option clicked sends the player on at once, and a closed option sends no one. */
public class PortalWindowGameTest {
	/** A portal block where the player stands, placed with no neighbour update so it stands without a frame. */
	static ServerPlayer inPortal(final GameTestHelper helper, final double x, final double z) {
		ServerPlayer player = NetherTestSupport.join(helper, x, z);
		helper.getLevel().setBlock(player.blockPosition(), Blocks.NETHER_PORTAL.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		return player;
	}

	@GameTest(environment = "mod-stella-nether-test:window_raid", maxTicks = 100)
	public void aRaidOptionClickedSendsThePlayerThroughAtOnce(final GameTestHelper helper) {
		NetherTestSupport.fresh(helper);
		ServerPlayer player = inPortal(helper, 1.5, 1.5);
		BlockPos portal = player.blockPosition();
		boolean sent = RaidTravel.chooseFromWindow(player, PortalWindow.Option.RAID_HARD);
		boolean inRaid = player.level().dimension().equals(RaidTravel.RAID);
		RaidState state = RaidInstance.state(helper.getLevel().getServer());
		NetherTestSupport.result("windowRaid", "sent=" + sent + " inRaid=" + inRaid + " mode=" + state.mode() + " active=" + state.active());
		helper.getLevel().setBlock(portal, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(sent && inRaid && state.active() && state.mode() == RaidMode.HARD, "the window's hard raid option: sent=" + sent + " inRaid=" + inRaid + " " + state);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:window_closed", maxTicks = 100)
	public void anOptionClosedByTheRunningRaidSendsNoOne(final GameTestHelper helper) {
		NetherTestSupport.fresh(helper);
		ServerPlayer host = RaidModeGameTest.enter(helper, RaidTravel.Choice.RAID, RaidMode.HARD);
		ServerPlayer late = inPortal(helper, 2.5, 2.5);
		BlockPos portal = late.blockPosition();
		boolean closed = !PortalWindow.Option.RAID_NORMAL.open(RaidMode.HARD) && PortalWindow.Option.RAID_HARD.open(RaidMode.HARD)
			&& PortalWindow.Option.NORMAL_NETHER.open(RaidMode.HARD);
		boolean sent = RaidTravel.chooseFromWindow(late, PortalWindow.Option.RAID_NORMAL);
		boolean stayed = late.level().dimension().equals(Level.OVERWORLD) && RaidTravel.pendingChoice(late) == null;
		NetherTestSupport.result("windowClosed", "closed=" + closed + " sent=" + sent + " stayed=" + stayed);
		helper.getLevel().setBlock(portal, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		NetherTestSupport.leave(helper, late);
		NetherTestSupport.leave(helper, host);
		helper.assertTrue(closed && !sent && stayed, "a normal raid option during a hard raid: closed=" + closed + " sent=" + sent + " stayed=" + stayed);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:window_codec", maxTicks = 20)
	public void theWindowsMessagesCrossTheWireIntact(final GameTestHelper helper) {
		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		PortalWindow.Open open = new PortalWindow.Open(RaidMode.HARD.ordinal());
		PortalWindow.Chosen chosen = new PortalWindow.Chosen(PortalWindow.Option.RAID_NORMAL.ordinal());
		PortalWindow.Open.CODEC.encode(buffer, open);
		PortalWindow.Chosen.CODEC.encode(buffer, chosen);
		PortalWindow.Open openBack = PortalWindow.Open.CODEC.decode(buffer);
		PortalWindow.Chosen chosenBack = PortalWindow.Chosen.CODEC.decode(buffer);
		NetherTestSupport.result("windowCodec", "open=" + openBack + " chosen=" + chosenBack + " options=" + openBack.options());
		helper.assertTrue(open.equals(openBack) && chosen.equals(chosenBack) && openBack.runningMode() == RaidMode.HARD && openBack.options().size() == 3,
			"the window's messages came back as " + openBack + " and " + chosenBack);
		helper.succeed();
	}

	/**
	 * Tagged, not required: the window itself is drawn by the client, and a server game test cannot open a
	 * screen or click it. It fails every run, by design, until a person has checked it.
	 */
	@GameTest(environment = "mod-stella-nether-test:window_client", maxTicks = 20, required = false)
	public void thePortalWindowOpensAndClicksRequiresManualTest(final GameTestHelper helper) {
		helper.fail("the portal window is drawn and clicked on the client; check it by hand");
	}
}
