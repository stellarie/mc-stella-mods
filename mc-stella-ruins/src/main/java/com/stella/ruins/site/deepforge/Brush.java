package com.stella.ruins.site.deepforge;

import com.stella.ruins.world.LayoutContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws through a layout context, but only over the part of the box that the current chunk covers. The layout
 * context drops writes outside the chunk anyway, but it still walks every cell of a fill, so a forge that fills its
 * whole box on every chunk would cost the box's volume each time. The brush learns the chunk's rectangle once and
 * clamps every fill to it.
 */
final class Brush {
	private final LayoutContext ctx;
	private final int minX;
	private final int maxX;
	private final int minZ;
	private final int maxZ;
	private final List<int[]> carved = new ArrayList<>();

	private Brush(final LayoutContext ctx, final int minX, final int maxX, final int minZ, final int maxZ) {
		this.ctx = ctx;
		this.minX = minX;
		this.maxX = maxX;
		this.minZ = minZ;
		this.maxZ = maxZ;
	}

	/** Finds the chunk's rectangle by writing a bedrock-level probe into every column of the box. */
	static Brush probe(final LayoutContext ctx) {
		BlockState probe = Blocks.DEEPSLATE.defaultBlockState();
		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (int x = 0; x < Geo.SIZE_X; x++) {
			for (int z = 0; z < Geo.SIZE_Z; z++) {
				ctx.set(x, 0, z, probe);
				if (ctx.get(x, 0, z) == probe) {
					minX = Math.min(minX, x);
					maxX = Math.max(maxX, x);
					minZ = Math.min(minZ, z);
					maxZ = Math.max(maxZ, z);
				}
			}
		}
		return new Brush(ctx, minX, maxX, minZ, maxZ);
	}

	boolean empty() {
		return this.minX > this.maxX;
	}

	LayoutContext ctx() {
		return this.ctx;
	}

	boolean covers(final int x, final int z) {
		return x >= this.minX && x <= this.maxX && z >= this.minZ && z <= this.maxZ;
	}

	void set(final int x, final int y, final int z, final BlockState state) {
		if (covers(x, z) && y >= 0 && y < Geo.SIZE_Y) {
			this.ctx.set(x, y, z, state);
		}
	}

	BlockState get(final int x, final int y, final int z) {
		return covers(x, z) ? this.ctx.get(x, y, z) : Blocks.AIR.defaultBlockState();
	}

	void fill(final int x0, final int y0, final int z0, final int x1, final int y1, final int z1, final BlockState state) {
		int ax = Math.max(Math.min(x0, x1), this.minX);
		int bx = Math.min(Math.max(x0, x1), this.maxX);
		int az = Math.max(Math.min(z0, z1), this.minZ);
		int bz = Math.min(Math.max(z0, z1), this.maxZ);
		int ay = Math.max(Math.min(y0, y1), 0);
		int by = Math.min(Math.max(y0, y1), Geo.SIZE_Y - 1);
		if (ax <= bx && az <= bz && ay <= by) {
			this.ctx.fill(ax, ay, az, bx, by, bz, state);
		}
	}

	/** Picks a block per cell, for walls with their cracks. */
	@FunctionalInterface
	interface CellFn {
		BlockState at(int x, int y, int z);
	}

	void box(final int x0, final int y0, final int z0, final int x1, final int y1, final int z1, final CellFn fn) {
		int ax = Math.max(Math.min(x0, x1), this.minX);
		int bx = Math.min(Math.max(x0, x1), this.maxX);
		int az = Math.max(Math.min(z0, z1), this.minZ);
		int bz = Math.min(Math.max(z0, z1), this.maxZ);
		int ay = Math.max(Math.min(y0, y1), 0);
		int by = Math.min(Math.max(y0, y1), Geo.SIZE_Y - 1);
		for (int x = ax; x <= bx; x++) {
			for (int z = az; z <= bz; z++) {
				for (int y = ay; y <= by; y++) {
					this.ctx.set(x, y, z, fn.at(x, y, z));
				}
			}
		}
	}

	/** Clears a box to air, and remembers it so {@link #seal} leaves it open. */
	void carve(final int x0, final int y0, final int z0, final int x1, final int y1, final int z1) {
		this.carved.add(new int[] {Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1)});
		fill(x0, y0, z0, x1, y1, z1, Blocks.AIR.defaultBlockState());
	}

	/**
	 * Replaces natural air and fluid inside the chunk's part of the box, outside every carved box, so no cave opens onto
	 * the forge. Run it after the carving, so the carved rooms are written once.
	 */
	void seal(final BlockState state) {
		if (empty()) {
			return;
		}
		List<int[]> near = new ArrayList<>();
		for (int[] box : this.carved) {
			if (box[0] <= this.maxX && box[3] >= this.minX && box[2] <= this.maxZ && box[5] >= this.minZ) {
				near.add(box);
			}
		}
		for (int x = this.minX; x <= this.maxX; x++) {
			for (int z = this.minZ; z <= this.maxZ; z++) {
				for (int y = 0; y < Geo.SIZE_Y; y++) {
					if (insideAny(near, x, y, z)) {
						continue;
					}
					BlockState now = this.ctx.get(x, y, z);
					if (now.isAir() || !now.getFluidState().isEmpty()) {
						this.ctx.set(x, y, z, state);
					}
				}
			}
		}
	}

	private static boolean insideAny(final List<int[]> boxes, final int x, final int y, final int z) {
		for (int[] box : boxes) {
			if (x >= box[0] && x <= box[3] && y >= box[1] && y <= box[4] && z >= box[2] && z <= box[5]) {
				return true;
			}
		}
		return false;
	}

	/** A set of iron bar cells. Bars connect to bars beside them, so a row reads as a rail and not as posts. */
	static final class Bars {
		private final Set<Long> cells = new HashSet<>();

		private static long key(final int x, final int y, final int z) {
			return ((long) x & 0xFFFFL) | (((long) z & 0xFFFFL) << 16) | (((long) y & 0xFFFFL) << 32);
		}

		void add(final int x, final int y, final int z) {
			this.cells.add(key(x, y, z));
		}

		void addColumn(final int x, final int y0, final int y1, final int z) {
			for (int y = y0; y <= y1; y++) {
				add(x, y, z);
			}
		}

		boolean has(final int x, final int y, final int z) {
			return this.cells.contains(key(x, y, z));
		}

		int size() {
			return this.cells.size();
		}

		void remove(final int x, final int y, final int z) {
			this.cells.remove(key(x, y, z));
		}

		void flush(final Brush brush) {
			for (long cell : this.cells) {
				int x = (int) (cell & 0xFFFFL);
				int z = (int) ((cell >> 16) & 0xFFFFL);
				int y = (int) ((cell >> 32) & 0xFFFFL);
				if (!brush.covers(x, z)) {
					continue;
				}
				BlockState state = Blocks.IRON_BARS.defaultBlockState()
					.setValue(IronBarsBlock.NORTH, has(x, y, z - 1))
					.setValue(IronBarsBlock.SOUTH, has(x, y, z + 1))
					.setValue(IronBarsBlock.EAST, has(x + 1, y, z))
					.setValue(IronBarsBlock.WEST, has(x - 1, y, z));
				brush.set(x, y, z, state);
			}
		}
	}
}
