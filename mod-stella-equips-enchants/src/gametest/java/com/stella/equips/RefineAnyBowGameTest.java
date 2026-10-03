package com.stella.equips;

import com.stella.equips.refine.Refine;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A bow from another mod refines as a ranged weapon, like the vanilla bow and crossbow. */
public class RefineAnyBowGameTest {
	@GameTest
	public void modBowIsRanged(final GameTestHelper helper) {
		boolean modBow = Refine.isRanged(new ItemStack(TestItems.MOD_BOW));
		boolean bow = Refine.isRanged(new ItemStack(Items.BOW));
		boolean crossbow = Refine.isRanged(new ItemStack(Items.CROSSBOW));
		boolean sword = Refine.isRanged(new ItemStack(Items.IRON_SWORD));
		StellaEquips.LOGGER.info("[BEHAVIOR] test=refineAnyBow RESULT modBow={} bow={} crossbow={} sword={}", modBow, bow, crossbow, sword);
		helper.assertTrue(modBow && bow && crossbow && !sword, "ranged checks: mod bow " + modBow + ", sword " + sword);
		helper.succeed();
	}
}
