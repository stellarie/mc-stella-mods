package com.stella.rpg;

import java.util.function.BiFunction;
import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ObjectShare;
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

	/** With the equips purse shares present, the respec counts and spends through them, bags included. */
	@GameTest
	public void respecUsesPurse(final GameTestHelper helper) {
		ServerPlayer player = allocated(helper);
		int[] bag = {400};
		Function<Object, Integer> count = who -> who == player ? bag[0] : StatPoints.countLoose((ServerPlayer) who);
		BiFunction<Object, Integer, Boolean> spend = (who, price) -> {
			if (who != player || bag[0] < price) {
				return false;
			}
			bag[0] -= price;
			return true;
		};
		ObjectShare share = FabricLoader.getInstance().getObjectShare();
		share.put(StatPoints.COUNT_SHARE, count);
		share.put(StatPoints.SPEND_SHARE, spend);
		StatPoints.Result result;
		try {
			result = StatPoints.respec(player);
		} finally {
			share.remove(StatPoints.COUNT_SHARE);
			share.remove(StatPoints.SPEND_SHARE);
		}
		RpgTestSupport.result("respecUsesPurse", "result=" + result + " bagLeft=" + bag[0]);
		helper.assertTrue(result == StatPoints.Result.OK && bag[0] == 80, "the respec did not pay through the purse: " + result + " " + bag[0]);
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
