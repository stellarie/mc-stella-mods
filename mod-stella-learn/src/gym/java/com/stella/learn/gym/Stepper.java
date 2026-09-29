package com.stella.learn.gym;

import com.stella.learn.LearnLog;
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.List;
import net.minecraft.server.MinecraftServer;

/**
 * Drives the decision loop from the server tick. Every `decisionTicks` ticks it sends one Step frame with an agent per
 * arena, then blocks the server thread until the Act frame comes back. A silent client for 30 seconds ends the session.
 */
public final class Stepper {
	private static final int SPRINT_TICKS = Integer.MAX_VALUE;
	private static final int RATE_EVERY_STEPS = 1000;

	private final MinecraftServer server;
	private final GymConfig config;
	private final ArenaPool pool;
	private final List<GymEpisode> episodes;
	private final Bridge bridge;
	private Bridge.Session session;
	private long sessionTicks;
	private int stepId;
	private long seedCounter;
	private long rateSteps;
	private long rateStartNanos;
	private boolean sprinting;

	public Stepper(final MinecraftServer server, final GymConfig config, final ArenaPool pool, final List<GymEpisode> episodes, final Bridge bridge) {
		this.server = server;
		this.config = config;
		this.pool = pool;
		this.episodes = episodes;
		this.bridge = bridge;
	}

	public void holdActions() {
		for (GymEpisode episode : episodes) {
			episode.tickHeld();
		}
	}

	public void endTick() {
		try {
			if (session == null && !accept()) {
				return;
			}

			sessionTicks++;
			if (sessionTicks % config.decisionTicks() == 0) {
				step();
			}
		} catch (SocketTimeoutException e) {
			close(GymLog.READ_TIMEOUT, "waited=" + Bridge.READ_TIMEOUT_MILLIS + "ms", true);
		} catch (EOFException e) {
			close("bye".equals(e.getMessage()) ? GymLog.CLIENT_BYE : GymLog.CLIENT_GONE, "error=" + e.getMessage(), false);
		} catch (SocketException e) {
			close(GymLog.CLIENT_GONE, "error=" + e.getMessage(), false);
		} catch (IOException | IllegalArgumentException e) {
			close(GymLog.BAD_FRAME, "error=" + e.getMessage(), false);
		}
	}

	private boolean accept() throws IOException {
		session = bridge.pollSession();
		if (session == null) {
			return false;
		}

		GymLog.info(GymLog.BRIDGE, "CONNECT", "remote=" + session.remote());
		session.send(Bridge.helloFrame(GymEpisode.OBSERVATION_SIZE, SkeletonActions.SIZE, pool.size(), new byte[Bridge.HASH_SIZE]));
		GymLog.info(GymLog.BRIDGE, "HELLO", "version=" + Bridge.PROTOCOL_VERSION + " obs=" + GymEpisode.OBSERVATION_SIZE
			+ " act=" + SkeletonActions.SIZE + " arenas=" + pool.size());
		sessionTicks = 0;
		stepId = 0;
		rateSteps = 0;
		rateStartNanos = System.nanoTime();
		for (GymEpisode episode : episodes) {
			episode.start(nextSeed());
		}

		if (config.sprint()) {
			sprinting = server.tickRateManager().requestGameToSprint(SPRINT_TICKS);
			GymLog.info(GymLog.BRIDGE, "SPRINT", "requested=" + sprinting);
		}

		return true;
	}

	private long nextSeed() {
		return config.seed() * 1_000_003L + seedCounter++;
	}

	private void step() throws IOException {
		boolean advance = true;
		while (true) {
			int count = episodes.size();
			int[] agents = new int[count];
			int[] flags = new int[count];
			float[] rewards = new float[count];
			float[][] observations = new float[count][GymEpisode.OBSERVATION_SIZE];
			for (int i = 0; i < count; i++) {
				GymEpisode episode = episodes.get(i);
				if (advance) {
					episode.advance(config.decisionTicks());
				}

				agents[i] = episode.arena().index();
				flags[i] = (episode.consumeStarted() ? Bridge.FLAG_START : 0)
					| (episode.ended() ? Bridge.FLAG_TERMINAL : 0)
					| (episode.truncated() ? Bridge.FLAG_TRUNCATED : 0);
				rewards[i] = episode.takeReward();
				episode.observe(observations[i], 0);
			}

			stepId++;
			session.send(Bridge.stepFrame(stepId, agents, flags, rewards, observations));
			for (GymEpisode episode : episodes) {
				if (episode.ended()) {
					episode.start(nextSeed());
				}
			}

			if (awaitAct(stepId)) {
				countStep(count);
				return;
			}

			advance = false;
		}
	}

	/** Reads one frame. Returns true for the Act of step `id`, false when a Reset arrived and a fresh Step must go out. */
	private boolean awaitAct(final int id) throws IOException {
		Bridge.Frame frame = session.read();
		switch (frame.type()) {
			case Bridge.ACT -> {
				Bridge.Act act = Bridge.decodeAct(frame.body(), SkeletonActions.SIZE);
				if (act.stepId() != id) {
					throw new IllegalArgumentException("act for step " + act.stepId() + " while waiting for " + id);
				}

				for (int i = 0; i < act.agents().length; i++) {
					int agent = act.agents()[i];
					if (agent < 0 || agent >= episodes.size()) {
						throw new IllegalArgumentException("act names unknown agent " + agent);
					}

					episodes.get(agent).actions().set(act.actions()[i]);
				}

				if (id % config.sampleTicks() == 0 && act.agents().length > 0) {
					GymLog.debug(GymLog.STEP, "ACT", "step=" + id + " arena=" + act.agents()[0] + " action=" + java.util.Arrays.toString(act.actions()[0]));
				}

				return true;
			}
			case Bridge.RESET -> {
				Bridge.Reset reset = Bridge.decodeReset(frame.body());
				GymLog.info(GymLog.BRIDGE, "RESET", "arena=" + (reset.arena() == Bridge.ALL_ARENAS ? "all" : String.valueOf(reset.arena()))
					+ " seed=" + Integer.toUnsignedString(reset.seed()));
				for (GymEpisode episode : episodes) {
					if (reset.arena() == Bridge.ALL_ARENAS || reset.arena() == episode.arena().index()) {
						episode.start(reset.seed() * 1_000_003L + episode.arena().index());
					}
				}

				return false;
			}
			case Bridge.BYE -> throw new EOFException("bye");
			default -> throw new IllegalArgumentException("unexpected frame type " + frame.type());
		}
	}

	private void countStep(final int agents) {
		rateSteps++;
		if (rateSteps % RATE_EVERY_STEPS != 0) {
			return;
		}

		long now = System.nanoTime();
		double seconds = (now - rateStartNanos) / 1.0e9;
		rateStartNanos = now;
		GymLog.info(GymLog.STEP, "RATE", "steps=" + RATE_EVERY_STEPS + " total=" + stepId + " stepsPerSec=" + LearnLog.num(RATE_EVERY_STEPS / seconds)
			+ " decisionsPerSec=" + LearnLog.num(RATE_EVERY_STEPS * (double) agents / seconds) + " agents=" + agents);
	}

	private void close(final String reason, final String detail, final boolean timeout) {
		if (session == null) {
			return;
		}

		if (timeout) {
			GymLog.info(GymLog.BRIDGE, "TIMEOUT", "reason=" + reason + " " + detail + " steps=" + stepId + " ticks=" + sessionTicks);
		}

		GymLog.info(GymLog.BRIDGE, "CLOSE", "reason=" + reason + " steps=" + stepId + " ticks=" + sessionTicks);
		session.close();
		session = null;
		if (sprinting) {
			server.tickRateManager().stopSprinting();
			sprinting = false;
		}
	}

	public void shutdown() {
		close(GymLog.SHUTDOWN, "", false);
		bridge.close();
	}
}
