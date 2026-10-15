package com.stella.spells;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Memorize: the next ten fully charged staff attacks that hit trigger every imbued spell of the staff, with no
 * chance roll. A swing that lands spends one charge, however many bolts it fires.
 */
final class Memorize {
	static final int CHARGES = SpellNumbers.MEMORIZE.targets();
	private static final Map<UUID, Integer> LEFT = new ConcurrentHashMap<>();

	private Memorize() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> LEFT.clear());
	}

	static boolean apply(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		LEFT.put(caster.getUUID(), CHARGES);
		return true;
	}

	/** Spends one charge; true when the player had one. */
	static boolean spend(final ServerPlayer player) {
		Integer left = LEFT.get(player.getUUID());
		if (left == null) {
			return false;
		}
		if (left <= 1) {
			LEFT.remove(player.getUUID());
		} else {
			LEFT.put(player.getUUID(), left - 1);
		}
		return true;
	}

	static int left(final UUID player) {
		return LEFT.getOrDefault(player, 0);
	}
}
