package com.stella.mobs;

import com.stella.mobs.behavior.WitherState;
import com.stella.mobs.behavior.WitherStats;
import com.stella.mobs.config.WitherConfig;
import com.stella.mobs.config.WitherConfigStore;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/**
 * The Wither's stats go to the rpg mod through its object share: vitality 200 and the rest from 50 to 150. Without the
 * share the spawn still completes. The rpg mod finds the encounter mark by the name of `WitherState.hasWitherFlag`.
 */
public final class WitherStatsBridgeGameTest {
	private static final Set<String> SIX = Set.of("str", "agi", "vit", "dex", "int", "luk");
	private static final int DRAWS = 3000;
	private static final long SEED = 2718L;

	private static WitherBoss spawn(final GameTestHelper helper) {
		WitherBoss wither = EntityTypes.WITHER.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 12.0, 1.5));
		wither.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		helper.getLevel().addFreshEntity(wither);
		return wither;
	}

	@GameTest
	public void theRollKeepsVitalityAndBoundsTheRest(final GameTestHelper helper) {
		WitherConfig config = WitherConfigStore.get();
		int low = config.getInt(WitherConfig.STATS_MIN);
		int high = config.getInt(WitherConfig.STATS_MAX);
		RandomSource random = RandomSource.create(SEED);
		int lowest = Integer.MAX_VALUE;
		int highest = Integer.MIN_VALUE;
		for (int i = 0; i < DRAWS; i++) {
			Map<String, Integer> stats = WitherStats.roll(random, config);
			helper.assertTrue(stats.keySet().equals(SIX), "the roll holds " + stats.keySet());
			helper.assertTrue(stats.get("vit") == config.getInt(WitherConfig.STATS_VIT), "vitality " + stats.get("vit"));
			for (Map.Entry<String, Integer> entry : stats.entrySet()) {
				if (!entry.getKey().equals("vit")) {
					lowest = Math.min(lowest, entry.getValue());
					highest = Math.max(highest, entry.getValue());
				}
			}
		}
		StellaMobs.LOGGER.info("[BEHAVIOR] test=witherStatRoll RESULT low={} high={} vit={}", lowest, highest, config.getInt(WitherConfig.STATS_VIT));
		WitherConfig shipped = WitherConfig.DEFAULTS;
		helper.assertTrue(shipped.getInt(WitherConfig.STATS_VIT) == 200 && shipped.getInt(WitherConfig.STATS_MIN) == 50 && shipped.getInt(WitherConfig.STATS_MAX) == 150,
			"the shipped bounds are not 200, 50 and 150");
		helper.assertTrue(lowest >= 50 && highest <= 150, "a stat fell outside 50 to 150: " + lowest + " to " + highest);
		helper.assertTrue(lowest <= 52 && highest >= 148, "the draws never neared the ends: " + lowest + " to " + highest);
		helper.succeed();
	}

	/** The share is global, so the body runs inside one tick and puts back what it found. */
	@GameTest
	public void theShareReceivesTheRoll(final GameTestHelper helper) {
		Map<Mob, Map<String, Integer>> got = new HashMap<>();
		BiConsumer<Mob, Map<String, Integer>> standIn = got::put;
		Object original = FabricLoader.getInstance().getObjectShare().put(WitherStats.SHARE_KEY, standIn);
		WitherBoss wither;
		try {
			wither = spawn(helper);
		} finally {
			FabricLoader.getInstance().getObjectShare().remove(WitherStats.SHARE_KEY);
			if (original != null) {
				FabricLoader.getInstance().getObjectShare().put(WitherStats.SHARE_KEY, original);
			}
		}
		Map<String, Integer> stats = got.get(wither);
		StellaMobs.LOGGER.info("[BEHAVIOR] test=witherStatsBridge RESULT stats={}", stats);
		helper.assertTrue(stats != null && stats.keySet().equals(SIX), "the share was not given six stats: " + stats);
		helper.assertTrue(stats.get("vit") == 200, "vitality " + stats.get("vit"));
		stats.forEach((key, value) -> helper.assertTrue(key.equals("vit") || (value >= 50 && value <= 150), key + " is " + value));
		wither.discard();
		helper.succeed();
	}

	@GameTest
	public void withoutTheShareTheSpawnCompletes(final GameTestHelper helper) {
		Object original = FabricLoader.getInstance().getObjectShare().remove(WitherStats.SHARE_KEY);
		WitherBoss wither;
		try {
			wither = spawn(helper);
		} finally {
			if (original != null) {
				FabricLoader.getInstance().getObjectShare().put(WitherStats.SHARE_KEY, original);
			}
		}
		helper.assertTrue(WitherState.hasWitherFlag(wither) && wither.isAlive(), "the Wither did not spawn as an encounter without the share");
		wither.discard();
		helper.succeed();
	}

	@GameTest
	public void aFailingShareDoesNotStopTheSpawn(final GameTestHelper helper) {
		BiConsumer<Mob, Map<String, Integer>> failing = (mob, stats) -> {
			throw new IllegalStateException("share failure");
		};
		Object original = FabricLoader.getInstance().getObjectShare().put(WitherStats.SHARE_KEY, failing);
		WitherBoss wither;
		try {
			wither = spawn(helper);
		} finally {
			FabricLoader.getInstance().getObjectShare().remove(WitherStats.SHARE_KEY);
			if (original != null) {
				FabricLoader.getInstance().getObjectShare().put(WitherStats.SHARE_KEY, original);
			}
		}
		helper.assertTrue(WitherState.hasWitherFlag(wither) && wither.isAlive(), "a failing share stopped the Wither spawning");
		wither.discard();
		helper.succeed();
	}

	/** The rpg mod resolves the flag with this exact name, class and type. */
	@GameTest
	public void theRpgReaderFindsTheFlag(final GameTestHelper helper) throws ReflectiveOperationException {
		MethodHandle flag = MethodHandles.publicLookup().findStatic(
			Class.forName("com.stella.mobs.behavior.WitherState"), "hasWitherFlag", MethodType.methodType(boolean.class, Mob.class)
		);
		WitherBoss encounter = spawn(helper);
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new Vec3(1.5, 2.0, 1.5));
		try {
			boolean onWither = (boolean) flag.invokeExact((Mob) encounter);
			boolean onZombie = (boolean) flag.invokeExact((Mob) zombie);
			StellaMobs.LOGGER.info("[BEHAVIOR] test=witherFlagReader RESULT wither={} zombie={}", onWither, onZombie);
			helper.assertTrue(onWither && !onZombie, "the reader gave " + onWither + " and " + onZombie);
		} catch (Throwable failure) {
			throw new AssertionError(failure);
		}
		encounter.discard();
		helper.succeed();
	}
}
