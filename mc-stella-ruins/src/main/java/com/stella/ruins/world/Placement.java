package com.stella.ruins.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/** Where on a column of ground a site may stand. The rules read the ground through a {@link Probe}, so tests can stub it. */
public sealed interface Placement permits Placement.Surface, Placement.Cave {
	/** How far apart the footprint is read; its edges are always read. */
	int SAMPLE_STEP = 4;
	/** The tries a cave site makes before it gives up. */
	int CAVE_TRIES = 8;

	/** The ground as a placement rule sees it. */
	interface Probe {
		/** The top solid block of a column, water and lava excluded. */
		int groundY(int x, int z);

		/** The top block of a column that is not air: water counts. */
		int surfaceY(int x, int z);

		BlockState stateAt(int x, int y, int z);

		int minY();

		int maxY();

		static Probe of(final ChunkGenerator generator, final LevelHeightAccessor accessor, final RandomState randomState) {
			return new Probe() {
				@Override
				public int groundY(final int x, final int z) {
					return generator.getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, accessor, randomState) - 1;
				}

				@Override
				public int surfaceY(final int x, final int z) {
					return generator.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, accessor, randomState) - 1;
				}

				@Override
				public BlockState stateAt(final int x, final int y, final int z) {
					NoiseColumn column = generator.getBaseColumn(x, z, accessor, randomState);
					return column.getBlock(y);
				}

				@Override
				public int minY() {
					return accessor.getMinY();
				}

				@Override
				public int maxY() {
					return accessor.getMaxY();
				}
			};
		}
	}

	static Placement surface(final int maxSlope) {
		return new Surface(maxSlope);
	}

	static Placement cave(final int minY, final int maxY, final int minCover) {
		return new Cave(minY, maxY, minCover);
	}

	/**
	 * Picks the lowest corner of a site's world box, or nothing when the ground refuses it.
	 *
	 * @param west the world x of the footprint's low corner
	 * @param north the world z of the footprint's low corner
	 */
	Optional<BlockPos> choose(Probe probe, RandomSource random, int west, int north, int worldSizeX, int sizeY, int worldSizeZ);

	/** How many blocks below its floor a site may draw: the foundation a slope needs. */
	int foundation();

	/** A site on the surface: dry ground, near level. Its floor is one block above the highest ground it covers. */
	record Surface(int maxSlope) implements Placement {
		public Surface {
			if (maxSlope < 0) {
				throw new IllegalArgumentException("maxSlope is negative");
			}
		}

		@Override
		public Optional<BlockPos> choose(final Probe probe, final RandomSource random, final int west, final int north, final int worldSizeX, final int sizeY, final int worldSizeZ) {
			int highest = Integer.MIN_VALUE;
			int lowest = Integer.MAX_VALUE;
			for (int[] column : columns(west, north, worldSizeX, worldSizeZ)) {
				int ground = probe.groundY(column[0], column[1]);
				if (probe.surfaceY(column[0], column[1]) != ground) {
					return Optional.empty();
				}
				highest = Math.max(highest, ground);
				lowest = Math.min(lowest, ground);
			}
			int floor = highest + 1;
			if (highest - lowest > this.maxSlope || floor + sizeY > probe.maxY()) {
				return Optional.empty();
			}
			return Optional.of(new BlockPos(west, floor, north));
		}

		@Override
		public int foundation() {
			return this.maxSlope;
		}
	}

	/**
	 * A site inside the rock, with at least {@code minCover} blocks of ground between its roof and the surface, and
	 * no fluid at its corners, its centre, its floor, its middle or its roof.
	 */
	record Cave(int minY, int maxY, int minCover) implements Placement {
		public Cave {
			if (maxY < minY || minCover < 0) {
				throw new IllegalArgumentException("a cave placement needs minY <= maxY and a cover of at least 0");
			}
		}

		@Override
		public Optional<BlockPos> choose(final Probe probe, final RandomSource random, final int west, final int north, final int worldSizeX, final int sizeY, final int worldSizeZ) {
			int lowestFloor = Math.max(this.minY, probe.minY());
			int highestFloor = Math.min(this.maxY - sizeY + 1, probe.maxY() - sizeY);
			if (highestFloor < lowestFloor) {
				return Optional.empty();
			}
			int[][] columns = columns(west, north, worldSizeX, worldSizeZ);
			int[] surface = new int[columns.length];
			for (int i = 0; i < columns.length; i++) {
				surface[i] = probe.surfaceY(columns[i][0], columns[i][1]);
			}
			for (int attempt = 0; attempt < CAVE_TRIES; attempt++) {
				int floor = lowestFloor + random.nextInt(highestFloor - lowestFloor + 1);
				if (covered(surface, floor + sizeY - 1) && dry(probe, columns, floor, sizeY)) {
					return Optional.of(new BlockPos(west, floor, north));
				}
			}
			return Optional.empty();
		}

		private boolean covered(final int[] surface, final int roof) {
			for (int top : surface) {
				if (top - roof < this.minCover) {
					return false;
				}
			}
			return true;
		}

		private static boolean dry(final Probe probe, final int[][] columns, final int floor, final int sizeY) {
			int[] heights = {floor, floor + sizeY / 2, floor + sizeY - 1};
			for (int[] column : columns) {
				for (int y : heights) {
					if (!probe.stateAt(column[0], y, column[1]).getFluidState().isEmpty()) {
						return false;
					}
				}
			}
			return true;
		}

		@Override
		public int foundation() {
			return 0;
		}
	}

	/** The columns a rule reads: every {@link #SAMPLE_STEP} blocks, and the far edge always. */
	private static int[][] columns(final int west, final int north, final int worldSizeX, final int worldSizeZ) {
		int east = west + worldSizeX - 1;
		int south = north + worldSizeZ - 1;
		List<int[]> list = new ArrayList<>();
		for (int x = west; ; x = Math.min(east, x + SAMPLE_STEP)) {
			for (int z = north; ; z = Math.min(south, z + SAMPLE_STEP)) {
				list.add(new int[] {x, z});
				if (z == south) {
					break;
				}
			}
			if (x == east) {
				break;
			}
		}
		return list.toArray(new int[0][]);
	}
}
