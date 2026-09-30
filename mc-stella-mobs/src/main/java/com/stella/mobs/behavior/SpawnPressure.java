package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.config.WorldRules;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;

/**
 * Elites and bosses quiet the natural hostile spawns around them: within the elite radius of an elite, or the boss radius
 * of a boss, a natural spawn attempt is skipped half the time, so the spawn rate is halved. Overlapping watchers halve
 * once, not once each. The watch list holds only elites and bosses, so an attempt reads a handful of entries and never
 * sweeps the level.
 */
public final class SpawnPressure {
	private static final AttachmentType<Set<Mob>> WATCHED = AttachmentRegistry.createDefaulted(
		Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "spawn_pressure_watchers"), HashSet::new
	);
	private static final AtomicLong ATTEMPTS = new AtomicLong();
	private static final AtomicLong SKIPPED = new AtomicLong();

	private SpawnPressure() {
	}

	public static void register() {
		StellaMobs.LOGGER.debug("Registered the spawn pressure watch list {}", WATCHED.identifier());
	}

	/** Adds an elite or a boss to its level's watch list. Adding twice adds nothing. */
	public static void watch(final Mob mob) {
		if (mob.level() instanceof ServerLevel level) {
			((AttachmentTarget) level).getAttachedOrCreate(WATCHED).add(mob);
		}
	}

	/** The share of natural spawn attempts that go ahead here: the quiet factor near a watcher, otherwise one. */
	public static double factorAt(final ServerLevel level, final double x, final double y, final double z) {
		WorldRules rules = GearConfigStore.get().world();
		if (rules.quietFactor() >= 1.0 || (rules.eliteQuietRadius() <= 0.0 && rules.bossQuietRadius() <= 0.0)) {
			return 1.0;
		}

		Set<Mob> watched = ((AttachmentTarget) level).getAttached(WATCHED);
		if (watched == null || watched.isEmpty()) {
			return 1.0;
		}

		for (Iterator<Mob> iterator = watched.iterator(); iterator.hasNext(); ) {
			Mob mob = iterator.next();
			if (mob.isRemoved() || !mob.isAlive()) {
				iterator.remove();
				continue;
			}

			// A mob marked before it is added to the level is kept, but does not quiet anything yet.
			if (level.getEntity(mob.getUUID()) != mob) {
				continue;
			}

			double radius = EliteState.hasBossFlag(mob) ? rules.bossQuietRadius() : rules.eliteQuietRadius();
			if (radius > 0.0 && mob.distanceToSqr(x, y, z) <= radius * radius) {
				return rules.quietFactor();
			}
		}

		return 1.0;
	}

	/** True when this natural spawn attempt is to be skipped. Only hostile spawns are quieted. */
	public static boolean skips(final ServerLevel level, final MobCategory category, final BlockPos pos, final RandomSource random) {
		if (category != MobCategory.MONSTER) {
			return false;
		}

		ATTEMPTS.incrementAndGet();
		double factor = factorAt(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		if (factor >= 1.0 || random.nextDouble() < factor) {
			return false;
		}

		SKIPPED.incrementAndGet();
		return true;
	}

	/** Hostile spawn attempts seen since the last reset. A test reads this to prove vanilla's spawner reaches the hook. */
	public static long attempts() {
		return ATTEMPTS.get();
	}

	/** Hostile spawn attempts the hook skipped since the last reset. */
	public static long skipped() {
		return SKIPPED.get();
	}

	public static void resetCounters() {
		ATTEMPTS.set(0L);
		SKIPPED.set(0L);
	}
}
