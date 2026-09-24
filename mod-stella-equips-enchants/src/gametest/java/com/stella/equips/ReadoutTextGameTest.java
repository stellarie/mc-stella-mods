package com.stella.equips;

import com.stella.equips.power.PowerCurve;
import com.stella.equips.readout.ReadoutText;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

public class ReadoutTextGameTest {
	private static final int MOB_LEVEL = 12;
	private static final String MOB_NAME = "Zombie";
	private static final String EXPECTED_TEXT = "[" + MOB_LEVEL + "] " + MOB_NAME;
	private static final Map<PowerCurve.Band, ChatFormatting> EXPECTED_COLORS = new LinkedHashMap<>();

	static {
		EXPECTED_COLORS.put(PowerCurve.Band.RED, ChatFormatting.RED);
		EXPECTED_COLORS.put(PowerCurve.Band.YELLOW, ChatFormatting.YELLOW);
		EXPECTED_COLORS.put(PowerCurve.Band.ORANGE, ChatFormatting.GOLD);
		EXPECTED_COLORS.put(PowerCurve.Band.GREEN, ChatFormatting.GREEN);
		EXPECTED_COLORS.put(PowerCurve.Band.WHITE, ChatFormatting.WHITE);
	}

	@GameTest
	public void theTextIsTheLevelPrefixThenTheMobOwnDisplayName(final GameTestHelper helper) {
		Component name = Component.literal(MOB_NAME);
		Component text = ReadoutText.build(MOB_LEVEL, name, PowerCurve.Band.GREEN);
		String rendered = text.getString();
		helper.assertTrue(
			EXPECTED_TEXT.equals(rendered),
			"a level " + MOB_LEVEL + " mob named " + MOB_NAME + " rendered as '" + rendered + "' instead of '"
				+ EXPECTED_TEXT + "'"
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=readoutText RESULT mobLevel={} mobName={} text={} color={}",
			MOB_LEVEL, MOB_NAME, rendered, ReadoutText.colorName(PowerCurve.Band.GREEN)
		);
		helper.succeed();
	}

	@GameTest
	public void theHealthTextIsTheLevelPrefixTheNameAndTheHealth(final GameTestHelper helper) {
		float health = 13.5F;
		float maxHealth = 20.0F;
		String expected = "[" + MOB_LEVEL + "] " + MOB_NAME + " 14/20";
		Component text = ReadoutText.build(
			MOB_LEVEL, Component.literal(MOB_NAME), PowerCurve.Band.GREEN, health, maxHealth
		);
		String rendered = text.getString();
		helper.assertTrue(
			expected.equals(rendered),
			"a mob at " + health + " of " + maxHealth + " rendered as '" + rendered + "' instead of '" + expected + "'"
		);
		helper.assertTrue(
			ReadoutText.shown(0.5F) == 1,
			"a mob at half a health point rendered as " + ReadoutText.shown(0.5F)
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=readoutTextHealth RESULT mobLevel={} mobName={} health={} maxHealth={} text={} "
				+ "halfShown={}",
			MOB_LEVEL, MOB_NAME, health, maxHealth, rendered, ReadoutText.shown(0.5F)
		);
		helper.succeed();
	}

	@GameTest
	public void everyBandCarriesItsOwnColorOverTheWholeLine(final GameTestHelper helper) {
		Map<PowerCurve.Band, Integer> measured = new LinkedHashMap<>();
		String broken = "none";

		for (Map.Entry<PowerCurve.Band, ChatFormatting> entry : EXPECTED_COLORS.entrySet()) {
			Component text = ReadoutText.build(MOB_LEVEL, Component.literal(MOB_NAME), entry.getKey());
			int expected = TextColor.fromLegacyFormat(entry.getValue()).getValue();
			int covered = 0;
			int colored = 0;
			List<Component> parts = text.toFlatList(Style.EMPTY);

			for (Component part : parts) {
				covered++;
				if (part.getStyle().getColor() != null && part.getStyle().getColor().getValue() == expected) {
					colored++;
				}
			}

			measured.put(entry.getKey(), expected);
			if (colored != covered || covered == 0) {
				broken = entry.getKey() + " colored " + colored + " of " + covered + " parts";
			}
		}

		helper.assertTrue("none".equals(broken), "a band did not color the whole line: " + broken);
		helper.assertTrue(
			measured.values().stream().distinct().count() == EXPECTED_COLORS.size(),
			"the bands do not carry " + EXPECTED_COLORS.size() + " distinct colors: " + measured
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=readoutColors RESULT red={} yellow={} orange={} green={} white={} distinct={}",
			hex(measured, PowerCurve.Band.RED),
			hex(measured, PowerCurve.Band.YELLOW),
			hex(measured, PowerCurve.Band.ORANGE),
			hex(measured, PowerCurve.Band.GREEN),
			hex(measured, PowerCurve.Band.WHITE),
			measured.values().stream().distinct().count()
		);
		helper.succeed();
	}

	private static String hex(final Map<PowerCurve.Band, Integer> colors, final PowerCurve.Band band) {
		Integer value = colors.get(band);
		return value == null ? "none" : String.format("#%06X", value);
	}
}
