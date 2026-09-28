package com.stella.nether;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;

/** A nether portal asks each player which nether to enter, and the raid nether's own portal leads back. */
public class RaidPortalGameTest {
	@GameTest
	public void aPlayerWhoHasNotChosenIsHeldAtThePortalAndAsked(final GameTestHelper helper) {
		ServerPlayer player = NetherTestSupport.join(helper, 1.5, 1.5);
		TeleportTransition transition = NetherTestSupport.portal(helper.getLevel(), player, helper.absolutePos(new BlockPos(1, 1, 1)));
		boolean asked = RaidTravel.wasAsked(player);
		NetherTestSupport.result("heldAndAsked", "held=" + (transition == null) + " asked=" + asked);
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(transition == null && asked, "an undecided player was sent on: " + transition + " asked=" + asked);
		helper.succeed();
	}

	@GameTest
	public void choosingTheRaidLeadsIntoTheRaidNether(final GameTestHelper helper) {
		ServerPlayer player = NetherTestSupport.join(helper, 1.5, 1.5);
		RaidTravel.choose(player, RaidTravel.Choice.RAID);
		TeleportTransition transition = NetherTestSupport.portal(helper.getLevel(), player, helper.absolutePos(new BlockPos(1, 1, 1)));
		NetherTestSupport.result("raidChoice", "to=" + (transition == null ? null : transition.newLevel().dimension().identifier()));
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(transition != null && transition.newLevel().dimension().equals(RaidTravel.RAID), "the raid choice led to " + transition);
		helper.succeed();
	}

	@GameTest
	public void choosingTheNormalNetherLeadsToTheNether(final GameTestHelper helper) {
		ServerPlayer player = NetherTestSupport.join(helper, 1.5, 1.5);
		RaidTravel.choose(player, RaidTravel.Choice.NORMAL);
		TeleportTransition transition = NetherTestSupport.portal(helper.getLevel(), player, helper.absolutePos(new BlockPos(1, 1, 1)));
		NetherTestSupport.result("normalChoice", "to=" + (transition == null ? null : transition.newLevel().dimension().identifier()));
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(transition != null && transition.newLevel().dimension().equals(Level.NETHER), "the normal choice led to " + transition);
		helper.succeed();
	}

	@GameTest
	public void aChoiceIsSpentByOneCrossing(final GameTestHelper helper) {
		ServerPlayer player = NetherTestSupport.join(helper, 1.5, 1.5);
		RaidTravel.choose(player, RaidTravel.Choice.RAID);
		BlockPos portal = helper.absolutePos(new BlockPos(1, 1, 1));
		TeleportTransition first = NetherTestSupport.portal(helper.getLevel(), player, portal);
		TeleportTransition second = NetherTestSupport.portal(helper.getLevel(), player, portal);
		NetherTestSupport.result("choiceSpent", "first=" + (first != null) + " second=" + (second != null));
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(first != null && second == null, "one choice gave crossings first=" + first + " second=" + second);
		helper.succeed();
	}

	@GameTest(maxTicks = RaidTravel.CHOICE_TICKS + 40)
	public void anUnusedChoiceLapses(final GameTestHelper helper) {
		ServerPlayer player = NetherTestSupport.join(helper, 1.5, 1.5);
		RaidTravel.choose(player, RaidTravel.Choice.RAID);
		helper.runAfterDelay(RaidTravel.CHOICE_TICKS + 5, () -> {
			TeleportTransition transition = NetherTestSupport.portal(helper.getLevel(), player, helper.absolutePos(new BlockPos(1, 1, 1)));
			NetherTestSupport.result("choiceLapses", "held=" + (transition == null));
			NetherTestSupport.leave(helper, player);
			helper.assertTrue(transition == null, "a lapsed choice still led to " + transition);
			helper.succeed();
		});
	}

	@GameTest
	public void theChoiceCommandRecordsTheChoice(final GameTestHelper helper) {
		ServerPlayer player = NetherTestSupport.join(helper, 1.5, 1.5);
		helper.getLevel().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withSuppressedOutput(), RaidTravel.COMMAND + " portal raid");
		RaidTravel.Choice choice = RaidTravel.pendingChoice(player);
		NetherTestSupport.result("choiceCommand", "choice=" + choice);
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(choice == RaidTravel.Choice.RAID, "the command left the choice at " + choice);
		helper.succeed();
	}

	@GameTest
	public void aMobIsNeverAskedAndReachesTheNether(final GameTestHelper helper) {
		Pig pig = helper.spawn(EntityTypes.PIG, new BlockPos(1, 2, 1));
		TeleportTransition transition = NetherTestSupport.portal(helper.getLevel(), pig, pig.blockPosition());
		NetherTestSupport.result("mobCrosses", "to=" + (transition == null ? null : transition.newLevel().dimension().identifier()));
		helper.assertTrue(transition != null && transition.newLevel().dimension().equals(Level.NETHER), "a pig was sent to " + transition);
		helper.succeed();
	}

	@GameTest
	public void theRaidPortalLeadsBackToTheEntrance(final GameTestHelper helper) {
		ServerPlayer player = NetherTestSupport.join(helper, 1.5, 1.5);
		RaidTravel.choose(player, RaidTravel.Choice.RAID);
		BlockPos entrance = helper.absolutePos(new BlockPos(1, 1, 1));
		NetherTestSupport.portal(helper.getLevel(), player, entrance);
		ServerLevel raid = NetherTestSupport.raid(helper);
		TeleportTransition back = NetherTestSupport.portal(raid, player, RaidTravel.exitPortal());
		double distance = back == null ? Double.NaN : back.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(entrance));
		NetherTestSupport.result("wayBack", "to=" + (back == null ? null : back.newLevel().dimension().identifier()) + " distance=" + distance);
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(back != null && back.newLevel() == helper.getLevel() && distance <= RaidTravel.ENTRY_STEP + 1.0,
			"the raid portal led to " + back + " at distance " + distance);
		helper.succeed();
	}

	@GameTest
	public void theArrivalStandsOnSolidGroundWithRoomToBreathe(final GameTestHelper helper) {
		ServerPlayer player = NetherTestSupport.join(helper, 1.5, 1.5);
		RaidTravel.choose(player, RaidTravel.Choice.RAID);
		TeleportTransition transition = NetherTestSupport.portal(helper.getLevel(), player, helper.absolutePos(new BlockPos(1, 1, 1)));
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(transition != null, "the raid choice led nowhere");
		ServerLevel raid = transition.newLevel();
		BlockPos feet = BlockPos.containing(transition.position());
		BlockState floor = raid.getBlockState(feet.below());
		boolean clear = raid.getBlockState(feet).isAir() && raid.getBlockState(feet.above()).isAir();
		NetherTestSupport.result("safeArrival", "floor=" + floor.getBlock() + " clear=" + clear);
		helper.assertTrue(floor.isFaceSturdy(raid, feet.below(), net.minecraft.core.Direction.UP) && clear,
			"the arrival stands on " + floor + " with clear=" + clear);
		helper.succeed();
	}

	@GameTest
	public void theArrivalHasALitPortalOut(final GameTestHelper helper) {
		ServerPlayer player = NetherTestSupport.join(helper, 1.5, 1.5);
		RaidTravel.choose(player, RaidTravel.Choice.RAID);
		TeleportTransition transition = NetherTestSupport.portal(helper.getLevel(), player, helper.absolutePos(new BlockPos(1, 1, 1)));
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(transition != null, "the raid choice led nowhere");
		BlockState exit = transition.newLevel().getBlockState(RaidTravel.exitPortal());
		NetherTestSupport.result("portalOut", "block=" + exit.getBlock());
		helper.assertTrue(exit.is(Blocks.NETHER_PORTAL), "the way out is " + exit);
		helper.succeed();
	}
}
