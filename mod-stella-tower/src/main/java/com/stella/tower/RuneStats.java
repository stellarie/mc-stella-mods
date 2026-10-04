package com.stella.tower;

import java.lang.reflect.Method;
import java.util.function.ToIntBiFunction;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * The stats a rune's signature scales with. They come from the rpg mod by reflection, so that mod stays
 * optional: without it every stat is 0 and each effect stays at its floor.
 */
public final class RuneStats {
	public enum Stat { STR, AGI, VIT, DEX, INT, LUK }

	private static final String SERVER_CLASS = "com.stella.rpg.StatBonuses";
	private static final String STAT_CLASS = "com.stella.rpg.Stat";
	private static final String CLIENT_CLASS = "com.stella.rpg.client.ClientStats";
	private static final int CEILING = 2048;

	private static volatile @Nullable ToIntBiFunction<LivingEntity, Stat> pinned;
	private static boolean probed;
	private static @Nullable Method effective;
	private static @Nullable Class<?> statClass;
	private static boolean clientProbed;
	private static @Nullable Method clientGet;
	private static @Nullable Method clientBase;
	private static @Nullable Method clientBonus;

	private RuneStats() {
	}

	/** The test seam: replaces the stat source for every entity; null restores the rpg read. */
	public static void pin(final @Nullable ToIntBiFunction<LivingEntity, Stat> source) {
		pinned = source;
	}

	/** The effective stat of an entity, 0 when the rpg mod is absent. */
	public static int of(final LivingEntity entity, final Stat stat) {
		ToIntBiFunction<LivingEntity, Stat> source = pinned;
		if (source != null) {
			return source.applyAsInt(entity, stat);
		}
		probe();
		if (effective == null || statClass == null) {
			return 0;
		}
		try {
			Object constant = Enum.valueOf(statClass.asSubclass(Enum.class), stat.name());
			return Math.max(0, (Integer) effective.invoke(null, entity, constant));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 0;
		}
	}

	/** The local player's stats as the server last sent them, for tooltips; 0 when unavailable. */
	public static int local(final Stat stat) {
		ToIntBiFunction<LivingEntity, Stat> source = pinned;
		if (source != null) {
			return source.applyAsInt(null, stat);
		}
		probeClient();
		if (clientGet == null || clientBase == null || clientBonus == null) {
			return 0;
		}
		try {
			Object payload = clientGet.invoke(null);
			if (payload == null) {
				return 0;
			}
			int base = ((int[]) clientBase.invoke(payload))[stat.ordinal()];
			int bonus = ((int[]) clientBonus.invoke(payload))[stat.ordinal()];
			return Math.max(0, Math.min(CEILING, base + bonus));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 0;
		}
	}

	private static synchronized void probe() {
		if (probed) {
			return;
		}
		probed = true;
		try {
			ClassLoader loader = RuneStats.class.getClassLoader();
			statClass = Class.forName(STAT_CLASS, true, loader);
			effective = Class.forName(SERVER_CLASS, true, loader).getMethod("effective", LivingEntity.class, statClass);
		} catch (ReflectiveOperationException | LinkageError e) {
			effective = null;
		}
	}

	private static synchronized void probeClient() {
		if (clientProbed) {
			return;
		}
		clientProbed = true;
		try {
			ClassLoader loader = RuneStats.class.getClassLoader();
			Class<?> stats = Class.forName(CLIENT_CLASS, true, loader);
			clientGet = stats.getMethod("get");
			Class<?> payload = clientGet.getReturnType();
			clientBase = payload.getMethod("base");
			clientBonus = payload.getMethod("bonus");
		} catch (ReflectiveOperationException | LinkageError e) {
			clientGet = null;
		}
	}
}
