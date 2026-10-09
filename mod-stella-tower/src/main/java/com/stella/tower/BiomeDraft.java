package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * What a biome floor adds to its plan: the ground's height, surface and water column by column, the blocks
 * standing on it, and the raised portal's stair. Offsets are from the room's inner corner; a height is the feet
 * level of a body walking the column, so the surface block lies one below. The arrays are shared: never write to them.
 *
 * @param feet the feet level of each column: 0 and up on land, -1 where the column holds water
 * @param surface the palette entry of each column's surface block
 * @param water whether the column holds a block of water at its feet level
 * @param fragile whether a cactus stands in or beside the column, so nothing solid may be set beside it
 * @param ceiling the height of the first block of rock over each column's air, or the column's own feet level where it is rock; null under an open roof
 * @param ceilCode the palette entry of that rock
 * @param lava whether the column holds a block of lava at its feet level, which no body walks
 * @param placed blocks above the surface, as runs of x, y, z and a palette entry
 * @param stand where a body stands in each column at the lowest, or {@link #NONE}; null until the plan is read
 * @param steps how many steps the portal's platform stands above the ground
 */
public record BiomeDraft(FloorBiome biome, short[][] feet, byte[][] surface, boolean[][] water, boolean[][] lava, boolean[][] fragile, short @Nullable [][] ceiling, byte @Nullable [][] ceilCode, int[] placed, short @Nullable [][] stand, int steps) {
	/** A column where no body stands. */
	public static final short NONE = -99;
	/** How far above the feet the portal's backdrop wall rises, in blocks. */
	public static final int BACKDROP = 8;

	BiomeDraft withStand(final short[][] read) {
		return new BiomeDraft(this.biome, this.feet, this.surface, this.water, this.lava, this.fragile, this.ceiling, this.ceilCode, this.placed, read, this.steps);
	}

	/** Whether the column is one the portal's stair, platform or backdrop covers, or the apron at its foot. */
	public boolean inDais(final TowerPlan plan, final int x, final int z) {
		int f = plan.portalFacing();
		int low = Math.min(plan.portalX() - f * 3, plan.portalX() + f * (2 + this.steps)) - 1;
		int high = Math.max(plan.portalX() - f * 3, plan.portalX() + f * (2 + this.steps)) + 1;
		return x >= low && x <= high && z >= plan.portalZ() - 4 - this.steps && z <= plan.portalZ() + 5 + this.steps;
	}

	/**
	 * The stair, platform and backdrop as columns: x, z, and the highest block's offset from the base. The
	 * platform is {@code steps} high; the treads fall one a row toward the room; the backdrop wall closes the
	 * portal's back. The apron at the stair's foot is paved ground and is not a column.
	 */
	List<int[]> daisColumns(final TowerPlan plan) {
		int f = plan.portalFacing();
		int px = plan.portalX();
		int pz = plan.portalZ();
		int s = this.steps;
		List<int[]> columns = new ArrayList<>();
		for (int dx = -2; dx <= 2; dx++) {
			for (int z = pz - 3; z <= pz + 4; z++) {
				columns.add(new int[] {px + dx, z, s - 1});
			}
		}
		for (int k = 1; k < s; k++) {
			for (int z = pz - 3 - k; z <= pz + 4 + k; z++) {
				columns.add(new int[] {px + f * (2 + k), z, s - k - 1});
			}
		}
		int from = f == -1 ? px + 3 : 0;
		int to = f == -1 ? TowerLayout.SIZE - 1 : px - 3;
		for (int z = pz - 4; z <= pz + 5; z++) {
			for (int x = from; x <= to; x++) {
				columns.add(new int[] {x, z, s + BACKDROP - 1});
			}
		}
		return columns;
	}

	/** One block of the dais's dressing: a kind of the volume, or a palette entry when the kind is a biome block. */
	record Piece(int x, int y, int z, byte kind, byte code) {
	}

	/**
	 * The portal's dressing, by room offsets: the stair's steps in the biome's stone, a pillar at each corner of the
	 * platform topped by a lamp, a gate of two pillars and a lintel round the frame with a lantern hung at each side,
	 * a brazier at each front corner, and a skull on a post at each end of the apron.
	 */
	List<Piece> decor(final TowerPlan plan) {
		int f = plan.portalFacing();
		int px = plan.portalX();
		int pz = plan.portalZ();
		int s = this.steps;
		List<Piece> pieces = new ArrayList<>();
		byte climbs = f == 1 ? TowerVolume.DAIS_STAIR_WEST : TowerVolume.DAIS_STAIR_EAST;
		for (int k = 1; k < s; k++) {
			for (int z = pz - 3 - k; z <= pz + 4 + k; z++) {
				pieces.add(new Piece(px + f * (2 + k), s - k - 1, z, climbs, (byte) 0));
			}
		}
		for (int sx : new int[] {-2, 2}) {
			for (int z : new int[] {pz - 3, pz + 4}) {
				pillar(pieces, px + sx, z, s, s + 6);
			}
		}
		for (int z : new int[] {pz - 2, pz + 3}) {
			pillar(pieces, px, z, s, s + 6);
		}
		for (int z = pz - 1; z <= pz + 2; z++) {
			pieces.add(new Piece(px, s + 6, z, TowerVolume.PLINTH, (byte) 0));
		}
		for (int z : new int[] {pz - 1, pz + 2}) {
			pieces.add(new Piece(px, s + 5, z, TowerVolume.BIOME_BLOCK, BiomeBlocks.CHAIN));
			pieces.add(new Piece(px, s + 4, z, TowerVolume.BIOME_BLOCK, BiomeBlocks.HANGING_LANTERN));
		}
		for (int z : new int[] {pz - 2, pz + 3}) {
			pieces.add(new Piece(px + f * 2, s, z, TowerVolume.BIOME_BLOCK, BiomeBlocks.SOUL_CAMPFIRE));
		}
		for (int z : new int[] {pz - 3 - s, pz + 4 + s}) {
			pieces.add(new Piece(px + f * (2 + s), 0, z, TowerVolume.BIOME_BLOCK, BiomeBlocks.FENCE));
			pieces.add(new Piece(px + f * (2 + s), 1, z, TowerVolume.BIOME_BLOCK, BiomeBlocks.SKULL));
		}
		return pieces;
	}

	private static void pillar(final List<Piece> pieces, final int x, final int z, final int from, final int to) {
		for (int y = from; y <= to; y++) {
			pieces.add(new Piece(x, y, z, TowerVolume.PLINTH, (byte) 0));
		}
		pieces.add(new Piece(x, to + 1, z, TowerVolume.LAMP, (byte) 0));
	}

	/** The dais as absolute positions, each column filled from the ground's top block up. */
	List<BlockPos> plinth(final TowerPlan plan) {
		BlockPos origin = plan.origin();
		List<BlockPos> blocks = new ArrayList<>();
		for (int[] column : daisColumns(plan)) {
			for (int y = -1; y <= column[2]; y++) {
				blocks.add(new BlockPos(origin.getX() + column[0], TowerLayout.BASE_Y + y, origin.getZ() + column[1]));
			}
		}
		return blocks;
	}
}
