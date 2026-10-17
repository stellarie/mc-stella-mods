package com.stella.end;

import java.util.Random;
import net.minecraft.world.level.block.state.BlockState;

/** The ground of the castle: the cleared volume, the foundation down to the island, and the paved yard. */
final class CastleGround {
	/** The outer zone in local keep cells, inclusive. */
	static final int ZONE_FROM = -EndCastle.ZONE_MARGIN;
	static final int ZONE_TO = EndCastle.SIZE + EndCastle.ZONE_MARGIN - 1;
	/** The yard is cleared this high above the ground; the keep and the disc are cleared to the tower tops. */
	private static final int YARD_CLEAR = 24;

	private CastleGround() {
	}

	/** Loads every chunk of the zone, so the build never meets a chunk that has not generated. */
	static void load(final CastleSite site) {
		int x0 = site.min.getX() + ZONE_FROM >> 4;
		int x1 = site.min.getX() + ZONE_TO >> 4;
		int z0 = site.min.getZ() + ZONE_FROM >> 4;
		int z1 = site.min.getZ() + ZONE_TO >> 4;
		for (int cx = x0; cx <= x1; cx++) {
			for (int cz = z0; cz <= z1; cz++) {
				site.level.getChunk(cx, cz);
			}
		}
	}

	static boolean inKeep(final int x, final int z) {
		return x >= 0 && z >= 0 && x < EndCastle.SIZE && z < EndCastle.SIZE;
	}

	/** Whether a local cell lies in the rooftop disc, which is centred on the middle block of the keep. */
	static boolean inDisc(final int x, final int z) {
		return EndCastle.inDisc(x - EndCastle.SIZE / 2, z - EndCastle.SIZE / 2);
	}

	/** Clears the castle's volume above the ground. Terrain that rises above the base goes with it. */
	static void clear(final CastleSite site) {
		for (int x = ZONE_FROM; x <= ZONE_TO; x++) {
			for (int z = ZONE_FROM; z <= ZONE_TO; z++) {
				int top = inKeep(x, z) || inDisc(x, z) ? EndCastle.ROOF_Y + EndCastle.TOWER_HEIGHT + 2 : EndCastle.BASE_Y + YARD_CLEAR;
				for (int y = EndCastle.BASE_Y; y <= top; y++) {
					if (!site.get(x, y, z).isAir()) {
						site.put(x, y, z, CastleBlocks.AIR);
					}
				}
			}
		}
	}

	/**
	 * Fills every column of the zone from the base down to the island, so the keep stands on the ground and no air
	 * lies between the two. A column with no ground within reach is filled to the limit.
	 */
	static void foundation(final CastleSite site) {
		for (int x = ZONE_FROM; x <= ZONE_TO; x++) {
			for (int z = ZONE_FROM; z <= ZONE_TO; z++) {
				int floor = EndCastle.BASE_Y - EndCastle.MAX_FOUNDATION;
				for (int y = EndCastle.BASE_Y - 1; y >= floor; y--) {
					if (!site.get(x, y, z).isAir()) {
						floor = y + 1;
						break;
					}
				}
				for (int y = floor; y < EndCastle.BASE_Y; y++) {
					site.put(x, y, z, y >= EndCastle.BASE_Y - 2 ? CastleBlocks.BRICKS : CastleBlocks.END_STONE);
				}
			}
		}
	}

	/** The yard round the keep is paved at the level of the floor 1 slab. */
	static void paving(final CastleSite site) {
		Random random = site.random(11);
		for (int x = ZONE_FROM; x <= ZONE_TO; x++) {
			for (int z = ZONE_FROM; z <= ZONE_TO; z++) {
				if (inKeep(x, z)) {
					continue;
				}
				int roll = random.nextInt(20);
				BlockState paving = roll < 13 ? CastleBlocks.BRICKS : roll < 17 ? CastleBlocks.END_STONE : roll < 19 ? CastleBlocks.CRACKED : CastleBlocks.GRAVEL;
				site.put(x, EndCastle.BASE_Y, z, paving);
			}
		}
	}
}
