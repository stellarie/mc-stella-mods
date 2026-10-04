package com.stella.equips;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Use and falls ask AnvilBlock.damage for the next state; every anvil keeps its own. */
public class AnvilNeverBreaksGameTest {
	@GameTest
	public void anvilsNeverWear(final GameTestHelper helper) {
		for (BlockState anvil : new BlockState[] {
			Blocks.ANVIL.defaultBlockState(), Blocks.CHIPPED_ANVIL.defaultBlockState(), Blocks.DAMAGED_ANVIL.defaultBlockState()
		}) {
			BlockState next = AnvilBlock.damage(anvil);
			StellaEquips.LOGGER.info("[BEHAVIOR] test=anvilsNeverWear RESULT from={} to={}", anvil, next);
			helper.assertTrue(anvil.equals(next), anvil + " became " + next);
		}
		helper.succeed();
	}
}
