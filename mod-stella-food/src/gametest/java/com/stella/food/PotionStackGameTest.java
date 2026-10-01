package com.stella.food;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;

/** Potions of the same kind stack to 16, different potions stay apart, and drinking takes one from the stack. */
public final class PotionStackGameTest {
	@GameTest
	public void stacking(final GameTestHelper helper) {
		ItemStack healing = PotionContents.createItemStack(Items.POTION, Potions.HEALING);
		ItemStack swift = PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS);
		int drink = healing.getMaxStackSize();
		int splash = PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING).getMaxStackSize();
		int lingering = PotionContents.createItemStack(Items.LINGERING_POTION, Potions.HEALING).getMaxStackSize();
		boolean sameKind = ItemStack.isSameItemSameComponents(healing, PotionContents.createItemStack(Items.POTION, Potions.HEALING));
		boolean mixed = ItemStack.isSameItemSameComponents(healing, swift);
		StellaFood.LOGGER.info("[BEHAVIOR] test=potionStacking RESULT drink={} splash={} lingering={} sameKind={} mixed={}",
			drink, splash, lingering, sameKind, mixed);
		helper.assertTrue(drink == 16 && splash == 16 && lingering == 16, "potions stack to " + drink + "/" + splash + "/" + lingering);
		helper.assertTrue(sameKind && !mixed, "potion kinds merge wrongly");
		helper.succeed();
	}

	@GameTest
	public void drinkFromStack(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		ItemStack stack = PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS);
		stack.setCount(3);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		ItemStack left = player.getMainHandItem().finishUsingItem(helper.getLevel(), player);
		player.setItemInHand(InteractionHand.MAIN_HAND, left);
		int potions = player.getInventory().countItem(Items.POTION);
		int bottles = player.getInventory().countItem(Items.GLASS_BOTTLE);
		StellaFood.LOGGER.info("[BEHAVIOR] test=drinkFromStack RESULT potions={} bottles={} swift={}", potions, bottles,
			player.hasEffect(net.minecraft.world.effect.MobEffects.SPEED));
		helper.assertTrue(potions == 2 && bottles == 1, "drinking one of three left potions=" + potions + " bottles=" + bottles);
		helper.succeed();
	}
}
