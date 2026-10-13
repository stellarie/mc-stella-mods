package com.stella.spells;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * The arena is 8 blocks wide, long and high, and a ring of barrier blocks closes it on every side. A case that
 * sends an arrow into the sky or aims a look ray far past the arena opens the ceiling for its length and puts
 * the barrier back after, so nothing else changes in the cell.
 */
final class ArenaCeiling {
	/** The ceiling is the layer just above the 8 blocks of arena, and the ring runs one block past each side. */
	private static final int CEILING = 8;
	private static final int FROM = -1;
	private static final int TO = 8;

	private ArenaCeiling() {
	}

	/** Opens every barrier of the ceiling; the result is what {@link #close} puts back. */
	static List<BlockPos> open(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<BlockPos> opened = new ArrayList<>();
		for (int x = FROM; x <= TO; x++) {
			for (int z = FROM; z <= TO; z++) {
				BlockPos at = helper.absolutePos(new BlockPos(x, CEILING, z));
				if (level.getBlockState(at).is(Blocks.BARRIER)) {
					level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
					opened.add(at);
				}
			}
		}
		return opened;
	}

	static void close(final GameTestHelper helper, final List<BlockPos> opened) {
		opened.forEach(at -> helper.getLevel().setBlock(at, Blocks.BARRIER.defaultBlockState(), 3));
	}
}
