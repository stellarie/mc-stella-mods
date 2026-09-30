package com.stella.rpg;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

public final class RpgCommandGameTest {
	@GameTest
	public void allocateCommandSpendsPoints(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		run(helper, player, "rpgstats allocate str 5");
		int base = StatPoints.sheet(player).get(Stat.STR);
		helper.assertTrue(base == 5, "STR=" + base);
		run(helper, player, "rpgstats allocate luk 0");
		helper.assertTrue(StatPoints.sheet(player).get(Stat.LUK) == 0, "an invalid amount changed LUK");
		RpgTestSupport.result("allocateCommandSpendsPoints", "STR=" + base);
		helper.succeed();
	}

	@GameTest
	public void statusListsSixLinesAndUnspent(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		run(helper, player, "rpgstats");
		List<String> lines = RpgCommand.statusLines(player);
		helper.assertTrue(lines.size() == 7, "lines=" + lines);
		helper.assertTrue(lines.get(0).equals("STR 0") && lines.get(6).equals("Unspent: 12"), "lines=" + lines);
		helper.succeed();
	}

	@GameTest
	public void diceLinesShowDice(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		RpgTestSupport.setStat(player, Stat.STR, 100);
		List<String> lines = RpgCommand.diceLines(player);
		helper.assertTrue(lines.equals(List.of("Atk dice 3d5", "Ranged dice 1d4")), "lines=" + lines);
		helper.succeed();
	}

	private static void run(final GameTestHelper helper, final ServerPlayer player, final String command) {
		helper.getLevel().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
	}
}
