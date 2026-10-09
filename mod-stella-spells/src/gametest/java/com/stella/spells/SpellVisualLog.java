package com.stella.spells;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;

final class SpellVisualLog {
	private static final Map<UUID, List<Played>> PLAYED = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Sounded>> SOUNDED = new ConcurrentHashMap<>();

	// Installed from the test class so the first cast of the first test is recorded.
	static void install() {
		SpellVisuals.setRecorder((caster, spell, particle, at, particles, casterSaw) ->
			PLAYED.computeIfAbsent(caster, key -> new CopyOnWriteArrayList<>()).add(new Played(spell, particle, at, particles, casterSaw))
		);
		SpellVisuals.setSoundRecorder((caster, spell, sound, at) ->
			SOUNDED.computeIfAbsent(caster, key -> new CopyOnWriteArrayList<>()).add(new Sounded(spell, sound, at))
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

	static List<Sounded> sounded(final UUID caster, final Identifier spell) {
		return SOUNDED.getOrDefault(caster, List.of()).stream().filter(entry -> entry.spell().equals(spell)).toList();
	}

	record Sounded(Identifier spell, SoundEvent sound, Vec3 at) {
	}

	record Played(Identifier spell, ParticleOptions particle, Vec3 at, int particles, boolean casterSaw) {
	}
}
