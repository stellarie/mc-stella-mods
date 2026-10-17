package com.stella.end;

import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

/**
 * The optional link to the rpg mod's health scale K, reached only through ObjectShare. That mod multiplies every
 * creature's health and the damage it takes by K, so a number meant in vanilla health is stated times K. Without the
 * rpg mod K is 1.
 */
public final class HealthBridge {
	public static final String SCALE_KEY = "mod-stella-rpg:health-scale";
	/** The modifiers of the rpg mod carry its health scale, and they stay. */
	private static final String SCALE_NAMESPACE = "mod-stella-rpg";

	private HealthBridge() {
	}

	public static double scale() {
		return FabricLoader.getInstance().getObjectShare().get(SCALE_KEY) instanceof Double value && value >= 1.0 ? value : 1.0;
	}

	/**
	 * Gives a mob a whole maximum health that its level and every other modifier cannot change. The base is the total
	 * over K, so the health scale of the rpg mod, added when the mob loads, brings it to the total. What the attribute
	 * ceiling cannot hold is cut. The mob stands at full health.
	 */
	public static void fixMaxHealth(final Mob mob, final double total) {
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
		if (health == null) {
			return;
		}
		for (AttributeModifier modifier : List.copyOf(health.getModifiers())) {
			if (!modifier.id().getNamespace().equals(SCALE_NAMESPACE)) {
				health.removeModifier(modifier);
			}
		}
		double ceiling = ((RangedAttribute) Attributes.MAX_HEALTH.value()).getMaxValue();
		health.setBaseValue(Math.min(total, ceiling) / scale());
		mob.setHealth(mob.getMaxHealth());
	}
}
