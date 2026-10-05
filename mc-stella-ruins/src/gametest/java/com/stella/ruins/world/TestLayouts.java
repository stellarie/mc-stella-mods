package com.stella.ruins.world;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

/** Layouts for the test sites. Each draws random elements: its palette, its decay, its pillars and its chest facing. */
final class TestLayouts {
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState[] PALETTE = {
		Blocks.STONE_BRICKS.defaultBlockState(),
		Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
		Blocks.COBBLESTONE.defaultBlockState(),
		Blocks.DEEPSLATE_BRICKS.defaultBlockState()
	};
	/** The share of wall blocks that have fallen, in hundredths. */
	static final int DECAY = 25;

	private TestLayouts() {
	}

	/**
	 * A walled yard with a floor, a decayed wall, pillars, a core in the middle, a vault in one corner, four spawn
	 * markers and a chest. {@code noise} adds a pass over the whole volume, to make a large layout heavy.
	 */
	static SiteLayout yard(final int sizeX, final int sizeY, final int sizeZ, final ResourceKey<LootTable> chestTable, final boolean noise) {
		return ctx -> {
			RandomSource random = ctx.random();
			BlockState wall = PALETTE[random.nextInt(PALETTE.length)];
			if (ctx.foundation() > 0) {
				ctx.fill(0, -ctx.foundation(), 0, sizeX - 1, -1, sizeZ - 1, Blocks.STONE.defaultBlockState());
			}
			ctx.fill(0, 0, 0, sizeX - 1, 0, sizeZ - 1, wall);
			ctx.fill(1, 1, 1, sizeX - 2, sizeY - 1, sizeZ - 2, AIR);
			for (int x = 0; x < sizeX; x++) {
				for (int z = 0; z < sizeZ; z++) {
					if (x == 0 || z == 0 || x == sizeX - 1 || z == sizeZ - 1) {
						for (int y = 1; y <= Math.min(4, sizeY - 1); y++) {
							if (random.nextInt(100) >= DECAY) {
								ctx.set(x, y, z, wall);
							}
						}
					}
				}
			}
			int pillars = 2 + random.nextInt(4);
			for (int i = 0; i < pillars; i++) {
				int x = 2 + random.nextInt(Math.max(1, sizeX - 4));
				int z = 2 + random.nextInt(Math.max(1, sizeZ - 4));
				ctx.fill(x, 1, z, x, 1 + random.nextInt(Math.min(4, sizeY - 2)), z, wall);
			}
			if (noise) {
				for (int x = 1; x < sizeX - 1; x++) {
					for (int y = 5; y < sizeY - 1; y++) {
						for (int z = 1; z < sizeZ - 1; z++) {
							if (random.nextInt(97) == 0) {
								ctx.set(x, y, z, Blocks.COBWEB.defaultBlockState());
							}
						}
					}
				}
			}
			ctx.placeCore(sizeX / 2, 1, sizeZ / 2);
			ctx.placeVault(2, 1, 2);
			ctx.placeChest(sizeX - 3, 1, sizeZ - 3, Direction.Plane.HORIZONTAL.getRandomDirection(random), chestTable);
			ctx.marker(RuinMarkers.SPAWN, 4, 1, 4);
			ctx.marker(RuinMarkers.SPAWN, sizeX - 5, 1, 4);
			ctx.marker(RuinMarkers.SPAWN, 4, 1, sizeZ - 5);
			ctx.marker(RuinMarkers.SPAWN, sizeX - 6, 1, sizeZ - 6);
		};
	}
}
