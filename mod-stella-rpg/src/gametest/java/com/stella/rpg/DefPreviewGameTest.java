package com.stella.rpg;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** The Def line combines armor and VIT: 1 - (1 - armorShare) x (1 - vitShare). */
public final class DefPreviewGameTest {
	private static final double EPSILON = 1e-9;

	@GameTest
	public void combined(final GameTestHelper helper) {
		double vit = 1.0 - RpgCurve.damageTakenMultiplier(100);
		check(helper, "none", DefPreview.combined(0.0, 0.0), 0.0);
		check(helper, "vit only", DefPreview.combined(0.0, vit), vit);
		check(helper, "armor 10", DefPreview.combined(10.0, 0.0), 0.4);
		check(helper, "armor 10 and vit", DefPreview.combined(10.0, vit), 1.0 - 0.6 * (1.0 - vit));
		check(helper, "armor 20", DefPreview.combined(20.0, 0.0), 0.8);
		check(helper, "armor 30 caps at 20", DefPreview.combined(30.0, 0.0), 0.8);
		check(helper, "negative armor", DefPreview.combined(-4.0, 0.0), 0.0);
		check(helper, "armor 20 and vit 15%", DefPreview.combined(20.0, 0.15), 1.0 - 0.2 * 0.85);
		RpgTestSupport.result("combined", "armor10+vit100=" + DefPreview.combined(10.0, vit));
		helper.succeed();
	}

	private static void check(final GameTestHelper helper, final String name, final double actual, final double expected) {
		helper.assertTrue(Math.abs(actual - expected) < EPSILON, name + " was " + actual + " expected " + expected);
	}
}
