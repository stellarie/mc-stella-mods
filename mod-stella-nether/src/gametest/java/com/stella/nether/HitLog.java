package com.stella.nether;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Records every hit that reaches a watched creature's after-damage event, so a test counts the parts a Sovereign
 * hit came in. Fabric events cannot be removed, so one listener serves every test and keeps only what is watched.
 */
final class HitLog {
	/** One hit as the victim took it: whose it was, its kind, and its size before shields and armor. */
	record Part(@Nullable Entity cause, boolean fire, boolean flameType, float base, float taken, boolean blocked) {
	}

	private static final Map<UUID, List<Part>> PARTS = new ConcurrentHashMap<>();

	private HitLog() {
	}

	static void install() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			List<Part> parts = PARTS.get(entity.getUUID());
			if (parts != null) {
				parts.add(new Part(source.getEntity(), source.is(DamageTypeTags.IS_FIRE), source.is(SovereignHits.FLAME), baseDamage, damageTaken, blocked));
			}
		});
	}

	/** Starts recording a creature's hits, from nothing. */
	static void watch(final LivingEntity victim) {
		PARTS.put(victim.getUUID(), new CopyOnWriteArrayList<>());
	}

	/** The hits a creature took from one attacker since it was watched. */
	static List<Part> from(final LivingEntity victim, final Entity attacker) {
		List<Part> parts = PARTS.getOrDefault(victim.getUUID(), List.of());
		return parts.stream().filter(part -> part.cause() == attacker).toList();
	}

	static void forget(final LivingEntity victim) {
		PARTS.remove(victim.getUUID());
	}

	static float sum(final List<Part> parts) {
		float total = 0.0F;
		for (Part part : parts) {
			total += part.base();
		}
		return total;
	}

	static long fire(final List<Part> parts) {
		return parts.stream().filter(Part::fire).count();
	}

	static long physical(final List<Part> parts) {
		return parts.stream().filter(part -> !part.fire()).count();
	}
}
