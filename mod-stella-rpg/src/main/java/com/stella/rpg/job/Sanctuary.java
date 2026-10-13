package com.stella.rpg.job;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Priest innate. Once per second, each player and ally within {@link #RADIUS} blocks heals {@link #SHARE} of its
 * max health, and each hostile mob within the radius loses {@link #SHARE} of its max health. Red and green sparkles
 * mark the radius at the feet of the priest.
 */
public final class Sanctuary {
	public static final double RADIUS = 3.0;
	public static final double SHARE = 0.015;
	public static final int PULSE_TICKS = 20;
	private static final int RING_TICKS = 10;
	private static final int RING_POINTS = 12;
	private static final int PRUNE_TICKS = 200;
	private static final DustParticleOptions RED = new DustParticleOptions(0xFF3030, 0.9F);
	private static final DustParticleOptions GREEN = new DustParticleOptions(0x30FF50, 0.9F);

	private static final Map<UUID, int[]> AGES = new ConcurrentHashMap<>();

	private Sanctuary() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Sanctuary::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> AGES.clear());
	}

	private static void tick(final MinecraftServer server) {
		for (ServerPlayer priest : server.getPlayerList().getPlayers()) {
			if (priest.isAlive() && !priest.isSpectator() && JobInnates.has(priest, Innate.SANCTUARY)) {
				int[] age = AGES.computeIfAbsent(priest.getUUID(), id -> new int[1]);
				age[0]++;
				if (age[0] % RING_TICKS == 0) {
					double phase = age[0] * 0.05;
					JobInnates.ring(priest.level(), GREEN, priest.position(), RADIUS, RING_POINTS, phase);
					JobInnates.ring(priest.level(), RED, priest.position(), RADIUS, RING_POINTS, phase + Math.PI / RING_POINTS);
				}
				if (age[0] % PULSE_TICKS == 0) {
					pulse(priest);
				}
			}
		}
		if (server.getTickCount() % PRUNE_TICKS == 0) {
			AGES.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		}
	}

	private static void pulse(final ServerPlayer priest) {
		for (LivingEntity target : InnateTargets.around(priest, RADIUS)) {
			if (InnateTargets.ally(target)) {
				InnateTargets.heal(target, SHARE);
			} else if (InnateTargets.hostile(target)) {
				InnateTargets.hurt(priest, target, SHARE);
			}
		}
	}
}
