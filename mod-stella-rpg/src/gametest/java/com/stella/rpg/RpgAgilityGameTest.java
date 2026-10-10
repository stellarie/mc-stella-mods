package com.stella.rpg;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class RpgAgilityGameTest {
	@GameTest
	public void attackSpeedModifier(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
		double base = speed.getBaseValue();
		RpgTestSupport.setStat(player, Stat.AGI, 100);
		double ratio = Math.round(speed.getValue() / base * 10_000.0) / 10_000.0;
		RpgTestSupport.result("attackSpeedModifier", "base=" + base + " value=" + speed.getValue() + " ratio=" + ratio);
		helper.assertTrue(Math.abs(ratio - 1.2857) < 1e-9, "ratio=" + ratio);
		helper.succeed();
	}

	@GameTest
	public void attackSpeedModifierIsRemoved(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
		RpgTestSupport.setStat(player, Stat.AGI, 100);
		helper.assertTrue(speed.getModifier(RpgAttributes.ATTACK_SPEED_ID) != null, "modifier missing at AGI 100");
		RpgTestSupport.setStat(player, Stat.AGI, 0);
		helper.assertTrue(speed.getModifier(RpgAttributes.ATTACK_SPEED_ID) == null, "modifier stayed at AGI 0");
		helper.succeed();
	}

	@GameTest
	public void drawTicks(final GameTestHelper helper) {
		int none = DrawSpeed.fullDrawTicks(0, 0);
		int agile = DrawSpeed.fullDrawTicks(50, 50);
		int capped = DrawSpeed.fullDrawTicks(1024, 1024);
		helper.assertTrue(none == 20, "no stats ticks=" + none);
		helper.assertTrue(agile == 15, "AGI 50 + DEX 50 ticks=" + agile);
		helper.assertTrue(capped == 10, "the cap ticks=" + capped);
		RpgTestSupport.result("drawTicks", "none=" + none + " agile=" + agile + " capped=" + capped);
		helper.succeed();
	}
}
