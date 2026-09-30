package com.stella.learn.gym;

import com.stella.learn.LearnLog;

/**
 * Gym lines in the `LearnLog` format, so the test tap covers them. A gym server turns `behavior_log` off to keep the
 * other lines of the mod out of its log; {@link #keep} lets the gym's own lines through anyway.
 */
public final class GymLog {
	public static final String CONFIG = "gymConfig";
	public static final String ARENA = "gymArena";
	public static final String EPISODE = "gymEpisode";
	public static final String BOT = "gymBot";
	public static final String BRIDGE = "gymBridge";
	public static final String STEP = "gymStep";
	public static final String TERRAIN = "gymTerrain";
	public static final String EVAL = "gymEval";
	public static final String PROFILE = "gymProfile";
	public static final String HEALTH = "gymHealth";

	/** The fixed `reason=` values a gym error or fallback line may carry. */
	public static final String READ_TIMEOUT = "readTimeout";
	public static final String CLIENT_BYE = "clientBye";
	public static final String CLIENT_GONE = "clientGone";
	public static final String BAD_FRAME = "badFrame";
	public static final String SHUTDOWN = "shutdown";
	public static final String LISTEN_FAILED = "listenFailed";
	public static final String SPAWN_FAILED = "spawnFailed";
	public static final String NOT_DRIVEN = "notDriven";
	public static final String BRAIN_ERROR = "brainError";
	public static final String BAD_RESET = "badReset";

	private GymLog() {
	}

	/** Keeps the gym lines and the model lines in a log whose `behavior_log` switch is off. */
	public static void keepWhenBehaviorLogIsOff() {
		LearnLog.keepWhenOff((test, verb) -> test.startsWith("gym") || "learnModel".equals(test) && !"RELEASE".equals(verb));
	}

	public static void info(final String component, final String verb, final String detail) {
		LearnLog.info(component, verb, detail);
	}

	public static void debug(final String component, final String verb, final String detail) {
		LearnLog.debug(component, verb, detail);
	}
}
