package com.stella.rpg;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

public final class PendingStatsGameTest {
	@GameTest
	public void plusRespectsLimits(final GameTestHelper helper) {
		PendingStats pending = new PendingStats();
		int applied = pending.plus(Stat.STR, 50, 12, 0);
		helper.assertTrue(applied == 12 && pending.get(Stat.STR) == 12, "unspent cap applied=" + applied);
		helper.assertTrue(pending.plus(Stat.AGI, 1, 12, 0) == 0, "plus past unspent");
		PendingStats other = new PendingStats();
		int ceiling = other.plus(Stat.VIT, 100, 5000, 1000);
		helper.assertTrue(ceiling == 24 && other.get(Stat.VIT) == 24, "ceiling cap applied=" + ceiling);
		helper.assertTrue(other.plus(Stat.VIT, 1, 5000, 1000) == 0, "plus past ceiling");
		helper.assertTrue(other.plus(Stat.LUK, 3, 5000, 0) == 3, "other stat blocked by ceiling");
		RpgTestSupport.result("plusRespectsLimits", "total=" + pending.total() + "/" + other.total());
		helper.succeed();
	}

	@GameTest
	public void minusStopsAtZero(final GameTestHelper helper) {
		PendingStats pending = new PendingStats();
		pending.plus(Stat.DEX, 4, 12, 0);
		helper.assertTrue(pending.minus(Stat.DEX, 10) == 4 && pending.get(Stat.DEX) == 0, "minus overshoot");
		helper.assertTrue(pending.minus(Stat.DEX, 1) == 0 && pending.get(Stat.DEX) == 0, "minus below zero");
		helper.assertTrue(pending.minus(Stat.INT, 1) == 0 && pending.get(Stat.INT) == 0, "minus on untouched stat");
		RpgTestSupport.result("minusStopsAtZero", "total=" + pending.total());
		helper.succeed();
	}

	@GameTest
	public void clearAndEffective(final GameTestHelper helper) {
		PendingStats pending = new PendingStats();
		pending.plus(Stat.STR, 3, 12, 25);
		pending.plus(Stat.LUK, 2, 12, 0);
		helper.assertTrue(pending.total() == 5, "total=" + pending.total());
		helper.assertTrue(pending.effective(Stat.STR, 25, 5) == 33, "effective=" + pending.effective(Stat.STR, 25, 5));
		helper.assertTrue(pending.effective(Stat.AGI, 4, 1) == 5, "effective without pending");
		pending.clear();
		helper.assertTrue(pending.total() == 0, "total after clear=" + pending.total());
		helper.assertTrue(pending.effective(Stat.STR, 25, 5) == 30, "effective after clear");
		RpgTestSupport.result("clearAndEffective", "ok");
		helper.succeed();
	}

	@GameTest
	public void clampAfterSync(final GameTestHelper helper) {
		PendingStats pending = new PendingStats();
		pending.plus(Stat.STR, 8, 12, 0);
		pending.clamp(5, new int[6]);
		helper.assertTrue(pending.total() == 5, "total after clamp=" + pending.total());
		RpgTestSupport.result("clampAfterSync", "total=" + pending.total());
		helper.succeed();
	}

	@GameTest
	public void atkSum(final GameTestHelper helper) {
		Holder<Enchantment> sharpness = helper.getLevel().registryAccess()
			.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
		Holder<Enchantment> smite = helper.getLevel().registryAccess()
			.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SMITE);
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		sword.enchant(sharpness, 5);
		double sharp = AttackPreview.withEnchantments(sword, 7.0);
		helper.assertTrue(Math.abs(sharp - 10.0) < 1e-6, "sharpness V total=" + sharp);
		String range = AttackPreview.format(sharp, new RpgDice.Dice(1, 4));
		helper.assertTrue(range.equals("11-14"), "text=" + range);
		String example = AttackPreview.format(9.0, new RpgDice.Dice(1, 4));
		helper.assertTrue(example.equals("10-13"), "example=" + example);
		ItemStack smiter = new ItemStack(Items.DIAMOND_SWORD);
		smiter.enchant(smite, 5);
		double smited = AttackPreview.withEnchantments(smiter, 7.0);
		helper.assertTrue(Math.abs(smited - 7.0) < 1e-6, "smite V total=" + smited);
		double plain = AttackPreview.withEnchantments(new ItemStack(Items.DIAMOND_SWORD), 7.0);
		helper.assertTrue(Math.abs(plain - 7.0) < 1e-6, "plain total=" + plain);
		helper.assertTrue(AttackPreview.number(7.25).equals("7.3") || AttackPreview.number(7.25).equals("7.2"), "one decimal");
		helper.assertTrue(AttackPreview.number(6.0).equals("6"), "whole number=" + AttackPreview.number(6.0));
		RpgTestSupport.result("atkSum", "sharp=" + sharp + " smite=" + smited);
		helper.succeed();
	}
}
