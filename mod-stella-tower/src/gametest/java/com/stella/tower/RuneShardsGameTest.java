package com.stella.tower;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Mining feeds the tower: a deepslate ore leaves a rune shard, shards craft into runes, and the ghost merchant buys raw ores.
 * Each case goes through the real path: a player's block break, the recipe manager, the shop menu's click.
 */
public class RuneShardsGameTest {
	private static int shardsLeftBy(final GameTestHelper helper, final ServerPlayer player, final ItemStack tool, final Block ore) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(6, 2, 6));
		level.setBlockAndUpdate(at, ore.defaultBlockState());
		player.setItemInHand(InteractionHand.MAIN_HAND, tool);
		helper.assertTrue(player.gameMode.destroyBlock(at), "the player could not break " + ore);
		int shards = 0;
		for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(4.0))) {
			shards += RuneShards.elementOf(entity.getItem()) != null ? entity.getItem().getCount() : 0;
			entity.discard();
		}
		return shards;
	}

	/** A pinned roll of nothing always passes the chance: only a plain player break of a deepslate ore may leave a shard. */
	@GameTest(maxTicks = 40)
	public void aDeepslateOreBrokenByAPlayerLeavesAShardButASilkTouchOrStoneBreakDoesNot(final GameTestHelper helper) {
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		ItemStack silk = new ItemStack(Items.IRON_PICKAXE);
		silk.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
		int plain;
		int silked;
		int stone;
		int debris;
		RuneShards.pinRoll(0.0);
		try {
			stone = shardsLeftBy(helper, player, new ItemStack(Items.IRON_PICKAXE), Blocks.IRON_ORE);
			silked = shardsLeftBy(helper, player, silk, Blocks.DEEPSLATE_IRON_ORE);
			plain = shardsLeftBy(helper, player, new ItemStack(Items.IRON_PICKAXE), Blocks.DEEPSLATE_IRON_ORE);
			debris = shardsLeftBy(helper, player, new ItemStack(Items.DIAMOND_PICKAXE), Blocks.ANCIENT_DEBRIS);
		} finally {
			RuneShards.pinRoll(null);
			TowerTestSupport.leave(helper, player);
		}
		TowerTestSupport.result("shardDrops", "deepslate=" + plain + " silkTouch=" + silked + " stoneOre=" + stone + " ancientDebris=" + debris
			+ " chance=" + RuneShards.DROP_CHANCE);
		helper.assertTrue(RuneShards.DROP_CHANCE == 0.04, "the shipped chance is " + RuneShards.DROP_CHANCE + ", not 4%");
		helper.assertTrue(plain == 1, "a deepslate ore left " + plain + " shards on a roll that passes");
		helper.assertTrue(debris == 1, "ancient debris left " + debris + " shards on a roll that passes");
		helper.assertTrue(silked == 0, "a silk touch break left " + silked + " shards");
		helper.assertTrue(stone == 0, "a stone ore left " + stone + " shards");
		helper.succeed();
	}

	private static ItemStack craft(final GameTestHelper helper, final List<ItemStack> stacks) {
		List<ItemStack> grid = new java.util.ArrayList<>(stacks);
		while (grid.size() < 9) {
			grid.add(ItemStack.EMPTY);
		}
		CraftingInput input = CraftingInput.of(3, 3, grid);
		return helper.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
			.map(holder -> holder.value().assemble(input)).orElse(ItemStack.EMPTY);
	}

	private static List<ItemStack> shards(final TowerElement element, final int count) {
		return java.util.stream.IntStream.range(0, count).mapToObj(i -> RuneShards.shard(element)).collect(java.util.stream.Collectors.toList());
	}

	/** Nine shards make a floor 10 rune, a rune and eight shards add ten floors, and any other mix makes nothing. */
	@GameTest(maxTicks = 40)
	public void shardsCraftIntoARuneAndRaiseOneTenFloors(final GameTestHelper helper) {
		ItemStack made = craft(helper, shards(TowerElement.WIND, RuneShards.SHARDS_PER_RUNE));
		List<ItemStack> raising = shards(TowerElement.ICE, RuneShards.UPGRADE_SHARDS);
		raising.add(TowerRunes.rune(TowerElement.ICE, 123));
		ItemStack raised = craft(helper, raising);
		List<ItemStack> mixed = shards(TowerElement.ICE, RuneShards.UPGRADE_SHARDS);
		mixed.add(RuneShards.shard(TowerElement.FIRE));
		List<ItemStack> wrongRune = shards(TowerElement.FIRE, RuneShards.UPGRADE_SHARDS);
		wrongRune.add(TowerRunes.rune(TowerElement.ICE, 123));
		List<ItemStack> eight = shards(TowerElement.ICE, RuneShards.UPGRADE_SHARDS);
		ItemStack mixedResult = craft(helper, mixed);
		ItemStack wrongRuneResult = craft(helper, wrongRune);
		ItemStack eightResult = craft(helper, eight);
		RuneData madeData = made.get(TowerItems.RUNE_DATA);
		RuneData raisedData = raised.get(TowerItems.RUNE_DATA);
		TowerTestSupport.result("shardCrafting", "made=" + madeData + " raised=" + raisedData + " mixed=" + mixedResult + " wrongRune=" + wrongRuneResult
			+ " eightAlone=" + eightResult);
		helper.assertTrue(made.is(TowerItems.RUNE) && madeData != null && madeData.element() == TowerElement.WIND && madeData.floor() == RuneShards.FLOOR_STEP,
			"nine wind shards made " + madeData);
		helper.assertTrue(raised.is(TowerItems.RUNE) && raisedData != null && raisedData.element() == TowerElement.ICE
			&& raisedData.floor() == 123 + RuneShards.FLOOR_STEP, "a floor 123 ice rune and eight ice shards made " + raisedData);
		helper.assertTrue(mixedResult.isEmpty(), "eight ice shards and a fire shard made " + mixedResult);
		helper.assertTrue(wrongRuneResult.isEmpty(), "an ice rune and eight fire shards made " + wrongRuneResult);
		helper.assertTrue(eightResult.isEmpty(), "eight shards alone made " + eightResult);
		helper.succeed();
	}

	/** The shop menu's slot that shows a stack of the player's: the inventory copies a stack it is given, so it is found by content. */
	private static int menuSlotHolding(final MerchantShopMenu menu, final ServerPlayer player, final net.minecraft.world.item.Item item, final int count) {
		for (Slot slot : menu.slots) {
			if (slot.container == player.getInventory() && slot.getItem().is(item) && slot.getItem().getCount() == count) {
				return slot.index;
			}
		}
		return -1;
	}

	private static int count(final ServerPlayer player, final net.minecraft.world.item.Item item) {
		int total = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			total += stack.is(item) ? stack.getCount() : 0;
		}
		return total;
	}

	private static int emeralds(final ServerPlayer player) {
		int total = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			total += stack.is(Items.EMERALD) ? stack.getCount() : 0;
		}
		return total;
	}

	/**
	 * Sells raw iron through the open shop: 32 pay 2 emeralds at 16 to 1. Then a stack of 40 pays 2 more and leaves 8, and those
	 * 8 with 15 more make 23, which pay 1 and leave 7.
	 */
	@GameTest(maxTicks = 60)
	public void theMerchantBuysRawIronAtSixteenToOneAndKeepsTheLeftover(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerElites.BOSS_EVERY != 0 && f % TowerElites.ELITE_EVERY != 0);
		QuietFloor.hold(floor);
		TowerFloors.ensure(tower, floor);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		TowerTestSupport.vulnerable(player);
		GhostMerchant merchant = TowerMerchants.appear(tower, floor, tower.getRandom());
		helper.assertTrue(merchant != null, "no merchant could be raised");
		merchant.stock(List.of(MerchantGoods.SOUL_TONIC));
		merchant.mobInteract(player, InteractionHand.MAIN_HAND);
		helper.assertTrue(player.containerMenu instanceof MerchantShopMenu, "the shop did not open in the tower");
		MerchantShopMenu menu = (MerchantShopMenu) player.containerMenu;
		int[] slots = new int[3];
		player.getInventory().add(new ItemStack(Items.RAW_IRON, 32));
		slots[0] = menuSlotHolding(menu, player, Items.RAW_IRON, 32);
		menu.clicked(slots[0], 0, ContainerInput.PICKUP, player);
		int afterFirst = emeralds(player);
		int ironAfterFirst = count(player, Items.RAW_IRON);
		player.getInventory().add(new ItemStack(Items.RAW_IRON, 40));
		slots[1] = menuSlotHolding(menu, player, Items.RAW_IRON, 40);
		menu.clicked(slots[1], 0, ContainerInput.PICKUP, player);
		int afterSecond = emeralds(player);
		int ironAfterSecond = count(player, Items.RAW_IRON);
		player.getInventory().add(new ItemStack(Items.RAW_IRON, 15));
		slots[2] = menuSlotHolding(menu, player, Items.RAW_IRON, 15 + ironAfterSecond);
		menu.clicked(slots[2], 0, ContainerInput.PICKUP, player);
		int afterSmall = emeralds(player);
		int ironAfterSmall = count(player, Items.RAW_IRON);
		TowerTestSupport.result("merchantBuysOre", "afterFirst=" + afterFirst + " ironAfterFirst=" + ironAfterFirst + " afterSecond=" + afterSecond
			+ " ironAfterSecond=" + ironAfterSecond + " afterSmall=" + afterSmall + " ironAfterSmall=" + ironAfterSmall);
		merchant.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(slots[0] >= 0 && slots[1] >= 0 && slots[2] >= 0, "the iron stacks were not found in the shop menu");
		helper.assertTrue(afterFirst == 2 && ironAfterFirst == 0, "32 raw iron paid " + afterFirst + " emeralds and left " + ironAfterFirst);
		helper.assertTrue(afterSecond == 4 && ironAfterSecond == 8, "40 raw iron paid " + (afterSecond - afterFirst) + " emeralds and left " + ironAfterSecond);
		helper.assertTrue(afterSmall == afterSecond + 1 && ironAfterSmall == 7, "23 raw iron paid " + (afterSmall - afterSecond) + " emeralds and left " + ironAfterSmall);
		helper.succeed();
	}
}
