package com.stella.learn;

import com.stella.learn.gym.Arena;
import com.stella.learn.gym.Body;
import com.stella.learn.gym.GymConfig;
import com.stella.learn.gym.GymEpisode;
import com.stella.learn.gym.Terrain;
import com.stella.learn.gym.brains.Brains;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** A Stage C episode scales both walkers by its seed, and the next episode starts from the base speed again. */
public class GymSpeedGameTest {
	private static final int INDEX = 992;
	private static final long SEED = 42L;

	@GameTest(maxTicks = 200, padding = LearnArena.PADDING)
	public void episodeSpeedsFollowTheSeedAndAreRemovedAtReset(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos start = helper.absolutePos(new BlockPos(0, 20, 0));
		BlockPos origin = new BlockPos((start.getX() & ~15) + 8, start.getY(), (start.getZ() & ~15) + 8);
		for (int cx = (origin.getX() - 1) >> 4; cx <= (origin.getX() + Arena.OPEN) >> 4; cx++) {
			for (int cz = (origin.getZ() - 1) >> 4; cz <= (origin.getZ() + Arena.OPEN) >> 4; cz++) {
				int x = cx;
				int z = cz;
				level.setChunkForced(x, z, true);
				level.getChunk(x, z);
				helper.runBeforeTestEnd(() -> level.setChunkForced(x, z, false));
			}
		}

		Arena arena = new Arena(INDEX, level, origin);
		arena.build();
		GymEpisode episode = new GymEpisode(arena, new GymConfig(0, 1, false, 2, 600, 1L, 20, Terrain.A, true, false, "speed"));
		Body.Range wide = new Body.Range(20, 60, 20, 12, 4, 0, 600, 1400);
		Body drawn = Body.draw(SEED, wide);
		helper.assertTrue(Math.abs(drawn.archerSpeed() - drawn.botSpeed()) > 0.2, "the seed does not give two different speeds: " + drawn.detail());
		episode.start(SEED, Brains.STATIC, Terrain.A, 600, wide);
		double archerScaled = episode.mob().getAttributeValue(Attributes.MOVEMENT_SPEED);
		double botScaled = episode.bot().player().getAttributeValue(Attributes.MOVEMENT_SPEED);
		episode.start(SEED, Brains.STATIC, Terrain.A, 600, new Body.Range(20, 60, 20, 12, 4, 0));
		double archerBase = episode.mob().getAttributeValue(Attributes.MOVEMENT_SPEED);
		double botBase = episode.bot().player().getAttributeValue(Attributes.MOVEMENT_SPEED);
		StellaLearn.LOGGER.info("[BEHAVIOR] test=episodeSpeeds RESULT archer={}/{} bot={}/{} drawn={}", archerScaled, archerBase, botScaled, botBase, drawn.detail());
		helper.assertTrue(Math.abs(archerScaled / archerBase - drawn.archerSpeed()) < 0.02, "the archer speed is " + archerScaled + " against a base of " + archerBase);
		helper.assertTrue(Math.abs(botScaled / botBase - drawn.botSpeed()) < 0.02, "the bot speed is " + botScaled + " against a base of " + botBase);
		LearnArena.whenReady(helper, List.of(episode.bot().player()), () -> {
			episode.abandon();
			helper.succeed();
		});
	}
}
