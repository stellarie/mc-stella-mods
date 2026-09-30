package com.stella.learn;

import com.stella.learn.gym.Terrain;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

/** Arena layouts come from a seed alone, and both fighters can always walk to each other. */
public class ArenaTerrainGameTest {
	private static final int SEEDS = 200;

	@GameTest
	public void everyLayoutHasAPath(final GameTestHelper helper) {
		Set<String> seen = new HashSet<>();
		int checked = 0;
		for (int stage : new int[] {Terrain.B, Terrain.C, Terrain.RELEASE}) {
			for (long seed = 1; seed <= SEEDS; seed++) {
				Terrain terrain = Terrain.generate(seed, stage);
				checked++;
				helper.assertTrue(terrain.connected(), "seed " + seed + " stage " + stage + " has no path: " + terrain.features());
				helper.assertTrue(terrain.pathBetween(terrain.mobSpawn(), terrain.botSpawn()), "no walk from the mob to the bot for seed " + seed);
				helper.assertTrue(terrain.pathBetween(terrain.botSpawn(), terrain.mobSpawn()), "no walk from the bot to the mob for seed " + seed);
				BlockPos a = terrain.mobSpawn();
				BlockPos b = terrain.botSpawn();
				double gap = Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
				helper.assertTrue(gap >= Terrain.MIN_SPAWN_GAP, "seed " + seed + " spawns " + gap + " blocks apart");
				helper.assertTrue(terrain.cell(a.getX(), 0, a.getZ()).isAir() && terrain.cell(b.getX(), 0, b.getZ()).isAir(),
					"seed " + seed + " puts a spawn inside a block");
				seen.addAll(terrain.features());
			}
		}

		// The flat stage never adds a feature.
		for (long seed = 1; seed <= SEEDS; seed++) {
			Terrain flat = Terrain.generate(seed, Terrain.A);
			helper.assertTrue(flat.connected() && flat.features().isEmpty(), "stage A seed " + seed + " is not flat: " + flat.features());
		}

		List<String> missing = new ArrayList<>(Terrain.FEATURES.subList(1, Terrain.FEATURES.size()));
		missing.removeAll(seen);
		StellaLearn.LOGGER.info("[BEHAVIOR] test=everyLayoutHasAPath RESULT layouts={} featuresSeen={}", checked, seen);
		helper.assertTrue(missing.isEmpty(), "no layout used the features " + missing);
		helper.succeed();
	}

	@GameTest
	public void sameSeedSameLayout(final GameTestHelper helper) {
		for (int stage : new int[] {Terrain.A, Terrain.B, Terrain.C, Terrain.RELEASE}) {
			for (long seed : new long[] {1L, 2L, 17L, 99L, 1_000_003L}) {
				Terrain first = Terrain.generate(seed, stage);
				Terrain second = Terrain.generate(seed, stage);
				for (int dx = 0; dx < 16; dx++) {
					for (int dy = -4; dy < 16; dy++) {
						for (int dz = 0; dz < 16; dz++) {
							helper.assertTrue(first.cell(dx, dy, dz) == second.cell(dx, dy, dz),
								"seed " + seed + " stage " + stage + " differs at " + dx + "," + dy + "," + dz);
						}
					}
				}

				helper.assertTrue(first.mobSpawn().equals(second.mobSpawn()) && first.botSpawn().equals(second.botSpawn()), "seed " + seed + " moved a spawn");
			}
		}

		Terrain a = Terrain.generate(5L, Terrain.C);
		Terrain b = Terrain.generate(6L, Terrain.C);
		boolean differ = !a.mobSpawn().equals(b.mobSpawn()) || !a.features().equals(b.features());
		helper.assertTrue(differ, "two seeds gave the same layout");
		helper.succeed();
	}
}
