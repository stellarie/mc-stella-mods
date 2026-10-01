package com.stella.mobs;

import com.stella.mobs.entity.StellaEntities;
import com.stella.mobs.entity.spawn.RedSlimeSpawnRule;
import com.stella.mobs.entity.spawn.SlimeFalloff;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Red and blue slimes thin out with distance from 0,0: 0.85 per 200 blocks, never below 10%. */
public class SlimeFalloffGameTest {
	@GameTest
	public void chanceCurve(final GameTestHelper helper) {
		double[][] cases = {
			{0, 0, 1.0}, {199, 0, 1.0}, {200, 0, 0.85}, {0, -400, 0.7225}, {300, 300, 0.7225}, {3000, 0, 0.10}, {-20000, 5000, 0.10}
		};
		List<String> wrong = new ArrayList<>();
		for (double[] row : cases) {
			double got = SlimeFalloff.chance(row[0], row[1]);
			if (Math.abs(got - row[2]) > 1.0E-9) {
				wrong.add(row[0] + "," + row[1] + "=" + got);
			}
		}
		StellaMobs.LOGGER.info("[BEHAVIOR] test=slimeFalloffCurve RESULT wrong={}", wrong);
		helper.assertTrue(wrong.isEmpty(), "the falloff is wrong at " + wrong);
		helper.succeed();
	}

	/** One roll per attempt: a failing roll stops the attempt, so no other spot gets a second chance. */
	@GameTest
	public void rollGatesSpawn(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ChunkPos chunk = new ChunkPos(origin.getX() >> 4, origin.getZ() >> 4);
		int[] rolls = new int[1];
		int spawned = 0;
		SlimeFalloff.setRoller((random, chance) -> {
			rolls[0]++;
			return false;
		});
		try {
			for (int attempt = 0; attempt < 20; attempt++) {
				if (RedSlimeSpawnRule.INSTANCE.trySpawn(level, chunk, 1_000_000L)) {
					spawned++;
				}
			}
		} finally {
			SlimeFalloff.setRoller(null);
		}
		StellaMobs.LOGGER.info("[BEHAVIOR] test=slimeFalloffGate RESULT spawned={} rolls={}", spawned, rolls[0]);
		helper.assertTrue(rolls[0] > 0 && rolls[0] <= 20, "the attempts rolled " + rolls[0] + " times, expected one per attempt");
		helper.assertTrue(spawned == 0, spawned + " red slimes spawned through a failing roll");
		helper.succeed();
	}
}
