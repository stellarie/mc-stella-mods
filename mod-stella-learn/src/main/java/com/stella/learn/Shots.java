package com.stella.learn;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

/**
 * Remembers when each mob last loosed an arrow, on the mob's own tick counter, so a mob can tell it has just shot. The
 * level clock is no use here: it barely moves under the game test runner.
 */
public final class Shots {
	/** Ticks after a shot in which the mob still counts as having just shot. */
	static final int FRESH_TICKS = 10;
	private static final Map<UUID, Integer> LAST = new ConcurrentHashMap<>();

	private Shots() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractArrow arrow && arrow.getOwner() instanceof Mob owner) {
				LAST.put(owner.getUUID(), owner.tickCount);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Mob) {
				LAST.remove(entity.getUUID());
			}
		});
	}

	/** True when this mob has loosed an arrow within `FRESH_TICKS` and no decision has used the shot yet. */
	static boolean fresh(final Mob mob) {
		Integer at = LAST.get(mob.getUUID());
		if (at == null) {
			return false;
		}

		if (mob.tickCount - at > FRESH_TICKS) {
			LAST.remove(mob.getUUID(), at);
			return false;
		}

		return true;
	}

	/** Marks the shot as answered, so one shot makes one decision. */
	static void spend(final Mob mob) {
		LAST.remove(mob.getUUID());
	}
}
