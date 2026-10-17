package com.stella.equips;

import com.stella.equips.refine.Refine;
import com.stella.equips.refine.RefineCatalyst;
import com.stella.equips.refine.RefineStation;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** The Refine Catalyst: a failed refine from +7 that would lower the level keeps it and spends one; deep ore drops it. */
public final class RefineCatalystGameTest {
	@GameTest
	public void failedRefineKeepsTheLevelAndSpendsACatalyst(final GameTestHelper helper) {
		// +8 keeps its level on a failure anyway, so the catalyst is not spent.
		Attempt eight = refine(helper, 8, 1, false);
		StellaEquips.LOGGER.info("[BEHAVIOR] test=refineCatalyst RESULT from=8 result={} level={} catalysts={}", eight.result, eight.level, eight.catalysts);
		helper.assertTrue(eight.result == RefineStation.Result.FAILURE && eight.level == 8, "a failed +8 refine left +" + eight.level);
		helper.assertTrue(eight.catalysts == 1, "the +8 failure left " + eight.catalysts + " catalysts, not 1");
		// +11 loses a level on a failure, so the catalyst is the only thing that holds it.
		Attempt held = refine(helper, 11, 1, false);
		Attempt bare = refine(helper, 11, 0, false);
		StellaEquips.LOGGER.info("[BEHAVIOR] test=refineCatalyst RESULT from=11 withCatalyst={} without={} catalysts={}", held.level, bare.level, held.catalysts);
		helper.assertTrue(held.level == 11 && held.catalysts == 0, "a failed +11 refine with a catalyst left +" + held.level + " and " + held.catalysts + " catalysts");
		helper.assertTrue(bare.level == Refine.levelAfterFailure(11) && bare.level < 11, "a failed +11 refine without a catalyst left +" + bare.level);
		// A successful roll, and a piece below +7, spend nothing.
		Attempt won = refine(helper, 8, 1, true);
		helper.assertTrue(won.result == RefineStation.Result.SUCCESS && won.level == 9 && won.catalysts == 1,
			"a successful refine ended at +" + won.level + " with " + won.catalysts + " catalysts");
		Attempt low = refine(helper, RefineCatalyst.MIN_LEVEL - 1, 1, false);
		helper.assertTrue(low.result == RefineStation.Result.FAILURE && low.catalysts == 1, "a failed +6 refine spent a catalyst: " + low.catalysts + " left");
		helper.succeed();
	}

	@GameTest
	public void deepOreDropsACatalystOnlyToAFairBreak(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos target = helper.absolutePos(new BlockPos(2, 2, 2));
		RefineCatalyst.setDropRoller((player, random, chance) -> true);
		try {
			int plain = breakOre(helper, target, new ItemStack(Items.IRON_PICKAXE));
			ItemStack silk = new ItemStack(Items.IRON_PICKAXE);
			silk.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
			int silked = breakOre(helper, target, silk);
			int handed = breakOre(helper, target, ItemStack.EMPTY);
			StellaEquips.LOGGER.info("[BEHAVIOR] test=refineCatalystDrop RESULT iron={} silk={} hand={}", plain, silked, handed);
			helper.assertTrue(plain == 1, "an iron pickaxe break dropped " + plain + " catalysts");
			helper.assertTrue(silked == 0, "a silk touch break dropped " + silked + " catalysts");
			helper.assertTrue(handed == 0, "a bare-handed break dropped " + handed + " catalysts");
		} finally {
			RefineCatalyst.setDropRoller(null);
		}
		helper.succeed();
	}

	private record Attempt(RefineStation.Result result, int level, int catalysts) {
	}

	/** One refine of a diamond sword from a level, with the forced outcome and a number of catalysts held. */
	private static Attempt refine(final GameTestHelper helper, final int level, final int catalysts, final boolean success) {
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		Refine.setLevel(sword, level);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getInventory().add(new ItemStack(Refine.material(sword), 16));
		if (catalysts > 0) {
			player.getInventory().add(new ItemStack(RefineCatalyst.ITEM, catalysts));
		}
		AnvilMenu menu = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
		player.containerMenu = menu;
		menu.getSlot(0).set(sword);
		RefiningGameTest.force(player, success);
		RefineStation.Result result = RefineStation.refine(player, menu.containerId);
		int held = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			held += stack.is(RefineCatalyst.ITEM) ? stack.getCount() : 0;
		}
		return new Attempt(result, Refine.level(menu.getSlot(0).getItem()), held);
	}

	/** Breaks a deepslate diamond ore with the tool as a survival player, and counts the catalysts on the ground. */
	private static int breakOre(final GameTestHelper helper, final BlockPos target, final ItemStack tool) {
		ServerLevel level = helper.getLevel();
		ServerPlayer miner = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		miner.setPos(target.getX() + 0.5, target.getY(), target.getZ() + 2.5);
		miner.setItemInHand(InteractionHand.MAIN_HAND, tool);
		level.setBlockAndUpdate(target, Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState());
		helper.assertTrue(miner.gameMode.destroyBlock(target), "the ore did not break");
		int found = 0;
		List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(target).inflate(2.0));
		for (ItemEntity drop : drops) {
			if (drop.getItem().is(RefineCatalyst.ITEM)) {
				found += drop.getItem().getCount();
			}
			drop.discard();
		}
		return found;
	}
}
