package com.stella.rpg;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** The XP curve is vanilla to level 30 and steeper above it. Stackable items stack to 99. */
public final class XpCurveGameTest {
	@GameTest
	public void curveAndStacks(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		player.setExperienceLevels(30);
		int at30 = player.getXpNeededForNextLevel();
		player.setExperienceLevels(60);
		int at60 = player.getXpNeededForNextLevel();
		int cobble = Items.COBBLESTONE.getDefaultMaxStackSize();
		int sword = Items.DIAMOND_SWORD.getDefaultMaxStackSize();
		RpgTestSupport.result("curveAndStacks", "at30=" + at30 + " at60=" + at60 + " cobble=" + cobble + " sword=" + sword);
		helper.assertTrue(at30 == 112, "level 30 needs " + at30);
		helper.assertTrue(at60 > XpCurve.vanilla(60) && at60 == XpCurve.DEFAULT.need(60), "level 60 needs " + at60);
		helper.assertTrue(cobble == 99 && sword == 1, "cobblestone " + cobble + ", sword " + sword);
		helper.succeed();
	}
}
