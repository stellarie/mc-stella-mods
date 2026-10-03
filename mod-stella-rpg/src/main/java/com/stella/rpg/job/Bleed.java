package com.stella.rpg.job;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * A bleed loses a share of the current health each second. It is a timer on the entity, not a mob effect, so
 * poison immunity does not stop it. A new bleed refreshes the timer and does not stack. Current health never
 * reaches zero by a share, so a bleed alone never kills.
 */
public final class Bleed {
	public static final int DURATION_TICKS = 100;
	public static final int PULSE_TICKS = 20;

	private static final class State {
		private final LivingEntity victim;
		private double share;
		private int remaining;

		private State(final LivingEntity victim, final double share) {
			this.victim = victim;
			this.share = share;
			this.remaining = DURATION_TICKS;
		}
	}

	private static final Map<UUID, State> ACTIVE = new ConcurrentHashMap<>();

	private Bleed() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
	}

	/** Starts a bleed, or refreshes the one the victim has with the higher share. */
	public static void apply(final LivingEntity victim, final double share) {
		State state = ACTIVE.get(victim.getUUID());
		if (state == null) {
			ACTIVE.put(victim.getUUID(), new State(victim, share));
			return;
		}
		state.share = Math.max(state.share, share);
		state.remaining = DURATION_TICKS;
	}

	public static boolean isBleeding(final LivingEntity victim) {
		return ACTIVE.containsKey(victim.getUUID());
	}

	static void tick() {
		Iterator<State> iterator = ACTIVE.values().iterator();
		while (iterator.hasNext()) {
			State state = iterator.next();
			if (!state.victim.isAlive() || state.victim.isRemoved()) {
				iterator.remove();
				continue;
			}
			state.remaining--;
			if (state.remaining % PULSE_TICKS == 0) {
				pulse(state);
			}
			if (state.remaining <= 0) {
				iterator.remove();
			}
		}
	}

	private static void pulse(final State state) {
		LivingEntity victim = state.victim;
		victim.setHealth((float) (victim.getHealth() * (1.0 - state.share)));
		if (victim.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, victim.getX(), victim.getY(0.5), victim.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
		}
	}
}
