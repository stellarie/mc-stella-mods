package com.stella.mobs.config;

import java.util.LinkedHashMap;
import java.util.Map;

/** The numbers behind a mob's level, health, damage, armor piercing, and kill experience. */
public record ScalingConfig(
	int floorRadius,
	int floorOffset,
	double depthBlocksPerLevel,
	double heightBlocksPerLevel,
	double healthPerLevel,
	double damageGrowth,
	int tailStart,
	double tailMax,
	double tailMidpoint,
	double tailWidth,
	double tailEnd,
	double tailFlatPerLevel,
	double piercePerLevel,
	double pierceCap,
	Map<String, Integer> xpBase,
	int xpBaseHostile,
	int xpBasePassive,
	double xpLevelFactor,
	double xpGapStep,
	double xpGapMin,
	double xpGapMax,
	double xpEliteMultiplier,
	double xpNemesisMultiplier
) {
	public static final ScalingConfig DEFAULTS = new ScalingConfig(
		128, 2, 6.4, 80.0, 0.20, 1.06, 80, 19.0, 500.0, 150.0, 1000.0, 0.02, 0.015, 0.5,
		defaultXpBase(), 6, 1, 1.5, 0.05, 0.1, 1.5, 3.0, 5.0
	);

	public ScalingConfig {
		xpBase = Map.copyOf(xpBase);
	}

	private static Map<String, Integer> defaultXpBase() {
		Map<String, Integer> base = new LinkedHashMap<>();
		put(base, 2, "slime", "magma_cube", "silverfish");
		put(base, 3, "endermite", "vex");
		put(base, 6, "zombie", "husk", "drowned", "zombie_villager", "skeleton", "stray", "bogged", "spider",
			"cave_spider", "creeper", "phantom", "zombified_piglin", "piglin", "pillager");
		put(base, 8, "hoglin", "zoglin");
		put(base, 12, "enderman", "witch", "blaze", "ghast", "vindicator", "guardian", "shulker",
			"wither_skeleton", "creaking");
		put(base, 20, "piglin_brute", "illusioner");
		put(base, 40, "evoker", "ravager", "elder_guardian", "breeze");
		put(base, 500, "wither", "warden");
		return base;
	}

	private static void put(final Map<String, Integer> base, final int value, final String... names) {
		for (String name : names) {
			base.put("minecraft:" + name, value);
		}
	}
}
