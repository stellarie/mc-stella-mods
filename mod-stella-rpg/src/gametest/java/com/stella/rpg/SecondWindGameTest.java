package com.stella.rpg;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Second Wind needs the equips enchantment. With it, the first lethal hit restores full health and clears harmful
 * effects; a second inside the cooldown kills. Without it, nothing is caught. The void is never caught.
 */
public class SecondWindGameTest {
	@GameTest
	public void anEnchantedPlayerIsRestoredToFullHealthWithoutHarmfulEffects(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		SecondWind.setEnchantCheck(p -> p == player);
		try {
			player.removeAttached(SecondWind.READY_AT);
			player.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1));
			player.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 1));
			player.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 0));
			player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 1.0E6F);
			float afterFirst = player.getHealth();
			boolean clean = !player.hasEffect(MobEffects.POISON) && !player.hasEffect(MobEffects.WITHER);
			boolean keptSpeed = player.hasEffect(MobEffects.SPEED);
			player.removeAllEffects();
			player.damageCooldownTime = 0;
			player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 1.0E6F);
			boolean aliveSecond = player.isAlive() && player.getHealth() > 0.0F;
			StellaRpg.LOGGER.info("[BEHAVIOR] test={} RESULT {}", "secondWind",
				"afterFirst=" + afterFirst + " max=" + player.getMaxHealth() + " clean=" + clean + " keptSpeed=" + keptSpeed + " aliveSecond=" + aliveSecond);
			helper.assertTrue(afterFirst == player.getMaxHealth(), "the first lethal hit left " + afterFirst + " of " + player.getMaxHealth());
			helper.assertTrue(clean && keptSpeed, "harmful effects cleared=" + clean + ", beneficial kept=" + keptSpeed);
			helper.assertTrue(!aliveSecond, "a second lethal hit inside the cooldown did not kill");
		} finally {
			SecondWind.setEnchantCheck(null);
		}
		helper.succeed();
	}

	@GameTest
	public void withoutTheEnchantmentNothingIsCaught(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		player.removeAttached(SecondWind.READY_AT);
		player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 1.0E6F);
		StellaRpg.LOGGER.info("[BEHAVIOR] test={} RESULT {}", "secondWindBare", "health=" + player.getHealth());
		helper.assertTrue(player.getHealth() <= 0.0F, "a player with no Second Wind enchantment was caught");
		helper.succeed();
	}

	@GameTest
	public void theVoidIsNeverCaught(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		SecondWind.setEnchantCheck(p -> p == player);
		try {
			player.removeAttached(SecondWind.READY_AT);
			player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().fellOutOfWorld(), 1.0E6F);
			StellaRpg.LOGGER.info("[BEHAVIOR] test={} RESULT {}", "secondWindVoid", "health=" + player.getHealth());
			helper.assertTrue(player.getHealth() <= 0.0F, "the void hit was caught");
		} finally {
			SecondWind.setEnchantCheck(null);
		}
		helper.succeed();
	}
}
