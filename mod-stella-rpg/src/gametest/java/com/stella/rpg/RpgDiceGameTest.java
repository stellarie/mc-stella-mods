package com.stella.rpg;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;

public final class RpgDiceGameTest {
	@GameTest
	public void diceTable(final GameTestHelper helper) {
		int[] stats = {0, 12, 49, 50, 100, 250, 1024};
		String[] dice = {"1d4", "1d4", "2d4", "3d4", "5d5", "11d6", "41d14"};
		int[] mins = {1, 1, 2, 3, 5, 11, 41};
		int[] maxes = {4, 4, 8, 12, 25, 66, 574};
		double[] means = {2.5, 2.5, 5.0, 7.5, 15.0, 38.5, 307.5};
		for (int i = 0; i < stats.length; i++) {
			RpgDice.Dice actual = RpgDice.of(stats[i]);
			String detail = "stat " + stats[i] + " gave " + actual;
			helper.assertTrue(actual.toString().equals(dice[i]), detail + " expected " + dice[i]);
			helper.assertTrue(actual.min() == mins[i], detail + " min=" + actual.min());
			helper.assertTrue(actual.max() == maxes[i], detail + " max=" + actual.max());
			helper.assertTrue(Math.abs(actual.mean() - means[i]) < 1e-9, detail + " mean=" + actual.mean());
		}
		RpgTestSupport.result("diceTable", "rows=" + stats.length);
		helper.succeed();
	}

	@GameTest
	public void rollBounds(final GameTestHelper helper) {
		RpgDice.Dice dice = RpgDice.of(100);
		RandomSource random = RandomSource.create(20260930L);
		int rolls = 10_000;
		long sum = 0;
		for (int i = 0; i < rolls; i++) {
			int value = dice.roll(random);
			helper.assertTrue(value >= 5 && value <= 25, "roll " + value + " is outside [5, 25]");
			sum += value;
		}
		double mean = (double) sum / rolls;
		RpgTestSupport.result("rollBounds", "dice=" + dice + " mean=" + mean);
		helper.assertTrue(Math.abs(mean - 15.0) < 0.1, "mean=" + mean);
		helper.succeed();
	}
}
