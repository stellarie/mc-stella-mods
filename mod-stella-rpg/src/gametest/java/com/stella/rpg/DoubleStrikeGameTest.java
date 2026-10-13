package com.stella.rpg;

import com.stella.rpg.job.DoubleStrike;
import com.stella.rpg.job.Job;
import com.stella.rpg.job.JobItems;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;

/**
 * The Fighter innate: about a quarter of the melee attacks that land hit again. Each hit rolls its own critical hit.
 * The second hit gives the target Nausea, and it never starts a third hit. A case that fixes a global roller runs in
 * one tick, so it cannot meet a neighbouring case; the rollers of the innate are fixed per attacker.
 */
public final class DoubleStrikeGameTest {
	private static final float RAW = 6.0F;
	private static final int SAMPLES = 2_000;
	private static final RpgCombat.Roller NEVER = (who, random, chance) -> false;
	private static final RpgCombat.Roller ALWAYS = (who, random, chance) -> true;

	@GameTest
	public void aboutAQuarterOfTheAttacksHitTwice(final GameTestHelper helper) {
		ServerPlayer fighter = InnateTestSupport.player(helper, Job.FIGHTER, 20);
		ServerPlayer archer = InnateTestSupport.player(helper, Job.ARCHER, 20);
		Zombie dummy = InnateTestSupport.dummy(helper, EntityTypes.ZOMBIE, 3, 2);
		RandomSource seeded = RandomSource.create(25L);
		DoubleStrike.setRoller(fighter.getUUID(), (who, random, chance) -> seeded.nextDouble() < chance);
		DoubleStrike.setRoller(archer.getUUID(), ALWAYS);
		int fighterRolls = 0;
		int archerRolls = 0;
		try {
			for (int i = 0; i < SAMPLES; i++) {
				int before = DoubleStrike.pending(fighter.getUUID());
				attack(helper, fighter, dummy);
				fighterRolls += DoubleStrike.pending(fighter.getUUID()) - before;
			}
			for (int i = 0; i < SAMPLES / 4; i++) {
				attack(helper, archer, dummy);
			}
			archerRolls = DoubleStrike.pending(archer.getUUID());
		} finally {
			DoubleStrike.setRoller(fighter.getUUID(), null);
			DoubleStrike.setRoller(archer.getUUID(), null);
			dummy.discard();
			InnateTestSupport.retire(fighter);
			InnateTestSupport.retire(archer);
		}
		double sigma = Math.sqrt(SAMPLES * DoubleStrike.CHANCE * (1.0 - DoubleStrike.CHANCE));
		RpgTestSupport.result("aboutAQuarterOfTheAttacksHitTwice",
			"fighter=" + fighterRolls + " archer=" + archerRolls + " of=" + SAMPLES + " chance=" + DoubleStrike.CHANCE);
		helper.assertTrue(DoubleStrike.CHANCE == 0.25, "the chance drifted from 25 percent to " + DoubleStrike.CHANCE);
		helper.assertTrue(Math.abs(fighterRolls - SAMPLES * DoubleStrike.CHANCE) <= 3.0 * sigma,
			"the fighter hit twice " + fighterRolls + " times in " + SAMPLES);
		helper.assertTrue(archerRolls == 0, "an archer whose every roll succeeds got " + archerRolls + " second hits");
		helper.succeed();
	}

	private static void attack(final GameTestHelper helper, final ServerPlayer attacker, final LivingEntity victim) {
		victim.damageCooldownTime = 0;
		victim.setHealth(victim.getMaxHealth());
		DamageSource source = helper.getLevel().damageSources().playerAttack(attacker);
		victim.hurtServer(helper.getLevel(), source, RAW);
	}

	/** The loss of health when one attack lands on a fresh dummy and no second hit follows. */
	private static double lossOfOneHit(final GameTestHelper helper, final ServerPlayer attacker, final int x, final boolean crit) {
		Zombie dummy = InnateTestSupport.dummy(helper, EntityTypes.ZOMBIE, x, 3);
		DoubleStrike.setRoller(attacker.getUUID(), NEVER);
		RpgCombat.setRoller((who, random, chance) -> crit);
		dummy.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(attacker), RAW);
		double lost = dummy.getMaxHealth() - dummy.getHealth();
		dummy.discard();
		return lost;
	}

	private static double lost(final LivingEntity victim) {
		return victim.getMaxHealth() - victim.getHealth();
	}

	@GameTest
	public void theSecondHitCopiesTheFirstGivesNauseaAndStopsThere(final GameTestHelper helper) {
		ServerPlayer fighter = InnateTestSupport.player(helper, Job.FIGHTER, 20);
		fighter.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(JobItems.KNIGHT_SWORD));
		AtomicInteger bleedRolls = new AtomicInteger();
		List<String> seen = new ArrayList<>();
		try {
			RpgCombat.setDiceRoller((who, random, dice) -> dice.max());
			JobItems.setRoller((who, random, chance) -> {
				bleedRolls.incrementAndGet();
				return false;
			});
			double one = lossOfOneHit(helper, fighter, 4, false);
			bleedRolls.set(0);
			Zombie victim = InnateTestSupport.dummy(helper, EntityTypes.ZOMBIE, 4, 2);
			DoubleStrike.setRecorder(fighter.getUUID(), (attacker, struck, landed) -> seen.add(struck.getUUID() + ":" + landed));
			DoubleStrike.setRoller(fighter.getUUID(), ALWAYS);
			RpgCombat.setRoller(NEVER);
			victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(fighter), RAW);
			double first = lost(victim);
			boolean nauseaAtFirst = victim.hasEffect(MobEffects.NAUSEA);
			int queued = DoubleStrike.pending(fighter.getUUID());
			DoubleStrike.settle(fighter);
			double both = lost(victim);
			MobEffectInstance nausea = victim.getEffect(MobEffects.NAUSEA);
			DoubleStrike.settle(fighter);
			double later = lost(victim);
			RpgTestSupport.result("theSecondHitCopiesTheFirstGivesNauseaAndStopsThere", "one=" + one + " first=" + first
				+ " both=" + both + " later=" + later + " nausea=" + (nausea == null ? -1 : nausea.getDuration()) + " seen=" + seen);
			helper.assertTrue(one > 0.0, "a single hit did no damage, so the copy proves nothing");
			helper.assertTrue(InnateTestSupport.near(first, one), "the first hit lost " + first + ", one hit loses " + one);
			helper.assertTrue(!nauseaAtFirst && queued == 1, "the first hit gave Nausea or queued " + queued + " hits: " + nauseaAtFirst);
			helper.assertTrue(InnateTestSupport.near(both, 2.0 * one), "two hits lost " + both + ", expected " + 2.0 * one);
			helper.assertTrue(DoubleStrike.NAUSEA_TICKS == 10, "Nausea drifted from 10 ticks to " + DoubleStrike.NAUSEA_TICKS);
			helper.assertTrue(nausea != null && nausea.getDuration() > 0 && nausea.getDuration() <= DoubleStrike.NAUSEA_TICKS,
				"the second hit gave Nausea " + (nausea == null ? "none" : nausea.getDuration() + " ticks"));
			helper.assertTrue(seen.equals(List.of(victim.getUUID() + ":true")), "the second hits that ran were " + seen);
			helper.assertTrue(bleedRolls.get() == 1, "the sword rolled its bleed " + bleedRolls.get() + " times for two hits");
			helper.assertTrue(InnateTestSupport.near(later, 2.0 * one) && DoubleStrike.pending(fighter.getUUID()) == 0,
				"a third hit followed: lost " + later);
			victim.discard();
		} finally {
			resetGlobals(fighter);
		}
		helper.succeed();
	}

	@GameTest
	public void bothHitsRollTheirOwnCrit(final GameTestHelper helper) {
		ServerPlayer fighter = InnateTestSupport.player(helper, Job.FIGHTER, 20);
		AtomicInteger rolls = new AtomicInteger();
		try {
			RpgCombat.setDiceRoller((who, random, dice) -> dice.max());
			double plain = lossOfOneHit(helper, fighter, 3, false);
			double crit = lossOfOneHit(helper, fighter, 5, true);
			DoubleStrike.setRoller(fighter.getUUID(), ALWAYS);
			// The first hit of the pair rolls no crit, and the second hit rolls one.
			RpgCombat.setRoller((who, random, chance) -> rolls.getAndIncrement() == 1);
			Zombie mixed = InnateTestSupport.dummy(helper, EntityTypes.ZOMBIE, 3, 2);
			mixed.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(fighter), RAW);
			DoubleStrike.settle(fighter);
			double mixedLoss = lost(mixed);
			RpgCombat.setRoller(ALWAYS);
			Zombie both = InnateTestSupport.dummy(helper, EntityTypes.ZOMBIE, 5, 2);
			both.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(fighter), RAW);
			DoubleStrike.settle(fighter);
			double bothLoss = lost(both);
			RpgTestSupport.result("bothHitsRollTheirOwnCrit",
				"plain=" + plain + " crit=" + crit + " mixed=" + mixedLoss + " both=" + bothLoss + " rolls=" + rolls.get());
			helper.assertTrue(crit > plain * 1.2, "a crit lost " + crit + " against a plain hit's " + plain + ", so the roll proves nothing");
			helper.assertTrue(InnateTestSupport.near(mixedLoss, plain + crit),
				"a plain hit then a crit lost " + mixedLoss + ", expected " + (plain + crit));
			helper.assertTrue(InnateTestSupport.near(bothLoss, 2.0 * crit), "two crits lost " + bothLoss + ", expected " + 2.0 * crit);
			mixed.discard();
			both.discard();
		} finally {
			resetGlobals(fighter);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void theSecondHitComesAfterTheDelayAndNoThirdFollows(final GameTestHelper helper) {
		ServerPlayer fighter = InnateTestSupport.player(helper, Job.FIGHTER, 20);
		Zombie victim = InnateTestSupport.dummy(helper, EntityTypes.ZOMBIE, 4, 2);
		List<Long> struck = new ArrayList<>();
		DoubleStrike.setRecorder(fighter.getUUID(), (attacker, hit, landed) -> struck.add(helper.getTick()));
		DoubleStrike.setRoller(fighter.getUUID(), ALWAYS);
		victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(fighter), RAW);
		double first = lost(victim);
		helper.assertTrue(first > 0.0 && DoubleStrike.pending(fighter.getUUID()) == 1 && !victim.hasEffect(MobEffects.NAUSEA),
			"the first hit lost " + first + " with " + DoubleStrike.pending(fighter.getUUID()) + " second hits waiting");
		long start = helper.getTick();
		helper.runAfterDelay(DoubleStrike.DELAY_TICKS - 1L, () -> helper.assertTrue(struck.isEmpty() && lost(victim) == first,
			"the second hit came before its delay: " + struck));
		helper.runAfterDelay(DoubleStrike.DELAY_TICKS + 2L, () -> {
			MobEffectInstance nausea = victim.getEffect(MobEffects.NAUSEA);
			RpgTestSupport.result("theSecondHitComesAfterTheDelayAndNoThirdFollows",
				"start=" + start + " struck=" + struck + " first=" + first + " both=" + lost(victim) + " nausea=" + (nausea == null ? -1 : nausea.getDuration()));
			helper.assertTrue(struck.size() == 1 && struck.getFirst() - start <= DoubleStrike.DELAY_TICKS + 1, "the second hit ran at " + struck);
			helper.assertTrue(lost(victim) > first, "the second hit did no damage");
			helper.assertTrue(nausea != null && nausea.getDuration() <= DoubleStrike.NAUSEA_TICKS, "Nausea after the second hit: " + nausea);
		});
		helper.runAfterDelay(DoubleStrike.DELAY_TICKS + 30L, () -> {
			helper.assertTrue(struck.size() == 1 && DoubleStrike.pending(fighter.getUUID()) == 0, "a third hit followed: " + struck);
			victim.discard();
			reset(fighter);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void theSecondHitStillComesAfterRespawn(final GameTestHelper helper) {
		ServerPlayer fighter = InnateTestSupport.player(helper, Job.FIGHTER, 45);
		UUID id = fighter.getUUID();
		ServerPlayer reborn = InnateTestSupport.respawn(helper, fighter);
		Zombie victim = InnateTestSupport.dummy(helper, EntityTypes.ZOMBIE, 3, 2);
		List<String> seen = new ArrayList<>();
		DoubleStrike.setRecorder(id, (attacker, hit, landed) -> seen.add(hit.getUUID() + ":" + landed));
		DoubleStrike.setRoller(id, ALWAYS);
		victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(reborn), RAW);
		helper.runAfterDelay(DoubleStrike.DELAY_TICKS + 4L, () -> {
			RpgTestSupport.result("theSecondHitStillComesAfterRespawn", "seen=" + seen);
			helper.assertTrue(seen.equals(List.of(victim.getUUID() + ":true")), "the second hits that ran were " + seen);
			victim.discard();
			reset(reborn);
			helper.succeed();
		});
	}

	private static void reset(final ServerPlayer fighter) {
		DoubleStrike.setRoller(fighter.getUUID(), null);
		DoubleStrike.setRecorder(fighter.getUUID(), null);
		InnateTestSupport.retire(fighter);
	}

	private static void resetGlobals(final ServerPlayer fighter) {
		RpgCombat.setRoller(null);
		RpgCombat.setDiceRoller(null);
		JobItems.setRoller(null);
		reset(fighter);
	}
}
