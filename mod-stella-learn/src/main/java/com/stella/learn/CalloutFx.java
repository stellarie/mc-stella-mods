package com.stella.learn;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * The small animation of a callout: a burst above the caller's head, a beam of dust from it to each mob that accepted,
 * a puff on each of those, and one soft cast sound. Each type has its own colour. It draws with vanilla particles from
 * the server, so no client jar is needed.
 */
public final class CalloutFx {
	/** Blocks between two beam particles. */
	public static final double BEAM_STEP = 0.75;
	/** Particles in the burst above the caller and in the puff on a receiver. */
	public static final int BURST_PARTICLES = 12;
	public static final int PUFF_PARTICLES = 4;
	/** Blocks within which a player sees the animation. */
	public static final double VIEW_RADIUS = 48.0;
	static final int GREY = 0x9A9A9A;
	private static final Map<Identifier, Integer> COLORS = Map.of(
		Rally.TYPE, 0xE04040,
		CalloutKits.FOCUS, 0xF08A24,
		CalloutKits.BACK_OFF, 0x5AA0E0,
		CalloutKits.HEAL, 0x40D060,
		CalloutKits.BUFF, 0xF0C020,
		CalloutKits.DEBUFF, 0xA050E0
	);

	/** Test seam: each thing the animation drew or played, and whether any player saw it. */
	public interface Recorder {
		void burst(UUID caller, Identifier type, Vec3 at, int count, boolean seen);

		void beam(UUID caller, UUID receiver, Identifier type, Vec3 from, Vec3 to, int steps, boolean seen);

		void sound(UUID caller, Identifier type, Vec3 at);
	}

	private static volatile Recorder recorder = new Recorder() {
		@Override
		public void burst(final UUID caller, final Identifier type, final Vec3 at, final int count, final boolean seen) {
		}

		@Override
		public void beam(final UUID caller, final UUID receiver, final Identifier type, final Vec3 from, final Vec3 to, final int steps, final boolean seen) {
		}

		@Override
		public void sound(final UUID caller, final Identifier type, final Vec3 at) {
		}
	};

	private CalloutFx() {
	}

	public static void setRecorder(final Recorder value) {
		recorder = value;
	}

	/** The dust colour a callout type is drawn in. */
	public static int colorOf(final Identifier type) {
		return COLORS.getOrDefault(type, GREY);
	}

	/** Draws one call: the burst over the caller, a beam and a puff for each mob in `accepted`, and the sound. */
	public static void play(final Mob caller, final Callout callout, final LearnConfig config, final List<Mob> accepted) {
		if (!config.calloutFx() || !(caller.level() instanceof ServerLevel level)) {
			return;
		}

		DustParticleOptions dust = new DustParticleOptions(colorOf(callout.type()), 1.0F);
		Vec3 head = caller.getEyePosition();
		int particles = 0;
		particles += burst(level, caller, callout, dust, head.add(0.0, 0.4, 0.0), BURST_PARTICLES, 0.25);
		for (Mob receiver : accepted) {
			particles += beam(level, caller, receiver, callout, dust, head, receiver.getEyePosition());
			particles += burst(level, caller, callout, dust, receiver.getEyePosition().add(0.0, 0.4, 0.0), PUFF_PARTICLES, 0.15);
		}

		level.playSound(null, head.x, head.y, head.z, SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 0.6F, pitchOf(callout.type()));
		recorder.sound(caller.getUUID(), callout.type(), head);
		LearnLog.info(config, "learnCallout", "FX", LearnLog.who(caller) + " type=" + callout.type() + " beams=" + accepted.size() + " particles=" + particles);
	}

	private static int burst(
		final ServerLevel level, final Mob caller, final Callout callout, final ParticleOptions particle, final Vec3 at, final int count, final double spread
	) {
		boolean seen = emit(level, particle, at, count, spread);
		recorder.burst(caller.getUUID(), callout.type(), at, count, seen);
		return count;
	}

	private static int beam(
		final ServerLevel level, final Mob caller, final Mob receiver, final Callout callout, final ParticleOptions particle, final Vec3 from, final Vec3 to
	) {
		Vec3 offset = to.subtract(from);
		int steps = Math.max(1, (int) Math.ceil(offset.length() / BEAM_STEP));
		boolean seen = false;
		for (int i = 1; i <= steps; i++) {
			seen |= emit(level, particle, from.add(offset.scale((double) i / steps)), 1, 0.0);
		}

		recorder.beam(caller.getUUID(), receiver.getUUID(), callout.type(), from, to, steps, seen);
		return steps;
	}

	/** Sends to every player in view of the point, and reports whether any player was sent it. */
	private static boolean emit(final ServerLevel level, final ParticleOptions particle, final Vec3 at, final int count, final double spread) {
		boolean seen = false;
		for (ServerPlayer viewer : level.players()) {
			if (viewer.position().distanceToSqr(at) <= VIEW_RADIUS * VIEW_RADIUS) {
				seen |= level.sendParticles(viewer, particle, false, true, at.x, at.y, at.z, count, spread, spread, spread, 0.0);
			}
		}

		return seen;
	}

	private static float pitchOf(final Identifier type) {
		if (type.equals(CalloutKits.HEAL)) {
			return 1.3F;
		}

		if (type.equals(CalloutKits.BUFF)) {
			return 1.2F;
		}

		if (type.equals(CalloutKits.DEBUFF)) {
			return 0.8F;
		}

		return type.equals(CalloutKits.BACK_OFF) ? 0.9F : 1.0F;
	}
}
