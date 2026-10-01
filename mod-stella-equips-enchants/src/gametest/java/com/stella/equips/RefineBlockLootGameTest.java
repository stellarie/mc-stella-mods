package com.stella.equips;

import com.stella.equips.refine.RefineBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** The refining anvil drops itself, and an explosion of radius 4 keeps about one in four. */
public final class RefineBlockLootGameTest {
	@GameTest
	public void survivesExplosion(final GameTestHelper helper) {
		int[] counts = blockDrops(helper, RefineBlocks.REFINING_ANVIL.defaultBlockState(), RefineBlocks.REFINING_ANVIL.asItem());
		StellaEquips.LOGGER.info("[BEHAVIOR] test=refiningAnvilLoot RESULT plain={} blast={}", counts[0], counts[1]);
		helper.assertTrue(counts[0] == 400, "without an explosion it dropped " + counts[0] + " of 400");
		helper.assertTrue(counts[1] >= 50 && counts[1] <= 150, "an explosion kept " + counts[1] + " of 400");
		helper.succeed();
	}

	/** Block drops without and with an explosion of radius 4: always without, about one in four with. */
	static int[] blockDrops(final GameTestHelper helper, final net.minecraft.world.level.block.state.BlockState state, final net.minecraft.world.item.Item item) {
		net.minecraft.server.level.ServerLevel level = helper.getLevel();
		net.minecraft.world.phys.Vec3 origin = helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 1.0, 1.5));
		int[] counts = new int[2];
		for (int roll = 0; roll < 400; roll++) {
			for (int blast = 0; blast < 2; blast++) {
				net.minecraft.world.level.storage.loot.LootParams.Builder params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
					.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, origin)
					.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL, net.minecraft.world.item.ItemStack.EMPTY);
				if (blast == 1) {
					params.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.EXPLOSION_RADIUS, 4.0F);
				}
				for (net.minecraft.world.item.ItemStack stack : state.getDrops(params)) {
					if (stack.is(item)) {
						counts[blast] += stack.getCount();
					}
				}
			}
		}
		return counts;
	}
}
