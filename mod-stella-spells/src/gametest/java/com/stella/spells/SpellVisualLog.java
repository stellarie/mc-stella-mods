package com.stella.spells;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

final class SpellVisualLog {
	private static final Map<UUID, List<Played>> PLAYED = new ConcurrentHashMap<>();

	// Installed from the test class so the first cast of the first test is recorded.
	static void install() {
		SpellVisuals.setRecorder((caster, spell, particle, at, particles, casterSaw) ->
			PLAYED.computeIfAbsent(caster, key -> new CopyOnWriteArrayList<>()).add(new Played(spell, particle, at, particles, casterSaw))
		);
	}

	private SpellVisualLog() {
	}

	static List<Played> played(final UUID caster) {
		return PLAYED.getOrDefault(caster, List.of());
	}

	static List<Played> played(final UUID caster, final Identifier spell) {
		return played(caster).stream().filter(entry -> entry.spell().equals(spell)).toList();
	}

	record Played(Identifier spell, ParticleOptions particle, Vec3 at, int particles, boolean casterSaw) {
	}
}
