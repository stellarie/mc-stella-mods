package com.stella.ruins.site.ruinedkeep;

import com.stella.ruins.world.RuinFrame;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A walk over drawn blocks. A node is a column and the layer of the block under the climber's feet. A step goes to a
 * neighbour column at the same layer, one layer up (a jump or a stair), or down by up to a drop of 8. Head room is two
 * clear cells. The ground is flat, one layer under the frame's floor, wherever the keep drew nothing.
 */
final class KeepWalker {
	static final int MAX_DROP = 8;
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	private final Map<Long, BlockState> blocks = new HashMap<>();
	private final RuinFrame frame;
	private final int groundY;
	private final Set<Long> seen = new HashSet<>();

	KeepWalker(final Map<BlockPos, BlockState> drawn, final RuinFrame frame) {
		drawn.forEach((pos, state) -> this.blocks.put(pos.asLong(), state));
		this.frame = frame;
		this.groundY = frame.min().getY() - 1;
	}

	/** Replaces a block, as the objective does when it opens the throne door. */
	void set(final BlockPos pos, final BlockState state) {
		this.blocks.put(pos.asLong(), state);
	}

	BlockState at(final int x, final int y, final int z) {
		BlockState state = this.blocks.get(BlockPos.asLong(x, y, z));
		if (state != null) {
			return state;
		}
		return y <= this.groundY ? Blocks.STONE.defaultBlockState() : AIR;
	}

	private static boolean passable(final BlockState state) {
		return state.isAir() || state.is(Blocks.LADDER) || state.is(Blocks.CARPET.red());
	}

	private boolean supports(final int x, final int y, final int z) {
		BlockState state = at(x, y, z);
		return !passable(state) && !state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
	}

	private boolean standable(final int x, final int y, final int z) {
		return supports(x, y, z) && passable(at(x, y + 1, z)) && passable(at(x, y + 2, z));
	}

	/** Walks from the ground at a local spot outside the gate, over the whole reachable area. */
	void walkFromGate() {
		this.seen.clear();
		BlockPos start = this.frame.toWorld(24, 0, 47);
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		queue.add(new int[] {start.getX(), this.groundY, start.getZ()});
		this.seen.add(key(start.getX(), this.groundY, start.getZ()));
		BoundingBox box = new BoundingBox(this.frame.box());
		int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!queue.isEmpty()) {
			int[] node = queue.poll();
			for (int[] d : around) {
				int nx = node[0] + d[0];
				int nz = node[2] + d[1];
				if (!box.contains(nx, nz)) {
					continue;
				}
				for (int y = node[1] + 1; y >= node[1] - MAX_DROP; y--) {
					if (standable(nx, y, nz)) {
						long k = key(nx, y, nz);
						if (this.seen.add(k)) {
							queue.add(new int[] {nx, y, nz});
						}
						break;
					}
				}
			}
		}
	}

	/** True when the walk stood on the block layer {@code y} at a local column. {@code y} is local. */
	boolean reached(final int localX, final int localY, final int localZ) {
		BlockPos pos = this.frame.toWorld(localX, localY, localZ);
		return this.seen.contains(key(pos.getX(), pos.getY(), pos.getZ()));
	}

	int count() {
		return this.seen.size();
	}

	private static long key(final int x, final int y, final int z) {
		return BlockPos.asLong(x, y, z);
	}

	/** A flat box, to keep a walk inside the frame's footprint with a margin for the forecourt. */
	private record BoundingBox(int minX, int maxX, int minZ, int maxZ) {
		BoundingBox(final net.minecraft.world.level.levelgen.structure.BoundingBox box) {
			this(box.minX(), box.maxX(), box.minZ(), box.maxZ());
		}

		boolean contains(final int x, final int z) {
			return x >= this.minX && x <= this.maxX && z >= this.minZ && z <= this.maxZ;
		}
	}
}
