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
	public void everyBandColorsThePrefixAndTheHealth(final GameTestHelper helper) {
		Map<PowerCurve.Band, Integer> measured = new LinkedHashMap<>();
		String broken = "none";

		for (Map.Entry<PowerCurve.Band, ChatFormatting> entry : EXPECTED_COLORS.entrySet()) {
			Component text = ReadoutText.build(
				THREAT, ReadoutRank.ELITE, Component.literal(MOB_NAME), entry.getKey(), 5.0F, 10.0F, false
			);
			int expected = TextColor.fromLegacyFormat(entry.getValue()).getValue();
			List<Component> parts = coloredParts(text);
			boolean prefix = colorOf(parts.get(0)) == expected;
			boolean health = colorOf(parts.get(parts.size() - 1)) == expected;
			measured.put(entry.getKey(), expected);
			if (!prefix || !health) {
				broken = entry.getKey() + " prefix=" + prefix + " health=" + health;
			}
		}

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=readoutColors RESULT red={} yellow={} orange={} green={} white={} broken={}",
			hex(measured, PowerCurve.Band.RED),
			hex(measured, PowerCurve.Band.YELLOW),
			hex(measured, PowerCurve.Band.ORANGE),
			hex(measured, PowerCurve.Band.GREEN),
			hex(measured, PowerCurve.Band.WHITE),
			broken
		);
		helper.assertTrue("none".equals(broken), "a band did not color its prefix and health: " + broken);
		helper.succeed();
	}

	@GameTest
	public void onlyTheNameFollowsHostility(final GameTestHelper helper) {
		int band = TextColor.fromLegacyFormat(ChatFormatting.YELLOW).getValue();
		int red = TextColor.fromLegacyFormat(ChatFormatting.RED).getValue();
		int green = TextColor.fromLegacyFormat(ChatFormatting.GREEN).getValue();
		List<Component> hostile = coloredParts(ReadoutText.build(
			THREAT, ReadoutRank.NORMAL, Component.literal(MOB_NAME), PowerCurve.Band.YELLOW, 5.0F, 10.0F, true
		));
		List<Component> passive = coloredParts(ReadoutText.build(
			THREAT, ReadoutRank.NORMAL, Component.literal(MOB_NAME), PowerCurve.Band.YELLOW, 5.0F, 10.0F, false
		));
		String hostileColors = colors(hostile);
		String passiveColors = colors(passive);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=readoutNameHostility RESULT hostile={} passive={}", hostileColors, passiveColors
		);
		helper.assertTrue(
			colorOf(hostile.get(1)) == red && colorOf(passive.get(1)) == green
				&& colorOf(hostile.get(0)) == band && colorOf(passive.get(0)) == band
				&& colorOf(hostile.get(2)) == band && colorOf(passive.get(2)) == band,
			"hostile parts read " + hostileColors + " and passive parts read " + passiveColors
		);
		helper.succeed();
	}

	@GameTest
	public void coloredCustomNameStillTakesHostilityColor(final GameTestHelper helper) {
		Component blueName = Component.literal("Bessie").withStyle(ChatFormatting.BLUE);
		List<Component> hostile = coloredParts(ReadoutText.build(
			THREAT, ReadoutRank.NORMAL, blueName, PowerCurve.Band.GREEN, 5.0F, 10.0F, true
		));
		int red = TextColor.fromLegacyFormat(ChatFormatting.RED).getValue();
		StellaEquips.LOGGER.info("[BEHAVIOR] test=readoutCustomNameColor RESULT parts={}", colors(hostile));
		helper.assertTrue(
			hostile.size() == 3 && colorOf(hostile.get(1)) == red && "Bessie".equals(hostile.get(1).getString()),
			"a blue custom name on a hostile mob read " + colors(hostile)
		);
		helper.succeed();
	}

	private static List<Component> coloredParts(final Component text) {
		return text.toFlatList(Style.EMPTY).stream().filter(part -> !part.getString().isEmpty()).toList();
	}

	private static int colorOf(final Component part) {
		return part.getStyle().getColor() == null ? -1 : part.getStyle().getColor().getValue();
	}

	private static String colors(final List<Component> parts) {
		StringBuilder found = new StringBuilder();
		for (Component part : parts) {
			found.append('[').append(part.getString()).append('=').append(String.format("#%06X", colorOf(part) & 0xFFFFFF)).append(']');
		}

		return found.toString();
	}

	private static String hex(final Map<PowerCurve.Band, Integer> colors, final PowerCurve.Band band) {
		Integer value = colors.get(band);
		return value == null ? "none" : String.format("#%06X", value);
	}
}
