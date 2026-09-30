package com.stella.rpg;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class HudLayoutGameTest {
	private static final int WIDTH = HudLayout.BAR_WIDTH;

	@GameTest
	public void fill(final GameTestHelper helper) {
		expect(helper, "fill(15,20)", HudLayout.fillWidth(15, 20, WIDTH), 60);
		expect(helper, "fill(0,20)", HudLayout.fillWidth(0, 20, WIDTH), 0);
		expect(helper, "fill(30,20)", HudLayout.fillWidth(30, 20, WIDTH), 81);
		RpgTestSupport.result("fill", "rows=3");
		helper.succeed();
	}

	@GameTest
	public void text(final GameTestHelper helper) {
		String text = HudLayout.healthText(14.4f, 40f);
		helper.assertTrue("15 / 40".equals(text), "healthText(14.4,40)=" + text);
		String tiny = HudLayout.healthText(0.4f, 20f);
		helper.assertTrue("1 / 20".equals(tiny), "healthText(0.4,20)=" + tiny);
		RpgTestSupport.result("text", "value=" + text);
		helper.succeed();
	}

	@GameTest
	public void food(final GameTestHelper helper) {
		helper.assertTrue("17 / 20".equals(HudLayout.foodText(17)), "foodText(17)=" + HudLayout.foodText(17));
		expect(helper, "fill(10,20)", HudLayout.fillWidth(10, HudLayout.MAX_FOOD, WIDTH), 40);
		RpgTestSupport.result("food", "value=" + HudLayout.foodText(17));
		helper.succeed();
	}

	@GameTest
	public void absorption(final GameTestHelper helper) {
		expect(helper, "absorption(8,20)", HudLayout.absorptionWidth(8, 20, WIDTH), 32);
		expect(helper, "absorption(50,20)", HudLayout.absorptionWidth(50, 20, WIDTH), 81);
		RpgTestSupport.result("absorption", "rows=2");
		helper.succeed();
	}

	@GameTest
	public void iconRows(final GameTestHelper helper) {
		int perRow = HudLayout.ICONS_PER_ROW;
		expect(helper, "rows(12)", HudLayout.iconRows(12, perRow), 2);
		expect(helper, "row0(12)", HudLayout.iconsInRow(12, perRow, 0), 9);
		expect(helper, "row1(12)", HudLayout.iconsInRow(12, perRow, 1), 3);
		expect(helper, "rows(0)", HudLayout.iconRows(0, perRow), 0);
		RpgTestSupport.result("iconRows", "first=9 second=3");
		helper.succeed();
	}

	private static void expect(final GameTestHelper helper, final String name, final int actual, final int expected) {
		helper.assertTrue(actual == expected, name + "=" + actual + " expected " + expected);
	}
}
