package com.stella.rpg;

import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Armor penetration of a melee or projectile hit, and the crit that ignores all armor. */
public final class ArmorPenGameTest {
	private static final float HIT = 6.0F;
	private static final float ARMOR = 20.0F;
	private static final float TOUGHNESS = 8.0F;

	@GameTest
	public void meleePenetration(final GameTestHelper helper) {
		fixRolls(false);
		try {
			ServerPlayer player = RpgTestSupport.realPlayer(helper);
			float base = check(helper, "base 25 percent", player, 0.25F, null);
			float rolled = check(helper, "25 percent roll", player, 0.50F, Map.of("armor_pen", 250));
			float capped = check(helper, "capped at 75 percent", player, 0.75F, Map.of("armor_pen", 900));
			RpgTestSupport.result("meleePenetration", "base=" + base + " rolled=" + rolled + " capped=" + capped);
			helper.assertTrue(base < rolled && rolled < capped, "more penetration did not deal more: " + base + " " + rolled + " " + capped);
		} finally {
			reset();
		}
		helper.succeed();
	}

	@GameTest
	public void projectilePenetration(final GameTestHelper helper) {
		fixRolls(false);
		try {
			ServerPlayer player = RpgTestSupport.realPlayer(helper);
			RpgTestSupport.bare(player);
			player.setItemSlot(EquipmentSlot.MAINHAND, RpgTestSupport.withAffixes(Items.BOW, Map.of("armor_pen", 250)));
			ServerLevel level = helper.getLevel();
			Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), null);
			level.addFreshEntity(arrow);
			DamageSource source = level.damageSources().arrow(arrow, player);
			Zombie victim = armored(helper);
			float expected = expected(victim, source, 0.50F);
			float loss = lossOf(level, victim, source);
			arrow.discard();
			RpgTestSupport.result("projectilePenetration", "loss=" + loss + " expected=" + expected);
			helper.assertTrue(Math.abs(loss - expected) < 0.01F * RpgScale.kf(), "an arrow lost " + loss + " expected " + expected);
		} finally {
			reset();
		}
		helper.succeed();
	}

	@GameTest
	public void mobAttackerIsSkipped(final GameTestHelper helper) {
		fixRolls(false);
		try {
			ServerLevel level = helper.getLevel();
			Zombie attacker = helper.spawnMob(EntityTypes.ZOMBIE, 1, 1, 2).withNoFreeWill().spawn();
			RpgTestSupport.bare(attacker);
			ServerPlayer victim = RpgTestSupport.realPlayer(helper);
			RpgTestSupport.bare(victim);
			arm(victim);
			DamageSource source = level.damageSources().mobAttack(attacker);
			float expected = expected(victim, source, 0.0F);
			float loss = lossOf(level, victim, source);
			RpgTestSupport.result("mobAttackerIsSkipped", "loss=" + loss + " expected=" + expected);
			helper.assertTrue(Math.abs(loss - expected) < 0.01F * RpgScale.kf(), "a mob hit lost " + loss + " expected " + expected);
		} finally {
			reset();
		}
		helper.succeed();
	}

	@GameTest
	public void critIgnoresAllArmor(final GameTestHelper helper) {
		fixRolls(true);
		try {
			ServerLevel level = helper.getLevel();
			ServerPlayer player = RpgTestSupport.realPlayer(helper);
			RpgTestSupport.bare(player);
			DamageSource byPlayer = level.damageSources().playerAttack(player);
			Zombie victim = armored(helper);
			float playerExpected = expected(victim, byPlayer, 1.0F);
			float playerLoss = lossOf(level, victim, byPlayer);

			Zombie attacker = helper.spawnMob(EntityTypes.ZOMBIE, 1, 1, 2).withNoFreeWill().spawn();
			RpgTestSupport.bare(attacker);
			ServerPlayer target = RpgTestSupport.realPlayer(helper);
			RpgTestSupport.bare(target);
			arm(target);
			DamageSource byMob = level.damageSources().mobAttack(attacker);
			float mobExpected = expected(target, byMob, 1.0F);
			float mobLoss = lossOf(level, target, byMob);
			RpgTestSupport.result("critIgnoresAllArmor", "player=" + playerLoss + "/" + playerExpected + " mob=" + mobLoss + "/" + mobExpected);
			helper.assertTrue(Math.abs(playerLoss - playerExpected) < 0.01F * RpgScale.kf(), "a player crit lost " + playerLoss + " expected " + playerExpected);
			helper.assertTrue(Math.abs(mobLoss - mobExpected) < 0.01F * RpgScale.kf(), "a mob crit lost " + mobLoss + " expected " + mobExpected);
		} finally {
			reset();
		}
		helper.succeed();
	}

	private static float check(
		final GameTestHelper helper, final String name, final ServerPlayer player, final float share, final Map<String, Integer> affixes
	) {
		RpgTestSupport.bare(player);
		if (affixes != null) {
			player.setItemSlot(EquipmentSlot.MAINHAND, RpgTestSupport.withAffixes(Items.IRON_SWORD, affixes));
		}
		ServerLevel level = helper.getLevel();
		DamageSource source = level.damageSources().playerAttack(player);
		Zombie victim = armored(helper);
		float expected = expected(victim, source, share);
		float loss = lossOf(level, victim, source);
		helper.assertTrue(Math.abs(loss - expected) < 0.01F * RpgScale.kf(), name + " lost " + loss + " expected " + expected);
		return loss;
	}

	/** The loss in health units when the hit ignores the given share of armor and toughness. */
	private static float expected(final LivingEntity victim, final DamageSource source, final float share) {
		float scaled = RpgCombat.scaleOutgoing(victim, source, HIT);
		float afterArmor = CombatRules.getDamageAfterAbsorb(victim, scaled, source, ARMOR * (1.0F - share), TOUGHNESS * (1.0F - share));
		float taken = RpgCombat.scaleIncoming(victim, source, afterArmor);
		RpgCombat.endHurt(victim);
		return taken * RpgScale.kf();
	}

	private static float lossOf(final ServerLevel level, final LivingEntity victim, final DamageSource source) {
		float before = victim.getHealth();
		victim.hurtServer(level, source, HIT);
		return before - victim.getHealth();
	}

	private static Zombie armored(final GameTestHelper helper) {
		Zombie zombie = helper.spawnMob(EntityTypes.ZOMBIE, 1, 1, 2).withNoFreeWill().spawn();
		RpgTestSupport.bare(zombie);
		arm(zombie);
		return zombie;
	}

	private static void arm(final LivingEntity victim) {
		victim.getAttribute(Attributes.ARMOR).setBaseValue(ARMOR);
		victim.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(TOUGHNESS);
		victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100000.0);
		victim.setHealth(victim.getMaxHealth());
	}

	private static void fixRolls(final boolean crit) {
		RpgCombat.setRoller((attacker, random, chance) -> crit);
		RpgCombat.setDiceRoller((attacker, random, dice) -> dice.max());
	}

	private static void reset() {
		RpgCombat.setRoller(null);
		RpgCombat.setDiceRoller(null);
	}
}
