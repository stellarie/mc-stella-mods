package com.stella.learn.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Times `LearnedArcherGoal.tick` when a window is open. The goal adds its nanoseconds; the driver closes one server tick
 * at a time, so the mean is the cost of every archer together per tick, which is the budget the plan sets. The three
 * phases of a tick are also summed, so a slow run shows where the time went.
 */
public final class ModelPerf {
	/** The parts of a goal tick that are timed apart from the whole. */
	enum Phase {
		OBSERVE,
		DECIDE,
		ACT,
		/** Inside OBSERVE: the sight line, and the 32 distance rays. */
		SIGHT,
		RAYS,
		/** Inside ACT, and left out of the total: the mobs mod's arrow release. */
		RELEASE
	}

	/** The cost of all archers in one server tick, in milliseconds. `phaseMeanMs` follows {@link Phase}: observe, decide, act, sight, rays, release. */
	public record Result(int ticks, double meanMs, double p95Ms, double maxMs, double[] phaseMeanMs) {
	}

	private static final Phase[] PHASES = Phase.values();
	private static volatile boolean recording;
	private static long tickNanos;
	private static final long[] PHASE_NANOS = new long[PHASES.length];
	private static final List<Long> TICKS = new ArrayList<>();

	private ModelPerf() {
	}

	public static void start() {
		synchronized (TICKS) {
			TICKS.clear();
			tickNanos = 0L;
			Arrays.fill(PHASE_NANOS, 0L);
			recording = true;
		}
	}

	/** Closes the window, logs one `learnModel PERF` line, and returns what it measured. */
	public static Result stop(final int archers) {
		double[] millis;
		double[] phases = new double[PHASES.length];
		synchronized (TICKS) {
			recording = false;
			millis = TICKS.stream().mapToDouble(nanos -> nanos / 1.0E6).toArray();
			for (int i = 0; i < phases.length; i++) {
				phases[i] = millis.length == 0 ? 0.0 : PHASE_NANOS[i] / 1.0E6 / millis.length;
			}

			TICKS.clear();
		}

		if (millis.length == 0) {
			ModelLog.perf(archers, 0, 0.0, 0.0, 0.0, phases);
			return new Result(0, 0.0, 0.0, 0.0, phases);
		}

		double mean = Arrays.stream(millis).average().orElse(0.0);
		Arrays.sort(millis);
		double p95 = millis[Math.min(millis.length - 1, (int) Math.ceil(millis.length * 0.95) - 1)];
		double max = millis[millis.length - 1];
		ModelLog.perf(archers, millis.length, mean, p95, max, phases);
		return new Result(millis.length, mean, p95, max, phases);
	}

	static boolean recording() {
		return recording;
	}

	static void record(final long nanos) {
		tickNanos += nanos;
	}

	static void phase(final Phase phase, final long nanos) {
		PHASE_NANOS[phase.ordinal()] += nanos;
	}

	/** Called at the end of every server tick. */
	static void endTick() {
		if (!recording) {
			return;
		}

		synchronized (TICKS) {
			TICKS.add(tickNanos);
			tickNanos = 0L;
		}
	}
}
