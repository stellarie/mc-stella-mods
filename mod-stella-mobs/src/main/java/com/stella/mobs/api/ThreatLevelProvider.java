package com.stella.mobs.api;

import com.stella.mobs.scaling.ThreatState;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/**
 * Publishes a mob's real level under {@code mod-stella-mobs:threat-level} as a {@code Function<Entity, Integer>}.
 * It returns {@code ThreatState.level} for a mob and -1 for any other entity.
 */
public final class ThreatLevelProvider {
	public static final String OBJECT_SHARE_KEY = "mod-stella-mobs:threat-level";

	private ThreatLevelProvider() {
	}

	public static void register() {
		Function<Entity, Integer> provider = ThreatLevelProvider::level;
		FabricLoader.getInstance().getObjectShare().put(OBJECT_SHARE_KEY, provider);
	}

	public static int level(final Entity entity) {
		return entity instanceof Mob mob ? ThreatState.level(mob) : -1;
	}
}
