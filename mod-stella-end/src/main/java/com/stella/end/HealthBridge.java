package com.stella.end;

import net.fabricmc.loader.api.FabricLoader;

/**
 * The optional link to the rpg mod's health scale K, reached only through ObjectShare. That mod multiplies every
 * creature's health and the damage it takes by K, so a number meant in vanilla health is stated times K. Without the
 * rpg mod K is 1.
 */
public final class HealthBridge {
	public static final String SCALE_KEY = "mod-stella-rpg:health-scale";

	private HealthBridge() {
	}

	public static double scale() {
		return FabricLoader.getInstance().getObjectShare().get(SCALE_KEY) instanceof Double value && value >= 1.0 ? value : 1.0;
	}
}
