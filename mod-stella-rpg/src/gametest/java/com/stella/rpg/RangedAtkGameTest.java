package com.stella.rpg;

import com.stella.rpg.roll.Affix;
import com.stella.rpg.roll.Affixes;
import com.stella.rpg.roll.ItemTiers;
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
 * Regression guard: a bow or crossbow shot scales with DEX and carries LUK's crit. The ranged Atk readout reads the
 * same dice and the same multiplier, and a melee weapon keeps the melee readout.
 */
public final class RangedAtkGameTest {
	private static final int DEX = 50;
	/** The dice land on their maximum, so a shot is exactly as big as the readout says. */
	private static final RpgCombat.DiceRoller MAX_DICE = (attacker, random, dice) -> dice.max();
	private static final RpgCombat.Roller NO_CRIT = (attacker, random, chance) -> false;

	@GameTest
	public void bowAndCrossbowShotsAddTheDexDice(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = RpgTestSupport.player(helper);
		Zombie victim = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
		float hit = 4.0F;
		ItemStack bow = new ItemStack(Items.BOW);
		ItemStack crossbow = new ItemStack(Items.CROSSBOW);
		double bowDie = PhysicalDice.weaponDice(bow, true).max();
		double crossbowDie = PhysicalDice.weaponDice(crossbow, true).max();
		RpgTestSupport.setStat(player, Stat.DEX, 0);
		player.setItemSlot(EquipmentSlot.MAINHAND, bow);
		float dexZero = shot(level, victim, player, hit);
		RpgTestSupport.setStat(player, Stat.DEX, DEX);
		double multiplier = PhysicalDice.multiplier(player, bow);
		float bowShot = shot(level, victim, player, hit);
		player.setItemSlot(EquipmentSlot.MAINHAND, crossbow);
		float crossbowShot = shot(level, victim, player, hit);
		RpgTestSupport.setStat(player, Stat.DEX, 0);
		player.setItemSlot(EquipmentSlot.MAINHAND, crossbow);
		float crossbowDexZero = shot(level, victim, player, hit);
		// DEX 50 rolls 3d4 against 1d4 at DEX 0, so the shot grows by the extra dice and the extra status ATK.
		double dexExpected = (dexDiceMax() - RpgDice.of(0).max()) * multiplier + RpgCombat.statusAtk(DEX) - RpgCombat.statusAtk(0);
		RpgTestSupport.result("bowAndCrossbowShotsAddTheDexDice", "dexDice=" + RpgDice.of(DEX) + " multiplier=" + multiplier
			+ " bowDiceMax=" + bowDie + " crossbowDiceMax=" + crossbowDie + " dex0=" + dexZero + " bow=" + bowShot + " crossbow="
			+ crossbowShot + " crossbowDex0=" + crossbowDexZero + " dexDelta=" + (bowShot - dexZero) + "/" + dexExpected);
		// DEX 50 rolls 3d4 against 1d4 at DEX 0, so the shot grows by the extra dice and its status ATK.
		helper.assertTrue(close(bowShot - dexZero, dexExpected), "DEX 50 added " + (bowShot - dexZero) + " expected " + dexExpected);
		helper.assertTrue(close(crossbowShot - crossbowDexZero, dexExpected), "a crossbow read " + (crossbowShot - crossbowDexZero)
			+ " expected " + dexExpected);
		// The ranged die of each weapon rides on top of the DEX dice.
		helper.assertTrue(close(crossbowShot - bowShot, (crossbowDie - bowDie) * multiplier),
			"a crossbow beat a bow by " + (crossbowShot - bowShot) + " expected " + (crossbowDie - bowDie) * multiplier);
		helper.assertTrue(bowDie > 0.0 && crossbowDie > bowDie, "the ranged dice read " + bowDie + " and " + crossbowDie);
		helper.succeed();
	}

	@GameTest
	public void lukCritAppliesToAShot(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = RpgTestSupport.player(helper);
		Zombie victim = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
		RpgTestSupport.setStat(player, Stat.DEX, DEX);
		RpgTestSupport.setStat(player, Stat.LUK, 200);
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		float hit = 4.0F;
		float plain;
		float crit;
		RpgCombat.setDiceRoller(MAX_DICE);
		try {
			RpgCombat.setRoller(NO_CRIT);
			plain = RpgCombat.scaleOutgoing(victim, shot(level, player), hit);
			RpgCombat.setRoller((attacker, random, chance) -> true);
			crit = RpgCombat.scaleOutgoing(victim, shot(level, player), hit);
		} finally {
			RpgCombat.setRoller(null);
			RpgCombat.setDiceRoller(null);
		}
		double multiplier = RpgCurve.critMultiplier();
		double ratio = crit / (double) plain;
		double luck = RpgCurve.critChance(200);
		RpgTestSupport.result("lukCritAppliesToAShot", "plain=" + plain + " crit=" + crit + " ratio=" + ratio
			+ " critChanceAtLuk200=" + luck + " critMultiplier=" + multiplier);
		helper.assertTrue(luck > 0.0, "LUK 200 gives no crit chance: " + luck);
		helper.assertTrue(close(ratio, multiplier), "a crit shot read " + ratio + " times the plain shot, expected " + multiplier);
		helper.succeed();
	}

	@GameTest
	public void atkPreviewReadsRangedNumbers(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = RpgTestSupport.player(helper);
		Zombie victim = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
		RpgTestSupport.setStat(player, Stat.DEX, DEX);
		ItemStack bow = new ItemStack(Items.BOW);
		ItemStack crossbow = WeaponDiceGameTest.refined(Items.CROSSBOW, 15);
		RpgDice.Dice dexDice = RpgDice.of(DEX);
		double bowPreview;
		double bowExpected;
		double crossbowPreview;
		double crossbowExpected;
		float bowShot;
		float crossbowShot;
		RpgCombat.setRoller(NO_CRIT);
		RpgCombat.setDiceRoller(MAX_DICE);
		try {
			// One shared fixture: the readout and the shot read the same player at the same level.
			player.setItemSlot(EquipmentSlot.MAINHAND, bow);
			bowPreview = AttackPreview.ranged(player, DEX)[1];
			bowExpected = previewHigh(dexDice, bow, player);
			bowShot = RpgCombat.scaleOutgoing(victim, shot(level, player), 0.0F);
			player.setItemSlot(EquipmentSlot.MAINHAND, crossbow);
			crossbowPreview = AttackPreview.ranged(player, DEX)[1];
			crossbowExpected = previewHigh(dexDice, crossbow, player);
			crossbowShot = RpgCombat.scaleOutgoing(victim, shot(level, player), 0.0F);
		} finally {
			RpgCombat.setRoller(null);
			RpgCombat.setDiceRoller(null);
		}
		RpgTestSupport.result("atkPreviewReadsRangedNumbers", "bow=" + bowPreview + "/" + bowExpected + " shot=" + bowShot
			+ " crossbow=" + crossbowPreview + "/" + crossbowExpected + " shot=" + crossbowShot);
		// The readout is the same K-scaled range the melee line uses, built from the ranged dice.
		helper.assertTrue(close(bowPreview, bowExpected), "the bow readout read " + bowPreview + " expected " + bowExpected);
		helper.assertTrue(close(crossbowPreview, crossbowExpected), "the crossbow readout read " + crossbowPreview
			+ " expected " + crossbowExpected);
		helper.assertTrue(bowShot > 0.0F && crossbowShot > bowShot, "the shots read " + bowShot + " and " + crossbowShot);
		helper.assertTrue(crossbowPreview > bowPreview, "a +15 crossbow read " + crossbowPreview + " against a bow " + bowPreview);
		helper.succeed();
	}

	@GameTest
	public void meleeReadoutIsUntouched(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		RpgTestSupport.setStat(player, Stat.DEX, DEX);
		RpgTestSupport.setStat(player, Stat.STR, DEX);
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		double[] swordMelee = AttackPreview.range(player, DEX);
		double swordRanged = AttackPreview.ranged(player, DEX)[1];
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		double[] bowMelee = AttackPreview.range(player, DEX);
		double bowRanged = AttackPreview.ranged(player, DEX)[1];
		RpgTestSupport.result("meleeReadoutIsUntouched", "swordMelee=" + swordMelee[0] + "-" + swordMelee[1] + " swordRanged="
			+ swordRanged + " bowMelee=" + bowMelee[0] + "-" + bowMelee[1] + " bowRanged=" + bowRanged);
		// The melee readout still follows the held item's attack damage, as it did before the change.
		helper.assertTrue(swordMelee[1] > bowMelee[1], "a sword read " + swordMelee[1] + " and a bow " + bowMelee[1] + " as melee");
		helper.assertTrue(swordMelee[1] != swordRanged && bowMelee[1] != bowRanged,
			"the melee and the ranged readouts agreed on " + swordMelee[1] + "/" + bowMelee[1]);
		helper.succeed();
	}

	@GameTest
	public void heldItemPicksTheRangedReadout(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		ItemStack crossbow = WeaponDiceGameTest.refined(Items.CROSSBOW, 15);
		StringBuilder log = new StringBuilder();
		for (ItemStack held : new ItemStack[] {new ItemStack(Items.BOW), crossbow, new ItemStack(Items.IRON_SWORD), ItemStack.EMPTY}) {
			player.setItemSlot(EquipmentSlot.MAINHAND, held);
			boolean ranged = AttackPreview.heldRanged(player);
			log.append(held.getItem()).append('=').append(ranged).append(' ');
			helper.assertTrue(ranged == isRanged(held), held + " read ranged=" + ranged);
		}
		RpgTestSupport.result("heldItemPicksTheRangedReadout", log.toString().trim());
		helper.succeed();
	}

	private static boolean isRanged(final ItemStack held) {
		ItemTiers.Info info = ItemTiers.of(held);
		return info != null && info.type() != null && info.type().ranged();
	}

	/** The top of the ranged readout, built exactly as {@link AttackPreview#ranged} builds it. */
	private static double previewHigh(final RpgDice.Dice dexDice, final ItemStack weapon, final ServerPlayer player) {
		RpgDice.Dice weaponDice = PhysicalDice.weaponDice(weapon, true);
		double multiplier = PhysicalDice.multiplier(player, weapon);
		double k = RpgScale.k();
		double extra = RpgCombat.statusAtk(DEX) + Affixes.total(player, Affix.DAMAGE_FLAT) / k;
		double factor = RpgCombat.damageFactor(player) * k;
		return ((dexDice.max() + weaponDice.max()) * multiplier + extra) * factor;
	}

	/** The maximum of the DEX dice, so a check on the die itself moves with the shipped tables. */
	private static double dexDiceMax() {
		return RpgDice.of(DEX).max();
	}

	/** Rolls one shot from the held weapon, with the dice at their maximum and no crit. */
	private static float shot(final ServerLevel level, final Zombie victim, final ServerPlayer player, final float hit) {
		RpgCombat.setRoller(NO_CRIT);
		RpgCombat.setDiceRoller(MAX_DICE);
		try {
			return RpgCombat.scaleOutgoing(victim, shot(level, player), hit);
		} finally {
			RpgCombat.setRoller(null);
			RpgCombat.setDiceRoller(null);
		}
	}
	/** A shot whose projectile names the held weapon, as a bow or a crossbow does in play. */
	private static DamageSource shot(final ServerLevel level, final ServerPlayer player) {
		Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), player.getMainHandItem().copy());
		level.addFreshEntity(arrow);
		return level.damageSources().arrow(arrow, player);
	}

	/** A shot whose projectile names no weapon, so the main hand gives no ranged die. */
	private static DamageSource loose(final ServerLevel level, final ServerPlayer player) {
		Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), null);
		level.addFreshEntity(arrow);
		return level.damageSources().arrow(arrow, player);
	}

	private static boolean close(final double actual, final double expected) {
		return Math.abs(actual - expected) < Math.max(1.0E-4, Math.abs(expected) * 1.0E-5);
	}
}
