package com.stella.rpg;

import java.util.IdentityHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;

/** DEX max rolls, bonus stat points per level, the mob level gap, and the player spell crit. */
public final class LevelRulesGameTest {
	private static final double EPSILON = 1e-6;

	private static final class Threats implements MobsBridge.Facts {
		private final Map<Mob, Integer> threats = new IdentityHashMap<>();

		@Override
		public int threat(final Mob mob) {
			return this.threats.getOrDefault(mob, 0);
		}

		@Override
		public String role(final Mob mob) {
			return "melee";
		}
	}

	@GameTest
	public void dexMaxRollIsOnePercentPerPoint(final GameTestHelper helper) {
		helper.assertTrue(RpgCurve.dexMaxRollChance(0) == 0.0, "DEX 0 chance " + RpgCurve.dexMaxRollChance(0));
		helper.assertTrue(Math.abs(RpgCurve.dexMaxRollChance(50) - 0.5) < EPSILON, "DEX 50 chance " + RpgCurve.dexMaxRollChance(50));
		helper.assertTrue(RpgCurve.dexMaxRollChance(100) == 1.0 && RpgCurve.dexMaxRollChance(700) == 1.0, "the chance passed 100 percent");

		ServerPlayer archer = RpgTestSupport.player(helper);
		RpgTestSupport.setStat(archer, Stat.DEX, 100);
		int rolled = RpgCombat.fairRoll(archer, RandomSource.create(5L), new RpgDice.Dice(3, 6));
		RpgTestSupport.result("dexMaxRollIsOnePercentPerPoint", "rolled=" + rolled);
		helper.assertTrue(rolled == 18, "three 1d6 dice at DEX 100 rolled " + rolled);
		helper.succeed();
	}

	@GameTest
	public void levelBonusPoints(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		RpgTestSupport.setStat(player, Stat.STR, 5);
		StringBuilder log = new StringBuilder();
		int[] levels = {0, 9, 10, 19, 20, 35};
		int[] bonus = {0, 0, 3, 3, 6, 9};
		for (int i = 0; i < levels.length; i++) {
			player.experienceLevel = levels[i];
			for (Stat stat : Stat.values()) {
				int expected = bonus[i];
				int actual = StatBonuses.bonus(player, stat);
				helper.assertTrue(actual == expected, stat + " bonus at level " + levels[i] + " was " + actual + " expected " + expected);
			}
			log.append(levels[i]).append(':').append(StatBonuses.bonus(player, Stat.INT)).append(' ');
		}
		player.experienceLevel = 20;
		helper.assertTrue(StatBonuses.effective(player, Stat.STR) == 11, "STR 5 at level 20 was " + StatBonuses.effective(player, Stat.STR));
		RpgTestSupport.result("levelBonusPoints", log.toString().trim());
		helper.succeed();
	}

	@GameTest
	public void mobLevelGap(final GameTestHelper helper) {
		Threats threats = new Threats();
		MobsBridge.setFacts(threats);
		RpgCombat.setRoller((attacker, random, chance) -> false);
		RpgCombat.setDiceRoller((attacker, random, dice) -> dice.max());
		try {
			ServerLevel level = helper.getLevel();
			Zombie mob = helper.spawnMob(EntityTypes.ZOMBIE, 1, 1, 2).withNoFreeWill().spawn();
			RpgTestSupport.bare(mob);
			threats.threats.put(mob, 35);
			ServerPlayer equal = playerAt(helper, 35);
			ServerPlayer near = playerAt(helper, 26);
			ServerPlayer far = playerAt(helper, 5);
			DamageSource melee = level.damageSources().mobAttack(mob);
			DamageSource spell = level.damageSources().indirectMagic(mob, mob);
			double base = RpgCombat.scaleOutgoing(equal, melee, 6.0F);
			double nearRatio = RpgCombat.scaleOutgoing(near, melee, 6.0F) / base;
			double farRatio = RpgCombat.scaleOutgoing(far, melee, 6.0F) / base;
			double spellBase = RpgCombat.scaleOutgoing(equal, spell, 4.0F);
			double spellRatio = RpgCombat.scaleOutgoing(far, spell, 4.0F) / spellBase;
			threats.threats.put(mob, 200);
			double top = RpgCombat.scaleOutgoing(playerAt(helper, 0), melee, 6.0F);
			double topEqual = RpgCombat.scaleOutgoing(playerAt(helper, 200), melee, 6.0F);
			RpgTestSupport.result("mobLevelGap", "near=" + nearRatio + " far=" + farRatio + " spell=" + spellRatio + " top=" + top / topEqual);
			helper.assertTrue(Math.abs(nearRatio - 1.0) < EPSILON, "a gap under 10 levels changed the hit: " + nearRatio);
			helper.assertTrue(Math.abs(farRatio - 1.3) < 1e-4, "a gap of 30 levels scaled the hit by " + farRatio + " expected 1.3");
			helper.assertTrue(Math.abs(spellRatio - 1.3) < 1e-4, "a gap of 30 levels scaled a spell by " + spellRatio + " expected 1.3");
			helper.assertTrue(Math.abs(top / topEqual - 3.0) < 1e-4, "a gap of 200 levels scaled the hit by " + top / topEqual + " expected 3.0");
		} finally {
			RpgCombat.setRoller(null);
			RpgCombat.setDiceRoller(null);
			MobsBridge.setFacts(null);
		}
		helper.succeed();
	}

	@GameTest
	public void playerSpellCritIgnoresDefenses(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer caster = RpgTestSupport.player(helper);
		ServerPlayer other = RpgTestSupport.player(helper);
		ServerPlayer victim = RpgTestSupport.realPlayer(helper);
		RpgTestSupport.bare(victim);
		RpgTestSupport.setStat(victim, Stat.INT, 1024);
		RpgTestSupport.setStat(victim, Stat.LUK, 300);
		DamageSource spell = level.damageSources().indirectMagic(caster, caster);
		DamageSource strangers = level.damageSources().indirectMagic(other, other);
		float plain;
		float crit;
		float stranger;
		double armor;
		try {
			RpgCombat.setRoller((attacker, random, chance) -> false);
			RpgSpells.spellDamage(caster, 100.0);
			plain = RpgCombat.scaleIncoming(victim, spell, 10.0F);
			RpgCombat.setRoller((attacker, random, chance) -> true);
			RpgSpells.spellDamage(caster, 100.0);
			crit = RpgCombat.scaleIncoming(victim, spell, 10.0F);
			stranger = RpgCombat.scaleIncoming(victim, strangers, 10.0F);
			armor = RpgCombat.armorIgnored(victim, spell);
			RpgCombat.setRoller((attacker, random, chance) -> false);
			RpgSpells.spellDamage(caster, 100.0);
		} finally {
			RpgCombat.setRoller(null);
		}
		float afterReroll = RpgCombat.scaleIncoming(victim, spell, 10.0F);
		RpgTestSupport.result("playerSpellCritIgnoresDefenses", "plain=" + plain + " crit=" + crit + " stranger=" + stranger + " armor=" + armor);
		helper.assertTrue(Math.abs(plain - 2.5F) < 0.001F, "a plain spell hit took " + plain + " expected 2.5");
		// A crit skips the 75 percent resistance. The victim's LUK 300 takes a quarter off.
		helper.assertTrue(Math.abs(crit - 7.5F) < 0.001F, "a spell crit took " + crit + " expected 7.5");
		helper.assertTrue(Math.abs(stranger - 2.5F) < 0.001F, "a crit of another caster applied: " + stranger);
		helper.assertTrue(armor == 1.0, "a spell crit ignored " + armor + " of the armor");
		helper.assertTrue(Math.abs(afterReroll - 2.5F) < 0.001F, "the crit mark outlived a new plain cast: " + afterReroll);
		helper.succeed();
	}

	private static ServerPlayer playerAt(final GameTestHelper helper, final int level) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		RpgTestSupport.bare(player);
		player.experienceLevel = level;
		return player;
	}
}
