package com.stella.rpg;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

/** DEX raises the chance of a maximum roll. INT defends against magic. LUK softens critical hits. */
public final class StatEffectsGameTest {
	private static final float TOLERANCE = 0.0005F;

	/** A random source that replays the given doubles, and rolls the lowest value for every bounded integer. */
	private static final class Scripted extends LegacyRandomSource {
		private final double[] doubles;
		private int next;

		private Scripted(final double... doubles) {
			super(1L);
			this.doubles = doubles;
		}

		@Override
		public double nextDouble() {
			return this.doubles[this.next++ % this.doubles.length];
		}

		@Override
		public int nextInt(final int bound) {
			return 0;
		}
	}

	@GameTest
	public void maxRollChance(final GameTestHelper helper) {
		helper.assertTrue(RpgCurve.dexMaxRollChance(0) == 0.0 && Math.abs(RpgCurve.dexMaxRollChance(200) - 0.25) < 1e-12
			&& Math.abs(RpgCurve.dexMaxRollChance(800) - 0.4) < 1e-12, "the DEX curve");

		// Three 1d6 dice and chance 0.25: 0.24 is a maximum, 0.26 is a fair roll of 1, and 0.10 is a maximum.
		int dice = new RpgDice.Dice(3, 6).roll(new Scripted(0.24, 0.26, 0.10), 0.25);
		helper.assertTrue(dice == 6 + 1 + 6, "three dice rolled " + dice);
		helper.assertTrue(new RpgDice.Dice(3, 6).roll(new Scripted(0.0), 0.0) == 3, "a zero chance rolled a maximum");

		ServerPlayer archer = RpgTestSupport.player(helper);
		RpgTestSupport.setStat(archer, Stat.DEX, 200);
		int viaFair = RpgCombat.fairRoll(archer, new Scripted(0.24, 0.26, 0.10), new RpgDice.Dice(3, 6));
		helper.assertTrue(viaFair == 13, "the fair dice roller used DEX 200 and rolled " + viaFair);
		RpgTestSupport.setStat(archer, Stat.DEX, 0);
		helper.assertTrue(RpgCombat.fairRoll(archer, new Scripted(0.0), new RpgDice.Dice(3, 6)) == 3, "DEX 0 rolled a maximum");

		helper.succeed();
	}

	@GameTest
	public void magicDefense(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		RpgTestSupport.bare(player);
		RpgTestSupport.setStat(player, Stat.INT, 300);
		float factor = (float) (1.0 - 0.3898314385112098);
		float damage = 10.0F;
		float generic = RpgCombat.scaleIncoming(player, level.damageSources().generic(), damage);
		float magic = RpgCombat.scaleIncoming(player, level.damageSources().magic(), damage);
		float indirect = RpgCombat.scaleIncoming(player, level.damageSources().indirectMagic(player, player), damage);
		float tagged = RpgCombat.scaleIncoming(player, new DamageSource(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
			.getOrThrow(DamageTypes.SONIC_BOOM)), damage);
		float poison = RpgCombat.scaleIncoming(player, RpgDamageTypes.poison(player), damage);
		float wither = RpgCombat.scaleIncoming(player, level.damageSources().wither(), damage);
		float hurt = 0.0F;
		player.setHealth(player.getMaxHealth());
		float before = player.getHealth();
		player.hurtServer(level, level.damageSources().magic(), damage);
		hurt = before - player.getHealth();
		RpgTestSupport.result("magicDefense", "factor=" + factor + " generic=" + generic + " magic=" + magic + " indirect=" + indirect
			+ " tagged=" + tagged + " poison=" + poison + " wither=" + wither + " hurt=" + hurt);
		helper.assertTrue(near(generic, damage) && near(poison, damage) && near(wither, damage), "a non magic hit changed: " + generic + " " + poison + " " + wither);
		helper.assertTrue(near(magic, damage * factor) && near(indirect, damage * factor), "magic " + magic + " indirect " + indirect);
		helper.assertTrue(near(tagged, damage * factor), "a member of the magic tag was not reduced: " + tagged);
		helper.assertTrue(Math.abs(hurt - damage * factor * RpgScale.kf()) < 0.01F, "the real hit lost " + hurt);
		helper.succeed();
	}

	@GameTest
	public void critReduction(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RpgConfigStore.set(RpgConfigStore.parse("{\"mob_stats\":{\"melee\":{\"luk\":300}}}", message -> { }));
		try {
			ServerPlayer attacker = RpgTestSupport.player(helper);
			Zombie victim = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
			RpgTestSupport.bare(victim);
			victim.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0.0);
			victim.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(2000.0);
			int luk = StatBonuses.effective(victim, Stat.LUK);
			helper.assertTrue(luk == 300, "the victim has LUK " + luk);
			float guard = RpgTestSupport.mobGuard(1.0F);
			float k = RpgScale.kf();
			DamageSource melee = level.damageSources().playerAttack(attacker);
			float hit = 4.0F;
			RpgCombat.setDiceRoller((mob, random, dice) -> 0);
			float plain;
			float crit;
			float after;
			try {
				RpgCombat.setRoller((mob, random, chance) -> false);
				plain = lost(victim, () -> victim.hurtServer(level, melee, hit));
				RpgCombat.setRoller((mob, random, chance) -> true);
				crit = lost(victim, () -> victim.hurtServer(level, melee, hit));
				RpgCombat.setRoller((mob, random, chance) -> false);
				after = lost(victim, () -> victim.hurtServer(level, level.damageSources().generic(), 10.0F));
			} finally {
				RpgCombat.setRoller(null);
				RpgCombat.setDiceRoller(null);
			}
			float reduction = 1.0F - 0.5F * 300.0F / 600.0F;
			RpgTestSupport.result("critReduction", "plain=" + plain + " crit=" + crit + " after=" + after + " reduction=" + reduction);
			helper.assertTrue(Math.abs(plain - hit * guard * k) < 0.01F, "a plain hit lost " + plain);
			helper.assertTrue(Math.abs(crit - hit * 1.5F * reduction * guard * k) < 0.01F, "a critical hit lost " + crit + " expected " + hit * 1.5F * reduction * guard * k);
			helper.assertTrue(Math.abs(after - 10.0F * guard * k) < 0.01F, "the crit flag leaked into the next hit: lost " + after);
			helper.assertTrue(!RpgCombat.critPending(victim), "a crit flag stayed after the hurt call");
		} finally {
			RpgConfigStore.set(null);
		}
		helper.succeed();
	}

	private static float lost(final Zombie victim, final Runnable hurt) {
		victim.damageCooldownTime = 0;
		victim.setHealth(victim.getMaxHealth());
		float before = victim.getHealth();
		hurt.run();
		return before - victim.getHealth();
	}

	private static boolean near(final float actual, final float expected) {
		return Math.abs(actual - expected) < TOLERANCE * Math.max(1.0F, Math.abs(expected));
	}
}
