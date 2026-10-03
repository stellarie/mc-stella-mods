package com.stella.spells;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Test harness only. The RPG mod is not in this harness, so its health scale K never multiplies a heal or a
 * hit. While a test switches this on, two test mixins apply K as the RPG mod does, from the same share.
 */
public final class KScaleHarness {
	public static final String SHARE = "mod-stella-rpg:health-scale";
	private static volatile boolean on;

	private KScaleHarness() {
	}

	/** Publishes K and switches the scaling on; returns the share it replaced. */
	public static Object enable(final double k) {
		Object saved = FabricLoader.getInstance().getObjectShare().get(SHARE);
		FabricLoader.getInstance().getObjectShare().put(SHARE, Double.valueOf(k));
		on = true;
		return saved;
	}

	public static void disable(final Object saved) {
		on = false;
		FabricLoader.getInstance().getObjectShare().put(SHARE, saved == null ? Double.valueOf(1.0) : saved);
	}

	/** K while a test has the scaling on, else 1. */
	public static float factor() {
		if (!on) {
			return 1.0F;
		}
		return FabricLoader.getInstance().getObjectShare().get(SHARE) instanceof Double k ? k.floatValue() : 1.0F;
	}
}
