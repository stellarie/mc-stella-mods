package com.stella.tower;

/**
 * What lies about a biome floor: the things of the adventurers who died in it. A floor holds 60 to 110 on the ground and
 * 40 to 70 on walls and trunks. A ground prop is one block high or lower, so a body steps over it; a wall prop hangs
 * two blocks or more over the feet, so it never cuts a walk. Barrels are empty, and there are at most
 * {@value #BARRELS} of them to a floor.
 */
final class BiomeProps {
	/** The most barrels a floor holds in all: those of its buildings and camps count. */
	static final int BARRELS = 30;
	private static final byte[] COMMON = {BiomeBlocks.SKULL, BiomeBlocks.SKULL_R4, BiomeBlocks.SKULL_R8, BiomeBlocks.SKULL_R12, BiomeBlocks.BARREL,
		BiomeBlocks.BONE_BLOCK, BiomeBlocks.CANDLE};

	private BiomeProps() {
	}

	/** The ground props a biome draws among: the common ones, and its own. */
	static byte[] floor(final FloorBiome biome) {
		byte[] own = switch (biome) {
			case DESTROYED_VILLAGE -> new byte[] {BiomeBlocks.ANVIL, BiomeBlocks.GRINDSTONE, BiomeBlocks.LECTERN, BiomeBlocks.CAULDRON, BiomeBlocks.DECORATED_POT};
			case DESTROYED_CASTLE -> new byte[] {BiomeBlocks.ANVIL, BiomeBlocks.GRINDSTONE, BiomeBlocks.CAULDRON, BiomeBlocks.WITHER_SKULL};
			case ADVENTURERS_GRAVEYARD -> new byte[] {BiomeBlocks.WITHER_SKULL, BiomeBlocks.ZOMBIE_HEAD, BiomeBlocks.COBWEB};
			case SCORCHED_BATTLEFIELD -> new byte[] {BiomeBlocks.ANVIL, BiomeBlocks.GRINDSTONE, BiomeBlocks.WITHER_SKULL};
			case JUNGLE -> new byte[] {BiomeBlocks.COBWEB, BiomeBlocks.DECORATED_POT};
			case MISTBOUND_MARSH -> new byte[] {BiomeBlocks.ZOMBIE_HEAD, BiomeBlocks.FLOWER_POT};
			case BASALT_DELTAS -> new byte[] {BiomeBlocks.WITHER_SKULL, BiomeBlocks.ANVIL};
			case ETERNAL_SNOWSTORM -> new byte[] {BiomeBlocks.BARREL, BiomeBlocks.ZOMBIE_HEAD};
			case DESERT_STORM -> new byte[] {BiomeBlocks.DECORATED_POT, BiomeBlocks.CAULDRON};
			case AMETHYST_CAVE -> new byte[] {BiomeBlocks.ANVIL, BiomeBlocks.GRINDSTONE};
			case SUNKEN_CATACOMBS -> new byte[] {BiomeBlocks.WITHER_SKULL, BiomeBlocks.COBWEB, BiomeBlocks.DECORATED_POT};
			case GREAT_HALL -> new byte[] {BiomeBlocks.LECTERN, BiomeBlocks.DECORATED_POT};
			default -> new byte[] {BiomeBlocks.COBWEB, BiomeBlocks.FLOWER_POT};
		};
		byte[] all = java.util.Arrays.copyOf(COMMON, COMMON.length + own.length);
		System.arraycopy(own, 0, all, COMMON.length, own.length);
		return all;
	}
}
