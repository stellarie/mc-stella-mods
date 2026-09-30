package com.stella.learn.gym;

/** Gym settings from JVM system properties. Port 0 leaves the gym off, which is what every GameTest run sees. */
public record GymConfig(
	int port, int arenas, boolean sprint, int decisionTicks, int episodeTicks, long seed, int sampleTicks,
	int stage, boolean eval, boolean baseline, String suite
) {
	public static final String PORT = "stella.gym.port";
	public static final String ARENAS = "stella.gym.arenas";
	public static final String SPRINT = "stella.gym.sprint";
	public static final String DECISION_TICKS = "stella.gym.decisionTicks";
	public static final String EPISODE_TICKS = "stella.gym.episodeTicks";
	public static final String SEED = "stella.gym.seed";
	public static final String SAMPLE_TICKS = "stella.gym.sampleTicks";
	/** The stage a Reset frame falls back to when it names none: A, B, C, or release. */
	public static final String STAGE = "stella.gym.stage";
	/** Eval mode logs one `gymEval RESULT` line per episode. */
	public static final String EVAL = "stella.gym.eval";
	/** Baseline mode drives no mob by a model; the current goals fight. */
	public static final String BASELINE = "stella.gym.baseline";
	/** The suite name, for the log only. The trainer picks the seeds and brains. */
	public static final String SUITE = "stella.gym.suite";

	public static GymConfig fromProperties() {
		return new GymConfig(
			Math.max(0, Integer.getInteger(PORT, 0)),
			Math.max(1, Integer.getInteger(ARENAS, 32)),
			Boolean.parseBoolean(System.getProperty(SPRINT, "true")),
			Math.max(1, Integer.getInteger(DECISION_TICKS, 2)),
			Math.max(1, Integer.getInteger(EPISODE_TICKS, 1200)),
			Long.getLong(SEED, 1L),
			Math.max(1, Integer.getInteger(SAMPLE_TICKS, 20)),
			stageCode(System.getProperty(STAGE, "A")),
			Boolean.parseBoolean(System.getProperty(EVAL, "false")),
			Boolean.parseBoolean(System.getProperty(BASELINE, "false")),
			System.getProperty(SUITE, "none")
		);
	}

	public static int stageCode(final String name) {
		return switch (name.toLowerCase(java.util.Locale.ROOT)) {
			case "a" -> Terrain.A;
			case "b" -> Terrain.B;
			case "c" -> Terrain.C;
			case "release" -> Terrain.RELEASE;
			default -> throw new IllegalArgumentException("unknown stage " + name);
		};
	}

	public static String stageName(final int code) {
		return switch (code) {
			case Terrain.A -> "A";
			case Terrain.B -> "B";
			case Terrain.C -> "C";
			case Terrain.RELEASE -> "release";
			default -> "?";
		};
	}

	public boolean enabled() {
		return port > 0;
	}

	public String detail() {
		return "port=" + port + " arenas=" + arenas + " sprint=" + sprint + " decisionTicks=" + decisionTicks
			+ " episodeTicks=" + episodeTicks + " seed=" + seed + " sampleTicks=" + sampleTicks + " stage=" + stageName(stage)
			+ " eval=" + eval + " baseline=" + baseline + " suite=" + suite;
	}
}
