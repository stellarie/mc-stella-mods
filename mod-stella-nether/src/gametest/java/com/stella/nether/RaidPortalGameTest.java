package com.stella.nether;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;

/** Where the nether portals lead: a mob in the world's own, and out of the raid hall's, which holds anything but a player. */
public class RaidPortalGameTest {
	@GameTest(environment = "mod-stella-nether-test:portal_mob", maxTicks = 100)
	public void aMobCrossesAnOverworldPortalToTheNetherAsAlways(final GameTestHelper helper) {
		Pig pig = helper.spawn(EntityTypes.PIG, new BlockPos(1, 2, 1));
		TeleportTransition transition = NetherTestSupport.portal(helper.getLevel(), pig, pig.blockPosition());
		NetherTestSupport.result("mobCrosses", "to=" + (transition == null ? null : transition.newLevel().dimension().identifier()));
		helper.assertTrue(transition != null && transition.newLevel().dimension().equals(Level.NETHER), "a pig was sent to " + transition);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:portal_hold", maxTicks = 100)
	public void aMobInTheRaidPortalIsHeldNotSent(final GameTestHelper helper) {
		NetherTestSupport.fresh(helper);
		ServerPlayer player = RaidModeGameTest.enter(helper, RaidMode.NORMAL);
		ServerLevel raid = (ServerLevel) player.level();
		Pig pig = EntityTypes.PIG.create(raid, EntitySpawnReason.MOB_SUMMONED);
		BlockPos exit = RaidInstance.exitPortal(raid.getServer(), RaidMode.NORMAL);
		TeleportTransition mob = NetherTestSupport.portal(raid, pig, exit);
		TeleportTransition raider = NetherTestSupport.portal(raid, player, exit);
		NetherTestSupport.result("raidPortalHolds", "mob=" + mob + " player=" + (raider == null ? null : raider.newLevel().dimension().identifier()));
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(mob == null && raider != null && raider.newLevel().dimension().equals(Level.NETHER),
			"the raid portal sent a pig to " + mob + " and the player to " + raider);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:portal_ground", maxTicks = 100)
	public void theArrivalStandsOnSolidGroundWithRoomToBreathe(final GameTestHelper helper) {
		NetherTestSupport.fresh(helper);
		ServerPlayer player = RaidModeGameTest.enter(helper, RaidMode.NORMAL);
		ServerLevel raid = (ServerLevel) player.level();
		BlockPos feet = player.blockPosition();
		BlockState floor = raid.getBlockState(feet.below());
		boolean clear = raid.getBlockState(feet).isAir() && raid.getBlockState(feet.above()).isAir();
		NetherTestSupport.leave(helper, player);
		NetherTestSupport.result("safeArrival", "floor=" + floor.getBlock() + " clear=" + clear);
		helper.assertTrue(floor.isFaceSturdy(raid, feet.below(), net.minecraft.core.Direction.UP) && clear,
			"the arrival stands on " + floor + " with clear=" + clear);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:portal_out", maxTicks = 100)
	public void bothRaidsHaveALitPortalOut(final GameTestHelper helper) {
		NetherTestSupport.fresh(helper);
		ServerPlayer normal = RaidModeGameTest.enter(helper, RaidMode.NORMAL);
		ServerPlayer hard = RaidModeGameTest.enter(helper, RaidMode.HARD);
		ServerLevel raid = (ServerLevel) normal.level();
		BlockState normalExit = raid.getBlockState(RaidInstance.exitPortal(raid.getServer(), RaidMode.NORMAL));
		BlockState hardExit = raid.getBlockState(RaidInstance.exitPortal(raid.getServer(), RaidMode.HARD));
		NetherTestSupport.leave(helper, normal);
		NetherTestSupport.leave(helper, hard);
		NetherTestSupport.result("portalOut", "normal=" + normalExit.getBlock() + " hard=" + hardExit.getBlock());
		helper.assertTrue(normalExit.is(Blocks.NETHER_PORTAL) && hardExit.is(Blocks.NETHER_PORTAL), "the ways out are " + normalExit + " and " + hardExit);
		helper.succeed();
	}
}
