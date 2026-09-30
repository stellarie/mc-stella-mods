package com.stella.rpg.api;

import com.stella.rpg.RpgScale;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Publishes the health scale K under {@code mod-stella-rpg:health-scale} as a {@code Double}.
 * A reader needs no class from this mod. The value follows the config after each reload.
 */
public final class HealthScaleShare {
	public static final String OBJECT_SHARE_KEY = "mod-stella-rpg:health-scale";

	private HealthScaleShare() {
	}

	public static void publish() {
		FabricLoader.getInstance().getObjectShare().put(OBJECT_SHARE_KEY, Double.valueOf(RpgScale.k()));
	}
}
