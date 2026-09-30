package com.stella.rpg;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class RpgRespecGameTest {
	@GameTest
	public void respecCharges320(final GameTestHelper helper) {
		ServerPlayer player = allocated(helper);
		for (int slot = 0; slot < 5; slot++) {
			player.getInventory().setItem(slot, new ItemStack(Items.EMERALD, 64));
		}
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.EMERALD, 64));
		player.getInventory().setItem(8, new ItemStack(Items.EMERALD, 16));
		helper.assertTrue(StatPoints.countEmeralds(player) == 400, "setup emeralds=" + StatPoints.countEmeralds(player));
		StatPoints.Result result = StatPoints.respec(player);
		helper.assertTrue(result == StatPoints.Result.OK, "result=" + result);
		helper.assertTrue(StatPoints.countEmeralds(player) == 80, "emeralds=" + StatPoints.countEmeralds(player));
		StatSheet sheet = StatPoints.sheet(player);
		for (Stat stat : Stat.values()) {
			helper.assertTrue(sheet.get(stat) == 0, stat + " base=" + sheet.get(stat));
		}
		int unspent = StatPoints.unspent(player.experienceLevel, sheet);
		helper.assertTrue(unspent == StatPoints.total(player.experienceLevel), "unspent=" + unspent);
		RpgTestSupport.result("respecCharges320", "emeralds=80 unspent=" + unspent);
		helper.succeed();
	}

	@GameTest
	public void respecRefusedShort(final GameTestHelper helper) {
		ServerPlayer player = allocated(helper);
		for (int slot = 0; slot < 4; slot++) {
			player.getInventory().setItem(slot, new ItemStack(Items.EMERALD, 64));
		}
		player.getInventory().setItem(4, new ItemStack(Items.EMERALD, 63));
		StatSheet before = StatPoints.sheet(player);
		StatPoints.Result result = StatPoints.respec(player);
		helper.assertTrue(result == StatPoints.Result.NOT_ENOUGH_EMERALDS, "result=" + result);
		helper.assertTrue(StatPoints.sheet(player).equals(before), "sheet changed: " + StatPoints.sheet(player));
		helper.assertTrue(StatPoints.countEmeralds(player) == 319, "emeralds=" + StatPoints.countEmeralds(player));
		RpgTestSupport.result("respecRefusedShort", "result=" + result);
		helper.succeed();
	}

	@GameTest
	public void respecRefusedEmpty(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		player.experienceLevel = 10;
		for (int slot = 0; slot < 7; slot++) {
			player.getInventory().setItem(slot, new ItemStack(Items.EMERALD, 64));
		}
		int held = StatPoints.countEmeralds(player);
		StatPoints.Result result = StatPoints.respec(player);
		helper.assertTrue(result == StatPoints.Result.NOTHING_TO_RESET, "result=" + result);
		helper.assertTrue(StatPoints.countEmeralds(player) == held, "emeralds " + held + " became " + StatPoints.countEmeralds(player));
		RpgTestSupport.result("respecRefusedEmpty", "result=" + result);
		helper.succeed();
	}

	private static ServerPlayer allocated(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		player.experienceLevel = 10;
		helper.assertTrue(StatPoints.allocate(player, Stat.STR, 6) == StatPoints.Result.OK, "allocation refused");
		helper.assertTrue(StatPoints.allocate(player, Stat.INT, 4) == StatPoints.Result.OK, "allocation refused");
		return player;
	}
}
