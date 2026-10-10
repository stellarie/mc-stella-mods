package com.stella.ruins.world;

import com.stella.ruins.StellaRuins;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Reads levels from the mobs mod through Fabric object shares and never imports a class of it. Without that mod
 * every spot has level 0 and no mob has a level.
 */
public final class RuinLevels {
	public static final String CONTAINER_LEVEL_KEY = "mod-stella-mobs:container-level";
	public static final String THREAT_LEVEL_KEY = "mod-stella-mobs:threat-level";

	private static volatile boolean warned;

	private RuinLevels() {
	}

	/** The container level at a spot, or 0 when the share is absent. */
	@SuppressWarnings("unchecked")
	public static int at(final ServerLevel level, final Vec3 at) {
		Object share = FabricLoader.getInstance().getObjectShare().get(CONTAINER_LEVEL_KEY);
		if (share == null) {
			return 0;
		}
		try {
			Integer value = ((BiFunction<ServerLevel, Vec3, Integer>) share).apply(level, at);
			return value == null ? 0 : Math.max(0, value);
		} catch (ClassCastException e) {
			warnOnce(CONTAINER_LEVEL_KEY, e);
			return 0;
		}
	}

	/** An entity's threat level from the mobs mod, or -1 when the share is absent or the entity has none. */
	@SuppressWarnings("unchecked")
	public static int of(final Entity entity) {
		Object share = FabricLoader.getInstance().getObjectShare().get(THREAT_LEVEL_KEY);
		if (share == null) {
			return -1;
		}
		try {
			Integer value = ((Function<Entity, Integer>) share).apply(entity);
			return value == null ? -1 : value;
		} catch (ClassCastException e) {
			warnOnce(THREAT_LEVEL_KEY, e);
			return -1;
		}
	}

	public static final String APPLY_LEVEL_KEY = "mod-stella-mobs:apply-level";

	private static volatile BiFunction<ServerLevel, Vec3, Integer> areaSource;

	/** Replaces the area level source and returns the old one, so a test can restore it in a finally block. */
	public static BiFunction<ServerLevel, Vec3, Integer> replaceAreaSource(final BiFunction<ServerLevel, Vec3, Integer> replacement) {
		BiFunction<ServerLevel, Vec3, Integer> old = areaSource;
		areaSource = replacement;
		return old;
	}

	/** The area level a test fixed for a spot, or -1 when none is set. */
	static int fixedArea(final ServerLevel level, final Vec3 at) {
		BiFunction<ServerLevel, Vec3, Integer> source = areaSource;
		Integer value = source == null ? null : source.apply(level, at);
		return value == null ? -1 : value;
	}

	/** True when the mobs mod publishes the level fix. */
	public static boolean canApply() {
		return FabricLoader.getInstance().getObjectShare().get(APPLY_LEVEL_KEY) instanceof BiConsumer<?, ?>;
	}

	/** Gives a mob the level through the mobs mod. Does nothing when the share is absent or has another type. */
	@SuppressWarnings("unchecked")
	public static void apply(final Mob mob, final int level) {
		Object share = FabricLoader.getInstance().getObjectShare().get(APPLY_LEVEL_KEY);
		if (!(share instanceof BiConsumer<?, ?>)) {
			return;
		}
		try {
			((BiConsumer<Mob, Integer>) share).accept(mob, level);
		} catch (ClassCastException e) {
			warnOnce(APPLY_LEVEL_KEY, e);
		}
	}

	private static void warnOnce(final String key, final ClassCastException e) {
		if (!warned) {
			warned = true;
			StellaRuins.LOGGER.warn("[RUINS] object share {} has an unexpected type, levels read as absent: {}", key, e.toString());
		}
	}
}
