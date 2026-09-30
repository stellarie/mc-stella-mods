package com.stella.equips;

import com.stella.equips.power.PowerCurve;
import com.stella.equips.readout.ReadoutRank;
import com.stella.equips.readout.ReadoutText;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.ChatFormatting;
import com.stella.equips.readout.ReadoutNames;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;

public class ReadoutTextGameTest {
	private static final Map<PowerCurve.Band, ChatFormatting> EXPECTED_COLORS = new LinkedHashMap<>();

	static {
		EXPECTED_COLORS.put(PowerCurve.Band.RED, ChatFormatting.RED);
		EXPECTED_COLORS.put(PowerCurve.Band.YELLOW, ChatFormatting.YELLOW);
		EXPECTED_COLORS.put(PowerCurve.Band.ORANGE, ChatFormatting.GOLD);
		EXPECTED_COLORS.put(PowerCurve.Band.GREEN, ChatFormatting.GREEN);
		EXPECTED_COLORS.put(PowerCurve.Band.WHITE, ChatFormatting.WHITE);
	}

	@GameTest
	public void theRankTagFollowsTheRankAndSitsBeforeTheName(final GameTestHelper helper) {
		String boss = ReadoutText.label(ReadoutRank.BOSS, "Wither");
		String elite = ReadoutText.label(ReadoutRank.ELITE, "Zombie");
		String normal = ReadoutText.label(ReadoutRank.NORMAL, "Zombie");
		StellaEquips.LOGGER.info("[BEHAVIOR] test=readoutRankTag RESULT boss={} elite={} normal={}", boss, elite, normal);
		helper.assertTrue(
			boss.equals("<Boss> Wither") && elite.equals("<Elite> Zombie") && normal.equals("Zombie"),
			"the three ranks read " + boss + " / " + elite + " / " + normal
		);
		helper.succeed();
	}

	@GameTest
	public void everyBandHasItsColor(final GameTestHelper helper) {
		StringBuilder broken = new StringBuilder();

		for (Map.Entry<PowerCurve.Band, ChatFormatting> entry : EXPECTED_COLORS.entrySet()) {
			if (ReadoutText.color(entry.getKey()) != entry.getValue()) {
				broken.append(entry.getKey()).append(' ');
			}
		}

		StellaEquips.LOGGER.info("[BEHAVIOR] test=readoutColors RESULT broken={}", broken.length() == 0 ? "none" : broken);
		helper.assertTrue(broken.length() == 0, "a band read the wrong color: " + broken);
		helper.assertTrue(
			EXPECTED_COLORS.size() == PowerCurve.Band.values().length,
			"the color table covers " + EXPECTED_COLORS.size() + " of " + PowerCurve.Band.values().length + " bands"
		);
		helper.succeed();
	}

	@GameTest
	public void onlyTheNameFollowsHostility(final GameTestHelper helper) {
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=readoutNameHostility RESULT hostile={} passive={}",
			ReadoutText.nameColor(true), ReadoutText.nameColor(false)
		);
		helper.assertTrue(
			ReadoutText.nameColor(true) == ChatFormatting.RED && ReadoutText.nameColor(false) == ChatFormatting.GREEN,
			"the name colors read " + ReadoutText.nameColor(true) + " and " + ReadoutText.nameColor(false)
		);
		helper.succeed();
	}

	@GameTest
	public void everyMobLosesItsVanillaNameTagButAPlayerKeepsIt(final GameTestHelper helper) {
		var cow = helper.spawn(EntityTypes.COW, new net.minecraft.core.BlockPos(1, 2, 1));
		var husk = helper.spawn(EntityTypes.HUSK, new net.minecraft.core.BlockPos(2, 2, 1));
		var stand = helper.spawn(EntityTypes.ARMOR_STAND, new net.minecraft.core.BlockPos(3, 2, 1));
		@SuppressWarnings("removal")
		var player = helper.makeMockServerPlayerInLevel();
		cow.setCustomName(net.minecraft.network.chat.Component.literal("Bessie"));
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=readoutHidesMobTag RESULT cow={} husk={} stand={} player={}",
			ReadoutNames.hidesVanillaTag(cow), ReadoutNames.hidesVanillaTag(husk),
			ReadoutNames.hidesVanillaTag(stand), ReadoutNames.hidesVanillaTag(player)
		);
		helper.assertTrue(
			ReadoutNames.hidesVanillaTag(cow) && ReadoutNames.hidesVanillaTag(husk),
			"a mob keeps its vanilla name tag"
		);
		helper.assertTrue(
			!ReadoutNames.hidesVanillaTag(player) && !ReadoutNames.hidesVanillaTag(stand),
			"a player or a non-mob lost its name tag"
		);
		helper.succeed();
	}
}
