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
 * On each blow of the archer it has a {@link #CHANCE} to strike the mob that was hit {@link #HITS} times. Blitz Beat
 * adds its LUK bonus to the chance. Every {@link #CHECK_TICKS} ticks, when the archer has not struck a blow for
 * {@link #IDLE_TICKS} ticks, it strikes the nearest hostile within {@link #RANGE} blocks {@link #HITS} times. Each hit is
 * the average of the lowest and highest ranged roll of the archer. A foe it kills pays the archer {@link #XP_SHARE} of its
 * experience.
 * The RPG mod owns the job. This class asks it through the job object share and does nothing when the share is absent.
 */
final class FalconGuard {
	static final String JOBS_SHARE = "mod-stella-rpg:jobs";
	static final String INNATE_FUNCTION = "innate";
	static final String INNATE_KEY = "falcon_guard";
	static final int CHECK_TICKS = 40;
	static final int IDLE_TICKS = 100;
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

	/** One roll against this chance: whether the falcon strikes this time. */
	static boolean roll(final ServerPlayer player, final double chance) {
		return RANDOMS.getOrDefault(player.getUUID(), player.getRandom()).nextDouble() < chance;
	}

	static void clear() {
		RANDOMS.clear();
	}
}
