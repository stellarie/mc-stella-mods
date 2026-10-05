package com.stella.tower;

import net.minecraft.core.Direction;

/**
 * A straight way from one story up to the next: stairs, one block up for each block along, or a ramp, one up for
 * every two (a stair, then a level step). The way is {@code width} blocks wide, and climbs {@code rise} blocks along
 * {@code direction} from the story whose {@code y} is {@code base}.
 * <p>
 * Its first step's cell, on its right-hand edge looking up the way, is ({@code x}, {@code z}); the way spreads to
 * the left, toward the direction's clockwise side. Step {@code i}, from 0, is walked at {@link #surface(int)}. The
 * cell before step 0 is its entry, on the lower story; the cell after the last is its exit, on the upper story.
 */
public record TowerFlight(int x, int z, Direction direction, int width, int base, int rise, int run) {
	/** The fewest blocks a way across is wide. */
	public static final int MIN_WIDTH = 2;
	public static final int STAIRS = 1;
	public static final int RAMP = 2;

	/** The steps along the way. */
	public int length() {
		return this.rise * this.run;
	}

	/** The height a body stands at on step {@code i}, as an offset from {@link TowerLayout#BASE_Y}. */
	public int surface(final int i) {
		return this.base + 1 + i / this.run;
	}

	/** Whether step {@code i} is a stair block, not a level step. */
	public boolean stairAt(final int i) {
		return this.run == STAIRS || i % this.run == 0;
	}

	/** The cell of step {@code i} (-1 for the entry, {@link #length()} for the exit), {@code across} blocks to the side. */
	public int cellX(final int i, final int across) {
		return this.x + this.direction.getStepX() * i + this.direction.getClockWise().getStepX() * across;
	}

	public int cellZ(final int i, final int across) {
		return this.z + this.direction.getStepZ() * i + this.direction.getClockWise().getStepZ() * across;
	}

	/** The upper story's {@code y}: where the exit is walked. */
	public int top() {
		return this.base + this.rise;
	}

	/**
	 * The same way, mirrored across the room's middle along x, along z, or both. A mirror turns left into right, so
	 * the first step's right-hand cell becomes the one at the far edge.
	 */
	TowerFlight mirrored(final boolean alongX, final boolean alongZ) {
		if (!alongX && !alongZ) {
			return this;
		}
		Direction direction = this.direction;
		if (alongX && direction.getAxis() == Direction.Axis.X || alongZ && direction.getAxis() == Direction.Axis.Z) {
			direction = direction.getOpposite();
		}
		// One mirror swaps the sides; two keep them.
		int edge = alongX ^ alongZ ? this.width - 1 : 0;
		int fromX = cellX(0, edge);
		int fromZ = cellZ(0, edge);
		return new TowerFlight(alongX ? TowerLayout.SIZE - 1 - fromX : fromX, alongZ ? TowerLayout.SIZE - 1 - fromZ : fromZ,
			direction, this.width, this.base, this.rise, this.run);
	}
}
