package com.stella.learn.gym;

import com.stella.learn.gym.brains.Brains;

/** With `stella.gym.eval=true` every finished episode writes one `gymEval RESULT` line, for the eval reports. */
final class EvalMode {
	private EvalMode() {
	}

	static void result(final GymConfig config, final GymEpisode episode) {
		if (!config.eval()) {
			return;
		}

		String result = switch (episode.endReason()) {
			case GymEpisode.BOT_DIED -> "win";
			case GymEpisode.MOB_DIED -> "loss";
			case GymEpisode.TIMEOUT -> "timeout";
			default -> "error";
		};
		GymLog.info(GymLog.EVAL, "RESULT", "suite=" + config.suite() + " baseline=" + config.baseline() + " arena=" + episode.arena().index()
			+ " brain=" + Brains.name(episode.brainIndex()) + " seed=" + episode.seed() + " stage=" + GymConfig.stageName(episode.stage())
			+ " result=" + result + " reason=" + episode.endReason() + " ticks=" + episode.ticks());
	}
}
