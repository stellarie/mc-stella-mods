package com.stella.learn;

import com.stella.learn.model.LearnedArcherGoal;
import com.stella.learn.model.Mlp;
import com.stella.learn.model.MlpSource;
import com.stella.learn.model.ModelDriver;
import com.stella.mobs.behavior.RetreatGoal;
import com.stella.mobs.behavior.SkeletonVolleyGoal;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

/** A model that cannot run leaves every archer on its own goals, and the reason is in the log. */
public class ModelFallbackGameTest {
	private static final long INSTALL_WAIT = 8L;

	/** An archer with a bad policy loaded, seen after the driver has had its tick. */
	private static void badPolicy(final GameTestHelper helper, final String resource, final String status, final String reason) {
		ModelDriver.reloadPolicy(resource);
		helper.assertTrue(status.equals(ModelDriver.status()), "the policy status is " + ModelDriver.status() + ", not " + status);
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 34.5);
		Mob archer = ModelTests.archer(helper, 24.5, 24.5);
		LearnArena.keepTargeting(helper, archer, player);
		LearnArena.whenReady(helper, List.of(player, archer), () -> helper.runAfterDelay(INSTALL_WAIT, () -> {
			helper.assertTrue(
				ModelTests.has(archer, SkeletonVolleyGoal.class) && ModelTests.has(archer, RetreatGoal.class) && !ModelTests.has(archer, LearnedArcherGoal.class) && !ModelTests.tagged(archer),
				"the archer did not keep its goals: " + ModelTests.goalNames(archer)
			);
			List<String> fallbacks = ModelTests.lines(archer, "FALLBACK", "reason=" + reason);
			helper.assertTrue(fallbacks.size() == 1, "the archer wrote " + fallbacks.size() + " FALLBACK reason=" + reason + " lines");
			helper.assertTrue(!LearnTestInit.linesWith("learnModel LOAD", "status=" + status).isEmpty(), "no LOAD line reported status=" + status);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=badPolicy RESULT status={} reason={} line={}", status, reason, fallbacks.get(0));
			ModelDriver.useNoPolicy();
			helper.succeed();
		}));
	}

	@GameTest(maxTicks = ModelTests.LONG, padding = LearnArena.PADDING, environment = ModelTests.ENV_MISSING)
	public void missingWeightsFallBack(final GameTestHelper helper) {
		badPolicy(helper, "policies/test/absent.bin", "missing", "missing");
	}

	@GameTest(maxTicks = ModelTests.LONG, padding = LearnArena.PADDING, environment = ModelTests.ENV_CONTRACT)
	public void wrongContractFallsBack(final GameTestHelper helper) {
		badPolicy(helper, "policies/test/wrong-contract.bin", "contract", "contract");
	}

	@GameTest(maxTicks = ModelTests.LONG, padding = LearnArena.PADDING, environment = ModelTests.ENV)
	public void nanOutputRestoresGoals(final GameTestHelper helper) {
		ModelTests.acquire(helper);
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 34.5);
		Mob archer = ModelTests.archer(helper, 24.5, 24.5);
		LearnArena.keepTargeting(helper, archer, player);
		// The archer runs the real network on a model whose forward bias is NaN.
		ModelDriver.setActionSource(archer, new MlpSource(new Mlp(ModelTests.policy("nan"))));
		// The install and the fall back can land on consecutive ticks, so the log is what shows the archer was driven.
		LearnArena.whenReady(helper, List.of(player, archer), () -> LearnArena.pollUntil(helper, () -> !ModelTests.lines(archer, "INSTALL").isEmpty(), 40, () -> {
			LearnArena.pollUntil(helper, () -> !ModelTests.tagged(archer) && ModelTests.has(archer, SkeletonVolleyGoal.class), 60, () -> helper.runAfterDelay(10L, () -> {
				helper.assertTrue(ModelTests.has(archer, RetreatGoal.class) && !ModelTests.has(archer, LearnedArcherGoal.class), "the archer did not get its goals back: " + ModelTests.goalNames(archer));
				helper.assertTrue(!ModelTests.tagged(archer) && !ModelDriver.isDriven(archer), "the archer was installed again");
				helper.assertTrue(ModelTests.lines(archer, "INSTALL").size() == 1, "the archer was installed " + ModelTests.lines(archer, "INSTALL").size() + " times");
				List<String> fallbacks = ModelTests.lines(archer, "FALLBACK", "reason=nan");
				helper.assertTrue(fallbacks.size() == 1, "the archer wrote " + fallbacks.size() + " FALLBACK reason=nan lines");
				StellaLearn.LOGGER.info("[BEHAVIOR] test=nanOutputRestoresGoals RESULT line={}", fallbacks.get(0));
				ModelTests.pass(helper);
			}), () -> "the archer never got its goals back: " + ModelTests.goalNames(archer));
		}, () -> "the archer was never made model driven"));
	}

	@GameTest(maxTicks = ModelTests.LONG, padding = LearnArena.PADDING, environment = ModelTests.ENV)
	public void aFailingSourceRestoresGoalsInsteadOfThrowing(final GameTestHelper helper) {
		ModelTests.acquire(helper);
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 34.5);
		Mob archer = ModelTests.archer(helper, 24.5, 24.5);
		LearnArena.keepTargeting(helper, archer, player);
		ModelDriver.setActionSource(archer, (mob, observation) -> {
			throw new IllegalStateException("a source that fails on purpose");
		});
		LearnArena.whenReady(helper, List.of(player, archer), () -> LearnArena.pollUntil(helper, () -> !ModelTests.lines(archer, "FALLBACK", "reason=error").isEmpty(), 60, () -> helper.runAfterDelay(6L, () -> {
			helper.assertTrue(ModelTests.has(archer, SkeletonVolleyGoal.class) && ModelTests.has(archer, RetreatGoal.class) && !ModelTests.tagged(archer), "the archer did not get its goals back: " + ModelTests.goalNames(archer));
			helper.assertTrue(archer.isAlive(), "the archer is gone");
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aFailingSourceRestoresGoalsInsteadOfThrowing RESULT lines={}", ModelTests.lines(archer, "FALLBACK", "reason=error").size());
			ModelTests.pass(helper);
		}), () -> "no FALLBACK reason=error line was written"));
	}
}
