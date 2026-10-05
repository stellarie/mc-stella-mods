package com.stella.ruins.site.provinggrounds;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The crowd. When a wave dies, the stands roar and sparks fly over the seats. It costs the fight nothing. The sink
 * is replaceable, so a test can count the cheers: a game test server cannot hear.
 */
public final class Cheer {
	/** Where the roar plays and the particles burst: one call for each wave cleared. */
	@FunctionalInterface
	public interface Sink {
		void cheer(ServerLevel level, List<BlockPos> seats);
	}

	public static final int PARTICLES_PER_SEAT = 12;
	public static final float VOLUME = 1.6F;
	public static final float PITCH = 0.7F;

	private static volatile Sink sink = Cheer::real;

	private Cheer() {
	}

	static void cheer(final ServerLevel level, final List<BlockPos> seats) {
		sink.cheer(level, seats);
	}

	/** Replaces the sink and returns the old one, so the caller can restore it in a finally block. */
	public static Sink replace(final Sink replacement) {
		Sink old = sink;
		sink = replacement;
		return old;
	}

	private static void real(final ServerLevel level, final List<BlockPos> seats) {
		for (BlockPos seat : seats) {
			level.playSound(null, seat, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, VOLUME, PITCH);
			level.sendParticles(ParticleTypes.FIREWORK, seat.getX() + 0.5, seat.getY() + 2.0, seat.getZ() + 0.5, PARTICLES_PER_SEAT, 0.8, 0.6, 0.8, 0.05);
		}
	}
}
