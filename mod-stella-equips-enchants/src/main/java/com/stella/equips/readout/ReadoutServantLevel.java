package com.stella.equips.readout;

import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;

/**
 * The live level a summoned servant publishes under {@code mod-stella-spells:servant-level}, or
 * {@link ReadoutThreat#NONE} when that mod is absent, the entity is not a servant, or the share answers
 * nothing usable. It is asked before the mobs mod's rolled spawn level, because a servant levels up.
 */
public final class ReadoutServantLevel {
	public static final String OBJECT_SHARE_KEY = "mod-stella-spells:servant-level";

	private ReadoutServantLevel() {
	}

	@SuppressWarnings("unchecked")
	public static int levelOf(final Entity entity) {
		Object shared = FabricLoader.getInstance().getObjectShare().get(OBJECT_SHARE_KEY);
		if (!(shared instanceof Function<?, ?> provider)) {
			return ReadoutThreat.NONE;
		}
		try {
			Object level = ((Function<Entity, ?>) provider).apply(entity);
			return level instanceof Integer value && value >= 0 ? value : ReadoutThreat.NONE;
		} catch (RuntimeException e) {
			return ReadoutThreat.NONE;
		}
	}
}
