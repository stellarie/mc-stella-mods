package com.stella.tower;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/** Mob pools by biome, and the cost of drawing, painting and building a biome floor (T15). */
public class TowerBiomePoolsGameTest {
	private static final long SEED = 0x91C0_B10E_0006L;

	/** Catches a floor that raises another kind than its biome's: spawns, guardians and ambushes all draw from the biome's pool. */
	@GameTest(maxTicks = 600)
	public void everyBiomeFloorRaisesOnlyItsOwnKinds(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		long seed = tower.getSeed();
		StringBuilder report = new StringBuilder();
		int wrong = 0;
		int empty = 0;
		for (FloorBiome biome : FloorBiome.DRAWN) {
			int floor = TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerElites.BOSS_EVERY != 0 && FloorBiomes.of(seed, f) == biome);
			TowerFloors.ensure(tower, floor);
			Set<EntityType<?>> allowed = new HashSet<>(BiomePools.of(biome));
			Set<EntityType<?>> seen = new HashSet<>();
			List<Mob> raised = new ArrayList<>();
			for (int i = 0; i < 40; i++) {
				Mob mob = TowerMobs.spawnOne(tower, floor);
				if (mob != null) {
					raised.add(mob);
					seen.add(mob.getType());
				}
			}
			raised.forEach(Mob::discard);
			Set<EntityType<?>> guardians = new HashSet<>(TowerElites.guardianPool(tower, floor));
			wrong += allowed.containsAll(seen) && allowed.containsAll(guardians) && TowerMobs.poolOf(tower, floor).equals(BiomePools.of(biome)) ? 0 : 1;
			empty += raised.size() < 20 || seen.size() < 2 ? 1 : 0;
			report.append(biome).append(" raised=").append(raised.size()).append(" kinds=").append(seen.size()).append("; ");
		}
		TowerTestSupport.result("biomePools", report.toString());
		helper.assertTrue(wrong == 0, wrong + " biomes raised a kind outside their pool: " + report);
		helper.assertTrue(empty == 0, empty + " biomes raised fewer than 20 mobs or one kind: " + report);
		helper.succeed();
	}

	/** Catches a great hall that raises its own pool: it raises the pool of the biome below it. */
	@GameTest(maxTicks = 400)
	public void aGreatHallRaisesThePoolOfTheBiomeBelow(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		long seed = tower.getSeed();
		int floor = TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerElites.BOSS_EVERY == 0);
		TowerFloors.ensure(tower, floor);
		List<EntityType<? extends Mob>> pool = TowerMobs.poolOf(tower, floor);
		List<EntityType<? extends Mob>> below = BiomePools.of(FloorBiomes.of(seed, floor - 1));
		helper.assertTrue(pool.equals(below), "the hall of floor " + floor + " raises " + pool + ", not the pool of the biome below, " + below);
		helper.succeed();
	}

	/** Catches an older floor whose pool changed: its pool is still the floor number's. */
	@GameTest
	public void anOlderFloorKeepsThePoolOfItsNumber(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloor();
		TowerFloors.ensure(tower, floor);
		helper.assertTrue(TowerMobs.poolOf(tower, floor).equals(TowerMobs.pool(floor)), "an older floor takes a biome's pool");
		helper.succeed();
	}

	/**
	 * T15, the cost of a biome floor: the plan drawn off the server thread within 50 ms (the median, and the 95th
	 * percentile within three times that), the biome painted a chunk at a time within 5 ms, and a floor built at
	 * the pace of its cells. The numbers are recorded with the machine they ran on.
	 */
	@GameTest(maxTicks = 600)
	public void aBiomeFloorCostsLittleToDrawPaintAndBuild(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		for (int i = 0; i < 20; i++) {
			BiomeGen.plan(SEED, 1000 + i);
		}
		List<Double> millis = new ArrayList<>();
		for (FloorBiome biome : FloorBiome.DRAWN) {
			int own = 0;
			for (int floor = 2; own < 8 && floor < 3000; floor++) {
				if (floor % TowerElites.BOSS_EVERY == 0 || FloorBiomes.of(SEED, floor) != biome) {
					continue;
				}
				long started = System.nanoTime();
				TowerPlan plan = BiomeGen.plan(SEED, floor);
				TowerVolume.of(plan);
				millis.add((System.nanoTime() - started) / 1.0E6);
				own++;
			}
		}
		Collections.sort(millis);
		double median = millis.get(millis.size() / 2);
		double p95 = millis.get(millis.size() * 95 / 100);
		double longest = millis.get(millis.size() - 1);
		long seed = tower.getSeed();
		int floor = TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerElites.BOSS_EVERY != 0 && FloorBiomes.of(seed, f) == FloorBiome.RAINY_FOREST);
		long built = System.nanoTime();
		TowerFloors.ensure(tower, floor);
		double buildMillis = (System.nanoTime() - built) / 1.0E6;
		TowerPlan plan = TowerPlan.at(tower, floor);
		double cellsPerSecond = TowerFloors.cellsOf(plan) / (buildMillis / 1000.0);
		List<Double> paints = new ArrayList<>();
		net.minecraft.core.BlockPos origin = plan.origin();
		for (int cx = (origin.getX() - 1) >> 4; cx <= (origin.getX() + TowerLayout.SIZE) >> 4; cx++) {
			for (int cz = (origin.getZ() - 1) >> 4; cz <= (origin.getZ() + TowerLayout.SIZE) >> 4; cz++) {
				long painted = System.nanoTime();
				TowerBiomes.paintChunk(tower, cx, cz, FloorBiome.RAINY_FOREST);
				paints.add((System.nanoTime() - painted) / 1.0E6);
			}
		}
		Collections.sort(paints);
		double paintMedian = paints.get(paints.size() / 2);
		TowerTestSupport.result("biomeCost", "plans=" + millis.size() + " medianMs=" + median + " p95Ms=" + p95 + " longestMs=" + longest + " buildMs=" + buildMillis
			+ " cellsPerSecond=" + cellsPerSecond + " paintMedianMs=" + paintMedian + " paintLongestMs=" + paints.get(paints.size() - 1) + " cells=" + TowerFloors.cellsOf(plan)
			+ " cpus=" + Runtime.getRuntime().availableProcessors() + " java=" + System.getProperty("java.version") + " os=" + System.getProperty("os.name"));
		helper.assertTrue(median <= 50.0, "the median biome plan takes " + median + " ms to draw");
		helper.assertTrue(p95 <= 150.0, "the 95th percentile of a biome plan is " + p95 + " ms");
		helper.assertTrue(paintMedian <= 5.0, "a chunk's paint takes " + paintMedian + " ms at the median");
		helper.succeed();
	}
}
