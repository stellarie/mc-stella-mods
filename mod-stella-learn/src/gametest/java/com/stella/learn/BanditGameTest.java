package com.stella.learn;

import com.mojang.serialization.DataResult;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/** The learner picks the tactic that has worked, tries the ones it has not, and follows a player who changes their ways. */
public class BanditGameTest {
	private static final String ZOMBIE = "minecraft:zombie";
	private static final List<Tactic> SWING = List.of(Tactic.SHIELD, Tactic.BACK_OFF, Tactic.TRADE);
	private static final double ALPHA = LearnConfig.DEFAULT_RL_ALPHA;
	private static final double FLOOR = LearnConfig.DEFAULT_RL_EPSILON;
	private static final double TOLERANCE = 1.0e-9;

	private static Policy trained(final Tactic tactic, final double value, final int tries, final Policy from) {
		Policy policy = from;
		// One update at alpha 1 sets the value, and the rest add tries at the same value.
		for (int i = 0; i < tries; i++) {
			policy = policy.with(Policy.key(ZOMBIE, Situation.SWING, tactic), value, i == 0 ? 1.0 : ALPHA);
		}

		return policy;
	}

	@GameTest
	public void everyTacticIsTriedBeforeAnyIsRepeated(final GameTestHelper helper) {
		Policy policy = Policy.EMPTY;
		RandomSource random = RandomSource.create(7L);
		Set<Tactic> tried = EnumSet.noneOf(Tactic.class);
		for (int i = 0; i < SWING.size(); i++) {
			Bandit.Choice choice = Bandit.choose(policy, ZOMBIE, Situation.SWING, SWING, 0.0, random);
			helper.assertTrue(choice.why().equals("untried"), "choice " + i + " was " + choice.why());
			tried.add(choice.tactic());
			policy = policy.with(Policy.key(ZOMBIE, Situation.SWING, choice.tactic()), 0.0, ALPHA);
		}

		StellaLearn.LOGGER.info("[BEHAVIOR] test=everyTacticIsTriedBeforeAnyIsRepeated RESULT tried={}", tried);
		helper.assertTrue(tried.size() == SWING.size(), "only " + tried + " were tried");
		helper.succeed();
	}

	@GameTest
	public void theBestKnownTacticIsChosenOnceAllAreKnown(final GameTestHelper helper) {
		Policy policy = trained(Tactic.SHIELD, 1.0, 5, trained(Tactic.BACK_OFF, 0.2, 5, trained(Tactic.TRADE, -0.5, 5, Policy.EMPTY)));
		Bandit.Choice choice = Bandit.choose(policy, ZOMBIE, Situation.SWING, SWING, 0.0, RandomSource.create(1L));
		StellaLearn.LOGGER.info("[BEHAVIOR] test=theBestKnownTacticIsChosenOnceAllAreKnown RESULT choice={}", choice);
		helper.assertTrue(choice.tactic() == Tactic.SHIELD && choice.why().equals("greedy"), "chose " + choice);
		helper.succeed();
	}

	@GameTest
	public void anUntriedTacticIsTriedEvenWhenAnotherIsKnownGood(final GameTestHelper helper) {
		Policy policy = trained(Tactic.SHIELD, 1.0, 5, Policy.EMPTY);
		Bandit.Choice choice = Bandit.choose(policy, ZOMBIE, Situation.SWING, SWING, 0.0, RandomSource.create(1L));
		StellaLearn.LOGGER.info("[BEHAVIOR] test=anUntriedTacticIsTriedEvenWhenAnotherIsKnownGood RESULT choice={}", choice);
		helper.assertTrue(choice.tactic() != Tactic.SHIELD && choice.why().equals("untried"), "chose " + choice);
		helper.succeed();
	}

	@GameTest
	public void explorationCanPickAnyOpenTactic(final GameTestHelper helper) {
		Policy policy = trained(Tactic.SHIELD, 1.0, 5, trained(Tactic.BACK_OFF, 0.2, 5, trained(Tactic.TRADE, -0.5, 5, Policy.EMPTY)));
		RandomSource random = RandomSource.create(11L);
		Set<Tactic> seen = EnumSet.noneOf(Tactic.class);
		for (int i = 0; i < 300; i++) {
			Bandit.Choice choice = Bandit.choose(policy, ZOMBIE, Situation.SWING, SWING, 1.0, random);
			helper.assertTrue(choice.why().equals("explore"), "a fully exploring choice was " + choice.why());
			seen.add(choice.tactic());
		}

		StellaLearn.LOGGER.info("[BEHAVIOR] test=explorationCanPickAnyOpenTactic RESULT seen={}", seen);
		helper.assertTrue(seen.containsAll(SWING), "exploration only ever picked " + seen);
		helper.succeed();
	}

	@GameTest
	public void explorationShrinksWithExperienceToItsFloor(final GameTestHelper helper) {
		double none = Bandit.epsilon(0, FLOOR);
		double some = Bandit.epsilon(60, FLOOR);
		double lots = Bandit.epsilon(100_000, FLOOR);
		StellaLearn.LOGGER.info("[BEHAVIOR] test=explorationShrinksWithExperienceToItsFloor RESULT none={} some={} lots={}", none, some, lots);
		helper.assertTrue(Math.abs(none - Bandit.START_EPSILON) < TOLERANCE, "a fresh mob explored " + none);
		helper.assertTrue(none > some && some > lots, "exploration did not shrink: " + none + " " + some + " " + lots);
		helper.assertTrue(lots >= FLOOR && lots < FLOOR + 1.0e-3, "exploration ended at " + lots + ", not the floor " + FLOOR);
		helper.succeed();
	}

	@GameTest
	public void anOutcomeMovesTheValueTowardIt(final GameTestHelper helper) {
		String key = Policy.key(ZOMBIE, Situation.SWING, Tactic.SHIELD);
		Policy once = Policy.EMPTY.with(key, 1.0, ALPHA);
		Policy twice = once.with(key, 1.0, ALPHA);
		StellaLearn.LOGGER.info("[BEHAVIOR] test=anOutcomeMovesTheValueTowardIt RESULT once={} twice={}", once.get(key), twice.get(key));
		helper.assertTrue(Math.abs(once.get(key).value() - ALPHA) < TOLERANCE && once.get(key).tries() == 1, "after one outcome " + once.get(key));
		helper.assertTrue(Math.abs(twice.get(key).value() - (ALPHA + ALPHA * (1.0 - ALPHA))) < TOLERANCE && twice.get(key).tries() == 2, "after two outcomes " + twice.get(key));
		helper.succeed();
	}

	@GameTest
	public void valuesAreKeptPerMobKindAndPerSituation(final GameTestHelper helper) {
		Policy policy = Policy.EMPTY.with(Policy.key(ZOMBIE, Situation.SWING, Tactic.SHIELD), 1.0, ALPHA);
		helper.assertTrue(policy.get(Policy.key("minecraft:skeleton", Situation.SWING, Tactic.SHIELD)).tries() == 0, "a skeleton shared a zombie's value");
		helper.assertTrue(policy.get(Policy.key(ZOMBIE, Situation.PROJECTILE, Tactic.SHIELD)).tries() == 0, "another situation shared the value");
		helper.assertTrue(policy.total(ZOMBIE, Situation.SWING) == 1 && policy.total(ZOMBIE, Situation.PROJECTILE) == 0, "the totals were wrong");
		helper.succeed();
	}

	@GameTest
	public void aPolicySurvivesASave(final GameTestHelper helper) {
		Policy policy = trained(Tactic.SHIELD, 1.0, 3, trained(Tactic.BACK_OFF, -0.4, 2, Policy.EMPTY));
		Tag saved = Policy.CODEC.encodeStart(NbtOps.INSTANCE, policy).getOrThrow();
		DataResult<Policy> loaded = Policy.CODEC.parse(NbtOps.INSTANCE, saved);
		Policy back = loaded.getOrThrow();
		String shield = Policy.key(ZOMBIE, Situation.SWING, Tactic.SHIELD);
		StellaLearn.LOGGER.info("[BEHAVIOR] test=aPolicySurvivesASave RESULT shield={}", back.get(shield));
		helper.assertTrue(back.get(shield).tries() == 3 && Math.abs(back.get(shield).value() - policy.get(shield).value()) < TOLERANCE, "the shield entry was " + back.get(shield));
		helper.assertTrue(back.get(Policy.key(ZOMBIE, Situation.SWING, Tactic.BACK_OFF)).tries() == 2, "the back-off entry was lost");
		helper.succeed();
	}

	@GameTest
	public void thePolicyIsSavedWithThePlayer(final GameTestHelper helper) {
		helper.assertTrue(Policies.TYPE.isPersistent(), "the policy attachment is not persistent");
		helper.succeed();
	}

	@GameTest(maxTicks = 400, padding = LearnArena.PADDING)
	public void eachPlayerLearnsTheirOwnPolicy(final GameTestHelper helper) {
		ServerPlayer first = LearnArena.player(helper, 24.5, 24.5);
		ServerPlayer second = LearnArena.player(helper, 20.5, 24.5);
		String key = Policy.key(ZOMBIE, Situation.SWING, Tactic.SHIELD);
		Policies.set(first, Policies.get(first).with(key, 1.0, ALPHA));
		StellaLearn.LOGGER.info("[BEHAVIOR] test=eachPlayerLearnsTheirOwnPolicy RESULT first={} second={}", Policies.get(first).get(key), Policies.get(second).get(key));
		helper.assertTrue(Policies.get(first).get(key).tries() == 1 && Policies.get(second).get(key).tries() == 0, "one player's policy leaked to another");
		helper.succeed();
	}

	/** Phase one rewards the shield, phase two the back-off: the learner follows the change without being told. */
	@GameTest
	public void theLearnerFollowsAPlayerWhoChangesTheirWays(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(42L);
		ToDoubleFunction<Tactic> shieldWorks = tactic -> tactic == Tactic.SHIELD ? 1.0 : tactic == Tactic.BACK_OFF ? 0.0 : -1.0;
		ToDoubleFunction<Tactic> backOffWorks = tactic -> tactic == Tactic.BACK_OFF ? 1.0 : -1.0;
		Policy policy = Policy.EMPTY;
		int shieldLate = 0;
		for (int i = 0; i < 80; i++) {
			Bandit.Choice choice = Bandit.choose(policy, ZOMBIE, Situation.SWING, SWING, Bandit.epsilon(policy.total(ZOMBIE, Situation.SWING), FLOOR), random);
			policy = policy.with(Policy.key(ZOMBIE, Situation.SWING, choice.tactic()), shieldWorks.applyAsDouble(choice.tactic()), ALPHA);
			shieldLate += i >= 70 && choice.tactic() == Tactic.SHIELD ? 1 : 0;
		}

		int backOffLate = 0;
		for (int i = 0; i < 80; i++) {
			Bandit.Choice choice = Bandit.choose(policy, ZOMBIE, Situation.SWING, SWING, Bandit.epsilon(policy.total(ZOMBIE, Situation.SWING), FLOOR), random);
			policy = policy.with(Policy.key(ZOMBIE, Situation.SWING, choice.tactic()), backOffWorks.applyAsDouble(choice.tactic()), ALPHA);
			backOffLate += i >= 70 && choice.tactic() == Tactic.BACK_OFF ? 1 : 0;
		}

		StellaLearn.LOGGER.info("[BEHAVIOR] test=theLearnerFollowsAPlayerWhoChangesTheirWays RESULT shieldInLastTenOfPhaseOne={} backOffInLastTenOfPhaseTwo={}", shieldLate, backOffLate);
		helper.assertTrue(shieldLate >= 8, "it chose the shield " + shieldLate + " times of the last 10 while it worked");
		helper.assertTrue(backOffLate >= 8, "it chose the back-off " + backOffLate + " times of the last 10 after the change");
		helper.succeed();
	}
}
