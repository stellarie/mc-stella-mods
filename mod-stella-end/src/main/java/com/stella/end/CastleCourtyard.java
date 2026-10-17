package com.stella.end;

import java.util.Random;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/** The courtyard in the heart of floors 1 to 3: grass, flowers, a fountain and a few trees, open up to the floor 4 slab. */
final class CastleCourtyard {
	private static final CastleLayout.Rect YARD = CastleLayout.COURTYARD;
	/** The fountain basin: a square ring of brick round four by four cells of water. */
	private static final int BASIN_FROM = 13;
	private static final int BASIN_TO = 18;
	private static final int[][] TREES = {{11, 11}, {20, 12}, {12, 20}};

	private CastleCourtyard() {
	}

	static void build(final CastleSite site) {
		Random random = site.random(21);
		int ground = EndCastle.slabY(1);
		int y = ground + 1;
		BlockState[] flowers = {
			Blocks.ALLIUM.defaultBlockState(), Blocks.AZURE_BLUET.defaultBlockState(), Blocks.CORNFLOWER.defaultBlockState(),
			Blocks.OXEYE_DAISY.defaultBlockState(), Blocks.LILY_OF_THE_VALLEY.defaultBlockState(), Blocks.POPPY.defaultBlockState()
		};
		for (int x = YARD.x0(); x <= YARD.x1(); x++) {
			for (int z = YARD.z0(); z <= YARD.z1(); z++) {
				boolean path = x == 15 || x == 16 || z == 15 || z == 16;
				site.put(x, ground, z, path && random.nextInt(4) > 0 ? CastleBlocks.COARSE : random.nextInt(8) == 0 ? CastleBlocks.COARSE : CastleBlocks.GRASS);
				boolean basin = x >= BASIN_FROM && x <= BASIN_TO && z >= BASIN_FROM && z <= BASIN_TO;
				if (!basin && !path) {
					int roll = random.nextInt(10);
					if (roll < 4) {
						site.put(x, y, z, CastleBlocks.SHORT_GRASS);
					} else if (roll < 6) {
						site.put(x, y, z, flowers[random.nextInt(flowers.length)]);
					}
				}
			}
		}
		fountain(site, ground);
		for (int[] tree : TREES) {
			tree(site, tree[0], tree[1], ground + 1, 7 + random.nextInt(4), random);
		}
	}

	/** A ring of brick one block high, water inside, and a pillar in the middle with two candles on the rim. */
	private static void fountain(final CastleSite site, final int ground) {
		int y = ground + 1;
		for (int x = BASIN_FROM; x <= BASIN_TO; x++) {
			for (int z = BASIN_FROM; z <= BASIN_TO; z++) {
				boolean rim = x == BASIN_FROM || x == BASIN_TO || z == BASIN_FROM || z == BASIN_TO;
				site.put(x, ground, z, CastleBlocks.BRICKS);
				site.put(x, y, z, rim ? CastleBlocks.BRICKS : CastleBlocks.WATER);
			}
		}
		for (int x = 15; x <= 16; x++) {
			for (int z = 15; z <= 16; z++) {
				for (int up = 0; up < 3; up++) {
					site.put(x, y + up, z, CastleBlocks.PILLAR);
				}
				site.put(x, y + 3, z, CastleBlocks.slab(Blocks.PURPUR_SLAB, SlabType.BOTTOM));
			}
		}
		site.put(BASIN_FROM, y + 1, BASIN_FROM, CastleBlocks.candle(2));
		site.put(BASIN_TO, y + 1, BASIN_TO, CastleBlocks.candle(3));
	}

	/** An oak that never decays: a trunk and a rounded crown. Leaves leave the walls and the doorways alone. */
	private static void tree(final CastleSite site, final int x, final int z, final int base, final int height, final Random random) {
		for (int up = 0; up < height; up++) {
			site.put(x, base + up, z, CastleBlocks.LOG);
		}
		for (int layer = -2; layer <= 1; layer++) {
			int radius = layer <= -1 ? 2 : 1;
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					boolean corner = Math.abs(dx) == radius && Math.abs(dz) == radius;
					if (corner && random.nextBoolean()) {
						continue;
					}
					site.putIfAir(x + dx, base + height + layer, z + dz, CastleBlocks.LEAVES);
				}
			}
		}
		site.put(x, base + height, z, CastleBlocks.LOG);
	}
}
