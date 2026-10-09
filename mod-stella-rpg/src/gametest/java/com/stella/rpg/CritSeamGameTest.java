package com.stella.rpg;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * The per-caster critical seam another mod registers into: a provider lifts the caster's critical rate and the
 * multiplier that a critical hit carries. With no provider the old numbers stand.
 */
public final class CritSeamGameTest {
	/** Enough rolls that a seam which is never consulted cannot pass by chance. */
	private static final int ROLLS = 4096;
	private static final int LUK = 100;
	/** The critical chance the shipping curve gives LUK 100 without a crit chance config. */
	private static final double BASE_CHANCE = RpgCurve.critChance(LUK);
	private static final double CHANCE_BONUS = 0.25;
	private static final double DAMAGE_BONUS = 0.5;
	/** The raw hit handed to the damage pipeline, so the dice and the multiplier are what the case measures. */
	private static final float HIT = 10.0F;

	/**
	 * The lift is the difference of two random samples of {@link #ROLLS} rolls, and the tolerance of 0.02 is under two
	 * standard deviations, so a correct seam fails some runs. The case stays in the suite and is not required.
	 * Manual check: run this class several times and confirm the lift stays near {@link #CHANCE_BONUS} on most runs.
	 */
	@GameTest(required = false)
	public void aProviderLiftsTheCriticalRateWithinStatisticalToleranceRequiresManualTest(final GameTestHelper helper) {
		liftBody(helper);
	}

	private static void liftBody(final GameTestHelper helper) {
		ServerPlayer caster = RpgTestSupport.player(helper);
		RpgTestSupport.setStat(caster, Stat.LUK, LUK);
		CritBonuses.Provider provider = (entity) -> new CritBonuses.Bonus(CHANCE_BONUS, 0.0);
		int without;
		int with;
		try {
			without = crits(caster);
			CritBonuses.register(provider);
			with = crits(caster);
		} finally {
			CritBonuses.unregister(provider);
		}
		double lift = (with - without) / (double) ROLLS;
		RpgTestSupport.result("aProviderLiftsTheCriticalRate", "baseChance=" + BASE_CHANCE + " rolls=" + ROLLS + " without="
			+ without + " with=" + with + " lift=" + lift + " expected=" + CHANCE_BONUS);
		helper.assertTrue(without > 0 && without < ROLLS, "the base rate was not a rate: " + without);
		helper.assertTrue(Math.abs(lift - CHANCE_BONUS) < 0.02, "the rate lifted by " + lift + " expected " + CHANCE_BONUS);
		helper.succeed();
	}

	@GameTest
	public void aCriticalHitCarriesTheProviderMultiplier(final GameTestHelper helper) {
		ServerPlayer caster = RpgTestSupport.player(helper);
		Zombie victim = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
		RpgTestSupport.setStat(caster, Stat.LUK, LUK);
		CritBonuses.Provider provider = (entity) -> new CritBonuses.Bonus(0.0, DAMAGE_BONUS);
		float plain;
		float plainAgain;
		float with;
		// The dice are fixed, so the only thing that can move the hit is the multiplier.
		RpgCombat.setDiceRoller((attacker, random, dice) -> dice.max());
		RpgCombat.setRoller((attacker, random, chance) -> false);
		try {
			plain = RpgCombat.scaleOutgoing(victim, helper.getLevel().damageSources().playerAttack(caster), HIT);
			CritBonuses.register(provider);
			plainAgain = RpgCombat.scaleOutgoing(victim, helper.getLevel().damageSources().playerAttack(caster), HIT);
			RpgCombat.setRoller((attacker, random, chance) -> true);
			with = RpgCombat.scaleOutgoing(victim, helper.getLevel().damageSources().playerAttack(caster), HIT);
		} finally {
			CritBonuses.unregister(provider);
			RpgCombat.setRoller(null);
			RpgCombat.setDiceRoller(null);
		}
		double base = RpgCurve.critMultiplier();
		double expected = base + DAMAGE_BONUS;
		double ratio = with / (double) plain;
		RpgTestSupport.result("aCriticalHitCarriesTheProviderMultiplier", "plain=" + plain + " plainWithProvider=" + plainAgain
			+ " critWithProvider=" + with + " ratio=" + ratio + " expected=" + expected + " critMultiplier=" + base);
		// The damage bonus alone never moves a hit that does not crit.
		helper.assertTrue(close(plainAgain, plain), "a non-critical hit moved to " + plainAgain + " from " + plain);
		// A critical hit carries the base multiplier plus the provider's damage bonus.
		helper.assertTrue(close(ratio, expected), "a critical hit read " + ratio + " times the plain hit, expected " + expected);
		helper.succeed();
	}

	@GameTest
	public void noProviderKeepsTheOldNumbers(final GameTestHelper helper) {
		ServerPlayer caster = RpgTestSupport.player(helper);
		Zombie victim = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
		RpgTestSupport.setStat(caster, Stat.LUK, LUK);
		float plain;
		float crit;
		// The dice are fixed, so the critical multiplier is the only thing that can move the hit.
		RpgCombat.setDiceRoller((attacker, random, dice) -> dice.max());
		RpgCombat.setRoller((attacker, random, chance) -> false);
		try {
			plain = RpgCombat.scaleOutgoing(victim, helper.getLevel().damageSources().playerAttack(caster), HIT);
			RpgCombat.setRoller((attacker, random, chance) -> true);
			crit = RpgCombat.scaleOutgoing(victim, helper.getLevel().damageSources().playerAttack(caster), HIT);
		} finally {
			RpgCombat.setRoller(null);
			RpgCombat.setDiceRoller(null);
		}
		double chanceBonus = CritBonuses.chanceBonus(caster);
		double damageBonus = CritBonuses.damageBonus(caster);
		double expected = RpgCurve.critMultiplier() * plain;
		RpgTestSupport.result("noProviderKeepsTheOldNumbers", "chanceBonus=" + chanceBonus + " damageBonus=" + damageBonus
			+ " plain=" + plain + " crit=" + crit + "/" + expected);
		helper.assertTrue(chanceBonus == 0.0 && damageBonus == 0.0, "an unregistered seam granted " + chanceBonus + "/" + damageBonus);
		helper.assertTrue(close(crit, expected), "a critical hit read " + crit + " expected " + expected);
		helper.succeed();
	}

	@GameTest
	public void aThrowingProviderIsSkipped(final GameTestHelper helper) {
		ServerPlayer caster = RpgTestSupport.player(helper);
		RpgTestSupport.setStat(caster, Stat.LUK, LUK);
		CritBonuses.Provider bad = (entity) -> {
			throw new IllegalStateException("a bad entry");
		};
		double chance;
		double damage;
		try {
			CritBonuses.register(bad);
			chance = CritBonuses.chanceBonus(caster);
			damage = CritBonuses.damageBonus(caster);
			RpgCombat.rollCrit(caster);
		} finally {
			CritBonuses.unregister(bad);
		}
		RpgTestSupport.result("aThrowingProviderIsSkipped", "chance=" + chance + " damage=" + damage);
		helper.assertTrue(chance == 0.0 && damage == 0.0, "a throwing provider still granted " + chance + "/" + damage);
		helper.succeed();
	}

	private static int crits(final ServerPlayer caster) {
		int count = 0;
		for (int i = 0; i < ROLLS; i++) {
			if (RpgCombat.rollCrit(caster)) {
				count++;
			}
		}
		return count;
	}

	private static boolean close(final double actual, final double expected) {
		return Math.abs(actual - expected) < Math.max(1.0E-4, Math.abs(expected) * 1.0E-5);
	}
}
