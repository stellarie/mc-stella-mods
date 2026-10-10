package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.config.WitherConfig;
import com.stella.mobs.config.WitherConfigStore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;

/**
 * An encounter Wither has vitality 200 and every other stat at random between 50 and 150. The rpg mod holds the
 * numbers: the share {@code mod-stella-rpg:mob-stat-set} takes them. Without that mod the Wither keeps its role stats.
 */
public final class WitherStats {
	/** The object share the rpg mod publishes: a {@code BiConsumer<Mob, Map<String, Integer>>} keyed by stat key. */
	public static final String SHARE_KEY = "mod-stella-rpg:mob-stat-set";
	private static final String VIT = "vit";
	private static final String[] OTHERS = {"str", "agi", "dex", "int", "luk"};
	private static final AtomicBoolean ABSENCE_LOGGED = new AtomicBoolean();

	private WitherStats() {
	}

	/** Vitality fixed, the rest drawn evenly from the range, both ends included. */
	public static Map<String, Integer> roll(final RandomSource random, final WitherConfig config) {
		int low = config.getInt(WitherConfig.STATS_MIN);
		int high = Math.max(low, config.getInt(WitherConfig.STATS_MAX));
		Map<String, Integer> stats = new LinkedHashMap<>();
		stats.put("str", low + random.nextInt(high - low + 1));
		stats.put("agi", low + random.nextInt(high - low + 1));
		stats.put(VIT, config.getInt(WitherConfig.STATS_VIT));
		stats.put("dex", low + random.nextInt(high - low + 1));
		stats.put("int", low + random.nextInt(high - low + 1));
		stats.put("luk", low + random.nextInt(high - low + 1));
		return stats;
	}

	/** Rolls the stats and hands them to the rpg mod. Does nothing, and says so once, when the share is absent. */
	@SuppressWarnings("unchecked")
	public static void apply(final Mob wither) {
		Object share = FabricLoader.getInstance().getObjectShare().get(SHARE_KEY);
		if (!(share instanceof BiConsumer<?, ?> setter)) {
			if (ABSENCE_LOGGED.compareAndSet(false, true)) {
				StellaMobs.LOGGER.info("No {} share: the Wither keeps its role stats.", SHARE_KEY);
			}
			return;
		}
		Map<String, Integer> stats = roll(wither.getRandom(), WitherConfigStore.get());
		try {
			((BiConsumer<Mob, Map<String, Integer>>) setter).accept(wither, stats);
			StellaMobs.LOGGER.info("[BEHAVIOR] test=witherStats SET uuid={} stats={}", wither.getUUID(), stats);
		} catch (RuntimeException failure) {
			StellaMobs.LOGGER.warn("The {} share failed; the Wither keeps its role stats: {}", SHARE_KEY, failure.toString());
		}
	}
}
