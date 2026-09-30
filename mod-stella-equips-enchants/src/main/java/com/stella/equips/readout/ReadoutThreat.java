package com.stella.equips.readout;

import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;

/** The level mod-stella-mobs rolled for a mob, or -1 when that mod is absent or the entity has none. */
public final class ReadoutThreat {
	public static final String OBJECT_SHARE_KEY = "mod-stella-mobs:threat-level";
	public static final int NONE = -1;

	private ReadoutThreat() {
	}

	@SuppressWarnings("unchecked")
	public static int levelOf(final Entity entity) {
		Object shared = FabricLoader.getInstance().getObjectShare().get(OBJECT_SHARE_KEY);
		if (!(shared instanceof Function<?, ?> provider)) {
			return NONE;
		}
		try {
			Object level = ((Function<Entity, ?>) provider).apply(entity);
			return level instanceof Integer value && value >= 0 ? value : NONE;
		} catch (RuntimeException e) {
			return NONE;
		}
	}
}
