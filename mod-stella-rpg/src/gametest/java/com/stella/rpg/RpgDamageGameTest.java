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
	private static final float HIT = 4.0F;
	private static final float TOLERANCE = 0.001F;

	@GameTest
	public void strengthScalesMelee(final GameTestHelper helper) {
		withRoller(false, () -> {
			float base = meleeLoss(helper, 0);
			float strong = meleeLoss(helper, 100);
			assertRatio(helper, "melee", strong, base, 1.5F);
		});
		helper.succeed();
	}

	@GameTest
	public void dexterityScalesArrows(final GameTestHelper helper) {
		withRoller(false, () -> {
			float base = arrowLoss(helper, 0);
			float strong = arrowLoss(helper, 100);
			assertRatio(helper, "arrow", strong, base, 1.5F);
		});
		helper.succeed();
	}

	@GameTest
	public void vitalityReducesDamage(final GameTestHelper helper) {
		Difficulty before = helper.getLevel().getDifficulty();
		float base;
		float guarded;
		try {
			helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
			base = zombieHitLoss(helper, 0);
			guarded = zombieHitLoss(helper, 100);
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
			RpgCombat.setRoller((attacker, random, chance) -> false);
			plain = meleeLoss(helper, 0);
			RpgCombat.setRoller((attacker, random, chance) -> true);
			crit = meleeLoss(helper, 0);
		} finally {
			RpgCombat.setRoller(null);
		}
		assertRatio(helper, "crit", crit, plain, 1.5F);
		helper.succeed();
	}

	@GameTest
	public void mobsAreUnchanged(final GameTestHelper helper) {
		float loss;
		try {
			RpgCombat.setRoller((attacker, random, chance) -> true);
			Zombie attacker = zombie(helper);
			Zombie victim = zombie(helper);
			victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), HIT);
			loss = victim.getMaxHealth() - victim.getHealth();
		} finally {
			RpgCombat.setRoller(null);
		}
		helper.assertTrue(Math.abs(loss - HIT) < TOLERANCE, "zombie hit dealt " + loss + " instead of " + HIT);
		RpgTestSupport.result("mobsAreUnchanged", "loss=" + loss);
		helper.succeed();
	}

	static float meleeLoss(final GameTestHelper helper, final int str) {
		ServerPlayer player = RpgTestSupport.player(helper);
		RpgTestSupport.setStat(player, Stat.STR, str);
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

	private static float zombieHitLoss(final GameTestHelper helper, final int vit) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		RpgTestSupport.bare(player);
		RpgTestSupport.setStat(player, Stat.VIT, vit);
		Zombie attacker = zombie(helper);
		float before = player.getHealth();
		player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 6.0F);
		return before - player.getHealth();
	}

	private static Zombie zombie(final GameTestHelper helper) {
		Zombie zombie = helper.spawnMob(EntityTypes.ZOMBIE, 1, 1, 2).withNoFreeWill().spawn();
		RpgTestSupport.bare(zombie);
		zombie.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		return zombie;
	}

	private static void withRoller(final boolean crit, final Runnable body) {
		try {
			RpgCombat.setRoller((attacker, random, chance) -> crit);
			body.run();
		} finally {
			RpgCombat.setRoller(null);
		}
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
