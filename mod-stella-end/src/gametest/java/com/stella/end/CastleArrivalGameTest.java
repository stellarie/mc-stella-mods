package com.stella.end;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/** A player the End portal sends lands inside floor 1 of the castle, and the obsidian platform is never made. */
public class CastleArrivalGameTest {
	@GameTest(environment = "mod-stella-end-test:arrival_inside", maxTicks = 100)
	public void aPlayerSentToTheEndLandsInsideFloorOneNotOnTheObsidianPlatform(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		ServerLevel overworld = helper.getLevel();
		ServerPlayer player = EndTestSupport.join(helper, overworld, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
		BlockPos platform = ServerLevel.END_SPAWN_POINT.below();
		TeleportTransition transition = ((EndPortalBlock) Blocks.END_PORTAL).getPortalDestination(overworld, player, player.blockPosition());
		helper.assertTrue(transition != null && transition.newLevel().dimension().equals(Level.END), "the End portal led to " + transition);
		player.teleport(transition);
		BlockPos feet = player.blockPosition();
		BlockPos min = EndCastle.min();
		boolean inside = feet.getX() >= min.getX() && feet.getX() < min.getX() + EndCastle.SIZE
			&& feet.getZ() >= min.getZ() && feet.getZ() < min.getZ() + EndCastle.SIZE
			&& EndCastle.floorAt(feet.getY()) == 1;
		boolean clear = end.getBlockState(feet).isAir() && end.getBlockState(feet.above()).isAir();
		boolean ground = end.getBlockState(feet.below()).isFaceSturdy(end, feet.below(), Direction.UP);
		boolean obsidian = end.getBlockState(platform).is(Blocks.OBSIDIAN);
		EndTestSupport.result("arrivalInside", "level=" + player.level().dimension().identifier() + " at=" + feet + " inside=" + inside
			+ " clear=" + clear + " ground=" + ground + " obsidian=" + obsidian);
		EndTestSupport.fresh(helper);
		helper.assertTrue(player.level().dimension().equals(Level.END), "the player is in " + player.level().dimension().identifier());
		helper.assertTrue(inside, "the player landed at " + feet + ", outside floor 1");
		helper.assertTrue(clear, "the arrival has a block at the feet or the head: " + end.getBlockState(feet) + " " + end.getBlockState(feet.above()));
		helper.assertTrue(ground, "the arrival stands on " + end.getBlockState(feet.below()));
		helper.assertTrue(!obsidian, "the obsidian platform was made at " + platform);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:arrival_landing", maxTicks = 100)
	public void theArrivalSpotIsThePlanLandingOfFloorOne(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		TeleportTransition transition = CastleArrival.transition(end);
		CastleLayout.FloorPlan plan = EndCastles.layout(end).floor(1);
		BlockPos expected = EndTestSupport.world(EndCastle.min(), plan.landingX(), EndCastle.standY(1), plan.landingZ());
		BlockPos got = BlockPos.containing(transition.position());
		EndTestSupport.result("arrivalLanding", "expected=" + expected + " got=" + got);
		helper.assertTrue(transition.newLevel() == end, "the crossing leads to " + transition.newLevel().dimension().identifier());
		helper.assertTrue(got.equals(expected), "the crossing lands at " + got + ", not " + expected);
		helper.succeed();
	}
}
