package com.stella.rpg;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
		int none = RpgCurve.fullDrawTicks(0);
		int agile = RpgCurve.fullDrawTicks(100);
		helper.assertTrue(none == 20, "AGI 0 ticks=" + none);
		helper.assertTrue(agile == 17, "AGI 100 ticks=" + agile);
		RpgTestSupport.result("drawTicks", "agi0=" + none + " agi100=" + agile);
		helper.succeed();
	}

	@GameTest
	public void bowReachesFullPowerSoonerWithAgility(final GameTestHelper helper) {
		boolean slow = releaseIsFullPower(helper, 0);
		boolean quick = releaseIsFullPower(helper, 100);
		RpgTestSupport.result("bowReachesFullPowerSoonerWithAgility", "agi0Full=" + slow + " agi100Full=" + quick);
		helper.assertTrue(!slow, "17 held ticks gave full power at AGI 0");
		helper.assertTrue(quick, "17 held ticks gave no full power at AGI 100");
		helper.succeed();
	}

	private static boolean releaseIsFullPower(final GameTestHelper helper, final int agi) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		RpgTestSupport.setStat(player, Stat.AGI, agi);
		ItemStack bow = new ItemStack(Items.BOW);
		player.setItemInHand(InteractionHand.MAIN_HAND, bow);
		player.getInventory().add(new ItemStack(Items.ARROW, 8));
		ServerLevel level = helper.getLevel();
		Set<Arrow> known = new HashSet<>(arrows(level, player));
		int held = 17;
		BowItem item = (BowItem) bow.getItem();
		item.releaseUsing(bow, level, player, item.getUseDuration(bow, player) - held);
		List<Arrow> fresh = arrows(level, player).stream().filter(arrow -> !known.contains(arrow)).toList();
		helper.assertTrue(fresh.size() == 1, "arrows released=" + fresh.size());
		boolean full = fresh.get(0).isCritArrow();
		fresh.forEach(Arrow::discard);
		return full;
	}

	private static List<Arrow> arrows(final ServerLevel level, final ServerPlayer player) {
		return level.getEntitiesOfClass(Arrow.class, player.getBoundingBox().inflate(6.0));
	}
}
