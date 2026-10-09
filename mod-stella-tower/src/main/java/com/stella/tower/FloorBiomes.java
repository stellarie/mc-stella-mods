package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Which biome a floor takes: a pure function of the tower's seed and the floor number, found in constant time at any floor.
 * <p>
 * Floor 1 draws among the gentle biomes. From floor 2 the floors run in blocks, one block to a shuffle of all the biomes,
 * so every biome comes about as often as any other and none twice in a block. A block's first biome is also kept off the
 * last biome of the block before, which depends only on that block's own shuffle, so no two floors in a row look alike.
 * With six biomes or more the block's second biome is kept off the block before's last but one as well.
 */
public final class FloorBiomes {
	private static final long SALT = 0x42494D45L;
	private static final long FIRST_SALT = 0x46495253L;
	private static final int TWO_BACK_FROM = 6;

	private FloorBiomes() {
	}

	public static FloorBiome of(final long seed, final int floor) {
		if (floor <= 1) {
			return first(seed);
		}
		int count = FloorBiome.DRAWN.size();
		int index = floor - 2;
		return FloorBiome.DRAWN.get(block(seed, Math.floorDiv(index, count))[index % count]);
	}

	private static FloorBiome first(final long seed) {
		List<FloorBiome> gentle = new ArrayList<>();
		for (FloorBiome biome : FloorBiome.DRAWN) {
			if (biome.gentle()) {
				gentle.add(biome);
			}
		}
		return gentle.get(new Random(TowerPlan.mix(seed ^ FIRST_SALT)).nextInt(gentle.size()));
	}

	/** The order of the biomes in a block, as indexes: a shuffle, its first places mended against the block before. */
	static int[] block(final long seed, final int block) {
		int[] order = shuffle(seed, block);
		int count = order.length;
		int[] before = block == 0 ? new int[] {FloorBiome.DRAWN.indexOf(first(seed))} : last(shuffle(seed, block - 1));
		int mend = count >= TWO_BACK_FROM ? 2 : 1;
		for (int place = 0; place < mend; place++) {
			if (!contains(before, order[place])) {
				continue;
			}
			// A place in the block's middle, which neither this block's first places nor its last ones are.
			for (int with = mend; with < count - 2; with++) {
				if (!contains(before, order[with])) {
					int moved = order[place];
					order[place] = order[with];
					order[with] = moved;
					break;
				}
			}
		}
		return order;
	}

	private static int[] last(final int[] order) {
		return order.length >= TWO_BACK_FROM ? new int[] {order[order.length - 1], order[order.length - 2]} : new int[] {order[order.length - 1]};
	}

	private static boolean contains(final int[] values, final int value) {
		for (int each : values) {
			if (each == value) {
				return true;
			}
		}
		return false;
	}

	private static int[] shuffle(final long seed, final int block) {
		int count = FloorBiome.DRAWN.size();
		int[] order = new int[count];
		for (int i = 0; i < count; i++) {
			order[i] = i;
		}
		Random random = new Random(TowerPlan.mix(seed ^ SALT ^ (block * 0x9E3779B97F4A7C15L)));
		for (int i = count - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			int moved = order[i];
			order[i] = order[j];
			order[j] = moved;
		}
		return order;
	}
}
