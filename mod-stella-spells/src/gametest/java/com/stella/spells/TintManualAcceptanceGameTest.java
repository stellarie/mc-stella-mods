package com.stella.spells;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public class TintManualAcceptanceGameTest {
	@GameTest(required = false)
	public void tintVisualRequiresManualClientReview(final GameTestHelper helper) {
		SpellTestLog.track(helper, "tintVisualRequiresManualClientReview");
		helper.fail("Manual client review required: inspect the Spark Shot icon tint and right-click cast.");
	}

	@GameTest(required = false)
	public void clientCraftAndAnvilLogRequiresManualReview(final GameTestHelper helper) {
		SpellTestLog.track(helper, "clientCraftAndAnvilLogRequiresManualReview");
		helper.fail("Manual client review required: verify client crafting and anvil actions emit no [SPELL] logs.");
	}
}
