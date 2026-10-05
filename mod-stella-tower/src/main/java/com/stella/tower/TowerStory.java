package com.stella.tower;

import org.jspecify.annotations.Nullable;

/**
 * One story of a floor: a walking level of the room, by offsets from its inner corner, its {@code y} counted up
 * from {@link TowerLayout#BASE_Y}. A floor holds one to three, the ground story first.
 * <p>
 * The story's band runs from its slab, one below {@code y}, up to {@code top}, which it does not include: the next
 * story's slab, a lid, or the roof. A {@code floor} cell has a slab under it (the ground story's every cell stands
 * on the ground). A {@code wall} cell is solid from {@code y} up, {@code wallTop} blocks high or to the band's top;
 * a {@code pillar} cell is a wall that always reaches the top, in the theme's pillar skin. A {@code cover} cell holds
 * a block one high on the story's floor, a pew or a low wall ({@link #COVER}), or a lamp set in one ({@link #COVER_LAMP}).
 * A cell with neither floor nor wall is open air down to the story below.
 * <p>
 * {@code stand} and {@code flat} are read from the floor as drawn, before its chests: where a body stands on the
 * story, and where it stands on the story's own floor (a trap's or a chest's ground), not on a stair. Both are null
 * until the plan is finished. The arrays are shared by every reader: never write to them.
 */
public record TowerStory(
	int y,
	int top,
	int wallTop,
	boolean[][] floor,
	boolean[][] wall,
	boolean @Nullable [][] pillar,
	byte @Nullable [][] cover,
	boolean @Nullable [][] stand,
	boolean @Nullable [][] flat
) {
	public static final byte COVER = 1;
	public static final byte COVER_LAMP = 2;

	/** A story whose walls fill its band, with no pillars, not yet read. */
	static TowerStory of(final int y, final int top, final boolean[][] floor, final boolean[][] wall) {
		return new TowerStory(y, top, top - y, floor, wall, null, null, null, null);
	}

	TowerStory withStands(final boolean[][] stand, final boolean[][] flat) {
		return new TowerStory(this.y, this.top, this.wallTop, this.floor, this.wall, this.pillar, this.cover, stand, flat);
	}

	/** Whether a body stands on this story at the spot, as drawn; false outside the room. */
	public boolean stands(final int x, final int z) {
		return this.stand != null && x >= 0 && z >= 0 && x < TowerLayout.SIZE && z < TowerLayout.SIZE && this.stand[x][z];
	}

	/** Whether the spot is this story's own floor to stand on: a trap's or a chest's ground. */
	public boolean flatAt(final int x, final int z) {
		return this.flat != null && x >= 0 && z >= 0 && x < TowerLayout.SIZE && z < TowerLayout.SIZE && this.flat[x][z];
	}

	public byte coverAt(final int x, final int z) {
		return this.cover == null ? 0 : this.cover[x][z];
	}

	public boolean isPillar(final int x, final int z) {
		return this.pillar != null && this.pillar[x][z];
	}

	/** A grid of the room, every cell set. */
	static boolean[][] full() {
		boolean[][] grid = new boolean[TowerLayout.SIZE][TowerLayout.SIZE];
		for (boolean[] row : grid) {
			java.util.Arrays.fill(row, true);
		}
		return grid;
	}
}
