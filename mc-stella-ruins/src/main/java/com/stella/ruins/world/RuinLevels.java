package com.stella.ruins.world;

import com.stella.ruins.StellaRuins;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
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

	private static void warnOnce(final String key, final ClassCastException e) {
		if (!warned) {
			warned = true;
			StellaRuins.LOGGER.warn("[RUINS] object share {} has an unexpected type, levels read as absent: {}", key, e.toString());
		}
	}
}
