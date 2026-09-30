package com.stella.learn;

import com.stella.learn.model.ActionSource;
import com.stella.learn.model.ArcherAction;
import com.stella.learn.model.ArcherObservation;
import com.stella.learn.model.ArcherState;
import com.stella.learn.model.Contract;
import com.stella.learn.model.Mlp;
import com.stella.learn.model.MlpSource;
import com.stella.learn.model.ModelDriver;
import com.stella.learn.model.ModelPerf;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** A hundred model-driven archers in a fight cost the server at most a millisecond a tick. */
public class ModelPerfGameTest {
	private static final int ARCHERS = 100;
	private static final int WARM_TICKS = 100;
	private static final int MEASURE_TICKS = 200;
	private static final int WINDOWS = 3;
	private static final double BUDGET_MS = 1.0;
	/** One shot per 40 ticks, the pace of the mobs mod's own skeleton. */
	private static final int DRAW_DECISIONS = 10;
	private static final int WARM_UP_CALLS = 4000;
	private static final int CYCLE_DECISIONS = 20;

	/**
	 * Runs the real network so its cost is in the number, then answers with a fixed shooting routine that walks and
	 * turns, so the rays never repeat and the bow is loosed as often as a busy archer would. Each archer starts at its own
	 * point of the routine, since a hundred archers loosing in one tick is not a fight anyone will see.
	 */
	private static ActionSource busy(final MlpSource network, final int offset) {
		int[] decisions = {offset};
		return (mob, observation) -> {
			network.decide(mob, observation);
			int step = decisions[0]++ % CYCLE_DECISIONS;
			ArcherAction.Bow bow = step < DRAW_DECISIONS ? ArcherAction.Bow.DRAW : step == DRAW_DECISIONS ? ArcherAction.Bow.RELEASE : ArcherAction.Bow.IDLE;
			return new ArcherAction(1.0F, 0.3F, 0.1F, 0.0F, step == 3, bow);
		};
	}

	/** Runs the observation and the network enough times for the JIT to compile them, as a server that has been up a while has. */
	private static void warmUp(final Mob archer, final ServerPlayer player, final MlpSource network) {
		ArcherState state = new ArcherState();
		float[] observation = new float[Contract.archer().observationSize()];
		for (int i = 0; i < WARM_UP_CALLS; i++) {
			ArcherObservation.build(archer, player, state, observation);
			network.decide(archer, observation);
		}
	}

	/**
	 * One window of `MEASURE_TICKS`. Other work on the machine only adds time, so the fastest of up to `WINDOWS` windows is the
	 * closest to the cost of the goal itself; the test passes on the first window that fits the budget.
	 */
	private static void measure(final GameTestHelper helper, final List<Mob> archers, final List<Double> means) {
		ModelPerf.start();
		helper.runAfterDelay(MEASURE_TICKS, () -> {
			int running = (int) archers.stream().filter(ModelTests::running).count();
			ModelPerf.Result result = ModelPerf.stop(running);
			means.add(result.meanMs());
			StellaLearn.LOGGER.info(
				"[BEHAVIOR] test=hundredArchersCostUnderOneMs RESULT window={} archers={} ticks={} mean_ms={} p95_ms={} max_ms={} budget_ms={} phases_ms={}",
				means.size(), running, result.ticks(), result.meanMs(), result.p95Ms(), result.maxMs(), BUDGET_MS, java.util.Arrays.toString(result.phaseMeanMs())
			);
			helper.assertTrue(ModelDriver.drivenCount() >= ARCHERS && running >= ARCHERS * 95 / 100, "only " + running + " archers were still running the model goal");
			helper.assertTrue(result.ticks() >= MEASURE_TICKS - 2, "the window held " + result.ticks() + " ticks");
			if (result.meanMs() <= BUDGET_MS) {
				ModelTests.pass(helper);
			} else if (means.size() < WINDOWS) {
				measure(helper, archers, means);
			} else {
				helper.fail("the archers cost " + means + " ms a tick on average in " + WINDOWS + " windows, over " + BUDGET_MS);
			}
		});
	}

	@GameTest(maxTicks = 900, padding = LearnArena.PADDING, environment = ModelTests.ENV_PERF)
	public void hundredArchersCostUnderOneMs(final GameTestHelper helper) {
		ModelTests.acquire(helper);
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		List<Mob> archers = new ArrayList<>();
		// One network for all, as the default source shares one, so its weights stay hot in the cache.
		MlpSource network = new MlpSource(new Mlp(ModelTests.policy("golden")));
		for (int i = 0; i < ARCHERS; i++) {
			Mob archer = ModelTests.archer(helper, 4.5 + (i % 10) * 4.0, 4.5 + (i / 10) * 4.0);
			// Their own arrows must not thin the crowd during the run.
			archer.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000.0);
			archer.setHealth(10000.0F);
			ModelDriver.setActionSource(archer, busy(network, i % CYCLE_DECISIONS));
			archers.add(archer);
		}

		warmUp(archers.get(0), player, network);
		helper.onEachTick(() -> {
			player.setInvulnerableTime(0);
			archers.forEach(archer -> archer.setTarget(player));
		});
		List<net.minecraft.world.entity.Entity> watched = new ArrayList<>(archers);
		watched.add(player);
		LearnArena.whenReady(helper, watched, () -> LearnArena.pollUntil(helper, () -> archers.stream().allMatch(ModelTests::running), 60, () ->
			helper.runAfterDelay(WARM_TICKS, () -> measure(helper, archers, new ArrayList<>())),
			() -> "only " + archers.stream().filter(ModelTests::running).count() + " archers started the model goal"));
	}
}
