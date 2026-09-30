package com.stella.learn;

import com.stella.learn.gym.Arena;
import com.stella.learn.gym.Body;
import com.stella.learn.gym.Bridge;
import com.stella.learn.gym.GymConfig;
import com.stella.learn.gym.GymEpisode;
import com.stella.learn.gym.Terrain;
import com.stella.learn.gym.brains.Brains;
import com.stella.learn.model.ArcherAction;
import com.stella.learn.model.ModelDriver;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/** A real gym episode, played twice in one arena: the bot that died in the first is hurt by arrows in the second. */
public class GymEpisodeGameTest {
	private static final int INDEX = 991;
	private static final int EPISODES = 3;
	private static final float[] FIRE_AT_THE_AIM_POINT = {0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 2.0F};

	@GameTest(maxTicks = 1800, padding = LearnArena.PADDING, environment = ModelTests.ENV)
	public void botStaysHittableAfterItDies(final GameTestHelper helper) {
		ModelTests.acquire(helper);
		ModelDriver.useSource((mob, observation) -> ArcherAction.STILL);
		helper.runBeforeTestEnd(ModelDriver::useNoPolicy);
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
		GymConfig config = new GymConfig(0, 1, false, 2, 600, 1L, 20, Terrain.A, true, false, "hittable");
		GymEpisode episode = new GymEpisode(arena, config);
		Body.Range frail = new Body.Range(10, 10, 0, 0, 0, 0);
		int[] played = {0};
		int[] wins = {0};
		double[] dealt = new double[EPISODES];
		episode.start(500L, Brains.STATIC, Terrain.A, 600, frail);
		played[0] = 1;
		LearnArena.whenReady(helper, List.of(episode.bot().player()), () -> LearnArena.pollUntil(helper, () -> {
			if (helper.getTick() % 2 == 0) {
				GymEpisode.Frame frame = episode.poll(2);
				if (frame != null && (frame.flags() & Bridge.FLAG_TERMINAL) != 0) {
					if ((frame.flags() & Bridge.FLAG_WIN) != 0) {
						wins[0]++;
					}

					var bot = episode.bot().player();
					StellaLearn.LOGGER.info("[BEHAVIOR] test=botStaysHittableAfterItDies BOT_AFTER_EPISODE played={} removed={} alive={} health={} invulnerableTime={} hurtTime={} deathTime={} pose={} box={} pickable={} hitByProjectile={} inLookup={} invulnerable={} loaded={}",
						played[0], bot.isRemoved(), bot.isAlive(), bot.getHealth(), bot.getInvulnerableTime(), bot.hurtTime, bot.deathTime, bot.getPose(), bot.getBoundingBox().getYsize(), bot.isPickable(), bot.canBeHitByProjectile(),
						!level.getEntities((net.minecraft.world.entity.Entity) null, bot.getBoundingBox().inflate(0.5), e -> e == bot).isEmpty(), bot.isInvulnerable(), bot.connection.hasClientLoaded());
					if (played[0] < EPISODES) {
						episode.start(500L + played[0], Brains.STATIC, Terrain.A, 600, frail);
						played[0]++;
					} else {
						played[0]++;
					}
				} else if (frame != null) {
					episode.act(FIRE_AT_THE_AIM_POINT);
				}
			}

			return played[0] > EPISODES;
		}, 1700, () -> {
			StellaLearn.LOGGER.info("[BEHAVIOR] test=botStaysHittableAfterItDies RESULT episodes={} wins={}", EPISODES, wins[0]);
			helper.assertTrue(wins[0] == EPISODES, "the archer killed the bot in " + wins[0] + " of " + EPISODES + " episodes; a bot that died once is not hittable");
			ModelTests.pass(helper);
		}, () -> "the episodes never finished: played " + played[0] + ", wins " + wins[0]));
	}
}
