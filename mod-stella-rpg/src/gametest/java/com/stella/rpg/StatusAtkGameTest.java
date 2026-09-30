package com.stella.rpg;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Ragnarok status ATK: the stat plus the square of a tenth of it, added after the dice and before the crit. */
public final class StatusAtkGameTest {
	private static final int[] STATS = {20, 50, 100, 200};
	/** The table in K units: STR + floor(STR / 10)^2. */
	private static final int[] TABLE = {24, 75, 200, 600};
	private static final float TOLERANCE = 0.0005F;

	@GameTest
	public void statusAtkTable(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Zombie victim = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
		StringBuilder log = new StringBuilder();
		RpgCombat.setRoller((attacker, random, chance) -> false);
		RpgCombat.setDiceRoller((attacker, random, dice) -> 0);
		try {
			for (int i = 0; i < STATS.length; i++) {
				ServerPlayer player = RpgTestSupport.player(helper);
				RpgTestSupport.setStat(player, Stat.STR, STATS[i]);
				RpgTestSupport.setStat(player, Stat.DEX, STATS[i]);
				float expected = TABLE[i] / RpgScale.kf();
				float melee = RpgCombat.scaleOutgoing(victim, level.damageSources().playerAttack(player), 0.0F);
				Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), null);
				level.addFreshEntity(arrow);
				float shot = RpgCombat.scaleOutgoing(victim, level.damageSources().arrow(arrow, player), 0.0F);
				arrow.discard();
				log.append(STATS[i]).append(":melee=").append(melee).append("/arrow=").append(shot).append(" expected=").append(expected).append(' ');
				helper.assertTrue(Math.abs(melee - expected) < TOLERANCE, "melee status ATK at " + STATS[i] + " was " + melee + " expected " + expected);
				helper.assertTrue(Math.abs(shot - expected) < TOLERANCE, "arrow status ATK at " + STATS[i] + " was " + shot + " expected " + expected);
				helper.assertTrue(TABLE[i] == STATS[i] + (STATS[i] / 10) * (STATS[i] / 10), "the table row " + STATS[i] + " is wrong");
			}
		} finally {
			RpgCombat.setRoller(null);
			RpgCombat.setDiceRoller(null);
		}
		RpgTestSupport.result("statusAtkTable", log.toString().trim());
		helper.succeed();
	}

	@GameTest
	public void statusAtkBetweenDiceAndCrit(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Zombie victim = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
		ServerPlayer player = RpgTestSupport.player(helper);
		RpgTestSupport.setStat(player, Stat.STR, 100);
		DamageSource melee = level.damageSources().playerAttack(player);
		float dice = RpgDice.of(100).max();
		float atk = 200.0F / RpgScale.kf();
		float plain;
		float crit;
		try {
			RpgCombat.setDiceRoller((attacker, random, roll) -> roll.max());
			RpgCombat.setRoller((attacker, random, chance) -> false);
			plain = RpgCombat.scaleOutgoing(victim, melee, 4.0F);
			RpgCombat.setRoller((attacker, random, chance) -> true);
			crit = RpgCombat.scaleOutgoing(victim, melee, 4.0F);
		} finally {
			RpgCombat.setRoller(null);
			RpgCombat.setDiceRoller(null);
		}
		float expectedPlain = 4.0F + dice + atk;
		RpgTestSupport.result("statusAtkBetweenDiceAndCrit", "plain=" + plain + " crit=" + crit + " expected=" + expectedPlain);
		helper.assertTrue(Math.abs(plain - expectedPlain) < TOLERANCE, "plain " + plain + " expected " + expectedPlain);
		helper.assertTrue(Math.abs(crit - expectedPlain * (float) RpgCurve.critMultiplier()) < TOLERANCE, "crit " + crit);
		helper.succeed();
	}
}
