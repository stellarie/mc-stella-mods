package com.stella.learn;

import com.stella.learn.gym.Arena;
import com.stella.learn.gym.Bridge;
import com.stella.learn.gym.GymConfig;
import com.stella.learn.gym.GymEpisode;
import com.stella.learn.gym.Terrain;
import com.stella.learn.gym.brains.Brains;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/** One short gym episode writes the training lines the log tools count: the layout, the bot, and the end. */
public class GymLogGameTest {
	private static final int INDEX = 990;
	private static final long SEED = 4242L;
	private static final int EPISODE_TICKS = 40;
	private static final int DECISION_TICKS = 2;

	@GameTest(maxTicks = 600, padding = LearnArena.PADDING)
	public void trainingLinesAppear(final GameTestHelper helper) {
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
		GymConfig config = new GymConfig(0, 1, false, DECISION_TICKS, EPISODE_TICKS, 1L, 20, Terrain.A, true, false, "logtest");
		GymEpisode episode = new GymEpisode(arena, config);
		int brain = Brains.indexOf("SwordSweeper");
		episode.start(SEED, brain, Terrain.B);
		Mob mob = episode.mob();
		mob.setNoAi(true);
		episode.forceLive();
		LearnArena.whenReady(helper, List.of(episode.bot().player(), mob), () -> {
			int[] polls = {0};
			GymEpisode.Frame[] last = {null};
			LearnArena.pollUntil(helper, () -> {
				if (helper.getTick() % DECISION_TICKS == 0 && !episode.ended()) {
					last[0] = episode.poll(DECISION_TICKS);
					polls[0]++;
				}

				return episode.ended();
			}, 2 * EPISODE_TICKS + 40, () -> {
				String tag = "seed=" + SEED;
				List<String> terrain = LearnTestInit.linesWith("gymTerrain BUILD", tag, "stage=1");
				List<String> bot = LearnTestInit.linesWith("gymBot BRAIN", "arena=" + INDEX, tag, "brain=SwordSweeper");
				List<String> end = LearnTestInit.linesWith("gymEpisode END", "arena=" + INDEX, tag, "reason=timeout");
				List<String> eval = LearnTestInit.linesWith("gymEval RESULT", "arena=" + INDEX, tag, "result=timeout");
				StellaLearn.LOGGER.info("[BEHAVIOR] test=trainingLinesAppear RESULT polls={} terrain={} bot={} end={} eval={}", polls[0], terrain.size(), bot.size(), end.size(), eval.size());
				helper.assertTrue(terrain.size() == 1, "gymTerrain BUILD lines: " + terrain.size());
				helper.assertTrue(terrain.get(0).contains("features=") && terrain.get(0).contains("path=true"), "the terrain line lacks features or the path check: " + terrain.get(0));
				helper.assertTrue(bot.size() == 1, "gymBot BRAIN lines: " + bot.size());
				helper.assertTrue(bot.get(0).contains("aimError=") && bot.get(0).contains("reaction=") && bot.get(0).contains("gear="), "the brain line lacks the skill values: " + bot.get(0));
				helper.assertTrue(end.size() == 1, "gymEpisode END lines: " + end.size());
				String line = end.get(0);
				helper.assertTrue(line.contains("terminal=") && line.contains("damageDealt=") && line.contains("damageTaken=") && line.contains("ticks=" + EPISODE_TICKS),
					"the end line lacks the reward terms: " + line);
				helper.assertTrue(eval.size() == 1, "gymEval RESULT lines: " + eval.size());
				helper.assertTrue(last[0] != null && (last[0].flags() & Bridge.FLAG_TERMINAL) != 0 && (last[0].flags() & Bridge.FLAG_TRUNCATED) != 0, "the last frame is not a truncated end");
				helper.succeed();
			}, () -> "the short episode never ended");
		});
	}
}
