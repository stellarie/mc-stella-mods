package com.stella.rpg;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The auto-pickup toggle: off leaves an item on the ground, sneaking still picks it up, and on picks it up. */
public final class AutoPickupGameTest {
	@GameTest
	public void toggleGatesWalkingPickup(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		player.getInventory().clearContent();
		helper.assertTrue(AutoPickup.isOn(player), "a new player does not start with auto-pickup on");

		AutoPickup.set(player, false);
		ItemEntity first = itemAtFeet(helper, player);
		first.playerTouch(player);
		helper.assertTrue(first.isAlive() && count(player) == 0, "auto-pickup off picked up the item");

		player.setShiftKeyDown(true);
		first.playerTouch(player);
		helper.assertTrue(!first.isAlive() && count(player) == 3, "sneaking did not pick up the item: " + count(player));
		player.setShiftKeyDown(false);

		AutoPickup.set(player, true);
		ItemEntity second = itemAtFeet(helper, player);
		second.playerTouch(player);
		helper.assertTrue(!second.isAlive() && count(player) == 6, "auto-pickup on did not pick up the item: " + count(player));
		RpgTestSupport.result("toggleGatesWalkingPickup", "count=" + count(player));
		helper.succeed();
	}

	private static ItemEntity itemAtFeet(final GameTestHelper helper, final ServerPlayer player) {
		ItemEntity item = new ItemEntity(helper.getLevel(), player.getX(), player.getY(), player.getZ(), new ItemStack(Items.APPLE, 3));
		item.setNoPickUpDelay();
		helper.getLevel().addFreshEntity(item);
		return item;
	}

	private static int count(final ServerPlayer player) {
		return player.getInventory().countItem(Items.APPLE);
	}
}
