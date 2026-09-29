package com.stella.learn.gym;

import com.stella.learn.LearnLog;

/** Gym lines in the `LearnLog` format, so the `behavior_log` switch and the test tap cover them too. */
public final class GymLog {
	public static final String CONFIG = "gymConfig";
	public static final String ARENA = "gymArena";
	public static final String EPISODE = "gymEpisode";
	public static final String BOT = "gymBot";
	public static final String BRIDGE = "gymBridge";
	public static final String STEP = "gymStep";

	/** The fixed `reason=` values a gym error or fallback line may carry. */
	public static final String READ_TIMEOUT = "readTimeout";
	public static final String CLIENT_BYE = "clientBye";
	public static final String CLIENT_GONE = "clientGone";
	public static final String BAD_FRAME = "badFrame";
	public static final String SHUTDOWN = "shutdown";
	public static final String LISTEN_FAILED = "listenFailed";
	public static final String SPAWN_FAILED = "spawnFailed";

	private GymLog() {
	}

	public static void info(final String component, final String verb, final String detail) {
		LearnLog.info(component, verb, detail);
	}

	public static void debug(final String component, final String verb, final String detail) {
		LearnLog.debug(component, verb, detail);
	}
}
