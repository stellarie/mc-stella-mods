package com.stella.equips;

import com.stella.equips.refine.RefineEffects;
import com.stella.equips.refine.RefineSpecials;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.entity.Entity;

/**
 * The chance seam for refine effects, shared by every test class. An entity that no test forced never wins a
 * roll, so a refined piece in an unrelated test cannot flake. Each roll is recorded with the chance it was given.
 */
final class RefineRolls {
	private static final Map<UUID, Boolean> FORCED = new ConcurrentHashMap<>();
	private static final Map<UUID, List<String>> ROLLS = new ConcurrentHashMap<>();

	private RefineRolls() {
	}

	static void install() {
		RefineEffects.setRoller((special, entity, random, chance) -> {
			ROLLS.computeIfAbsent(entity.getUUID(), key -> new CopyOnWriteArrayList<>()).add(special.name() + "@" + chance);
			return FORCED.getOrDefault(entity.getUUID(), false);
		});
	}

	static void force(final Entity entity, final boolean outcome) {
		FORCED.put(entity.getUUID(), outcome);
	}

	/** The rolls an entity made, as effect name and chance in percent. */
	static List<String> rolls(final Entity entity) {
		return ROLLS.getOrDefault(entity.getUUID(), List.of());
	}

	static String roll(final RefineSpecials.Special special, final double chance) {
		return special.name() + "@" + chance;
	}
}
