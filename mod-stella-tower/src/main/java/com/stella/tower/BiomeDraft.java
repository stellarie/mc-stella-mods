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
 * @param placed blocks above the surface, as runs of x, y, z and a palette entry
 * @param stand where a body stands in each column at the lowest, or {@link #NONE}; null until the plan is read
 * @param steps how many steps the portal's platform stands above the ground
 */
public record BiomeDraft(FloorBiome biome, short[][] feet, byte[][] surface, boolean[][] water, int[] placed, short @Nullable [][] stand, int steps) {
	/** A column where no body stands. */
	public static final short NONE = -99;
	/** How far above the feet the portal's backdrop wall rises, in blocks. */
	public static final int BACKDROP = 8;

	BiomeDraft withStand(final short[][] read) {
		return new BiomeDraft(this.biome, this.feet, this.surface, this.water, this.placed, read, this.steps);
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
