package com.stella.rpg;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class RpgCurveGameTest {
	private static final int[] STATS = {0, 12, 100, 1024};

	@GameTest
	public void curveTable(final GameTestHelper helper) {
		check(helper, "attackSpeed", RpgCurve::attackSpeedBonus, 0.0000, 0.0458, 0.2857, 0.8038);
		check(helper, "damageTaken", RpgCurve::damageTakenMultiplier, 1.0000, 0.9769, 0.8500, 0.5360);
		check(helper, "cooldown", RpgCurve::cooldownMultiplier, 1.0000, 0.9846, 0.9000, 0.6906);
		check(helper, "critChance", RpgCurve::critChance, 0.0000, 0.0192, 0.1250, 0.3867);
		int[] ticks = {20, 20, 17, 12};
		for (int i = 0; i < STATS.length; i++) {
			int actual = RpgCurve.fullDrawTicks(STATS[i]);
			helper.assertTrue(actual == ticks[i], "fullDrawTicks(" + STATS[i] + ")=" + actual);
		}
		RpgTestSupport.result("curveTable", "rows=5");
		helper.succeed();
	}

	private static void check(final GameTestHelper helper, final String name, final Effect effect, final double... expected) {
		for (int i = 0; i < STATS.length; i++) {
			double actual = effect.at(STATS[i]);
			helper.assertTrue(
				Math.abs(Math.round(actual * 10_000.0) / 10_000.0 - expected[i]) < 1e-9,
				name + "(" + STATS[i] + ")=" + actual + " expected " + expected[i]
			);
		}
	}

	@FunctionalInterface
	private interface Effect {
		double at(int stat);
	}
}
