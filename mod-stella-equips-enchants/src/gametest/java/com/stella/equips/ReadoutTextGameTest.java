package com.stella.equips;

import com.stella.equips.power.PowerCurve;
import com.stella.equips.readout.ReadoutRank;
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
	private static final int THREAT = 12;
	private static final String MOB_NAME = "Zombie";
	private static final Map<PowerCurve.Band, ChatFormatting> EXPECTED_COLORS = new LinkedHashMap<>();

	static {
		EXPECTED_COLORS.put(PowerCurve.Band.RED, ChatFormatting.RED);
		EXPECTED_COLORS.put(PowerCurve.Band.YELLOW, ChatFormatting.YELLOW);
		EXPECTED_COLORS.put(PowerCurve.Band.ORANGE, ChatFormatting.GOLD);
		EXPECTED_COLORS.put(PowerCurve.Band.GREEN, ChatFormatting.GREEN);
		EXPECTED_COLORS.put(PowerCurve.Band.WHITE, ChatFormatting.WHITE);
	}

	@GameTest
	public void theTextIsTheRawThreatThenTheMobOwnDisplayName(final GameTestHelper helper) {
		String low = ReadoutText.build(THREAT, ReadoutRank.NORMAL, Component.literal(MOB_NAME), PowerCurve.Band.GREEN).getString();
		String high = ReadoutText.build(THREAT + 1, ReadoutRank.NORMAL, Component.literal(MOB_NAME), PowerCurve.Band.GREEN).getString();
		String renamed = ReadoutText.build(THREAT, ReadoutRank.NORMAL, Component.literal("Husk"), PowerCurve.Band.GREEN).getString();
		StellaEquips.LOGGER.info("[BEHAVIOR] test=readoutText RESULT low={} high={} renamed={}", low, high, renamed);
		helper.assertTrue(
			low.startsWith("Threat " + THREAT + " ") && high.startsWith("Threat " + (THREAT + 1) + " ")
				&& low.endsWith(MOB_NAME) && renamed.endsWith("Husk"),
			"the threat and name did not follow their inputs: " + low + " / " + high + " / " + renamed
		);
		helper.succeed();
	}

	@GameTest
	public void theHealthTextRoundsUpAndFollowsTheMobHealth(final GameTestHelper helper) {
		String hurt = ReadoutText.build(
			THREAT, ReadoutRank.NORMAL, Component.literal(MOB_NAME), PowerCurve.Band.GREEN, 13.5F, 20.0F
		).getString();
		String whole = ReadoutText.build(
			THREAT, ReadoutRank.NORMAL, Component.literal(MOB_NAME), PowerCurve.Band.GREEN, 20.0F, 20.0F
		).getString();
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=readoutTextHealth RESULT hurt={} whole={} halfShown={}", hurt, whole, ReadoutText.shown(0.5F)
		);
		helper.assertTrue(
			hurt.endsWith(" 14/20") && whole.endsWith(" 20/20") && ReadoutText.shown(0.5F) == 1,
			"the health text read " + hurt + " and " + whole + " and half a point showed " + ReadoutText.shown(0.5F)
		);
		helper.succeed();
	}

	@GameTest
	public void theRankTagFollowsTheRankAndSitsBetweenThreatAndName(final GameTestHelper helper) {
		String boss = ReadoutText.build(
			12, ReadoutRank.BOSS, Component.literal("Wither"), PowerCurve.Band.RED, 200.0F, 200.0F
		).getString();
		String elite = ReadoutText.build(
			7, ReadoutRank.ELITE, Component.literal("Zombie"), PowerCurve.Band.GREEN, 40.0F, 40.0F
		).getString();
		String normal = ReadoutText.build(
			7, ReadoutRank.NORMAL, Component.literal("Zombie"), PowerCurve.Band.GREEN, 40.0F, 40.0F
		).getString();
		StellaEquips.LOGGER.info("[BEHAVIOR] test=readoutRankTag RESULT boss={} elite={} normal={}", boss, elite, normal);
		helper.assertTrue(
			boss.equals("Threat 12 <Boss> Wither 200/200") && elite.equals("Threat 7 <Elite> Zombie 40/40")
				&& normal.equals("Threat 7 Zombie 40/40"),
			"the three ranks read " + boss + " / " + elite + " / " + normal
		);
		helper.succeed();
	}

	@GameTest
	public void everyBandCarriesItsOwnColorOverTheWholeLine(final GameTestHelper helper) {
		Map<PowerCurve.Band, Integer> measured = new LinkedHashMap<>();
		String broken = "none";

		for (Map.Entry<PowerCurve.Band, ChatFormatting> entry : EXPECTED_COLORS.entrySet()) {
			Component text = ReadoutText.build(THREAT, ReadoutRank.ELITE, Component.literal(MOB_NAME), entry.getKey());
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
