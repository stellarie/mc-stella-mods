package com.stella.learn.gym;

import com.stella.learn.LearnLog;
import com.stella.learn.model.Contract;
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;

/**
 * Drives the decision loop from the server tick. Every `decisionTicks` ticks it sends one Step frame with the arenas that
 * have something to report, then blocks the server thread until the Act frame comes back. Reset frames may arrive first:
 * each one starts a new episode in one arena. An arena that ended waits idle until the trainer resets it. A silent client
 * for 30 seconds ends the session.
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
	private long rateSteps;
	private long rateDecisions;
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

		Contract contract = Contract.archer();
		GymLog.info(GymLog.BRIDGE, "CONNECT", "remote=" + session.remote());
		session.send(Bridge.helloFrame(contract.observationSize(), GymSource.ACTION_SIZE, pool.size(), contract.hash()));
		GymLog.info(GymLog.BRIDGE, "HELLO", "version=" + Bridge.PROTOCOL_VERSION + " obs=" + contract.observationSize()
			+ " act=" + GymSource.ACTION_SIZE + " arenas=" + pool.size() + " contract=" + contract.hashHex().substring(0, 8));
		sessionTicks = 0;
		stepId = 0;
		rateSteps = 0;
		rateDecisions = 0;
		rateStartNanos = System.nanoTime();
		if (config.sprint()) {
			sprinting = server.tickRateManager().requestGameToSprint(SPRINT_TICKS);
			GymLog.info(GymLog.BRIDGE, "SPRINT", "requested=" + sprinting);
		}

		return true;
	}

	private void step() throws IOException {
		List<GymEpisode.Frame> frames = new ArrayList<>(episodes.size());
		for (GymEpisode episode : episodes) {
			GymEpisode.Frame frame = episode.poll(config.decisionTicks());
			if (frame != null) {
				frames.add(frame);
			}
		}

		int count = frames.size();
		int[] agents = new int[count];
		int[] flags = new int[count];
		float[] rewards = new float[count];
		float[] shaping = new float[count];
		float[][] observations = new float[count][];
		for (int i = 0; i < count; i++) {
			GymEpisode.Frame frame = frames.get(i);
			agents[i] = frame.arena();
			flags[i] = frame.flags();
			rewards[i] = frame.reward();
			shaping[i] = frame.shaping();
			observations[i] = frame.observation();
		}

		stepId++;
		session.send(Bridge.stepFrame(stepId, agents, flags, rewards, shaping, observations));
		awaitAct(stepId);
		countStep(count);
	}

	/** Reads frames until the Act of step `id`, starting an episode for each Reset that comes first. */
	private void awaitAct(final int id) throws IOException {
		while (true) {
			Bridge.Frame frame = session.read();
			switch (frame.type()) {
				case Bridge.ACT -> {
					Bridge.Act act = Bridge.decodeAct(frame.body(), GymSource.ACTION_SIZE);
					if (act.stepId() != id) {
						throw new IllegalArgumentException("act for step " + act.stepId() + " while waiting for " + id);
					}

					for (int i = 0; i < act.agents().length; i++) {
						int agent = act.agents()[i];
						if (agent < 0 || agent >= episodes.size()) {
							throw new IllegalArgumentException("act names unknown agent " + agent);
						}

						episodes.get(agent).act(act.actions()[i]);
					}

					if (id % config.sampleTicks() == 0 && act.agents().length > 0) {
						GymLog.debug(GymLog.STEP, "ACT", "step=" + id + " arena=" + act.agents()[0] + " action=" + java.util.Arrays.toString(act.actions()[0]));
					}

					return;
				}
				case Bridge.RESET -> reset(Bridge.decodeReset(frame.body()));
				case Bridge.BYE -> throw new EOFException("bye");
				default -> throw new IllegalArgumentException("unexpected frame type " + frame.type());
			}
		}
	}

	private void reset(final Bridge.Reset reset) {
		long seed = Integer.toUnsignedLong(reset.seed());
		GymLog.debug(GymLog.BRIDGE, "RESET", "arena=" + (reset.arena() == Bridge.ALL_ARENAS ? "all" : String.valueOf(reset.arena()))
			+ " seed=" + seed + " brain=" + reset.brain() + " stage=" + reset.stage());
		for (GymEpisode episode : episodes) {
			if (reset.arena() == Bridge.ALL_ARENAS || reset.arena() == episode.arena().index()) {
				episode.start(seed, reset.brain(), reset.stage());
			}
		}
	}

	private void countStep(final int agents) {
		rateSteps++;
		rateDecisions += agents;
		if (rateSteps % RATE_EVERY_STEPS != 0) {
			return;
		}

		long now = System.nanoTime();
		double seconds = (now - rateStartNanos) / 1.0e9;
		rateStartNanos = now;
		GymLog.info(GymLog.STEP, "RATE", "steps=" + RATE_EVERY_STEPS + " total=" + stepId + " stepsPerSec=" + LearnLog.num(RATE_EVERY_STEPS / seconds)
			+ " decisionsPerSec=" + LearnLog.num(rateDecisions / seconds) + " agents=" + agents);
		rateDecisions = 0;
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
		for (GymEpisode episode : episodes) {
			episode.abandon();
		}

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
