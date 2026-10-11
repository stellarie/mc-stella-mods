package com.stella.rpg;

import com.stella.rpg.roll.Affix;
import com.stella.rpg.roll.Affixes;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A bow or crossbow in the tag mod-stella-equips-enchants:netherite_ranged rolls its die at the netherite material,
 * and a weapon that carries MATK affixes (a staff) is read for them. The tag and the stand-in items live in the test pack.
 */
public final class MerchantRangedDieGameTest {
	private static boolean near(final float actual, final float expected) {
		return Math.abs(actual - expected) < 0.0005F;
	}

	@GameTest
	public void netheriteRangedGetsTheNetheriteDie(final GameTestHelper helper) {
		ItemStack bow = new ItemStack(RpgTestMod.netheriteBow);
		ItemStack crossbow = new ItemStack(RpgTestMod.netheriteCrossbow);
		RpgDice.Dice bowDie = PhysicalDice.weaponDice(bow, true);
		RpgDice.Dice crossbowDie = PhysicalDice.weaponDice(crossbow, true);
		double netherite = RpgConfigStore.current().tables().materialMultiplier("netherite");
		RpgTestSupport.result("netheriteRangedGetsTheNetheriteDie", "bow=" + bowDie + " crossbow=" + crossbowDie
			+ " bowMul=" + PhysicalDice.materialMultiplier(bow) + " plainMul=" + PhysicalDice.materialMultiplier(new ItemStack(Items.BOW)));
		helper.assertTrue(bowDie != null && bowDie.count() == 1 && bowDie.sides() == 4, "netherite bow die " + bowDie);
		helper.assertTrue(crossbowDie != null && crossbowDie.count() == 1 && crossbowDie.sides() == 6, "netherite crossbow die " + crossbowDie);
		helper.assertTrue(PhysicalDice.materialMultiplier(bow) == netherite && PhysicalDice.materialMultiplier(crossbow) == netherite,
			"the netherite ranged items kept the iron multiplier");
		helper.assertTrue(netherite > PhysicalDice.materialMultiplier(new ItemStack(Items.BOW)), "netherite does not beat the plain bow");
		helper.succeed();
	}

	@GameTest
	public void netheriteArrowHitRollsTheNetheriteDie(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = RpgTestSupport.player(helper);
		Zombie victim = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
		float hit = 4.0F;
		RpgCombat.setRoller((attacker, random, chance) -> false);
		RpgCombat.setDiceRoller((attacker, random, dice) -> dice.max());
		try {
			player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(RpgTestMod.netheriteBow));
			Arrow fromNetherite = new Arrow(level, player, new ItemStack(Items.ARROW), new ItemStack(RpgTestMod.netheriteBow));
			level.addFreshEntity(fromNetherite);
			float shot = RpgCombat.scaleOutgoing(victim, level.damageSources().arrow(fromNetherite, player), hit);
			Arrow fromPlain = new Arrow(level, player, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
			level.addFreshEntity(fromPlain);
			player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
			float plain = RpgCombat.scaleOutgoing(victim, level.damageSources().arrow(fromPlain, player), hit);
			fromNetherite.discard();
			fromPlain.discard();
			RpgTestSupport.result("netheriteArrowHitRollsTheNetheriteDie", "netherite=" + shot + " plain=" + plain);
			// DEX 0 rolls 1d4 and the bow adds 1d4, both at maximum 4, times the material.
			helper.assertTrue(near(shot, hit + (4 + 4) * 1.6F), "an arrow from the netherite bow " + shot);
			helper.assertTrue(near(plain, hit + (4 + 4) * 1.2F), "an arrow from the plain bow " + plain);
		} finally {
			RpgCombat.setRoller(null);
			RpgCombat.setDiceRoller(null);
		}
		helper.succeed();
	}

	@GameTest
	public void aWeaponReadsItsMatkAndMagicPenetration(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		ItemStack staff = RpgTestSupport.withAffixes(Items.NETHERITE_SWORD, Map.of("matk_flat", 40, "matk_pct", 120, "magic_pen", 120));
		player.setItemSlot(EquipmentSlot.MAINHAND, staff);
		double flat = Affixes.matkFlat(player);
		double percent = Affixes.total(player, Affix.MATK_PCT);
		double penetration = Affixes.total(player, Affix.MAGIC_PEN);
		RpgTestSupport.result("aWeaponReadsItsMatkAndMagicPenetration", "flat=" + flat + " pct=" + percent + " pen=" + penetration);
		helper.assertTrue(flat == 40.0, "MATK flat " + flat);
		helper.assertTrue(Math.abs(percent - 0.12) < 1e-9, "MATK percent " + percent);
		helper.assertTrue(Math.abs(penetration - 0.12) < 1e-9, "magic penetration " + penetration);
		helper.succeed();
	}
}
