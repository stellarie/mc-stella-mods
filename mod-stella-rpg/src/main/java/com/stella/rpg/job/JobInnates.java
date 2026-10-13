package com.stella.rpg.job;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The innate skill of a player. A job grants one innate, read from the job of the player each time, so a job change
 * switches it at once and death never removes it. The Archer innate lives in the spells mod, which reads
 * {@link Innate#FALCON_GUARD} through the job object share.
 */
public final class JobInnates {
	private JobInnates() {
	}

	public static void register() {
		DoubleStrike.register();
		ArcaneWard.register();
		Sanctuary.register();
	}

	public static Innate of(final ServerPlayer player) {
		return Jobs.of(player).innate();
	}

	public static boolean has(final ServerPlayer player, final Innate innate) {
		return of(player) == innate;
	}

	/** One particle at each point of a circle around the feet. */
	static void ring(
		final ServerLevel level, final ParticleOptions particle, final Vec3 feet, final double radius, final int points,
		final double phase
	) {
		for (int point = 0; point < points; point++) {
			double angle = phase + point * 2.0 * Math.PI / points;
			level.sendParticles(particle, feet.x + Math.cos(angle) * radius, feet.y + 0.1, feet.z + Math.sin(angle) * radius,
				1, 0.0, 0.0, 0.0, 0.0);
		}
	}
}
