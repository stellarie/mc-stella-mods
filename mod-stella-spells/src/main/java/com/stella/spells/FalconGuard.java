package com.stella.spells;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * The Archer innate, Falcon Guard. A black falcon circles above an archer for good, beside the brown Blitz Beat falcon.
 * Every {@link #CHECK_TICKS} ticks it has a {@link #CHANCE} to strike a hostile within {@link #RANGE} blocks of the
 * archer {@link #HITS} times. Each hit is the average of the lowest and highest ranged roll of the archer. A foe it
 * kills pays the archer {@link #XP_SHARE} of its experience.
 * The RPG mod owns the job. This class asks it through the job object share and does nothing when the share is absent.
 */
final class FalconGuard {
	static final String JOBS_SHARE = "mod-stella-rpg:jobs";
	static final String INNATE_FUNCTION = "innate";
	static final String INNATE_KEY = "falcon_guard";
	static final int CHECK_TICKS = 40;
	static final double CHANCE = 0.35;
	static final int HITS = 3;
	static final double RANGE = 8.0;
	static final double XP_SHARE = 0.25;

	private static final Map<UUID, RandomSource> RANDOMS = new ConcurrentHashMap<>();

	private FalconGuard() {
	}

	/** True when the job of this player grants the Falcon Guard. Read on each call, so a job change counts at once. */
	@SuppressWarnings("unchecked")
	static boolean holds(final @Nullable ServerPlayer player) {
		if (player == null || !(FabricLoader.getInstance().getObjectShare().get(JOBS_SHARE) instanceof Map<?, ?> share)
			|| !(share.get(INNATE_FUNCTION) instanceof Function<?, ?> innate)) {
			return false;
		}
		try {
			return INNATE_KEY.equals(((Function<ServerPlayer, ?>) innate).apply(player));
		} catch (RuntimeException failure) {
			Spellbooks.LOGGER.warn("[SPELL] event=FALCON_GUARD_JOB_READ_FAILED error={}", failure.toString());
			return false;
		}
	}

	/** A test fixes the random source of one archer, so parallel cases do not meet. Null restores the archer's own. */
	static void setRandom(final UUID player, final @Nullable RandomSource source) {
		if (source == null) {
			RANDOMS.remove(player);
		} else {
			RANDOMS.put(player, source);
		}
	}

	/** One check: whether the falcon strikes this time. */
	static boolean roll(final ServerPlayer player) {
		return RANDOMS.getOrDefault(player.getUUID(), player.getRandom()).nextDouble() < CHANCE;
	}

	static void clear() {
		RANDOMS.clear();
	}
}
