package com.stella.mobs;

import com.stella.mobs.behavior.WitherEncounter;
import com.stella.mobs.behavior.WitherMinions;
import com.stella.mobs.behavior.WitherState;
import com.stella.mobs.config.WitherConfig;
import com.stella.mobs.config.WitherConfigStore;
import com.stella.mobs.scaling.ThreatState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/**
 * The Wither calls skeletons, zombies, husks, strays and wither skeletons within 32 blocks at half its level, never
 * above a cap, and they drop nothing. Their end comes with the Wither's. One scene runs the phases in order, because
 * each arena costs the server a large chunk load.
 */
public final class WitherMinionGameTest {
	private static final int WAVE_INTERVAL = 20;
	private static final int WAVE_SIZE = 4;
	private static final int RUN_TICKS = 160;
	private static final int SETTLE_TICKS = 15;
	private static final int PICKS = 10_000;
	private static final long SEED = 7031L;
	private static final List<Drop> DROPS = new CopyOnWriteArrayList<>();
	private static boolean watching;

	private record Drop(Vec3 at) {
	}

	private static synchronized void watchDrops() {
		if (watching) {
			return;
		}
		watching = true;
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
				DROPS.add(new Drop(entity.position()));
			}
		});
	}

	private static int nearPlayers(final ServerLevel level, final WitherBoss wither) {
		double radius = WitherConfigStore.get().get(WitherConfig.MINIONS_CAP_RADIUS);
		int near = 0;
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.distanceToSqr(wither) <= radius * radius) {
				near++;
			}
		}
		return near;
	}

	@GameTest
	public void theKindsFollowTheirWeights(final GameTestHelper helper) {
		WitherConfig config = WitherConfigStore.get();
		Map<EntityType<?>, Integer> counts = new HashMap<>();
		RandomSource random = RandomSource.create(SEED);
		for (int i = 0; i < PICKS; i++) {
			counts.merge(WitherMinions.pick(random), 1, Integer::sum);
		}
		double total = config.get(WitherConfig.MINIONS_WEIGHT_SKELETON) + config.get(WitherConfig.MINIONS_WEIGHT_ZOMBIE)
			+ config.get(WitherConfig.MINIONS_WEIGHT_HUSK) + config.get(WitherConfig.MINIONS_WEIGHT_STRAY) + config.get(WitherConfig.MINIONS_WEIGHT_WITHER_SKELETON);
		Object[][] wanted = {
			{EntityTypes.SKELETON, config.get(WitherConfig.MINIONS_WEIGHT_SKELETON)}, {EntityTypes.ZOMBIE, config.get(WitherConfig.MINIONS_WEIGHT_ZOMBIE)},
			{EntityTypes.HUSK, config.get(WitherConfig.MINIONS_WEIGHT_HUSK)}, {EntityTypes.STRAY, config.get(WitherConfig.MINIONS_WEIGHT_STRAY)},
			{EntityTypes.WITHER_SKELETON, config.get(WitherConfig.MINIONS_WEIGHT_WITHER_SKELETON)}
		};
		int seen = 0;
		for (Object[] row : wanted) {
			int count = counts.getOrDefault((EntityType<?>) row[0], 0);
			seen += count;
			double share = count / (double) PICKS;
			helper.assertTrue(Math.abs(share - (double) row[1] / total) < 0.02, row[0] + " came " + share + " of the time, weight " + row[1] + " of " + total);
		}
		StellaMobs.LOGGER.info("[BEHAVIOR] test=witherMinionKinds RESULT counts={}", counts);
		helper.assertTrue(seen == PICKS, "a pick fell outside the five kinds: " + counts);
		helper.succeed();
	}

	/** The scene: waves to the cap, then every minion killed for drops, then the Wither killed for the rest. */
	@GameTest(maxTicks = 1700, padding = WideArena.PADDING)
	public void theWaveCapTheDropsAndTheEnd(final GameTestHelper helper) {
		watchDrops();
		WideArena arena = WideArena.build(helper);
		WitherBoss[] wither = new WitherBoss[1];
		ServerPlayer[] player = new ServerPlayer[1];
		List<Mob> spawned = new CopyOnWriteArrayList<>();
		List<Double> distances = new CopyOnWriteArrayList<>();
		WitherConfig previous = WitherConfigStore.get();
		WitherConfigStore.set(previous.with(WitherConfig.MINIONS_WAVE_INTERVAL, WAVE_INTERVAL).with(WitherConfig.MINIONS_WAVE_SIZE, WAVE_SIZE));
		WitherMinions.Recorder recorder = (owner, minion, distance) -> {
			if (owner == wither[0]) {
				spawned.add(minion);
				distances.add(distance);
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
		int[] maxAlive = {0};
		int[] killed = {0};
		int[] dropMark = {0};
		int[] controlMark = {0};
		Zombie[] control = new Zombie[1];
		List<Vec3> deathSpots = new ArrayList<>();
		Vec3 controlAt = arena.at(10, WideArena.SPAWN_Y, 10);
		helper.onEachTick(() -> {
			if (wither[0] == null) {
				return;
			}
			WideArena.keepValid(player[0]);
			ServerLevel level = helper.getLevel();
			long elapsed = helper.getTick() - phaseStart[0];
			maxAlive[0] = Math.max(maxAlive[0], WitherMinions.alive(level, wither[0]).size());
			WitherConfig config = WitherConfigStore.get();
			if (phase[0] == 0 && elapsed >= RUN_TICKS) {
				int cap = Math.max(config.getInt(WitherConfig.MINIONS_CAP_BASE), Math.min(config.getInt(WitherConfig.MINIONS_CAP_MAX),
					config.getInt(WitherConfig.MINIONS_CAP_BASE) + config.getInt(WitherConfig.MINIONS_CAP_PER_PLAYER) * nearPlayers(level, wither[0])));
				int halfLevel = ThreatState.level(wither[0]) / 2;
				double reach = config.get(WitherConfig.MINIONS_SPAWN_MAX);
				double farthest = distances.stream().mapToDouble(Double::doubleValue).max().orElse(-1.0);
				WitherState state = WitherState.find(wither[0]);
				List<UUID> mine = spawned.stream().map(Mob::getUUID).toList();
				StellaMobs.LOGGER.info("[BEHAVIOR] test=witherMinionWaves RESULT spawned={} maxAlive={} cap={} farthest={} halfLevel={} recorded={}",
					spawned.size(), maxAlive[0], cap, farthest, halfLevel, state.minions().size());
				helper.assertTrue(spawned.size() >= cap, "only " + spawned.size() + " minions came, cap " + cap);
				helper.assertTrue(maxAlive[0] == cap, "at most " + maxAlive[0] + " minions lived at once, cap " + cap);
				helper.assertTrue(farthest <= reach + 0.5, "a minion came " + farthest + " blocks away, reach " + reach);
				helper.assertTrue(mine.containsAll(state.minions()) && !state.minions().isEmpty(), "the record holds a mob the Wither did not add");
				for (Mob minion : spawned) {
					helper.assertTrue(WitherMinions.isKind(minion.getType()), "a minion of kind " + minion.getType());
					helper.assertTrue(WitherEncounter.isMinion(minion), "a minion without the tag");
					helper.assertTrue(ThreatState.level(minion) == halfLevel, "a minion at level " + ThreatState.level(minion) + ", want " + halfLevel);
					if (minion.isAlive()) {
						helper.assertTrue(minion.hasEffect(MobEffects.FIRE_RESISTANCE), "a minion without fire resistance");
						float before = minion.getHealth();
						minion.hurtServer(level, level.damageSources().inFire(), 5.0F);
						helper.assertTrue(minion.getHealth() >= before, "fire hurt a minion");
					}
				}
				// Phase 1: kill every minion, and one plain zombie as the control that shows the watcher sees drops.
				control[0] = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.TRIGGERED);
				control[0].snapTo(controlAt.x, controlAt.y, controlAt.z, 0.0F, 0.0F);
				level.addFreshEntity(control[0]);
				arena.track(control[0]);
				dropMark[0] = DROPS.size();
				for (Mob minion : spawned) {
					deathSpots.add(minion.position());
					minion.setLastHurtByPlayer(player[0], 100);
					minion.hurtServer(level, level.damageSources().playerAttack(player[0]), 1.0E6F);
					killed[0]++;
				}
				controlMark[0] = DROPS.size();
				control[0].setLastHurtByPlayer(player[0], 100);
				control[0].hurtServer(level, level.damageSources().playerAttack(player[0]), 1.0E6F);
				phase[0] = 1;
				phaseStart[0] = helper.getTick();
			} else if (phase[0] == 1 && elapsed >= SETTLE_TICKS) {
				long fromMinions = DROPS.subList(dropMark[0], controlMark[0]).stream()
					.filter(drop -> deathSpots.stream().anyMatch(spot -> spot.distanceToSqr(drop.at()) < 16.0)).count();
				long fromControl = DROPS.subList(controlMark[0], DROPS.size()).stream()
					.filter(drop -> drop.at().distanceToSqr(controlAt) < 16.0).count();
				StellaMobs.LOGGER.info("[BEHAVIOR] test=witherMinionDrops RESULT killed={} minionDrops={} controlDrops={}", killed[0], fromMinions, fromControl);
				helper.assertTrue(fromControl > 0, "the watcher saw no drop from a plain zombie");
				helper.assertTrue(fromMinions == 0, killed[0] + " dead minions dropped " + fromMinions + " items or orbs");
				phase[0] = 2;
				phaseStart[0] = helper.getTick();
			} else if (phase[0] == 2 && WitherMinions.alive(level, wither[0]).size() >= 3) {
				// Phase 3: the Wither dies, and every minion it still has goes with it.
				wither[0].hurtServer(level, level.damageSources().genericKill(), 1.0E9F);
				helper.assertTrue(wither[0].isDeadOrDying(), "the Wither did not die");
				phase[0] = 3;
				phaseStart[0] = helper.getTick();
			} else if (phase[0] == 3 && elapsed >= SETTLE_TICKS) {
				long left = spawned.stream().filter(minion -> !minion.isRemoved()).count();
				StellaMobs.LOGGER.info("[BEHAVIOR] test=witherMinionDeath RESULT spawned={} left={}", spawned.size(), left);
				helper.assertTrue(left == 0, left + " of " + spawned.size() + " minions outlived the Wither");
				helper.succeed();
			}
		});
		arena.whenReady(List.of(new Vec3(28, 3, 28), new Vec3(28, 3, 44)), () -> {
			player[0] = arena.survivalPlayer(28.5, 44.5);
			wither[0] = WitherRig.wither(helper, arena, 28, WideArena.SPAWN_Y, 28, 0);
			phaseStart[0] = helper.getTick();
		});
	}
}
