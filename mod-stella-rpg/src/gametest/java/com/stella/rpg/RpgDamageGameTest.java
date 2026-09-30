package com.stella.rpg;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class RpgDamageGameTest {
	/** The largest roll of the STR or DEX 100 dice. */
	private static final float MAX_DICE_100 = RpgDice.of(100).max();
	private static final float HIT = 4.0F;
	private static final float TOLERANCE = 0.001F;

	@GameTest
	public void meleeAddsDice(final GameTestHelper helper) {
		float loss;
		try {
			forceMax(false);
			loss = meleeLoss(helper, 100);
		} finally {
			resetRollers();
		}
		assertLoss(helper, "meleeAddsDice", loss, guarded(HIT + MAX_DICE_100 + statusAtk(100)));
		helper.succeed();
	}

	@GameTest
	public void chargeScalesDice(final GameTestHelper helper) {
		float spam;
		float half;
		float next;
		try {
			forceMax(false);
			ServerPlayer player = RpgTestSupport.player(helper);
			RpgTestSupport.setStat(player, Stat.STR, 100);
			RpgCombat.beginAttack(player, 0.0F);
			spam = hitLoss(helper, player);
			RpgCombat.beginAttack(player, 0.5F);
			half = hitLoss(helper, player);
			RpgCombat.endAttack(player);
			next = hitLoss(helper, player);
		} finally {
			resetRollers();
		}
		float factor = (float) (0.2 + 0.5 * 0.5 * 0.8);
		assertLoss(helper, "chargeScalesDice spam", spam, guarded(HIT + 0.2F * MAX_DICE_100 + statusAtk(100)));
		assertLoss(helper, "chargeScalesDice half", half, guarded(HIT + factor * MAX_DICE_100 + statusAtk(100)));
		assertLoss(helper, "chargeScalesDice after", next, guarded(HIT + MAX_DICE_100 + statusAtk(100)));
		helper.succeed();
	}

	@GameTest
	public void arrowAddsDexDice(final GameTestHelper helper) {
		float loss;
		try {
			forceMax(false);
			loss = arrowLoss(helper, 100);
		} finally {
			resetRollers();
		}
		assertLoss(helper, "arrowAddsDexDice", loss, guarded(HIT + MAX_DICE_100 + statusAtk(100)));
		helper.succeed();
	}

	@GameTest
	public void critMultipliesTotal(final GameTestHelper helper) {
		float loss;
		try {
			forceMax(true);
			loss = meleeLoss(helper, 0);
		} finally {
			resetRollers();
		}
		assertLoss(helper, "critMultipliesTotal", loss, guarded((HIT + 4.0F) * 1.5F));
		helper.succeed();
	}

	@GameTest
	public void defensePercentThenFlat(final GameTestHelper helper) {
		Difficulty before = helper.getLevel().getDifficulty();
		DefenseBonuses.Provider flat = entity -> 2.0F;
		float partial;
		float clamped;
		DefenseBonuses.register(flat);
		try {
			helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
			partial = zombieHitLoss(helper, 100, 6.0F);
			clamped = zombieHitLoss(helper, 100, 1.0F);
		} finally {
			DefenseBonuses.unregister(flat);
			helper.getLevel().getServer().setDifficulty(before, true);
		}
		assertLoss(helper, "defense partial", partial, (6.0F + mobStatusAtk()) * 0.85F - 2.0F);
		assertLoss(helper, "defense clamped", clamped, 0.0F);
		helper.succeed();
	}

	@GameTest
	public void spellsUnchanged(final GameTestHelper helper) {
		float loss;
		try {
			forceMax(true);
			Zombie victim = zombie(helper);
			victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().magic(), HIT);
			loss = victim.getMaxHealth() - victim.getHealth();
		} finally {
			resetRollers();
		}
		assertLoss(helper, "spellsUnchanged", loss, guarded(HIT));
		helper.succeed();
	}

	@GameTest
	public void vitalityReducesDamage(final GameTestHelper helper) {
		Difficulty before = helper.getLevel().getDifficulty();
		float base;
		float guarded;
		try {
			helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
			base = zombieHitLoss(helper, 0, 6.0F);
			guarded = zombieHitLoss(helper, 100, 6.0F);
		} finally {
			helper.getLevel().getServer().setDifficulty(before, true);
		}
		assertRatio(helper, "vitality", guarded, base, 0.85F);
		helper.succeed();
	}

	@GameTest
	public void luckCritMultiplies(final GameTestHelper helper) {
		float plain;
		float crit;
		try {
			forceMax(false);
			plain = meleeLoss(helper, 0);
			forceMax(true);
			crit = meleeLoss(helper, 0);
		} finally {
			resetRollers();
		}
		assertRatio(helper, "crit", crit, plain, 1.5F);
		helper.succeed();
	}

	@GameTest
	public void thornsIsNotMelee(final GameTestHelper helper) {
		float loss;
		try {
			forceMax(true);
			ServerPlayer player = RpgTestSupport.player(helper);
			RpgTestSupport.setStat(player, Stat.STR, 100);
			Zombie victim = zombie(helper);
			victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().thorns(player), HIT);
			loss = victim.getMaxHealth() - victim.getHealth();
		} finally {
			resetRollers();
		}
		assertLoss(helper, "thornsIsNotMelee", loss, guarded(HIT));
		helper.succeed();
	}

	static float meleeLoss(final GameTestHelper helper, final int str) {
		ServerPlayer player = RpgTestSupport.player(helper);
		RpgTestSupport.setStat(player, Stat.STR, str);
		return hitLoss(helper, player);
	}

	private static float hitLoss(final GameTestHelper helper, final ServerPlayer player) {
		Zombie victim = zombie(helper);
		victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), HIT);
		return victim.getMaxHealth() - victim.getHealth();
	}

	private static float arrowLoss(final GameTestHelper helper, final int dex) {
		ServerPlayer player = RpgTestSupport.player(helper);
		RpgTestSupport.setStat(player, Stat.DEX, dex);
		ServerLevel level = helper.getLevel();
		Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), null);
		level.addFreshEntity(arrow);
		Zombie victim = zombie(helper);
		DamageSource source = level.damageSources().arrow(arrow, player);
		victim.hurtServer(level, source, HIT);
		arrow.discard();
		return victim.getMaxHealth() - victim.getHealth();
	}

	private static float zombieHitLoss(final GameTestHelper helper, final int vit, final float damage) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		RpgTestSupport.bare(player);
		RpgTestSupport.setStat(player, Stat.VIT, vit);
		Zombie attacker = zombie(helper);
		float before = player.getHealth();
		try {
			RpgCombat.setRoller((roller, random, chance) -> false);
			RpgCombat.setDiceRoller((roller, random, dice) -> 0);
			player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), damage);
		} finally {
			resetRollers();
		}
		return before - player.getHealth();
	}

	private static Zombie zombie(final GameTestHelper helper) {
		Zombie zombie = helper.spawnMob(EntityTypes.ZOMBIE, 1, 1, 2).withNoFreeWill().spawn();
		RpgTestSupport.bare(zombie);
		zombie.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		// Room for the larger dice, so no hit is capped by death.
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		zombie.setHealth(zombie.getMaxHealth());
		return zombie;
	}

	/** Status ATK of the STR stat a bare mob attacker has. It adds to every mob melee hit. */
	private static float mobStatusAtk() {
		return (float) RpgCombat.statusAtk(MobStats.base(RpgConfig.defaults(), MobStats.MELEE, Stat.STR, 0));
	}

	private static float statusAtk(final int stat) {
		return (float) RpgCombat.statusAtk(stat);
	}

	private static float guarded(final float damage) {
		return RpgTestSupport.mobGuard(damage);
	}

	private static void forceMax(final boolean crit) {
		RpgCombat.setRoller((attacker, random, chance) -> crit);
		RpgCombat.setDiceRoller((attacker, random, dice) -> dice.max());
	}

	private static void resetRollers() {
		RpgCombat.setRoller(null);
		RpgCombat.setDiceRoller(null);
	}

	/** The expected value is in vanilla units. The loss is in health units, so it is K times larger. */
	private static void assertLoss(final GameTestHelper helper, final String name, final float loss, final float expected) {
		float scaled = expected * RpgScale.kf();
		RpgTestSupport.result(name, "loss=" + loss + " expected=" + scaled);
		helper.assertTrue(Math.abs(loss - scaled) < TOLERANCE * RpgScale.kf(), name + " loss=" + loss + " expected " + scaled);
	}

	private static void assertRatio(
		final GameTestHelper helper, final String name, final float scaled, final float base, final float expected
	) {
		helper.assertTrue(base > 0.0F, name + " base loss was " + base + ", so the case proves nothing");
		float ratio = scaled / base;
		RpgTestSupport.result(name, "base=" + base + " scaled=" + scaled + " ratio=" + ratio);
		helper.assertTrue(Math.abs(ratio - expected) < TOLERANCE, name + " ratio=" + ratio + " expected " + expected);
	}
}
