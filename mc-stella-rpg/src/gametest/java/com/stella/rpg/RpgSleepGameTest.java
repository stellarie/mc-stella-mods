package com.stella.rpg;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

public final class RpgSleepGameTest {
	@GameTest
	public void sleeperHealsAwakeDoesNot(final GameTestHelper helper) {
		ServerPlayer sleeper = RpgTestSupport.realPlayer(helper);
		ServerPlayer awake = RpgTestSupport.realPlayer(helper);
		sleeper.setHealth(4.0F);
		awake.setHealth(4.0F);
		BlockPos bed = new BlockPos(1, 1, 1);
		helper.setBlock(bed, Blocks.STRAW_BED);
		sleeper.startSleeping(helper.absolutePos(bed));

		helper.assertTrue(sleeper.isSleeping(), "sleeper not sleeping");
		helper.assertTrue(helper.getLevel().players().contains(sleeper), "sleeper not in level players; level=" + sleeper.level().dimension());
		RpgSleep.healSleepers(helper.getLevel());

		helper.assertTrue(sleeper.getHealth() == sleeper.getMaxHealth(), "sleeper health=" + sleeper.getHealth());
		helper.assertTrue(awake.getHealth() == 4.0F, "awake health=" + awake.getHealth());
		RpgTestSupport.result("sleeperHealsAwakeDoesNot", "sleeper=" + sleeper.getHealth() + " awake=" + awake.getHealth());
		helper.succeed();
	}
}
