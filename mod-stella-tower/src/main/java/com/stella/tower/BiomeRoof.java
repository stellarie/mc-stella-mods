package com.stella.tower;

/**
 * The roof of an open biome floor: a layer of solid stone over the whole room, so the sky is never seen. Each biome has
 * its own two or three kinds of stone, drawn in patches three blocks across so the vault reads as rock and not as paint.
 */
final class BiomeRoof {
	private static final long SALT = 0x524F4F46L;
	/** The patch size, in blocks, a kind of stone is drawn over. */
	private static final int PATCH = 3;

	private BiomeRoof() {
	}

	/** The palette entry of the roof's block over a column: the first kind most of the time, the others in patches. */
	static byte at(final FloorBiome biome, final long seed, final int floor, final int x, final int z) {
		byte[] kinds = kinds(biome);
		long h = TowerPlan.mix(seed ^ SALT ^ (floor * 0x9E3779B97F4A7C15L) ^ (Math.floorDiv(x, PATCH) * 0xC2B2AE3D27D4EB4FL) ^ (Math.floorDiv(z, PATCH) * 0x165667B19E3779F9L));
		int roll = (int) ((h >>> 11) % 100L);
		// 70 in a hundred the first kind, 20 the second, 10 the third; a biome with two kinds gives the third's share to the first.
		if (roll < 70 || kinds.length == 1) {
			return kinds[0];
		}
		return roll < 90 || kinds.length == 2 ? kinds[1] : kinds[2];
	}

	/** The kinds of stone a biome's roof is made of, most common first. */
	static byte[] kinds(final FloorBiome biome) {
		return switch (biome) {
			case RAINY_FOREST -> new byte[] {BiomeBlocks.DEEPSLATE, BiomeBlocks.TUFF, BiomeBlocks.MOSS};
			case DESTROYED_VILLAGE -> new byte[] {BiomeBlocks.STONE, BiomeBlocks.COBBLESTONE, BiomeBlocks.POLISHED_ANDESITE};
			case DESTROYED_CASTLE -> new byte[] {BiomeBlocks.DEEPSLATE_BRICKS, BiomeBlocks.CRACKED_DEEPSLATE, BiomeBlocks.DEEPSLATE};
			case ADVENTURERS_GRAVEYARD -> new byte[] {BiomeBlocks.DEEPSLATE, BiomeBlocks.BLACKSTONE, BiomeBlocks.CRACKED_DEEPSLATE};
			case SCORCHED_BATTLEFIELD -> new byte[] {BiomeBlocks.BLACKSTONE, BiomeBlocks.BASALT, BiomeBlocks.COAL_BLOCK};
			case JUNGLE -> new byte[] {BiomeBlocks.MOSS, BiomeBlocks.COBBLE, BiomeBlocks.STONE};
			case MISTBOUND_MARSH -> new byte[] {BiomeBlocks.PACKED_MUD, BiomeBlocks.MUD, BiomeBlocks.MOSS};
			case ETERNAL_SNOWSTORM -> new byte[] {BiomeBlocks.PACKED_ICE, BiomeBlocks.SNOW_BLOCK, BiomeBlocks.BLUE_ICE};
			case DESERT_STORM -> new byte[] {BiomeBlocks.SANDSTONE, BiomeBlocks.CUT_SANDSTONE};
			// The closed biomes roof themselves with the tower's own wall; these are the open ones' kin, for a caller that asks.
			default -> new byte[] {BiomeBlocks.DEEPSLATE};
		};
	}
}
