package com.stella.end;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A castle being built: the level, the keep corner, and placement in local keep cells. Local cells may lie outside the keep. */
final class CastleSite {
	/** Bulk placement skips neighbour shape updates: the plan already holds every shape it wants. */
	static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	final ServerLevel level;
	final BlockPos min;
	final CastleLayout layout;
	private final List<BlockPos> shaped = new ArrayList<>();
	private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

	CastleSite(final ServerLevel level, final BlockPos min, final CastleLayout layout) {
		this.level = level;
		this.min = min;
		this.layout = layout;
	}

	BlockPos pos(final int x, final int y, final int z) {
		return new BlockPos(min.getX() + x, y, min.getZ() + z);
	}

	void put(final int x, final int y, final int z, final BlockState state) {
		level.setBlock(cursor.set(min.getX() + x, y, min.getZ() + z), state, FLAGS);
	}

	/** Places a block whose shape depends on its neighbours; {@link #relink} fixes the shape once everything stands. */
	void putShaped(final int x, final int y, final int z, final BlockState state) {
		put(x, y, z, state);
		shaped.add(pos(x, y, z));
	}

	void putIfAir(final int x, final int y, final int z, final BlockState state) {
		if (get(x, y, z).isAir()) {
			put(x, y, z, state);
		}
	}

	BlockState get(final int x, final int y, final int z) {
		return level.getBlockState(cursor.set(min.getX() + x, y, min.getZ() + z));
	}

	void fill(final int x0, final int y0, final int z0, final int x1, final int y1, final int z1, final BlockState state) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
				for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
					put(x, y, z, state);
				}
			}
		}
	}

	/** Gives every connectable block placed with {@link #putShaped} the shape its neighbours ask for. */
	void relink() {
		for (BlockPos at : shaped) {
			BlockState state = level.getBlockState(at);
			BlockState fixed = Block.updateFromNeighbourShapes(state, level, at);
			if (fixed != state) {
				level.setBlock(at, fixed, FLAGS);
			}
		}
		shaped.clear();
	}

	/** A random source fixed by the world seed and a salt, so one world always builds the same props. */
	Random random(final long salt) {
		long mixed = layout.seed() * 0x9E3779B97F4A7C15L + salt * 0xC2B2AE3D27D4EB4FL;
		mixed ^= mixed >>> 31;
		mixed *= 0xBF58476D1CE4E5B9L;
		mixed ^= mixed >>> 29;
		return new Random(mixed);
	}

	static Direction[] horizontals() {
		return new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
	}
}
