package com.stella.villages.mission;

import com.stella.villages.McStellaVillages;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The rpg mod's job functions, reached through the object share so this mod has no link to it.
 * Every call answers with a safe value when the share is absent or malformed.
 */
public final class RpgJobs {
	public static final String SHARE_KEY = "mod-stella-rpg:jobs";
	public static final String NONE = "none";
	public static final int MIN_LEVEL = 10;
	public static final int FIRST_TRIAL_LEVEL = 40;
	public static final int TRIAL_STEP = 30;
	/** The jobs a player can choose, in menu order. */
	public static final List<String> JOBS = List.of("archer", "fighter", "mage", "priest");

	private RpgJobs() {
	}

	private static @Nullable Map<?, ?> share() {
		return FabricLoader.getInstance().getObjectShare().get(SHARE_KEY) instanceof Map<?, ?> map ? map : null;
	}

	public static boolean available() {
		return share() != null;
	}

	/** The job key of the player, or {@link #NONE}. */
	@SuppressWarnings("unchecked")
	public static String job(final ServerPlayer player) {
		try {
			Map<?, ?> share = share();
			if (share != null && share.get("get") instanceof Function<?, ?> get) {
				return ((Function<ServerPlayer, String>) get).apply(player) instanceof String key ? key : NONE;
			}
		} catch (RuntimeException error) {
			fail("get", error);
		}
		return NONE;
	}

	@SuppressWarnings("unchecked")
	public static boolean choose(final ServerPlayer player, final String job) {
		try {
			Map<?, ?> share = share();
			if (share != null && share.get("choose") instanceof BiFunction<?, ?, ?> choose) {
				return Boolean.TRUE.equals(((BiFunction<ServerPlayer, String, Boolean>) choose).apply(player, job));
			}
		} catch (RuntimeException error) {
			fail("choose", error);
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	public static boolean grantItems(final ServerPlayer player) {
		try {
			Map<?, ?> share = share();
			if (share != null && share.get("grantItems") instanceof Function<?, ?> grant) {
				return Boolean.TRUE.equals(((Function<ServerPlayer, Boolean>) grant).apply(player));
			}
		} catch (RuntimeException error) {
			fail("grantItems", error);
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	public static boolean grantTrial(final ServerPlayer player, final int level) {
		try {
			Map<?, ?> share = share();
			if (share != null && share.get("grantTrial") instanceof BiFunction<?, ?, ?> grant) {
				return Boolean.TRUE.equals(((BiFunction<ServerPlayer, Integer, Boolean>) grant).apply(player, level));
			}
		} catch (RuntimeException error) {
			fail("grantTrial", error);
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	public static Set<Integer> trialsDone(final ServerPlayer player) {
		try {
			Map<?, ?> share = share();
			if (share != null && share.get("trialsDone") instanceof Function<?, ?> done
				&& ((Function<ServerPlayer, Set<Integer>>) done).apply(player) instanceof Set<Integer> levels) {
				return levels;
			}
		} catch (RuntimeException error) {
			fail("trialsDone", error);
		}
		return Set.of();
	}

	public static boolean isTrialLevel(final int level) {
		return level >= FIRST_TRIAL_LEVEL && (level - FIRST_TRIAL_LEVEL) % TRIAL_STEP == 0;
	}

	private static void fail(final String function, final RuntimeException error) {
		McStellaVillages.LOGGER.warn("[BEHAVIOR] mod={} event=rpg_jobs_call_failed function={} error={}",
			McStellaVillages.MOD_ID, function, error.toString());
	}
}
