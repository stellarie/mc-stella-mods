package com.stella.equips;

import com.stella.equips.warning.LowHealthWarning;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public class LowHealthWarningGameTest {
	private static final float MAX = 20.0F;

	@GameTest
	public void theWarningIsOnBelowThirtyPercent(final GameTestHelper helper) {
		float edge = MAX * LowHealthWarning.THRESHOLD;
		boolean at = LowHealthWarning.active(edge, MAX);
		boolean below = LowHealthWarning.active(Math.nextDown(edge), MAX);
		boolean above = LowHealthWarning.active(edge + 1.0F, MAX);
		StellaEquips.LOGGER.info("[BEHAVIOR] test=lowHealthThreshold RESULT edge={} atEdge={} below={} above={}", edge, at, below, above);
		helper.assertTrue(!at, "exactly 30 percent warned");
		helper.assertTrue(below, "just under 30 percent did not warn");
		helper.assertTrue(!above, "above 30 percent warned");
		helper.succeed();
	}

	@GameTest
	public void theThresholdScalesWithMaxHealth(final GameTestHelper helper) {
		helper.assertTrue(LowHealthWarning.active(50.0F, 200.0F), "50 of 200 did not warn");
		helper.assertTrue(!LowHealthWarning.active(70.0F, 200.0F), "70 of 200 warned");
		helper.succeed();
	}

	@GameTest
	public void deadOrBrokenValuesDoNotWarn(final GameTestHelper helper) {
		helper.assertTrue(!LowHealthWarning.active(0.0F, MAX), "a dead player warned");
		helper.assertTrue(!LowHealthWarning.active(-1.0F, MAX), "negative health warned");
		helper.assertTrue(!LowHealthWarning.active(5.0F, 0.0F), "a zero max health warned");
		helper.assertTrue(!LowHealthWarning.active(Float.NaN, MAX), "NaN health warned");
		helper.succeed();
	}

	@GameTest
	public void theHeartbeatFallsOnEveryTwentiethTick(final GameTestHelper helper) {
		StringBuilder beats = new StringBuilder();

		for (int tick = 0; tick < 3 * LowHealthWarning.HEARTBEAT_TICKS; tick++) {
			if (LowHealthWarning.beatDue(tick)) {
				beats.append(tick).append(' ');
			}
		}

		StellaEquips.LOGGER.info("[BEHAVIOR] test=lowHealthHeartbeat RESULT beats={}", beats.toString().trim());
		helper.assertTrue(LowHealthWarning.HEARTBEAT_TICKS == 20, "the interval read " + LowHealthWarning.HEARTBEAT_TICKS);
		helper.assertTrue(beats.toString().equals("0 20 40 "), "the beats fell on " + beats);
		helper.succeed();
	}

	@GameTest
	public void theVignetteIsOffWhenHealthyAndDeeperWhenLower(final GameTestHelper helper) {
		float healthy = LowHealthWarning.vignette(MAX, MAX, 0);
		float shallow = LowHealthWarning.vignette(5.0F, MAX, 0);
		float deep = LowHealthWarning.vignette(1.0F, MAX, 0);
		StellaEquips.LOGGER.info("[BEHAVIOR] test=lowHealthVignette RESULT healthy={} shallow={} deep={}", healthy, shallow, deep);
		helper.assertTrue(healthy == 0.0F, "a healthy player drew a vignette: " + healthy);
		helper.assertTrue(shallow > 0.0F && shallow <= 1.0F, "the shallow vignette read " + shallow);
		helper.assertTrue(deep > shallow && deep <= 1.0F, "the deep vignette read " + deep + " against " + shallow);
		helper.succeed();
	}

	@GameTest
	public void theVignettePulsesWithTheHeartbeat(final GameTestHelper helper) {
		float onBeat = LowHealthWarning.vignette(3.0F, MAX, 0);
		float offBeat = LowHealthWarning.vignette(3.0F, MAX, LowHealthWarning.HEARTBEAT_TICKS / 2);
		float nextBeat = LowHealthWarning.vignette(3.0F, MAX, LowHealthWarning.HEARTBEAT_TICKS);
		helper.assertTrue(onBeat > offBeat, "the beat tick read " + onBeat + " against " + offBeat);
		helper.assertTrue(offBeat > 0.0F, "the off beat vanished");
		helper.assertTrue(onBeat == nextBeat, "the pulse did not repeat each interval");
		helper.succeed();
	}
}
