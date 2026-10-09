package com.stella.rpg;

import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A smelted material keeps its bonus maps and enchantments. Gear smelted to a material carries nothing. */
public final class SmeltBonusGameTest {
	private static final int INPUT = 0;
	private static final int FUEL = 1;
	private static final int OUTPUT = 2;
	/** A furnace cooks for 200 ticks. */
	private static final int COOK_TICKS = 260;

	private static FurnaceBlockEntity furnace(final GameTestHelper helper, final BlockPos relative) {
		helper.setBlock(relative, Blocks.FURNACE);
		return helper.getBlockEntity(relative, FurnaceBlockEntity.class);
	}

	private static void tick(final GameTestHelper helper, final BlockPos relative, final FurnaceBlockEntity furnace, final int ticks) {
		ServerLevel level = helper.getLevel();
		BlockPos absolute = helper.absolutePos(relative);
		for (int i = 0; i < ticks; i++) {
			BlockState state = level.getBlockState(absolute);
			AbstractFurnaceBlockEntity.serverTick(level, absolute, state, furnace);
		}
	}

	@GameTest
	public void furnaceKeepsBonus(final GameTestHelper helper) {
		Holder<Enchantment> sharpness = BonusTestSupport.enchantment(helper, Enchantments.SHARPNESS);
		BlockPos pos = new BlockPos(1, 1, 1);
		FurnaceBlockEntity furnace = furnace(helper, pos);
		ItemStack raw = BonusTestSupport.enchanted(BonusTestSupport.rolled(Items.RAW_IRON, 1, Map.of("str", 3, "dex", 1)), sharpness, 2);
		furnace.setItem(INPUT, raw);
		furnace.setItem(FUEL, new ItemStack(Items.COAL));
		tick(helper, pos, furnace, COOK_TICKS);
		ItemStack out = furnace.getItem(OUTPUT);
		RpgTestSupport.result("furnaceKeepsBonus", "out=" + out + " stats=" + BonusTestSupport.stats(out) + " sharp=" + BonusTestSupport.level(out, sharpness));
		helper.assertTrue(out.is(Items.IRON_INGOT), "furnace output " + out);
		helper.assertTrue(Map.of("str", 3, "dex", 1).equals(BonusTestSupport.stats(out)), "stats " + BonusTestSupport.stats(out));
		helper.assertTrue(BonusTestSupport.level(out, sharpness) == 2, "sharpness " + BonusTestSupport.level(out, sharpness));
		helper.succeed();
	}

	@GameTest
	public void furnaceStallsOnOtherSet(final GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		FurnaceBlockEntity furnace = furnace(helper, pos);
		ItemStack setA = BonusTestSupport.rolled(Items.IRON_INGOT, 1, Map.of("str", 2));
		Map<String, Integer> setB = Map.of("agi", 4);
		furnace.setItem(OUTPUT, setA.copy());
		furnace.setItem(INPUT, BonusTestSupport.rolled(Items.RAW_IRON, 1, setB));
		furnace.setItem(FUEL, new ItemStack(Items.COAL, 4));
		tick(helper, pos, furnace, COOK_TICKS);
		boolean stalled = furnace.getItem(OUTPUT).getCount() == 1 && furnace.getItem(INPUT).getCount() == 1;
		boolean keptA = ItemStack.isSameItemSameComponents(setA, furnace.getItem(OUTPUT));
		furnace.setItem(OUTPUT, ItemStack.EMPTY);
		tick(helper, pos, furnace, COOK_TICKS);
		ItemStack out = furnace.getItem(OUTPUT);
		RpgTestSupport.result("furnaceStallsOnOtherSet", "stalled=" + stalled + " keptA=" + keptA + " after=" + BonusTestSupport.stats(out));
		helper.assertTrue(stalled && keptA, "the furnace did not stall on a different set");
		helper.assertTrue(out.is(Items.IRON_INGOT) && setB.equals(BonusTestSupport.stats(out)), "after the output was taken: " + BonusTestSupport.stats(out));
		helper.succeed();
	}

	@GameTest
	public void gearSmeltedToNuggetCarriesNothing(final GameTestHelper helper) {
		ItemStack sword = BonusTestSupport.rolled(Items.IRON_SWORD, 1, Map.of("str", 5));
		ItemStack nugget = BonusTestSupport.smelt(helper, sword);
		RpgTestSupport.result("gearSmeltedToNuggetCarriesNothing", "nugget=" + nugget + " patch=" + nugget.getComponentsPatch());
		helper.assertTrue(nugget.is(Items.IRON_NUGGET) && nugget.getComponentsPatch().isEmpty(), "nugget carries " + nugget.getComponentsPatch());
		helper.succeed();
	}
}
