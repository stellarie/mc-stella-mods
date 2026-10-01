package com.stella.mobs;

import com.stella.mobs.entity.RedSlime;
import com.stella.mobs.entity.StellaEntities;
import com.stella.mobs.entity.spawn.LightSources;
import com.stella.mobs.entity.spawn.RedSlimeSpawnRule;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/** The red and blue slime rules keep 7 blocks, in a straight line, from any light-emitting block. */
public class SlimeLightRuleGameTest {
	private static final int SPAWNS = 30;

	@GameTest
	public void radius(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos light = helper.absolutePos(new BlockPos(1, 2, 1));
		level.setBlockAndUpdate(light, Blocks.GLOWSTONE.defaultBlockState());
		boolean straight = LightSources.near(level, light.offset(7, 0, 0), LightSources.RADIUS);
		boolean beyond = LightSources.near(level, light.offset(8, 0, 0), LightSources.RADIUS);
		boolean diagonalIn = LightSources.near(level, light.offset(4, 4, 4), LightSources.RADIUS);
		boolean diagonalOut = LightSources.near(level, light.offset(5, 5, 0), LightSources.RADIUS);
		level.setBlockAndUpdate(light, Blocks.AIR.defaultBlockState());
		boolean dark = LightSources.near(level, light.offset(1, 0, 0), LightSources.RADIUS);
		StellaMobs.LOGGER.info("[BEHAVIOR] test=slimeLightRadius RESULT straight={} beyond={} diagonalIn={} diagonalOut={} dark={}",
			straight, beyond, diagonalIn, diagonalOut, dark);
		helper.assertTrue(straight && diagonalIn, "a light 7 blocks away was missed: straight " + straight + ", diagonal " + diagonalIn);
		helper.assertTrue(!beyond && !diagonalOut && !dark, "a far or removed light counted: " + beyond + ", " + diagonalOut + ", " + dark);
		helper.succeed();
	}

	/** Thirty red slimes spawned in a chunk with a glowstone block in its middle all stand more than 7 blocks from it. */
	@GameTest(maxTicks = 100)
	public void rulesSkipLitSpots(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ChunkPos chunk = new ChunkPos(origin.getX() >> 4, origin.getZ() >> 4);
		int x = chunk.getMinBlockX() + 8;
		int z = chunk.getMinBlockZ() + 8;
		BlockPos light = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		level.setBlockAndUpdate(light, Blocks.GLOWSTONE.defaultBlockState());
		Set<UUID> before = new HashSet<>();
		level.getEntities(StellaEntities.RED_SLIME, slime -> true).forEach(slime -> before.add(slime.getUUID()));
		List<Double> distances = new ArrayList<>();
		for (int attempt = 0; attempt < 600 && distances.size() < SPAWNS; attempt++) {
			if (!RedSlimeSpawnRule.INSTANCE.trySpawn(level, chunk, 1_000_000L)) {
				continue;
			}
			for (RedSlime slime : level.getEntities(StellaEntities.RED_SLIME, slime -> slime.isAlive() && !before.contains(slime.getUUID()))) {
				distances.add(Math.sqrt(slime.blockPosition().distSqr(light)));
				slime.discard();
			}
		}
		level.setBlockAndUpdate(light, Blocks.AIR.defaultBlockState());
		long close = distances.stream().filter(distance -> distance <= LightSources.RADIUS).count();
		StellaMobs.LOGGER.info("[BEHAVIOR] test=slimeLightRule RESULT spawns={} close={} distances={}", distances.size(), close, distances);
		helper.assertTrue(distances.size() >= 20, "only " + distances.size() + " red slimes spawned");
		helper.assertTrue(close == 0, close + " red slimes spawned within 7 blocks of the light");
		helper.succeed();
	}
}
