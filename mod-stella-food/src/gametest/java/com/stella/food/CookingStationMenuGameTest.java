package com.stella.food;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;

public final class CookingStationMenuGameTest {
	@GameTest
	public void stationOpensScreenWithNineInputsAndKeepsHeldFood(final GameTestHelper helper) {
		FoodTestLog.track(helper, "stationOpensScreenWithNineInputsAndKeepsHeldFood");
		BlockPos relative = new BlockPos(2, 1, 2);
		helper.setBlock(relative, CookingStationBlock.BLOCK);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos absolute = helper.absolutePos(relative);
		player.setPos(absolute.getX() + 0.5, absolute.getY() + 1, absolute.getZ() + 0.5);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
		ItemStack before = player.getMainHandItem().copy();
		helper.useBlock(relative, player);
		AbstractContainerMenu menu = player.containerMenu;
		helper.assertTrue(menu != player.inventoryMenu, "station did not open its crafting menu");
		helper.assertTrue(StellaFood.id("cooking_station").equals(BuiltInRegistries.MENU.getKey(menu.getType())),
			"station did not open its crafting menu");
		helper.assertTrue(menu.slots.size() >= 46, "station menu lacks nine inputs, output, or player inventory");
		helper.assertTrue(menu.stillValid(player), "station crafting screen closes while its block remains");
		helper.assertTrue(ItemStack.isSameItemSameComponents(before, player.getMainHandItem())
			&& before.getCount() == player.getMainHandItem().getCount(), "opening the screen consumed held food");
		helper.setBlock(relative, Blocks.AIR);
		helper.assertTrue(!menu.stillValid(player), "crafting screen stayed valid after the station was removed");
		helper.succeed();
	}

	@GameTest
	public void stationGridCooksFoodAndConsumesOnlyItsInput(final GameTestHelper helper) {
		FoodTestLog.track(helper, "stationGridCooksFoodAndConsumesOnlyItsInput");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		AbstractContainerMenu menu = openStation(helper, player);
		menu.getSlot(1).set(new ItemStack(Items.APPLE));
		menu.slotsChanged(menu.getSlot(1).container);
		ItemStack result = menu.getSlot(0).getItem();
		helper.assertTrue(result.is(Items.APPLE)
			&& result.get(DataComponents.FOOD).nutrition() > Items.APPLE.getDefaultInstance()
				.get(DataComponents.FOOD).nutrition()
			&& result.getOrDefault(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false),
			"station grid did not preview improved apple food");
		menu.clicked(0, 0, ContainerInput.PICKUP, player);
		helper.assertTrue(menu.getCarried().is(Items.APPLE) && menu.getSlot(1).getItem().isEmpty(),
			"taking cooked output failed to consume exactly one apple");
		helper.succeed();
	}

	@GameTest
	public void stationGridCombinesDishAndReturnsHoneyBottle(final GameTestHelper helper) {
		FoodTestLog.track(helper, "stationGridCombinesDishAndReturnsHoneyBottle");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		AbstractContainerMenu menu = openStation(helper, player);
		menu.getSlot(1).set(new ItemStack(Items.APPLE));
		menu.getSlot(2).set(new ItemStack(Items.WHEAT));
		menu.getSlot(3).set(new ItemStack(Items.HONEY_BOTTLE));
		menu.slotsChanged(menu.getSlot(1).container);
		helper.assertTrue(menu.getSlot(0).getItem().is(FoodItems.ORCHARD_TART),
			"station grid did not preview Orchard Tart");
		menu.clicked(0, 0, ContainerInput.PICKUP, player);
		helper.assertTrue(menu.getCarried().is(FoodItems.ORCHARD_TART)
			&& menu.getSlot(1).getItem().isEmpty() && menu.getSlot(2).getItem().isEmpty(),
			"taking dish output failed to consume its ingredients");
		int bottles = player.getInventory().countItem(Items.GLASS_BOTTLE);
		for (int input = 1; input <= 9; input++) {
			if (menu.getSlot(input).getItem().is(Items.GLASS_BOTTLE)) {
				bottles++;
			}
		}
		helper.assertTrue(bottles == 1, "dish recipe lost or duplicated its honey bottle");
		helper.succeed();
	}

	@GameTest
	public void stationGridInfusesDishAndReturnsPotionBottle(final GameTestHelper helper) {
		FoodTestLog.track(helper, "stationGridInfusesDishAndReturnsPotionBottle");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		AbstractContainerMenu menu = openStation(helper, player);
		menu.getSlot(1).set(new ItemStack(FoodItems.HUNTER_STEW));
		ItemStack potion = PotionContents.createItemStack(Items.SPLASH_POTION, Potions.POISON);
		menu.getSlot(2).set(potion);
		menu.slotsChanged(menu.getSlot(1).container);
		ItemStack result = menu.getSlot(0).getItem();
		helper.assertTrue(result.is(FoodItems.HUNTER_STEW)
			&& potion.get(DataComponents.POTION_CONTENTS).equals(result.get(DataComponents.POTION_CONTENTS)),
			"station grid did not preview the infused dish");
		menu.clicked(0, 0, ContainerInput.PICKUP, player);
		helper.assertTrue(menu.getCarried().has(DataComponents.POTION_CONTENTS)
			&& menu.getSlot(1).getItem().isEmpty(), "infusion failed to consume its dish");
		int bottles = player.getInventory().countItem(Items.GLASS_BOTTLE);
		for (int input = 1; input <= 9; input++) {
			if (menu.getSlot(input).getItem().is(Items.GLASS_BOTTLE)) {
				bottles++;
			}
		}
		helper.assertTrue(bottles == 1, "infusion lost or duplicated its potion bottle");
		helper.succeed();
	}

	@GameTest
	public void recipeBookSelectionFillsDishFromInventory(final GameTestHelper helper) {
		FoodTestLog.track(helper, "recipeBookSelectionFillsDishFromInventory");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getInventory().add(new ItemStack(Items.COOKED_BEEF));
		player.getInventory().add(new ItemStack(Items.BROWN_MUSHROOM));
		player.getInventory().add(new ItemStack(Items.CARROT));
		AbstractContainerMenu menu = openStation(helper, player);
		menu.clickMenuButton(player, 0);
		helper.assertTrue(menu.getSlot(0).getItem().is(FoodItems.HUNTER_STEW),
			"recipe book did not fill the Hunter Stew ingredients");
		helper.assertTrue(player.getInventory().countItem(Items.COOKED_BEEF) == 0
			&& player.getInventory().countItem(Items.BROWN_MUSHROOM) == 0
			&& player.getInventory().countItem(Items.CARROT) == 0,
			"recipe book left ingredients in the inventory after filling the grid");
		menu.clicked(0, 0, ContainerInput.PICKUP, player);
		helper.assertTrue(menu.getCarried().is(FoodItems.HUNTER_STEW),
			"recipe book filled a dish that the station could not craft");
		helper.succeed();
	}

	@GameTest
	public void shiftClickCookingPreservesInputOutputCount(final GameTestHelper helper) {
		FoodTestLog.track(helper, "shiftClickCookingPreservesInputOutputCount");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		AbstractContainerMenu menu = openStation(helper, player);
		menu.getSlot(1).set(new ItemStack(Items.BEEF, 3));
		menu.slotsChanged(menu.getSlot(1).container);
		menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
		int cooked = player.getInventory().countItem(Items.COOKED_BEEF);
		int raw = menu.getSlot(1).getItem().getCount();
		helper.assertTrue(cooked > 0 && cooked + raw == 3,
			"shift-click duplicated or lost food: cooked=" + cooked + " raw=" + raw);
		helper.succeed();
	}

	@GameTest
	public void closingStationReturnsUncraftedInputs(final GameTestHelper helper) {
		FoodTestLog.track(helper, "closingStationReturnsUncraftedInputs");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		AbstractContainerMenu menu = openStation(helper, player);
		menu.getSlot(1).set(new ItemStack(Items.APPLE, 2));
		menu.getSlot(2).set(PotionContents.createItemStack(Items.POTION, Potions.POISON));
		menu.slotsChanged(menu.getSlot(1).container);
		player.closeContainer();
		helper.assertTrue(player.getInventory().countItem(Items.APPLE) == 2
			&& player.getInventory().countItem(Items.POTION) == 1,
			"closing station lost or duplicated uncrafted inputs");
		helper.succeed();
	}

	private static AbstractContainerMenu openStation(final GameTestHelper helper, final ServerPlayer player) {
		BlockPos relative = new BlockPos(2, 1, 2);
		helper.setBlock(relative, CookingStationBlock.BLOCK);
		BlockPos absolute = helper.absolutePos(relative);
		player.setPos(absolute.getX() + 0.5, absolute.getY() + 1, absolute.getZ() + 0.5);
		helper.useBlock(relative, player);
		helper.assertTrue(player.containerMenu != player.inventoryMenu, "station menu did not open");
		helper.assertTrue(StellaFood.id("cooking_station").equals(
			BuiltInRegistries.MENU.getKey(player.containerMenu.getType())), "station menu did not open");
		return player.containerMenu;
	}
}
