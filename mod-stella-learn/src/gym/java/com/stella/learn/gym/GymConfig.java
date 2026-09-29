package com.stella.learn.gym;

/** Gym settings from JVM system properties. Port 0 leaves the gym off, which is what every GameTest run sees. */
public record GymConfig(int port, int arenas, boolean sprint, int decisionTicks, int episodeTicks, long seed, int sampleTicks) {
	public static final String PORT = "stella.gym.port";
	public static final String ARENAS = "stella.gym.arenas";
	public static final String SPRINT = "stella.gym.sprint";
	public static final String DECISION_TICKS = "stella.gym.decisionTicks";
	public static final String EPISODE_TICKS = "stella.gym.episodeTicks";
	public static final String SEED = "stella.gym.seed";
	public static final String SAMPLE_TICKS = "stella.gym.sampleTicks";

	public static GymConfig fromProperties() {
		return new GymConfig(
			Math.max(0, Integer.getInteger(PORT, 0)),
			Math.max(1, Integer.getInteger(ARENAS, 32)),
			Boolean.parseBoolean(System.getProperty(SPRINT, "true")),
			Math.max(1, Integer.getInteger(DECISION_TICKS, 2)),
			Math.max(1, Integer.getInteger(EPISODE_TICKS, 1200)),
			Long.getLong(SEED, 1L),
			Math.max(1, Integer.getInteger(SAMPLE_TICKS, 20))
		);
	}

	public boolean enabled() {
		return port > 0;
	}

	public String detail() {
		return "port=" + port + " arenas=" + arenas + " sprint=" + sprint + " decisionTicks=" + decisionTicks
			+ " episodeTicks=" + episodeTicks + " seed=" + seed + " sampleTicks=" + sampleTicks;
	}
}
