package com.stella.tower;

/**
 * Which biome a floor takes: a pure function of the tower's seed and the floor number.
 */
public final class FloorBiomes {
	private FloorBiomes() {
	}

	public static FloorBiome of(final long seed, final int floor) {
		return FloorBiome.RAINY_FOREST;
	}
}
