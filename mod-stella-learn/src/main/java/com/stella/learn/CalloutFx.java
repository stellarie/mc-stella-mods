package com.stella.learn;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * The small animation of a callout: a burst above the caller's head, a beam of dust from it to each mob that accepted,
 * a puff on each of those, and one soft cast sound. Each type has its own colour. It draws with vanilla particles from
 * the server, so no client jar is needed. Stub: written after its tests fail.
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

	public static void play(final Mob caller, final Callout callout, final LearnConfig config, final List<Mob> accepted) {
	}
}
