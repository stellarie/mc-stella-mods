package com.stella.rpg;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

/** The numbers a skill book reads from {@link RpgSkills}. */
public final class RpgSkillsGameTest {
	private static final double EPSILON = 1e-9;

	@GameTest
	public void strGrowsWithStatAndWeapon(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		equip(player, new ItemStack(Items.WOODEN_SWORD));
		RpgTestSupport.setStat(player, Stat.STR, 0);
		double woodLow = RpgSkills.physicalHit(player, "str", 100);
		RpgTestSupport.setStat(player, Stat.STR, 100);
		double woodHigh = RpgSkills.physicalHit(player, "str", 100);
		equip(player, new ItemStack(Items.DIAMOND_SWORD));
		double diamondHigh = RpgSkills.physicalHit(player, "str", 100);
		RpgTestSupport.result("RpgSkillsGameTest.strGrowsWithStatAndWeapon", "woodStr0=" + woodLow + " woodStr100=" + woodHigh + " diamondStr100=" + diamondHigh);
		helper.assertTrue(woodLow > 0.0, "a wooden sword at STR 0 hit " + woodLow);
		helper.assertTrue(woodHigh > woodLow, "STR 100 did not beat STR 0: " + woodHigh + " vs " + woodLow);
		helper.assertTrue(diamondHigh > woodHigh, "diamond did not beat wood: " + diamondHigh + " vs " + woodHigh);
		helper.succeed();
	}

	@GameTest
	public void percentIsLinear(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		equip(player, new ItemStack(Items.IRON_SWORD));
		RpgTestSupport.setStat(player, Stat.STR, 100);
		double one = RpgSkills.physicalHit(player, "str", 100);
		double two = RpgSkills.physicalHit(player, "str", 200);
		RpgTestSupport.result("RpgSkillsGameTest.percentIsLinear", "p100=" + one + " p200=" + two);
		helper.assertTrue(one > 0.0, "the 100 percent hit was " + one);
		helper.assertTrue(Math.abs(two - 2.0 * one) < EPSILON, "200 percent " + two + " is not twice " + one);
		helper.succeed();
	}

	@GameTest
	public void dexUsesBow(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		equip(player, ItemStack.EMPTY);
		RpgTestSupport.setStat(player, Stat.DEX, 100);
		double noBow = RpgSkills.physicalHit(player, "dex", 100);
		equip(player, new ItemStack(Items.BOW));
		RpgTestSupport.setStat(player, Stat.DEX, 0);
		double bowLow = RpgSkills.physicalHit(player, "dex", 100);
		RpgTestSupport.setStat(player, Stat.DEX, 100);
		double bowHigh = RpgSkills.physicalHit(player, "dex", 100);
		ItemStack powered = new ItemStack(Items.BOW);
		powered.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.POWER), 5);
		equip(player, powered);
		double bowPower = RpgSkills.physicalHit(player, "dex", 100);
		RpgTestSupport.result("RpgSkillsGameTest.dexUsesBow", "noBow=" + noBow + " bowDex0=" + bowLow + " bowDex100=" + bowHigh + " bowPower5=" + bowPower);
		helper.assertTrue(bowLow > 0.0, "a bow at DEX 0 hit " + bowLow);
		helper.assertTrue(bowHigh > bowLow, "DEX 100 did not beat DEX 0: " + bowHigh + " vs " + bowLow);
		helper.assertTrue(bowHigh > noBow, "the bow die did not count: " + bowHigh + " vs " + noBow);
		helper.assertTrue(bowPower > bowHigh, "Power did not count: " + bowPower + " vs " + bowHigh);
		helper.succeed();
	}

	@GameTest
	public void quickSlotWeapon(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		Inventory inventory = player.getInventory();
		inventory.clearContent();
		inventory.setItem(WeaponSlot.SLOT, new ItemStack(Items.IRON_SWORD));
		inventory.setItem(0, new ItemStack(Items.BOOK));
		inventory.setSelectedSlot(0);
		ItemStack weapon = RpgSkills.weapon(player);
		RpgTestSupport.result("RpgSkillsGameTest.quickSlotWeapon", "weapon=" + weapon + " hand=" + player.getMainHandItem());
		helper.assertTrue(player.getMainHandItem().is(Items.BOOK), "the fixture hand held " + player.getMainHandItem());
		helper.assertTrue(weapon.is(Items.IRON_SWORD), "the weapon was " + weapon);
		inventory.setItem(WeaponSlot.SLOT, ItemStack.EMPTY);
		inventory.setItem(0, new ItemStack(Items.DIAMOND_AXE));
		helper.assertTrue(RpgSkills.weapon(player).is(Items.DIAMOND_AXE), "an empty slot did not fall back to the hand");
		inventory.setItem(0, new ItemStack(Items.BOOK));
		helper.assertTrue(RpgSkills.weapon(player).isEmpty(), "a book counted as the weapon: " + RpgSkills.weapon(player));
		helper.succeed();
	}

	@GameTest
	public void healGrowsWithVit(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		RpgTestSupport.setStat(player, Stat.VIT, 0);
		double low = RpgSkills.skillHeal(player, 1.0);
		RpgTestSupport.setStat(player, Stat.VIT, 100);
		double high = RpgSkills.skillHeal(player, 1.0);
		RpgTestSupport.result("RpgSkillsGameTest.healGrowsWithVit", "vit0=" + low + " vit100=" + high);
		helper.assertTrue(high > low, "VIT 100 did not beat VIT 0: " + high + " vs " + low);
		helper.assertTrue(Math.abs(high - low - 200.0 / RpgScale.k()) < EPSILON, "VIT does not count twice: " + (high - low));
		helper.succeed();
	}

	@GameTest
	public void durationMatchesCurve(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		RpgTestSupport.setStat(player, Stat.VIT, 150);
		double actual = RpgSkills.durationMultiplier(player, "vit");
		double expected = HpModel.durationMultiplier(150);
		RpgTestSupport.result("RpgSkillsGameTest.durationMatchesCurve", "actual=" + actual + " expected=" + expected);
		helper.assertTrue(expected > 1.0, "the fixture curve is flat: " + expected);
		helper.assertTrue(Math.abs(actual - expected) < EPSILON, "duration " + actual + " expected " + expected);
		helper.succeed();
	}

	@GameTest
	public void skillSourceUnscaled(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		equip(player, new ItemStack(Items.DIAMOND_SWORD));
		RpgTestSupport.setStat(player, Stat.STR, 100);
		Zombie victim = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 2);
		RpgCombat.setRoller((attacker, random, chance) -> false);
		RpgCombat.setDiceRoller((attacker, random, dice) -> dice.max());
		try {
			DamageSource source = RpgSkills.skillSource(player);
			float scaled = RpgCombat.scaleOutgoing(victim, source, 10.0F);
			RpgTestSupport.result("RpgSkillsGameTest.skillSourceUnscaled", "scaled=" + scaled + " type=" + source.type().msgId());
			helper.assertTrue(source.getEntity() == player, "the source names " + source.getEntity());
			helper.assertTrue(source.getDirectEntity() == player, "the direct entity is " + source.getDirectEntity());
			helper.assertTrue(source.is(RpgDamageTypes.SKILL), "the source is not the skill type");
			helper.assertTrue(scaled == 10.0F, "scaleOutgoing changed the skill hit: " + scaled);
		} finally {
			RpgCombat.setRoller(null);
			RpgCombat.setDiceRoller(null);
		}
		helper.succeed();
	}

	/** Puts the weapon in the weapon slot and a book in hand, as a quick-slot cast does. */
	private static void equip(final ServerPlayer player, final ItemStack weapon) {
		Inventory inventory = player.getInventory();
		inventory.clearContent();
		inventory.setItem(WeaponSlot.SLOT, weapon);
		inventory.setItem(0, new ItemStack(Items.BOOK));
		inventory.setSelectedSlot(0);
	}
}
