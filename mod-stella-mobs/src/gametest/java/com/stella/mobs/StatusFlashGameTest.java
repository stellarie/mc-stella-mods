package com.stella.mobs;

import com.stella.mobs.behavior.Chill;
import com.stella.mobs.flash.StatusFlash;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * The orange and frost screen edge flashes. The client draws a ring from {@link StatusFlash}, and the vanilla fire
 * and frost overlays are cancelled by two client mixins that a headless server cannot run. These cases prove the
 * strength the player would see for the real state that vanilla syncs: the fire flag and the frozen ticks.
 *
 * <p>Manual check: stand in fire and in powder snow. The vanilla fire and frost overlays stay hidden, and the edge
 * ring shows orange pulsing and bluish-white. A client renders both mixins.
 */
public class StatusFlashGameTest {
	private static final String TEST = "statusFlash";
	private static final int FIRE_TICKS = 200;
	private static final int PULSE_SAMPLES = 64;
	private static final int FREEZE_TICKS = 100;
	private static final int RING_STEPS = 20;
	private static final int ALPHA_SHIFT = 24;
	private static final int RGB_MASK = 0xFFFFFF;

	@GameTest
	public void burningPlayerFlashesOrangeWithinThePulseBand(final GameTestHelper helper) {
		ServerPlayer player = Arena.addSurvivalPlayer(helper);
		player.setRemainingFireTicks(FIRE_TICKS);
		float low = Float.MAX_VALUE;
		float high = 0.0F;

		for (int sample = 0; sample < PULSE_SAMPLES; sample++) {
			float alpha = StatusFlash.burnAlpha(player, sample * StatusFlash.PULSE_TICKS / (float) PULSE_SAMPLES);
			low = Math.min(low, alpha);
			high = Math.max(high, alpha);
		}

		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT burning={} burnLow={} burnHigh={}", TEST, player.isOnFire(), low, high);
		helper.assertTrue(player.isOnFire(), "the player did not burn");
		helper.assertTrue(low >= StatusFlash.BURN_MIN_ALPHA - 0.001F, "the flash fell below the band: " + low);
		helper.assertTrue(high <= StatusFlash.BURN_MAX_ALPHA + 0.001F, "the flash rose above the band: " + high);
		helper.assertTrue(high - low > 0.1F, "the flash does not pulse: " + low + " to " + high);
		helper.succeed();
	}

	@GameTest
	public void playerThatIsNotBurningHasNoOrangeFlash(final GameTestHelper helper) {
		ServerPlayer player = Arena.addSurvivalPlayer(helper);
		player.setRemainingFireTicks(FIRE_TICKS);
		player.clearFire();
		float alpha = StatusFlash.burnAlpha(player, 3.0F);
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT burning={} alpha={}", TEST, player.isOnFire(), alpha);
		helper.assertTrue(!player.isOnFire(), "the fire did not go out");
		helper.assertTrue(alpha == 0.0F, "an unburnt player flashed " + alpha);
		helper.succeed();
	}

	@GameTest
	public void chillFlashGrowsWithHowFrozenThePlayerIs(final GameTestHelper helper) {
		ServerPlayer player = Arena.addSurvivalPlayer(helper);
		int required = player.getTicksRequiredToFreeze();
		player.setTicksFrozen(0);
		float none = StatusFlash.chillAlpha(player);
		player.setTicksFrozen(required / 4);
		float quarter = StatusFlash.chillAlpha(player);
		player.setTicksFrozen(required);
		float full = StatusFlash.chillAlpha(player);
		player.setTicksFrozen(required * 3);
		float over = StatusFlash.chillAlpha(player);
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT chill none={} quarter={} full={} over={}", TEST, none, quarter, full, over);
		helper.assertTrue(none == 0.0F, "an unfrozen player flashed " + none);
		helper.assertTrue(quarter > 0.0F && quarter < full, "a quarter frozen player flashed " + quarter + " against full " + full);
		helper.assertTrue(Math.abs(full - StatusFlash.CHILL_MAX_ALPHA) < 0.001F, "a frozen player did not reach the maximum: " + full);
		helper.assertTrue(over == full, "freezing past full raised the flash to " + over);
		helper.succeed();
	}

	@GameTest
	public void chillApplyAloneRaisesTheFrostFlash(final GameTestHelper helper) {
		ServerPlayer player = Arena.addSurvivalPlayer(helper);
		player.setTicksFrozen(0);
		Chill.apply(player, null, FREEZE_TICKS, FREEZE_TICKS, 0);
		float alpha = StatusFlash.chillAlpha(player);
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT chillApply frozen={} alpha={}", TEST, player.getTicksFrozen(), alpha);
		helper.assertTrue(alpha > 0.0F, "a chilled player did not flash, frozen ticks " + player.getTicksFrozen());
		helper.succeed();
	}

	@GameTest
	public void ringFadesFromTheEdgeAndKeepsItsColour(final GameTestHelper helper) {
		float previous = Float.MAX_VALUE;

		for (int step = 0; step < RING_STEPS; step++) {
			float alpha = StatusFlash.ringAlpha(StatusFlash.BURN_MAX_ALPHA, step, RING_STEPS);
			helper.assertTrue(alpha < previous, "step " + step + " did not fade: " + alpha + " after " + previous);
			previous = alpha;
		}

		int edge = StatusFlash.ringColor(StatusFlash.ORANGE, StatusFlash.BURN_MAX_ALPHA, 0, RING_STEPS);
		int inner = StatusFlash.ringColor(StatusFlash.ORANGE, StatusFlash.BURN_MAX_ALPHA, RING_STEPS - 1, RING_STEPS);
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT ring edge={} inner={}", TEST, Integer.toHexString(edge), Integer.toHexString(inner));
		helper.assertTrue((edge & RGB_MASK) == StatusFlash.ORANGE, "the edge colour is not orange: " + Integer.toHexString(edge));
		helper.assertTrue((edge >>> ALPHA_SHIFT) > (inner >>> ALPHA_SHIFT), "the edge is not brighter than the inner step");
		helper.assertTrue((edge >>> ALPHA_SHIFT) > 0, "the edge alpha is zero");
		helper.succeed();
	}
}
