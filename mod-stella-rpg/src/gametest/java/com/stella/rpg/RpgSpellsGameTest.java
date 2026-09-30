package com.stella.rpg;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

public final class RpgSpellsGameTest {
	@GameTest
	public void multipliers(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		double zeroPower = RpgSpells.power(player);
		double zeroCooldown = RpgSpells.cooldown(player);
		RpgTestSupport.setStat(player, Stat.INT, 100);
		double power = RpgSpells.power(player);
		double cooldown = RpgSpells.cooldown(player);
		RpgTestSupport.result("multipliers", "int0=" + zeroPower + "/" + zeroCooldown + " int100=" + power + "/" + cooldown);
		helper.assertTrue(near(zeroPower, 1.0), "power at INT 0 = " + zeroPower);
		helper.assertTrue(near(zeroCooldown, 1.0), "cooldown at INT 0 = " + zeroCooldown);
		helper.assertTrue(near(power, 1.0), "the retired power curve still applies at INT 100 = " + power);
		helper.assertTrue(near(cooldown, 0.9), "cooldown at INT 100 = " + cooldown);
		helper.succeed();
	}

	private static boolean near(final double actual, final double expected) {
		return Math.abs(Math.round(actual * 10_000.0) / 10_000.0 - expected) < 1e-9;
	}
}
