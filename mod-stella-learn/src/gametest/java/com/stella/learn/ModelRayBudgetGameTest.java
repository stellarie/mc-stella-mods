package com.stella.learn;

import com.stella.learn.model.ArcherObservation;
import com.stella.learn.model.ArcherState;
import com.stella.learn.model.ModelDriver;
import com.stella.learn.model.Mlp;
import com.stella.learn.model.MlpSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

/**
 * The observation rays end, however far from the origin the archers stand: ten model-driven archers against one player for
 * 200 ticks stay under a per-tick budget, and an archer with a broken facing still builds its observation at once. A ray walk
 * that lost its way once held a server tick for minutes.
 */
public class ModelRayBudgetGameTest {
	private static final int ARCHERS = 10;
	private static final int TICKS = 200;
	private static final int WARM_TICKS = 20;
	private static final double P95_BUDGET_MS = 50.0;
	private static final double WORST_BUDGET_MS = 1000.0;
	private static final double BROKEN_FACING_BUDGET_MS = 200.0;

	@GameTest(maxTicks = 900, padding = LearnArena.PADDING, environment = ModelTests.ENV_PERF)
	public void tenArchersStayUnderTheTickBudget(final GameTestHelper helper) {
		ModelTests.acquire(helper);
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 40.5);
		MlpSource network = new MlpSource(new Mlp(ModelTests.policy("forward")));
		List<Mob> archers = new ArrayList<>();
		for (int i = 0; i < ARCHERS; i++) {
			Mob archer = ModelTests.archer(helper, 8.5 + i * 3.0, 20.5 + (i % 2) * 4.0);
			ModelDriver.setActionSource(archer, network);
			archers.add(archer);
		}

		Mob broken = archers.get(0);
		long overrunsBefore = ModelDriver.rayOverruns();
		List<Double> ticks = new ArrayList<>();
		long[] last = {0L};
		int[] count = {0};
		ModelTests.everyTick(helper, player, () -> {
			long now = System.nanoTime();
			if (last[0] != 0L && ModelTests.running(archers.get(ARCHERS - 1))) {
				count[0]++;
				if (count[0] > WARM_TICKS) {
					ticks.add((now - last[0]) / 1.0e6);
				}
			}

			last[0] = now;
		}, archers.toArray(new Mob[0]));
		LearnArena.whenReady(helper, new ArrayList<net.minecraft.world.entity.Entity>(archers), () -> {
			long began = System.nanoTime();
			broken.setYRot(Float.NaN);
			float[] observation = ArcherObservation.build(broken, player, new ArcherState());
			double brokenMs = (System.nanoTime() - began) / 1.0e6;
			broken.setYRot(0.0F);
			helper.assertTrue(brokenMs < BROKEN_FACING_BUDGET_MS, "an archer facing NaN took " + brokenMs + " ms to build its observation");
			helper.assertTrue(observation.length > 0, "no observation");
			LearnArena.pollUntil(helper, () -> ticks.size() >= TICKS - WARM_TICKS, TICKS + 200, () -> {
				List<Double> sorted = new ArrayList<>(ticks);
				Collections.sort(sorted);
				double p95 = sorted.get((int) (sorted.size() * 0.95));
				double worst = sorted.get(sorted.size() - 1);
				double mean = sorted.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
				LearnLog.info("learnModel", "PERF", "scenario=tenArchers archers=" + ARCHERS + " ticks=" + sorted.size() + " mean_ms=" + LearnLog.num(mean)
					+ " p95_ms=" + LearnLog.num(p95) + " max_ms=" + LearnLog.num(worst) + " overruns=" + (ModelDriver.rayOverruns() - overrunsBefore));
				helper.assertTrue(p95 < P95_BUDGET_MS, "the 95th percentile tick took " + p95 + " ms");
				helper.assertTrue(worst < WORST_BUDGET_MS, "the worst tick took " + worst + " ms");
				ModelTests.pass(helper);
			}, () -> "the ticks were never counted");
		});
	}
}
