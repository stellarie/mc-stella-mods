package com.stella.mobs;

import com.stella.mobs.behavior.WitherMinions;
import com.stella.mobs.behavior.WitherShots;
import com.stella.mobs.behavior.WitherState;
import com.stella.mobs.config.WitherConfig;
import com.stella.mobs.config.WitherConfigStore;
import com.stella.mobs.projectile.WitherSkullShot;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.Vec3;

/**
 * At half health the Wither rages for thirty seconds: a barrage of skulls in every direction, none past sixteen blocks.
 * At a quarter health a second rage replaces it. Each rage starts once. A rage holds volleys and new minions back. One
 * scene runs the phases in order, because each arena costs the server a large chunk load.
 */
public final class WitherRageGameTest {
	private static final int SHIPPED_RAGE_TICKS = 600;
	private static final double HALF = 0.5;
	private static final double QUARTER = 0.25;
	private static final double ABOVE_HALF = 0.52;
	private static final int GAP = 25;
	private static final int BARRAGE_WATCH = 100;
	private static final int FLOOD_RING = 30;
	private static final int FLOOD_INTERVAL = 2;
	private static final int MINION_WAVE = 20;
	private static final int MINION_BATCH = 4;
	private static final int SHIPPED_RANGE = 16;
	private static final int SHIPPED_CAP = 40;
	private static final double STEP_SLACK = 2.0;

	private static void hit(final ServerLevel level, final WitherBoss wither, final double healthLoss) {
		double divisor = WitherState.find(wither).healthDivisor();
		wither.setInvulnerableTime(0);
		wither.hurtServer(level, level.damageSources().genericKill(), (float) (divisor * healthLoss));
	}

	@GameTest
	public void theShippedRageLastsThirtySeconds(final GameTestHelper helper) {
		WitherConfig config = WitherConfigStore.get();
		helper.assertTrue(config.getInt(WitherConfig.RAGE_TICKS) == SHIPPED_RAGE_TICKS, "a rage lasts " + config.getInt(WitherConfig.RAGE_TICKS) + " ticks, want 30 s");
		helper.assertTrue(config.get(WitherConfig.RAGE_A_THRESHOLD) == HALF && config.get(WitherConfig.RAGE_B_THRESHOLD) == QUARTER, "the thresholds are not 50 and 25 percent");
		helper.assertTrue(config.getInt(WitherConfig.RAGE_A_RANGE) == SHIPPED_RANGE && config.getInt(WitherConfig.SKULL_LIVE_CAP) == SHIPPED_CAP, "the barrage range or the skull cap moved");
		helper.succeed();
	}

	@GameTest(maxTicks = 2400, padding = WideArena.PADDING)
	public void theRagesStartOnceHoldBackHelpAndOneReplacesTheOther(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		WitherBoss[] wither = new WitherBoss[1];
		ServerPlayer[] player = new ServerPlayer[1];
		List<Mob> spawned = new CopyOnWriteArrayList<>();
		WitherConfig previous = WitherConfigStore.get();
		// Frequent waves from the start, so a wave is due inside the rage and its hold shows.
		WitherConfigStore.set(previous.with(WitherConfig.MINIONS_WAVE_INTERVAL, MINION_WAVE).with(WitherConfig.MINIONS_WAVE_SIZE, MINION_BATCH));
		WitherMinions.Recorder recorder = (owner, minion, distance) -> {
			if (owner == wither[0]) {
				spawned.add(minion);
			}
		};
		WitherMinions.addRecorder(recorder);
		helper.runBeforeTestEnd(() -> {
			WitherConfigStore.set(previous);
			WitherMinions.removeRecorder(recorder);
			arena.finish();
		});
		int[] phase = {0};
		long[] phaseStart = {0};
		int[] spawnedAtStart = {0};
		int[] peakLive = {0};
		int[] aimedSeen = {0};
		Map<WitherSkullShot, Double> farthest = new IdentityHashMap<>();
		Set<WitherSkullShot> beforeRage = Collections.newSetFromMap(new IdentityHashMap<>());
		helper.onEachTick(() -> {
			if (wither[0] == null) {
				return;
			}
			WideArena.keepValid(player[0]);
			ServerLevel level = helper.getLevel();
			WitherBoss boss = wither[0];
			WitherConfig config = WitherConfigStore.get();
			long elapsed = helper.getTick() - phaseStart[0];
			for (WitherSkullShot skull : WitherShots.snapshot(boss)) {
				farthest.merge(skull, skull.position().distanceTo(skull.origin()), Math::max);
				if (skull.spec().share() == config.get(WitherConfig.AIM_SHARE) && !beforeRage.contains(skull) && WitherState.find(boss).rageKind() != WitherState.RAGE_NONE) {
					aimedSeen[0]++;
				}
			}
			peakLive[0] = Math.max(peakLive[0], WitherShots.live(boss));
			WitherState state = WitherState.find(boss);
			switch (phase[0]) {
				case 0 -> {
					if (elapsed >= GAP) {
						boss.setHealth((float) (boss.getMaxHealth() * ABOVE_HALF));
						hit(level, boss, 1.0);
						helper.assertTrue(WitherState.find(boss).rageKind() == WitherState.RAGE_NONE && !WitherState.find(boss).rageAUsed(),
							"a rage began at " + boss.getHealth() / boss.getMaxHealth() + " of max health");
						next(phase, phaseStart, helper);
					}
				}
				case 1 -> {
					if (elapsed >= GAP) {
						boss.setHealth((float) (boss.getMaxHealth() * HALF + 0.5));
						WitherConfigStore.set(WitherConfigStore.get().with(WitherConfig.RAGE_A_RING_SIZE, FLOOD_RING).with(WitherConfig.RAGE_A_RING_INTERVAL, FLOOD_INTERVAL));
						hit(level, boss, 2.0);
						WitherState after = WitherState.find(boss);
						StellaMobs.LOGGER.info("[BEHAVIOR] test=witherRageHalf RESULT kind={} ticksLeft={} aUsed={} bUsed={} fraction={}",
							after.rageKind(), after.rageTicksLeft(), after.rageAUsed(), after.rageBUsed(), boss.getHealth() / boss.getMaxHealth());
						helper.assertTrue(after.rageKind() == WitherState.RAGE_BARRAGE, "no barrage at half health: kind " + after.rageKind());
						helper.assertTrue(after.rageTicksLeft() == SHIPPED_RAGE_TICKS, "the barrage lasts " + after.rageTicksLeft() + " ticks, want " + SHIPPED_RAGE_TICKS);
						helper.assertTrue(after.rageAUsed() && !after.rageBUsed(), "the used flags are wrong after the first rage");
						beforeRage.addAll(WitherShots.snapshot(boss));
						// Room for new minions, so a held-back wave shows.
						for (Mob minion : WitherMinions.alive(level, boss)) {
							minion.hurtServer(level, level.damageSources().genericKill(), 1.0E6F);
						}
						spawnedAtStart[0] = spawned.size();
						next(phase, phaseStart, helper);
					}
				}
				case 2 -> {
					if (elapsed >= BARRAGE_WATCH) {
						double widest = farthest.values().stream().mapToDouble(Double::doubleValue).max().orElse(-1.0);
						StellaMobs.LOGGER.info("[BEHAVIOR] test=witherBarrage RESULT ticksLeft={} peakLive={} widest={} skulls={} aimedDuringRage={} minionsAdded={}",
							state.rageTicksLeft(), peakLive[0], widest, farthest.size(), aimedSeen[0], spawned.size() - spawnedAtStart[0]);
						helper.assertTrue(Math.abs(state.rageTicksLeft() - (SHIPPED_RAGE_TICKS - BARRAGE_WATCH)) <= 3, "the rage has " + state.rageTicksLeft() + " ticks left after " + BARRAGE_WATCH);
						helper.assertTrue(peakLive[0] == SHIPPED_CAP, "the live skulls peaked at " + peakLive[0] + ", cap " + SHIPPED_CAP);
						helper.assertTrue(farthest.size() > SHIPPED_CAP, "only " + farthest.size() + " skulls flew");
						helper.assertTrue(widest <= SHIPPED_RANGE + STEP_SLACK, "a skull flew " + widest + " blocks from where it began, range " + SHIPPED_RANGE);
						helper.assertTrue(aimedSeen[0] == 0, "an aimed volley came during the rage");
						helper.assertTrue(spawned.size() == spawnedAtStart[0], (spawned.size() - spawnedAtStart[0]) + " minions came during the rage");
						next(phase, phaseStart, helper);
					}
				}
				case 3 -> {
					if (elapsed >= GAP) {
						boss.setHealth((float) (boss.getMaxHealth() * QUARTER + 0.5));
						hit(level, boss, 2.0);
						WitherState after = WitherState.find(boss);
						StellaMobs.LOGGER.info("[BEHAVIOR] test=witherRageQuarter RESULT kind={} ticksLeft={} aUsed={} bUsed={}", after.rageKind(), after.rageTicksLeft(), after.rageAUsed(), after.rageBUsed());
						helper.assertTrue(after.rageKind() == WitherState.RAGE_RAIN, "the second rage did not replace the first: kind " + after.rageKind());
						helper.assertTrue(after.rageTicksLeft() == SHIPPED_RAGE_TICKS, "the second rage lasts " + after.rageTicksLeft() + " ticks");
						helper.assertTrue(after.rageAUsed() && after.rageBUsed(), "the used flags are wrong after the second rage");
						// The rage runs thirty seconds. Cut it short so the test reaches its end.
						WitherState.set(boss, after.withRage(true, true, WitherState.RAGE_RAIN, GAP));
						next(phase, phaseStart, helper);
					}
				}
				case 4 -> {
					if (state.rageKind() == WitherState.RAGE_NONE) {
						helper.assertTrue(state.rageTicksLeft() == 0, "an ended rage keeps " + state.rageTicksLeft() + " ticks");
						spawnedAtStart[0] = spawned.size();
						next(phase, phaseStart, helper);
					} else if (elapsed > 3L * GAP) {
						helper.fail("the second rage never ended: " + state);
					}
				}
				case 5 -> {
					// No wave in the pause that follows a rage, and waves again after it.
					if (elapsed == 90) {
						helper.assertTrue(spawned.size() == spawnedAtStart[0], (spawned.size() - spawnedAtStart[0]) + " minions came in the pause after the rage");
					}
					if (elapsed > 90 && spawned.size() > spawnedAtStart[0]) {
						StellaMobs.LOGGER.info("[BEHAVIOR] test=witherRagePause RESULT resumedAfter={}", elapsed);
						next(phase, phaseStart, helper);
					} else if (elapsed > 400) {
						helper.fail("no minion came after the pause");
					}
				}
				case 6 -> {
					if (elapsed >= GAP) {
						boss.setHealth((float) (boss.getMaxHealth() * HALF + 0.5));
						hit(level, boss, 2.0);
						helper.assertTrue(WitherState.find(boss).rageKind() == WitherState.RAGE_NONE, "the first rage began a second time");
						next(phase, phaseStart, helper);
					}
				}
				case 7 -> {
					if (elapsed >= GAP) {
						boss.setHealth((float) (boss.getMaxHealth() * QUARTER + 0.5));
						hit(level, boss, 2.0);
						helper.assertTrue(WitherState.find(boss).rageKind() == WitherState.RAGE_NONE, "the second rage began a second time");
						helper.succeed();
					}
				}
				default -> {
				}
			}
		});
		arena.whenReady(List.of(new Vec3(28, 3, 28), new Vec3(28, 3, 44)), () -> {
			player[0] = arena.survivalPlayer(28.5, 44.5);
			wither[0] = WitherRig.wither(helper, arena, 28, WideArena.SPAWN_Y, 28, 0);
			phaseStart[0] = helper.getTick();
		});
	}

	private static void next(final int[] phase, final long[] phaseStart, final GameTestHelper helper) {
		phase[0]++;
		phaseStart[0] = helper.getTick();
	}
}
