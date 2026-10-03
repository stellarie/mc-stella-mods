package com.stella.rpg;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/** The first lethal hit leaves the player at 1 health; a second one inside the cooldown kills. The void is never caught. */
public class SecondWindGameTest {
	@GameTest
	public void firstLethalHitIsCaughtOnce(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		player.removeAttached(SecondWind.READY_AT);
		player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 1.0E6F);
		float afterFirst = player.getHealth();
		boolean aliveFirst = player.isAlive();
		player.removeAllEffects();
		player.damageCooldownTime = 0;
		player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 1.0E6F);
		boolean aliveSecond = player.isAlive() && player.getHealth() > 0.0F;
		StellaRpg.LOGGER.info("[BEHAVIOR] test={} RESULT {}", "secondWind",
			"afterFirst=" + afterFirst + " aliveFirst=" + aliveFirst + " aliveSecond=" + aliveSecond);
		helper.assertTrue(aliveFirst && afterFirst == 1.0F, "the first lethal hit left " + afterFirst + ", alive " + aliveFirst);
		helper.assertTrue(!aliveSecond, "a second lethal hit inside the cooldown did not kill");
		helper.succeed();
	}

	@GameTest
	public void theVoidIsNeverCaught(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		player.removeAttached(SecondWind.READY_AT);
		player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().fellOutOfWorld(), 1.0E6F);
		StellaRpg.LOGGER.info("[BEHAVIOR] test={} RESULT {}", "secondWindVoid", "health=" + player.getHealth());
		helper.assertTrue(player.getHealth() <= 0.0F, "the void hit was caught");
		helper.succeed();
	}
}
