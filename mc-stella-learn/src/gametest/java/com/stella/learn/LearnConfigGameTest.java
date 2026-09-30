package com.stella.learn;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** A config file is edited by hand, so an exact entry wins and a bad entry logs and is skipped. */
public class LearnConfigGameTest {
	private static final double FAR = LearnConfig.DEFAULT_CALLOUT_RADIUS * 2.0;
	private static final int FEW = LearnConfig.DEFAULT_MAX_RESPONDERS + 3;

	@GameTest
	public void anExactEntryReplacesItsDefault(final GameTestHelper helper) {
		LearnConfig parsed = LearnConfig.parse("{\"callout_radius\": " + FAR + "}");
		StellaLearn.LOGGER.info("[BEHAVIOR] test=anExactEntryReplacesItsDefault RESULT calloutRadius={}", parsed.calloutRadius());
		helper.assertTrue(parsed.calloutRadius() == FAR, "callout_radius was " + parsed.calloutRadius());
		helper.succeed();
	}

	@GameTest
	public void anUnlistedEntryKeepsItsDefault(final GameTestHelper helper) {
		LearnConfig parsed = LearnConfig.parse("{\"callout_radius\": " + FAR + "}");
		StellaLearn.LOGGER.info("[BEHAVIOR] test=anUnlistedEntryKeepsItsDefault RESULT maxResponders={}", parsed.maxResponders());
		helper.assertTrue(parsed.maxResponders() == LearnConfig.DEFAULT_MAX_RESPONDERS, "max_responders was " + parsed.maxResponders());
		helper.succeed();
	}

	@GameTest
	public void aBadEntryIsSkippedAndTheRestApply(final GameTestHelper helper) {
		LearnConfig parsed = LearnConfig.parse("{\"callout_radius\": \"far\", \"max_responders\": " + FEW + "}");
		StellaLearn.LOGGER.info(
			"[BEHAVIOR] test=aBadEntryIsSkippedAndTheRestApply RESULT calloutRadius={} maxResponders={}",
			parsed.calloutRadius(), parsed.maxResponders()
		);
		helper.assertTrue(parsed.calloutRadius() == LearnConfig.DEFAULT_CALLOUT_RADIUS, "bad radius gave " + parsed.calloutRadius());
		helper.assertTrue(parsed.maxResponders() == FEW, "max_responders was " + parsed.maxResponders());
		helper.succeed();
	}

	@GameTest
	public void aNegativeEntryIsSkipped(final GameTestHelper helper) {
		LearnConfig parsed = LearnConfig.parse("{\"max_responders\": -1}");
		StellaLearn.LOGGER.info("[BEHAVIOR] test=aNegativeEntryIsSkipped RESULT maxResponders={}", parsed.maxResponders());
		helper.assertTrue(parsed.maxResponders() == LearnConfig.DEFAULT_MAX_RESPONDERS, "negative responders gave " + parsed.maxResponders());
		helper.succeed();
	}

	@GameTest
	public void aBrokenFileGivesTheDefaults(final GameTestHelper helper) {
		LearnConfig parsed = LearnConfig.parse("{ not json");
		StellaLearn.LOGGER.info("[BEHAVIOR] test=aBrokenFileGivesTheDefaults RESULT isDefault={}", parsed.equals(LearnConfig.defaults()));
		helper.assertTrue(parsed.equals(LearnConfig.defaults()), "a broken file gave " + parsed);
		helper.succeed();
	}
}
