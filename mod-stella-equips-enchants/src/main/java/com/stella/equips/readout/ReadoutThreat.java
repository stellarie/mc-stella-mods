package com.stella.equips.readout;

import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;

/** The level a mob shows: a summoned servant's live level first, else the level mod-stella-mobs rolled. */
public final class ReadoutThreat {
	public static final String OBJECT_SHARE_KEY = "mod-stella-mobs:threat-level";
	public static final int NONE = -1;

	private ReadoutThreat() {
	}

	@SuppressWarnings("unchecked")
	public static int levelOf(final Entity entity) {
		int servant = ReadoutServantLevel.levelOf(entity);
		if (servant != NONE) {
			return servant;
		}
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
