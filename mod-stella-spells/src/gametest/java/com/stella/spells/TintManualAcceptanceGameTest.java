package com.stella.spells;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public class TintManualAcceptanceGameTest {
	@GameTest(required = false)
	public void customBookIconsAndCircleAnimationRequireManualReview(final GameTestHelper helper) {
		SpellTestLog.track(helper, "customBookIconsAndCircleAnimationRequireManualReview");
		helper.fail("Manual client review required: inspect all 42 book icons, an area circle, its animation, and scroll tint.");
	}

	@GameTest(required = false)
	public void clientCraftAndAnvilLogRequiresManualReview(final GameTestHelper helper) {
		SpellTestLog.track(helper, "clientCraftAndAnvilLogRequiresManualReview");
		helper.fail("Manual client review required: verify client crafting and anvil actions emit no [SPELL] logs.");
	}
}
