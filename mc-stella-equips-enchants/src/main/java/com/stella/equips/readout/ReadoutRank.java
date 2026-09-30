package com.stella.equips.readout;

import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;

/** The optional mob rank published by mod-stella-mobs; any missing or odd value reads as no rank. */
public enum ReadoutRank {
	NORMAL(""),
	ELITE("<Elite>"),
	BOSS("<Boss>");

	public static final String OBJECT_SHARE_KEY = "mod-stella-mobs:readout-rank";

	private final String tag;

	ReadoutRank(final String tag) {
		this.tag = tag;
	}

	public String tag() {
		return this.tag;
	}

	@SuppressWarnings("unchecked")
	public static ReadoutRank of(final Entity entity) {
		Object shared = FabricLoader.getInstance().getObjectShare().get(OBJECT_SHARE_KEY);
		if (!(shared instanceof Function<?, ?> provider)) {
			return NORMAL;
		}

		try {
			Object rank = ((Function<Entity, ?>) provider).apply(entity);
			if ("elite".equals(rank)) {
				return ELITE;
			}

			return "boss".equals(rank) ? BOSS : NORMAL;
		} catch (RuntimeException e) {
			return NORMAL;
		}
	}
}
