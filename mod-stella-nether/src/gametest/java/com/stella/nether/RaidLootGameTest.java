package com.stella.nether;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Everything rolled from a loot table in the raid nether comes more plentifully: x1.15 in normal, x1.5 in hard. */
public class RaidLootGameTest {
	static final int ROLLS = 2000;

	/** The mean quartz a nether quartz ore gives over many breaks at a spot; one a break, plain. */
	static double meanQuartz(final ServerLevel level, final BlockPos at) {
		long quartz = 0;
		for (int i = 0; i < ROLLS; i++) {
			for (ItemStack stack : Block.getDrops(Blocks.NETHER_QUARTZ_ORE.defaultBlockState(), level, at, null)) {
				if (stack.is(Items.QUARTZ)) {
					quartz += stack.getCount();
				}
			}
		}
		return (double) quartz / ROLLS;
	}

	static boolean near(final double mean, final double multiplier) {
		double fraction = multiplier - Math.floor(multiplier);
		double sigma = Math.sqrt(fraction * (1 - fraction) / ROLLS);
		return Math.abs(mean - multiplier) <= 4 * sigma + 1.0E-9;
	}

	static void inRaid(final GameTestHelper helper, final RaidMode mode, final double multiplier, final String name) {
		NetherTestSupport.fresh(helper);
		ServerPlayer player = RaidModeGameTest.enter(helper, mode);
		double mean = meanQuartz((ServerLevel) player.level(), player.blockPosition());
		NetherTestSupport.result(name, "mean=" + mean + " multiplier=" + multiplier + " rolls=" + ROLLS);
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(near(mean, multiplier), mode + " raid quartz came at " + mean + " against x" + multiplier);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:loot_normal", maxTicks = 100)
	public void aNormalRaidGivesMoreOfEverything(final GameTestHelper helper) {
		inRaid(helper, RaidMode.NORMAL, RaidLoot.NORMAL_MULTIPLIER, "lootNormal");
	}

	@GameTest(environment = "mod-stella-nether-test:loot_hard", maxTicks = 100)
	public void aHardRaidGivesMoreStill(final GameTestHelper helper) {
		inRaid(helper, RaidMode.HARD, RaidLoot.HARD_MULTIPLIER, "lootHard");
	}

	@GameTest(environment = "mod-stella-nether-test:loot_outside", maxTicks = 100)
	public void outsideTheRaidLootIsUnchanged(final GameTestHelper helper) {
		double mean = meanQuartz(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)));
		NetherTestSupport.result("lootOutside", "mean=" + mean);
		helper.assertTrue(mean == 1.0, "outside the raid quartz came at " + mean);
		helper.succeed();
	}
}
