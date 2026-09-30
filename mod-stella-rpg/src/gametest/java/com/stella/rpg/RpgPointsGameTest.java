package com.stella.rpg;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

public final class RpgPointsGameTest {
	@GameTest
	public void freshPlayerHasTwelvePoints(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		StatSheet sheet = StatPoints.sheet(player);
		int unspent = StatPoints.unspent(player.experienceLevel, sheet);
		helper.assertTrue(player.experienceLevel == 0, "level=" + player.experienceLevel);
		helper.assertTrue(unspent == 12, "unspent=" + unspent);
		for (Stat stat : Stat.values()) {
			helper.assertTrue(sheet.get(stat) == 0, stat + " base=" + sheet.get(stat));
		}
		RpgTestSupport.result("freshPlayerHasTwelvePoints", "unspent=" + unspent);
		helper.succeed();
	}

	@GameTest
	public void allocationSpendsPoints(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		player.experienceLevel = 5;
		StatPoints.Result result = StatPoints.allocate(player, Stat.STR, 10);
		StatSheet sheet = StatPoints.sheet(player);
		int unspent = StatPoints.unspent(player.experienceLevel, sheet);
		helper.assertTrue(result == StatPoints.Result.OK, "result=" + result);
		helper.assertTrue(sheet.get(Stat.STR) == 10, "STR=" + sheet.get(Stat.STR));
		helper.assertTrue(unspent == 7, "unspent=" + unspent);
		RpgTestSupport.result("allocationSpendsPoints", "STR=10 unspent=" + unspent);
		helper.succeed();
	}

	@GameTest
	public void overspendIsRefused(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		StatSheet before = StatPoints.sheet(player);
		StatPoints.Result result = StatPoints.allocate(player, Stat.STR, StatPoints.total(0) + 1);
		helper.assertTrue(result == StatPoints.Result.NOT_ENOUGH_POINTS, "result=" + result);
		helper.assertTrue(StatPoints.sheet(player).equals(before), "sheet changed: " + StatPoints.sheet(player));
		RpgTestSupport.result("overspendIsRefused", "result=" + result);
		helper.succeed();
	}

	@GameTest
	public void ceilingIsRefused(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		player.experienceLevel = 2000;
		StatPoints.Result above = StatPoints.allocate(player, Stat.STR, StatSheet.CEILING + 1);
		helper.assertTrue(above == StatPoints.Result.ABOVE_CEILING, "ceiling+1 result=" + above);
		helper.assertTrue(StatPoints.sheet(player).get(Stat.STR) == 0, "STR changed by a refusal");
		helper.assertTrue(StatPoints.allocate(player, Stat.STR, StatSheet.CEILING) == StatPoints.Result.OK, "ceiling refused");
		StatSheet full = StatPoints.sheet(player);
		StatPoints.Result over = StatPoints.allocate(player, Stat.STR, 1);
		helper.assertTrue(over == StatPoints.Result.ABOVE_CEILING, "one past ceiling result=" + over);
		helper.assertTrue(StatPoints.sheet(player).equals(full), "sheet changed: " + StatPoints.sheet(player));
		helper.assertTrue(
			StatPoints.allocate(player, Stat.STR, Integer.MAX_VALUE) == StatPoints.Result.NOT_ENOUGH_POINTS,
			"huge amount was not refused"
		);
		RpgTestSupport.result("ceilingIsRefused", "result=" + over);
		helper.succeed();
	}
}
