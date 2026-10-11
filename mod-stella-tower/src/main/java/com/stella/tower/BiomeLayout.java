package com.stella.tower;

/**
 * How a biome floor is laid out. Every floor has the standard layout; a floor larger than the base size may take one of
 * the others, each a set piece that the larger room makes room for, set in the biome's own stone with its cover and hills.
 */
public enum BiomeLayout {
	/** The biome's own features, scattered over the floor. */
	STANDARD,
	/** A town inside a curtain wall with gates, towers at the corners, streets, a plaza with a well and ruined houses. */
	WALLED_TOWN,
	/** Cliffs on both sides of the road, broken by gaps, and buttes of rock on the plains. */
	CANYON,
	/** A fortress's yard: a thick ruined wall with gates and breaches, four tall corner towers, a keep and two ruined rows. */
	FORTRESS_YARD;

	/** The share of the larger open floors that take a layout other than the standard. */
	public static final double SHARE = 0.6;
	private static final long SALT = 0x4C41594FL;

	/** The layout a floor is drawn with: a pure function of the tower's seed, the floor, its biome and its size. */
	public static BiomeLayout of(final long seed, final int floor, final FloorBiome biome, final TowerSizes.Size size) {
		if (size.area() <= TowerLayout.SIZE * TowerLayout.SIZE || !biome.openRoof() || biome == FloorBiome.DESTROYED_CASTLE) {
			return STANDARD;
		}
		java.util.Random random = new java.util.Random(TowerPlan.mix(seed ^ SALT ^ (floor * 0x9E3779B97F4A7C15L)));
		if (random.nextDouble() >= SHARE) {
			return STANDARD;
		}
		java.util.List<BiomeLayout> fits = switch (biome) {
			case RAINY_FOREST -> java.util.List.of(WALLED_TOWN, CANYON);
			case DESTROYED_VILLAGE -> java.util.List.of(WALLED_TOWN, FORTRESS_YARD);
			case ADVENTURERS_GRAVEYARD, MISTBOUND_MARSH -> java.util.List.of(FORTRESS_YARD);
			case SCORCHED_BATTLEFIELD -> java.util.List.of(CANYON, FORTRESS_YARD);
			case JUNGLE -> java.util.List.of(CANYON);
			case ETERNAL_SNOWSTORM -> java.util.List.of(WALLED_TOWN, FORTRESS_YARD);
			case DESERT_STORM -> java.util.List.of(WALLED_TOWN, CANYON);
			default -> java.util.List.<BiomeLayout>of();
		};
		return fits.isEmpty() ? STANDARD : fits.get(random.nextInt(fits.size()));
	}
}
