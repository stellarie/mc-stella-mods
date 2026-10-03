package com.stella.equips;

import com.stella.equips.readout.DangerColor;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTestHelper;

public class DangerColorGameTest {
	private static final int PLAYER = 30;

	private static DangerColor at(final int gap) {
		return DangerColor.of(PLAYER + gap, PLAYER);
	}

	@GameTest
	public void theGapBoundsPickTheColor(final GameTestHelper helper) {
		DangerColor belowFive = at(-5);
		DangerColor belowFour = at(-4);
		DangerColor even = at(0);
		DangerColor aboveFour = at(4);
		DangerColor aboveFive = at(5);
		DangerColor aboveNine = at(9);
		DangerColor aboveTen = at(10);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=dangerBounds RESULT -5={} -4={} 0={} +4={} +5={} +9={} +10={}",
			belowFive, belowFour, even, aboveFour, aboveFive, aboveNine, aboveTen
		);
		helper.assertTrue(belowFive == DangerColor.GREEN, "5 below read " + belowFive);
		helper.assertTrue(belowFour == DangerColor.WHITE, "4 below read " + belowFour);
		helper.assertTrue(even == DangerColor.WHITE, "an equal level read " + even);
		helper.assertTrue(aboveFour == DangerColor.WHITE, "4 above read " + aboveFour);
		helper.assertTrue(aboveFive == DangerColor.YELLOW, "5 above read " + aboveFive);
		helper.assertTrue(aboveNine == DangerColor.YELLOW, "9 above read " + aboveNine);
		helper.assertTrue(aboveTen == DangerColor.RED, "10 above read " + aboveTen);
		helper.succeed();
	}

	@GameTest
	public void farGapsKeepTheirEndColor(final GameTestHelper helper) {
		helper.assertTrue(DangerColor.of(0, 1000) == DangerColor.GREEN, "a far weaker mob did not read green");
		helper.assertTrue(DangerColor.of(1000, 0) == DangerColor.RED, "a far stronger mob did not read red");
		helper.succeed();
	}

	@GameTest
	public void theColorsAreTheNamedLegacyColors(final GameTestHelper helper) {
		helper.assertTrue(DangerColor.GREEN.format() == ChatFormatting.GREEN, "green mapped to " + DangerColor.GREEN.format());
		helper.assertTrue(DangerColor.WHITE.format() == ChatFormatting.WHITE, "white mapped to " + DangerColor.WHITE.format());
		helper.assertTrue(DangerColor.YELLOW.format() == ChatFormatting.YELLOW, "yellow mapped to " + DangerColor.YELLOW.format());
		helper.assertTrue(DangerColor.RED.format() == ChatFormatting.RED, "red mapped to " + DangerColor.RED.format());
		helper.succeed();
	}
}
