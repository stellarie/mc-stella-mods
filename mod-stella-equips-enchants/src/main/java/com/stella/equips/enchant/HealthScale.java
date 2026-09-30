package com.stella.equips.enchant;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.LivingEntity;

/**
 * The factor K by which mod-stella-rpg multiplies health, read from its object share and 1 when that mod is
 * absent. A proc that takes a share of max health divides by K first, so its number stays in vanilla units.
 */
public final class HealthScale {
	public static final String OBJECT_SHARE_KEY = "mod-stella-rpg:health-scale";

	private HealthScale() {
	}

	public static double factor() {
		Object shared = FabricLoader.getInstance().getObjectShare().get(OBJECT_SHARE_KEY);
		return shared instanceof Double scale && Double.isFinite(scale) && scale > 0.0 ? scale : 1.0;
	}

	/** The entity's max health in vanilla units. */
	public static float vanillaMaxHealth(final LivingEntity entity) {
		return (float) (entity.getMaxHealth() / factor());
	}
}
