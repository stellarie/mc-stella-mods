package com.stella.spells;

import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The nine spell status effects. Every expected number is a literal from the task table, never the shipped
 * constant, so a wrong constant fails the test. A damage comparison uses two zombies in one test, so armor
 * and every health scale cancel.
 */
public class SpellStatusGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.status");
	private static final float EPSILON = 0.02F;
	// How long the stagger lane waits before it stuns: enough for a live zombie spawned two blocks up to land.
	private static final long SETTLE_TICKS = 14L;
	// The landing is polled for, never asserted at a fixed tick; this is only how long to wait before the first look.
	private static final long LANDING_GRACE_TICKS = 10L;
	// A late landing is waited out; one that never comes fails loudly here instead of timing the test out.
	private static final long LANDING_DEADLINE_TICKS = 90L;
	// The control zombie lands its first hit 50 to 70 ticks after it gets a target, so the window is 120 of them.
	private static final long CONTROL_WINDOW_TICKS = 120L;
	// Tests share one level and run side by side, so the difficulty is restored by the last live-zombie test to end.
	private static int fighters;
	private static Difficulty difficultyBefore = Difficulty.PEACEFUL;

	private static final class Stub implements RpgBridge.Stats {
		@Override
		public double power(final Player player) {
			return 1.0;
		}

		@Override
		public double cooldown(final Player player) {
			return 1.0;
		}
	}

	// Item 1.
	@GameTest(maxTicks = 40)
	public void frostbindStacksChillThenFreezes(final GameTestHelper helper) {
		try {
			RpgBridge.setStats(new Stub());
			ServerPlayer caster = aimedCaster(helper);
			Mob zombie = zombie(helper, 2, 5);
			for (int cast = 0; cast < 3; cast++) {
				apply(caster, "frostbind", zombie, null);
				MobEffectInstance chilled = zombie.getEffect(SpellStatus.CHILLED);
				helper.assertTrue(chilled != null && chilled.getAmplifier() == cast,
					"cast " + (cast + 1) + " left Chilled " + describe(chilled) + ", want amplifier " + cast);
				helper.assertTrue(!zombie.hasEffect(SpellStatus.FROZEN), "cast " + (cast + 1) + " froze the mob too early");
			}
			apply(caster, "frostbind", zombie, null);
			MobEffectInstance frozen = zombie.getEffect(SpellStatus.FROZEN);
			LOG.info("[SPELLTEST] class=SpellStatusGameTest test=frostbindStacksChillThenFreezes RESULT frozen={} chilled={}",
				describe(frozen), describe(zombie.getEffect(SpellStatus.CHILLED)));
			helper.assertTrue(!zombie.hasEffect(SpellStatus.CHILLED), "the fourth cast kept Chilled");
			helper.assertTrue(frozen != null && frozen.getDuration() == 60, "the fourth cast left Frozen " + describe(frozen) + ", want 60 ticks");
			helper.getLevel().getServer().getPlayerList().remove(caster);
		} finally {
			RpgBridge.setStats(null);
		}
		helper.succeed();
	}

	// Item 2. The window is long because an unfrozen zombie lands its first hit 50 to 70 ticks after it gets a target.
	@GameTest(maxTicks = 200)
	public void frozenMobCannotMoveOrAttack(final GameTestHelper helper) {
		arena(helper);
		fightingDifficulty(helper);
		// The structure is 9 by 9 by 9 with its floor at the bottom, so every lane sits inside it.
		Lane adjacent = lane(helper, 0, 3, 2.5);
		Lane distant = lane(helper, 2, 6, 0.5);
		Lane control = lane(helper, 4, 3, 2.5);
		// The harness only ticks entities inside this box; a lane outside it never lands and never fights.
		assertLanesInside(helper, adjacent, distant, control);
		final long[] controlFirstHit = {-1L};
		helper.onEachTick(() -> {
			if (controlFirstHit[0] < 0 && lost(control.player()) > 0.0F) {
				controlFirstHit[0] = helper.getTick();
			}
		});
		final boolean[] engaged = {false};
		helper.onEachTick(() -> {
			if (engaged[0] || helper.getTick() < LANDING_GRACE_TICKS) {
				return;
			}
			for (Lane lane : new Lane[] {adjacent, distant, control}) {
				if (!lane.player().isAlive()) {
					helper.fail("a lane player died while its zombie was still landing");
				}
			}
			if (helper.getTick() >= 2L * LANDING_GRACE_TICKS && helper.getTick() % 10L == 0L) {
				LOG.info("[SPELLTEST] class=SpellStatusGameTest test=frozenMobCannotMoveOrAttack WAIT tick={} adjacent={} distant={} control={}",
					helper.getTick(), state(adjacent), state(distant), state(control));
			}
			helper.assertTrue(helper.getTick() <= LANDING_DEADLINE_TICKS,
				"a lane zombie never landed: " + state(adjacent) + " " + state(distant) + " " + state(control));
			if (!landed(adjacent) || !landed(distant) || !landed(control)) {
				return;
			}
			SpellStatusApi.freeze(adjacent.zombie(), 200, null);
			SpellStatusApi.freeze(distant.zombie(), 200, null);
			helper.assertTrue(adjacent.zombie().hasEffect(SpellStatus.FROZEN), "the adjacent zombie is not Frozen");
			helper.assertTrue(distant.zombie().hasEffect(SpellStatus.FROZEN), "the distant zombie is not Frozen");
			long landedAt = helper.getTick();
			engaged[0] = true;
			engage(helper, adjacent, distant, control);
			LOG.info("[SPELLTEST] class=SpellStatusGameTest test=frozenMobCannotMoveOrAttack LANDED at={} difficulty={}",
				landedAt, helper.getLevel().getDifficulty());
			helper.runAfterDelay(CONTROL_WINDOW_TICKS, () -> {
				float adjacentLost = lost(adjacent.player());
				float distantLost = lost(distant.player());
				float controlLost = lost(control.player());
				double adjacentMoved = adjacent.zombie().position().distanceTo(adjacent.start());
				double distantMoved = distant.zombie().position().distanceTo(distant.start());
				LOG.info("[SPELLTEST] class=SpellStatusGameTest test=frozenMobCannotMoveOrAttack RESULT adjacentLost={} distantLost={} controlLost={} "
					+ "controlFirstHit={} adjacentMoved={} distantMoved={}", adjacentLost, distantLost, controlLost, controlFirstHit[0], adjacentMoved, distantMoved);
				helper.assertTrue(controlLost > 0.0F, "the unfrozen control never hurt its player, so the arena proves nothing");
				helper.assertTrue(adjacentLost == 0.0F, "the adjacent Frozen zombie dealt " + adjacentLost);
				helper.assertTrue(distantLost == 0.0F, "the distant Frozen zombie dealt " + distantLost);
				helper.assertTrue(adjacentMoved < 0.1, "the adjacent Frozen zombie moved " + adjacentMoved);
				helper.assertTrue(distantMoved < 0.1, "the distant Frozen zombie moved " + distantMoved);
				lanesOff(helper, adjacent, distant, control);
				helper.succeed();
			});
		});
	}

	// Item 3.
	@GameTest(maxTicks = 40)
	public void eliteHalvesFreezeAndStagger(final GameTestHelper helper) {
		arena(helper);
		Mob plain = zombie(helper, 2, 5);
		Mob elite = elite(zombie(helper, 3, 5));
		SpellStatusApi.freeze(plain, 100, null);
		SpellStatusApi.freeze(elite, 100, null);
		int plainFrozen = ticks(plain, SpellStatus.FROZEN);
		int eliteFrozen = ticks(elite, SpellStatus.FROZEN);
		plain.removeAllEffects();
		elite.removeAllEffects();
		SpellStatusApi.stagger(plain, 100, null);
		SpellStatusApi.stagger(elite, 100, null);
		int plainStaggered = ticks(plain, SpellStatus.STAGGERED);
		int eliteStaggered = ticks(elite, SpellStatus.STAGGERED);
		LOG.info("[SPELLTEST] class=SpellStatusGameTest test=eliteHalvesFreezeAndStagger RESULT plainFrozen={} eliteFrozen={} plainStaggered={} "
			+ "eliteStaggered={}", plainFrozen, eliteFrozen, plainStaggered, eliteStaggered);
		helper.assertTrue(plainFrozen == 100, "plain Frozen lasts " + plainFrozen + ", want 100");
		helper.assertTrue(eliteFrozen == 50, "elite Frozen lasts " + eliteFrozen + ", want 50");
		helper.assertTrue(plainStaggered == 100, "plain Staggered lasts " + plainStaggered + ", want 100");
		helper.assertTrue(eliteStaggered == 50, "elite Staggered lasts " + eliteStaggered + ", want 50");
		helper.succeed();
	}

	// Item 4.
	@GameTest(maxTicks = 40)
	public void playerHitShattersFrozen(final GameTestHelper helper) {
		arena(helper);
		ServerPlayer player = aimedCaster(helper);
		Mob frozen = zombie(helper, 2, 5);
		Mob plain = zombie(helper, 3, 5);
		SpellStatusApi.freeze(frozen, 100, null);
		float frozenHit = melee(helper, player, frozen, 4.0F);
		float plainHit = melee(helper, player, plain, 4.0F);
		LOG.info("[SPELLTEST] class=SpellStatusGameTest test=playerHitShattersFrozen RESULT frozenHit={} plainHit={} frozenAfter={}",
			frozenHit, plainHit, frozen.hasEffect(SpellStatus.FROZEN));
		helper.assertTrue(near(plainHit, 4.0F), "the plain hit dealt " + plainHit + ", want 4");
		helper.assertTrue(near(frozenHit, 6.0F), "the hit on Frozen dealt " + frozenHit + ", want 6");
		helper.assertTrue(!frozen.hasEffect(SpellStatus.FROZEN), "Frozen survived the shattering hit");
		helper.getLevel().getServer().getPlayerList().remove(player);
		helper.succeed();
	}

	// Item 4, failure mode: a spell does not shatter.
	@GameTest(maxTicks = 40)
	public void spellHitDoesNotShatterFrozen(final GameTestHelper helper) {
		arena(helper);
		ServerPlayer player = aimedCaster(helper);
		Mob frozen = zombie(helper, 2, 5);
		SpellStatusApi.freeze(frozen, 100, null);
		float before = frozen.getHealth();
		frozen.damageCooldownTime = 0;
		frozen.hurtServer(helper.getLevel(), SpellCasting.spellSource(player), 4.0F);
		float dealt = before - frozen.getHealth();
		LOG.info("[SPELLTEST] class=SpellStatusGameTest test=spellHitDoesNotShatterFrozen RESULT dealt={} stillFrozen={}", dealt,
			frozen.hasEffect(SpellStatus.FROZEN));
		helper.assertTrue(near(dealt, 4.0F), "a spell hit on Frozen dealt " + dealt + ", want 4");
		helper.assertTrue(frozen.hasEffect(SpellStatus.FROZEN), "a spell hit ended Frozen");
		helper.getLevel().getServer().getPlayerList().remove(player);
		helper.succeed();
	}

	// Item 5.
	@GameTest(maxTicks = 40)
	public void staticLashDealsMoreToSoaked(final GameTestHelper helper) {
		try {
			RpgBridge.setStats(new Stub());
			ServerPlayer caster = aimedCaster(helper);
			Mob soaked = zombie(helper, 0, 5);
			Mob dry = zombie(helper, 5, 5);
			SpellStatusApi.soak(soaked, 200, null);
			float soakedLoss = lossFrom(soaked, () -> apply(caster, "static_lash", soaked, null));
			float dryLoss = lossFrom(dry, () -> apply(caster, "static_lash", dry, null));
			LOG.info("[SPELLTEST] class=SpellStatusGameTest test=staticLashDealsMoreToSoaked RESULT soakedLoss={} dryLoss={}", soakedLoss, dryLoss);
			helper.assertTrue(dryLoss > 0.0F, "Static Lash dealt nothing to the dry zombie");
			helper.assertTrue(near(soakedLoss, dryLoss * 1.5F), "Soaked took " + soakedLoss + ", want 1.5 times " + dryLoss);
			helper.getLevel().getServer().getPlayerList().remove(caster);
		} finally {
			RpgBridge.setStats(null);
		}
		helper.succeed();
	}

	// Item 5.
	@GameTest(maxTicks = 40)
	public void tidalPushSoaks(final GameTestHelper helper) {
		try {
			RpgBridge.setStats(new Stub());
			ServerPlayer caster = aimedCaster(helper);
			Mob zombie = zombie(helper, 2, 5);
			apply(caster, "tidal_push", zombie, null);
			MobEffectInstance soaked = zombie.getEffect(SpellStatus.SOAKED);
			LOG.info("[SPELLTEST] class=SpellStatusGameTest test=tidalPushSoaks RESULT soaked={}", describe(soaked));
			helper.assertTrue(soaked != null && soaked.getDuration() == 200, "Tidal Push left Soaked " + describe(soaked) + ", want 200 ticks");
			helper.getLevel().getServer().getPlayerList().remove(caster);
		} finally {
			RpgBridge.setStats(null);
		}
		helper.succeed();
	}

	// Item 6.
	@GameTest(maxTicks = 40)
	public void emberBrandDriesSoakedMob(final GameTestHelper helper) {
		try {
			RpgBridge.setStats(new Stub());
			ServerPlayer caster = aimedCaster(helper);
			Mob soaked = zombie(helper, 0, 5);
			Mob dry = zombie(helper, 5, 5);
			SpellStatusApi.soak(soaked, 200, null);
			apply(caster, "ember_brand", soaked, null);
			apply(caster, "ember_brand", dry, null);
			LOG.info("[SPELLTEST] class=SpellStatusGameTest test=emberBrandDriesSoakedMob RESULT soakedStillSoaked={} soakedFire={} dryFire={} "
				+ "dryScorched={}", soaked.hasEffect(SpellStatus.SOAKED), soaked.getRemainingFireTicks(), dry.getRemainingFireTicks(),
				describe(dry.getEffect(SpellStatus.SCORCHED)));
			helper.assertTrue(dry.getRemainingFireTicks() == 600, "the dry zombie burns " + dry.getRemainingFireTicks() + ", want 600");
			helper.assertTrue(ticks(dry, SpellStatus.SCORCHED) == 600, "the dry zombie is Scorched for " + ticks(dry, SpellStatus.SCORCHED) + ", want 600");
			helper.assertTrue(!soaked.hasEffect(SpellStatus.SOAKED), "Ember Brand left the zombie Soaked");
			helper.assertTrue(soaked.getRemainingFireTicks() <= 0, "Ember Brand set a Soaked zombie on fire: " + soaked.getRemainingFireTicks());
			helper.getLevel().getServer().getPlayerList().remove(caster);
		} finally {
			RpgBridge.setStats(null);
		}
		helper.succeed();
	}

	// Item 7.
	@GameTest(maxTicks = 40)
	public void scorchedHalvesHealing(final GameTestHelper helper) {
		arena(helper);
		Mob scorched = zombie(helper, 2, 5);
		Mob healthy = zombie(helper, 3, 5);
		scorched.setHealth(500.0F);
		healthy.setHealth(500.0F);
		SpellStatusApi.scorch(scorched, 200, null);
		scorched.heal(4.0F);
		healthy.heal(4.0F);
		float scorchedGain = scorched.getHealth() - 500.0F;
		float healthyGain = healthy.getHealth() - 500.0F;
		LOG.info("[SPELLTEST] class=SpellStatusGameTest test=scorchedHalvesHealing RESULT scorchedGain={} healthyGain={}", scorchedGain, healthyGain);
		helper.assertTrue(near(healthyGain, 4.0F), "the unscorched mob healed " + healthyGain + ", want 4");
		helper.assertTrue(near(scorchedGain, 2.0F), "the Scorched mob healed " + scorchedGain + ", want 2");
		helper.succeed();
	}

	// Item 8.
	@GameTest(maxTicks = 40)
	public void brittleBoostsNextHitOnly(final GameTestHelper helper) {
		arena(helper);
		ServerPlayer player = aimedCaster(helper);
		Mob brittle = zombie(helper, 2, 5);
		Mob plain = zombie(helper, 3, 5);
		SpellStatusApi.brittle(brittle, 200, null);
		float first = melee(helper, player, brittle, 4.0F);
		boolean endedAfterFirst = !brittle.hasEffect(SpellStatus.BRITTLE);
		float second = melee(helper, player, brittle, 4.0F);
		float plainHit = melee(helper, player, plain, 4.0F);
		LOG.info("[SPELLTEST] class=SpellStatusGameTest test=brittleBoostsNextHitOnly RESULT first={} second={} plain={} ended={}", first, second,
			plainHit, endedAfterFirst);
		helper.assertTrue(near(plainHit, 4.0F), "the plain hit dealt " + plainHit + ", want 4");
		helper.assertTrue(near(first, 6.0F), "the first hit on Brittle dealt " + first + ", want 6");
		helper.assertTrue(endedAfterFirst, "Brittle survived the first hit");
		helper.assertTrue(near(second, 4.0F), "the second hit dealt " + second + ", want 4");
		helper.getLevel().getServer().getPlayerList().remove(player);
		helper.succeed();
	}

	// Item 9.
	@GameTest(maxTicks = 40)
	public void markedBoostsEveryHitAndGlows(final GameTestHelper helper) {
		arena(helper);
		ServerPlayer player = aimedCaster(helper);
		Mob marked = zombie(helper, 2, 5);
		Mob plain = zombie(helper, 3, 5);
		SpellStatusApi.mark(marked, 200, null);
		float first = melee(helper, player, marked, 4.0F);
		float second = melee(helper, player, marked, 4.0F);
		float plainHit = melee(helper, player, plain, 4.0F);
		LOG.info("[SPELLTEST] class=SpellStatusGameTest test=markedBoostsEveryHitAndGlows RESULT first={} second={} plain={} glowing={}", first, second,
			plainHit, marked.hasEffect(MobEffects.GLOWING));
		helper.assertTrue(near(plainHit, 4.0F), "the plain hit dealt " + plainHit + ", want 4");
		helper.assertTrue(near(first, 4.8F), "the first hit on Marked dealt " + first + ", want 4.8");
		helper.assertTrue(near(second, 4.8F), "the second hit on Marked dealt " + second + ", want 4.8");
		helper.assertTrue(marked.hasEffect(SpellStatus.MARKED), "Marked ended after a hit");
		helper.assertTrue(ticks(marked, MobEffects.GLOWING) > 0, "the Marked mob does not glow");
		helper.getLevel().getServer().getPlayerList().remove(player);
		helper.succeed();
	}

	// Item 9.
	@GameTest(maxTicks = 40)
	public void blindingFlashMarks(final GameTestHelper helper) {
		try {
			RpgBridge.setStats(new Stub());
			ServerPlayer caster = aimedCaster(helper);
			Mob zombie = zombie(helper, 2, 5);
			apply(caster, "blinding_flash", zombie, null);
			LOG.info("[SPELLTEST] class=SpellStatusGameTest test=blindingFlashMarks RESULT marked={} glowing={}", describe(zombie.getEffect(SpellStatus.MARKED)),
				describe(zombie.getEffect(MobEffects.GLOWING)));
			helper.assertTrue(ticks(zombie, SpellStatus.MARKED) == 600, "Blinding Flash left Marked for " + ticks(zombie, SpellStatus.MARKED) + ", want 600");
			helper.assertTrue(ticks(zombie, MobEffects.GLOWING) == 600, "Blinding Flash left Glowing for " + ticks(zombie, MobEffects.GLOWING) + ", want 600");
			helper.getLevel().getServer().getPlayerList().remove(caster);
		} finally {
			RpgBridge.setStats(null);
		}
		helper.succeed();
	}

	// Item 10. The first stretch outlasts an unstunned zombie's 50 to 70 tick start-up, so a missing stun shows as damage.
	@GameTest(maxTicks = 250)
	public void staggerStopsAttackThenEnds(final GameTestHelper helper) {
		arena(helper);
		fightingDifficulty(helper);
		Lane lane = lane(helper, 2, 3, 2.5);
		helper.runAfterDelay(SETTLE_TICKS, () -> {
			SpellStatusApi.stagger(lane.zombie(), 100, null);
			helper.assertTrue(lane.zombie().hasEffect(SpellStatus.STAGGERED), "the zombie is not Staggered");
			engage(helper, lane);
		});
		helper.runAfterDelay(SETTLE_TICKS + 90L, () -> {
			float lostWhileStaggered = lost(lane.player());
			LOG.info("[SPELLTEST] class=SpellStatusGameTest test=staggerStopsAttackThenEnds RESULT lostWhileStaggered={} staggered={}", lostWhileStaggered,
				lane.zombie().hasEffect(SpellStatus.STAGGERED));
			helper.assertTrue(lane.zombie().hasEffect(SpellStatus.STAGGERED), "Staggered ended before its 100 ticks");
			helper.assertTrue(lostWhileStaggered == 0.0F, "the Staggered zombie dealt " + lostWhileStaggered);
		});
		helper.runAfterDelay(SETTLE_TICKS + 210L, () -> {
			float lostAfter = lost(lane.player());
			LOG.info("[SPELLTEST] class=SpellStatusGameTest test=staggerStopsAttackThenEnds RESULT lostAfter={} staggered={}", lostAfter,
				lane.zombie().hasEffect(SpellStatus.STAGGERED));
			helper.assertTrue(!lane.zombie().hasEffect(SpellStatus.STAGGERED), "Staggered never ended");
			helper.assertTrue(lostAfter > 0.0F, "the zombie never attacked after Staggered ended");
			lanesOff(helper, lane);
			helper.succeed();
		});
	}

	// Item 10, the stun sets and clears NoAI on a mob that had it off.
	@GameTest(maxTicks = 60)
	public void staggerSetsAndClearsNoAi(final GameTestHelper helper) {
		arena(helper);
		Mob zombie = zombie(helper, 2, 5);
		helper.assertTrue(!zombie.isNoAi(), "the test zombie starts with NoAI");
		SpellStatusApi.stagger(zombie, 20, null);
		helper.assertTrue(zombie.isNoAi(), "Staggered did not set NoAI");
		helper.assertTrue(zombie.entityTags().contains(SpellStatus.STUN_TAG), "Staggered did not tag the mob");
		helper.runAfterDelay(30L, () -> {
			LOG.info("[SPELLTEST] class=SpellStatusGameTest test=staggerSetsAndClearsNoAi RESULT noAi={} tagged={} staggered={}", zombie.isNoAi(),
				zombie.entityTags().contains(SpellStatus.STUN_TAG), zombie.hasEffect(SpellStatus.STAGGERED));
			helper.assertTrue(!zombie.hasEffect(SpellStatus.STAGGERED), "Staggered never ended");
			helper.assertTrue(!zombie.isNoAi(), "NoAI stayed after Staggered ended");
			helper.assertTrue(!zombie.entityTags().contains(SpellStatus.STUN_TAG), "the stun tag stayed after Staggered ended");
			helper.succeed();
		});
	}

	// Item 11.
	@GameTest(maxTicks = 60)
	public void staggerKeepsPreexistingNoAi(final GameTestHelper helper) {
		arena(helper);
		Mob zombie = zombie(helper, 2, 5);
		zombie.setNoAi(true);
		SpellStatusApi.stagger(zombie, 20, null);
		helper.assertTrue(ticks(zombie, SpellStatus.STAGGERED) == 20, "Staggered was not applied for 20 ticks");
		helper.runAfterDelay(30L, () -> {
			LOG.info("[SPELLTEST] class=SpellStatusGameTest test=staggerKeepsPreexistingNoAi RESULT noAi={} tagged={} staggered={}", zombie.isNoAi(),
				zombie.entityTags().contains(SpellStatus.STUN_TAG), zombie.hasEffect(SpellStatus.STAGGERED));
			helper.assertTrue(!zombie.hasEffect(SpellStatus.STAGGERED), "Staggered never ended");
			helper.assertTrue(zombie.isNoAi(), "Staggered woke a mob that had NoAI before it");
			helper.assertTrue(!zombie.entityTags().contains(SpellStatus.STUN_TAG), "Staggered tagged a mob that already had NoAI");
			helper.succeed();
		});
	}

	// Item 12.
	@GameTest(maxTicks = 60)
	public void bleedingLosesHealthPerSecond(final GameTestHelper helper) {
		arena(helper);
		Mob low = zombie(helper, 2, 5);
		Mob high = zombie(helper, 3, 5);
		low.setHealth(100.0F);
		high.setHealth(100.0F);
		SpellStatusApi.bleed(low, 200, 1, null);
		SpellStatusApi.bleed(high, 200, 1, null);
		SpellStatusApi.bleed(high, 200, 1, null);
		SpellStatusApi.bleed(high, 200, 1, null);
		helper.assertTrue(amplifier(low, SpellStatus.BLEEDING) == 0, "one bleed left amplifier " + amplifier(low, SpellStatus.BLEEDING) + ", want 0");
		helper.assertTrue(amplifier(high, SpellStatus.BLEEDING) == 1, "three bleeds under a cap of 1 left amplifier " + amplifier(high, SpellStatus.BLEEDING));
		helper.runAfterDelay(10L, () -> helper.assertTrue(low.getHealth() == 100.0F && high.getHealth() == 100.0F,
			"Bleeding hit before its first second: " + low.getHealth() + " and " + high.getHealth()));
		helper.runAfterDelay(30L, () -> {
			float lowLost = 100.0F - low.getHealth();
			float highLost = 100.0F - high.getHealth();
			LOG.info("[SPELLTEST] class=SpellStatusGameTest test=bleedingLosesHealthPerSecond RESULT lowLost={} highLost={}", lowLost, highLost);
			helper.assertTrue(near(lowLost, 2.0F), "Bleeding amplifier 0 lost " + lowLost + ", want 2");
			helper.assertTrue(near(highLost, 4.0F), "Bleeding amplifier 1 lost " + highLost + ", want 4");
			helper.succeed();
		});
	}

	// Item 13.
	@GameTest(maxTicks = 40)
	public void arcaneWardAbsorbsBeforeHealth(final GameTestHelper helper) {
		arena(helper);
		Mob zombie = zombie(helper, 2, 5);
		SpellStatusApi.ward(zombie, 6.0F, 200);
		helper.assertTrue(zombie.hasEffect(SpellStatus.ARCANE_WARD), "the ward effect is missing");
		DamageSource source = helper.getLevel().damageSources().generic();
		float first = lossFrom(zombie, () -> {
			zombie.damageCooldownTime = 0;
			zombie.hurtServer(helper.getLevel(), source, 4.0F);
		});
		boolean wardHeld = zombie.hasEffect(SpellStatus.ARCANE_WARD);
		float second = lossFrom(zombie, () -> {
			zombie.damageCooldownTime = 0;
			zombie.hurtServer(helper.getLevel(), source, 4.0F);
		});
		LOG.info("[SPELLTEST] class=SpellStatusGameTest test=arcaneWardAbsorbsBeforeHealth RESULT first={} second={} wardHeld={} wardAfter={}", first,
			second, wardHeld, zombie.hasEffect(SpellStatus.ARCANE_WARD));
		helper.assertTrue(first == 0.0F, "the first hit of 4 cost " + first + " health, want 0");
		helper.assertTrue(wardHeld, "the ward ended after the first hit");
		helper.assertTrue(near(second, 2.0F), "the second hit of 4 cost " + second + " health, want 2");
		helper.assertTrue(!zombie.hasEffect(SpellStatus.ARCANE_WARD), "the ward outlived its pool");
		helper.succeed();
	}

	// Stun on a player is the speed modifier only.
	@GameTest(maxTicks = 40)
	public void stunOnPlayerIsSpeedOnly(final GameTestHelper helper) {
		arena(helper);
		ServerPlayer player = aimedCaster(helper);
		double before = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
		SpellStatusApi.freeze(player, 100, null);
		double frozen = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
		LOG.info("[SPELLTEST] class=SpellStatusGameTest test=stunOnPlayerIsSpeedOnly RESULT before={} frozen={}", before, frozen);
		helper.assertTrue(before > 0.0, "the player has no base speed");
		helper.assertTrue(frozen == 0.0, "Frozen left the player speed at " + frozen);
		helper.assertTrue(player.hasEffect(SpellStatus.FROZEN), "the player is not Frozen");
		helper.getLevel().getServer().getPlayerList().remove(player);
		helper.succeed();
	}

	// A save made mid-stun: tag and NoAI with no effect left.
	@GameTest(maxTicks = 40)
	public void loadRecoversStrandedNoAi(final GameTestHelper helper) {
		arena(helper);
		Mob stranded = zombie(helper, 2, 5);
		stranded.setNoAi(true);
		stranded.addTag(SpellStatus.STUN_TAG);
		Mob natural = zombie(helper, 3, 5);
		natural.setNoAi(true);
		Mob stunned = zombie(helper, 4, 5);
		SpellStatusApi.stagger(stunned, 100, null);
		ServerEntityEvents.ENTITY_LOAD.invoker().onLoad(stranded, helper.getLevel());
		ServerEntityEvents.ENTITY_LOAD.invoker().onLoad(natural, helper.getLevel());
		ServerEntityEvents.ENTITY_LOAD.invoker().onLoad(stunned, helper.getLevel());
		LOG.info("[SPELLTEST] class=SpellStatusGameTest test=loadRecoversStrandedNoAi RESULT strandedNoAi={} strandedTag={} naturalNoAi={} stunnedNoAi={}",
			stranded.isNoAi(), stranded.entityTags().contains(SpellStatus.STUN_TAG), natural.isNoAi(), stunned.isNoAi());
		helper.assertTrue(!stranded.isNoAi(), "the stranded mob kept NoAI after load");
		helper.assertTrue(!stranded.entityTags().contains(SpellStatus.STUN_TAG), "the stranded mob kept the stun tag after load");
		helper.assertTrue(natural.isNoAi(), "load woke a mob whose NoAI the stun never set");
		helper.assertTrue(stunned.isNoAi(), "load woke a mob that is still Staggered");
		helper.succeed();
	}

	// A peaceful level drops every player from a zombie's canAttack, so a lane needs a fighting difficulty.
	private static void fightingDifficulty(final GameTestHelper helper) {
		if (fighters++ == 0) {
			difficultyBefore = helper.getLevel().getDifficulty();
		}
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		helper.runBeforeTestEnd(() -> {
			if (--fighters == 0) {
				helper.getLevel().getServer().setDifficulty(difficultyBefore, true);
			}
		});
	}

	private static final class Lane {
		private final ServerPlayer player;
		private final Mob zombie;
		private Vec3 start;

		Lane(final ServerPlayer player, final Mob zombie) {
			this.player = player;
			this.zombie = zombie;
		}

		ServerPlayer player() {
			return this.player;
		}

		Mob zombie() {
			return this.zombie;
		}

		Vec3 start() {
			return this.start;
		}
	}

	private static void arena(final GameTestHelper helper) {
		helper.setTime(18_000L);
		for (int x = 0; x <= 5; x++) {
			for (int z = 0; z <= 12; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
				for (int y = 1; y <= 4; y++) {
					helper.setBlock(x, y, z, Blocks.AIR);
				}
			}
		}
	}

	// A durable player and a live zombie dropped two blocks onto the floor; the landing is polled, then engage() lets it fight.
	// The player stands the given blocks into the arena along z, so a distant lane has to walk before it can swing.
	private static Lane lane(final GameTestHelper helper, final int x, final int zombieZ, final double playerZ) {
		// A survival player a zombie may target; the in-level mock player counts as creative and is never a valid target.
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player,
			CommonListenerCookie.createInitial(player.getGameProfile(), false));
		player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		player.setHealth(1000.0F);
		Vec3 at = helper.absoluteVec(new Vec3(x + 0.5, 1.0, playerZ));
		player.setPos(at.x, at.y, at.z);
		Mob zombie = helper.spawnMob(EntityTypes.ZOMBIE, x, 3, zombieZ).spawn();
		return new Lane(player, zombie);
	}

	/** Every lane sits inside the box the harness ticks entities in, or its zombie never lands and never fights. */
	private static void assertLanesInside(final GameTestHelper helper, final Lane... lanes) {
		AABB inside = helper.getBounds();
		for (Lane lane : lanes) {
			Vec3 player = lane.player().position();
			Vec3 zombie = lane.zombie().position();
			helper.assertTrue(inside.contains(player) && inside.contains(zombie),
				"a lane sits outside the ticking box " + inside + ": player=" + player + " zombie=" + zombie);
		}
	}

	/** True once the lane's zombie has landed and its player is still a live target, so the lane can fight. */
	private static boolean landed(final Lane lane) {
		return lane.zombie().onGround() && lane.player().isAlive() && lane.zombie().isAlive();
	}

	private static void engage(final GameTestHelper helper, final Lane... lanes) {
		for (Lane lane : lanes) {
			helper.assertTrue(lane.zombie().onGround(), "a lane zombie has not landed yet");
			lane.start = lane.zombie().position();
			lane.zombie().setTarget(lane.player());
		}
	}

	private static void lanesOff(final GameTestHelper helper, final Lane... lanes) {
		for (Lane lane : lanes) {
			lane.zombie().discard();
			helper.getLevel().getServer().getPlayerList().remove(lane.player());
		}
	}

	private static float lost(final ServerPlayer player) {
		return player.getMaxHealth() - player.getHealth();
	}

	/** One lane as it stands now: what it cost its player, where its zombie is, and whether it is still hunting. */
	private static String state(final Lane lane) {
		return "{lost=" + lost(lane.player()) + " pos=" + lane.zombie().position() + " onGround=" + lane.zombie().onGround()
			+ " target=" + (lane.zombie().getTarget() == lane.player()) + " targetAlive=" + lane.player().isAlive()
			+ " zombieAlive=" + lane.zombie().isAlive() + " noAi=" + lane.zombie().isNoAi() + "}";
	}

	private static ServerPlayer aimedCaster(final GameTestHelper helper) {
		arena(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 1.5));
		player.setPos(at.x, at.y, at.z);
		player.setYRot(0.0F);
		player.setXRot(0.0F);
		helper.setBlock(2, 1, 8, Blocks.STONE);
		player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 1, 8))));
		return player;
	}

	private static Mob zombie(final GameTestHelper helper, final int x, final int z) {
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, x, 1, z).withNoFreeWill().spawn();
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		mob.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		mob.setHealth(1000.0F);
		return mob;
	}

	// The mobs mod marks an elite with this scale modifier; the stand-in is the one RpgDurationsGameTest uses.
	private static Mob elite(final Mob mob) {
		mob.getAttribute(Attributes.SCALE).addPermanentModifier(new AttributeModifier(
			Identifier.fromNamespaceAndPath("mod-stella-mobs", "elite_scale"), 0.4, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		return mob;
	}

	// A melee hit by the player, as a plain player attack, with the hurt cooldown cleared so hits do not blur.
	private static float melee(final GameTestHelper helper, final ServerPlayer player, final LivingEntity victim, final float amount) {
		return lossFrom(victim, () -> {
			victim.damageCooldownTime = 0;
			victim.hurtServer(helper.getLevel(), player.damageSources().playerAttack(player), amount);
		});
	}

	private static float lossFrom(final LivingEntity victim, final Runnable action) {
		float before = victim.getHealth();
		action.run();
		return before - victim.getHealth();
	}

	private static boolean near(final float actual, final float expected) {
		return Math.abs(actual - expected) < EPSILON;
	}

	private static int ticks(final LivingEntity entity, final Holder<MobEffect> effect) {
		MobEffectInstance instance = entity.getEffect(effect);
		return instance == null ? -1 : instance.getDuration();
	}

	private static int amplifier(final LivingEntity entity, final Holder<MobEffect> effect) {
		MobEffectInstance instance = entity.getEffect(effect);
		return instance == null ? -1 : instance.getAmplifier();
	}

	private static String describe(final MobEffectInstance instance) {
		return instance == null ? "none" : "amp " + instance.getAmplifier() + " for " + instance.getDuration();
	}

	// The cast's own effect without the book's cooldown, for a second cast of one spell.
	private static void apply(final ServerPlayer caster, final String spell, final Mob target, final Vec3 point) {
		SpellbookItem book = (SpellbookItem) BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, spell));
		ItemStack stack = book.getDefaultInstance();
		SpellModifiers.CastModifiers modifiers = RpgBridge.grow(caster, spell,
			SpellModifiers.resolve(stack, ItemStack.EMPTY, ItemStack.EMPTY));
		book.definition().effect().apply(caster, target, point, modifiers);
	}
}
