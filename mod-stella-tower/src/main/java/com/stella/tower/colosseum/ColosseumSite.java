package com.stella.tower.colosseum;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Where the colosseum stands: a spot 96 to 256 blocks from the world spawn, read from the terrain noise alone, so no chunk
 * is generated to look. Dry and level ground comes first; if too little turns up in the tries, the least bad spots follow
 * and the builder levels them.
 */
final class ColosseumSite {
	private static final long SALT = 0x434F4C4F53534D31L;
	private static final int TRIES = 64;
	private static final int FALLBACK_TRIES = 12;
	/** The spot is drawn inside the band, away from its ends, so rounding never leaves it. */
	private static final double NEAREST = Colosseum.MIN_FROM_SPAWN + 16.0;
	private static final double FARTHEST = Colosseum.MAX_FROM_SPAWN - 24.0;
	private static final int STEP = 16;
	/** A site this level and dry is taken without looking further. */
	private static final int LEVEL_ENOUGH = 5;
	private static final int WET_WEIGHT = 3;

	private ColosseumSite() {
	}

	/** How many spots are offered to the builder. */
	static final int KEPT = 6;
	/** The search ends once it has this many level dry spots. */
	private static final int ENOUGH = 3;
	/** A spot whose five spread samples differ by more than this is not worth the full reading. */
	private static final int QUICK_SLOPE = 12;
	private static final int QUICK_REACH = 24;

	/**
	 * The best spots found, flattest and driest first, each the centre column at the floor layer. The search ends once it has
	 * found {@value #ENOUGH} level dry spots.
	 */
	static List<BlockPos> candidates(final ServerLevel level) {
		List<BlockPos> found = search(level, QUICK_SLOPE, TRIES);
		// Steep all round the spawn: take the least bad of a few spots, unscreened, and let the builder level it.
		return found.isEmpty() ? search(level, Integer.MAX_VALUE, FALLBACK_TRIES) : found;
	}

	private static List<BlockPos> search(final ServerLevel level, final int quickSlope, final int tries) {
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState randomState = level.getChunkSource().randomState();
		BlockPos spawn = level.getRespawnData().pos();
		RandomSource random = RandomSource.create(level.getSeed() ^ SALT);
		List<BlockPos> levelSpots = new ArrayList<>();
		List<int[]> scored = new ArrayList<>();
		List<BlockPos> spots = new ArrayList<>();
		for (int attempt = 0; attempt < tries; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = NEAREST + random.nextDouble() * (FARTHEST - NEAREST);
			int x = spawn.getX() + (int) Math.round(Math.cos(angle) * distance);
			int z = spawn.getZ() + (int) Math.round(Math.sin(angle) * distance);
			if (!quickLevel(generator, randomState, level, x, z, quickSlope)) {
				continue;
			}
			List<Integer> dry = new ArrayList<>();
			int wet = 0;
			for (int dx = -Colosseum.RADIUS; dx <= Colosseum.RADIUS; dx += STEP) {
				for (int dz = -Colosseum.RADIUS; dz <= Colosseum.RADIUS; dz += STEP) {
					if (dx * dx + dz * dz > Colosseum.RADIUS * Colosseum.RADIUS) {
						continue;
					}
					int ground = generator.getFirstOccupiedHeight(x + dx, z + dz, Heightmap.Types.OCEAN_FLOOR_WG, level, randomState) - 1;
					int top = generator.getFirstOccupiedHeight(x + dx, z + dz, Heightmap.Types.WORLD_SURFACE_WG, level, randomState) - 1;
					if (top != ground || ground < level.getSeaLevel()) {
						wet++;
					} else {
						dry.add(ground);
					}
				}
			}
			int slope = 0;
			int floor = level.getSeaLevel();
			if (!dry.isEmpty()) {
				Collections.sort(dry);
				slope = dry.get(dry.size() - 1) - dry.get(0);
				floor = dry.get(dry.size() / 2);
			}
			BlockPos spot = new BlockPos(x, Math.max(floor, level.getSeaLevel()), z);
			if (wet == 0 && slope <= LEVEL_ENOUGH) {
				levelSpots.add(spot);
				if (levelSpots.size() >= ENOUGH) {
					break;
				}
			} else {
				scored.add(new int[] {slope + wet * WET_WEIGHT, spots.size()});
				spots.add(spot);
			}
		}
		List<BlockPos> ranked = new ArrayList<>(levelSpots);

		scored.sort(java.util.Comparator.comparingInt(entry -> entry[0]));
		for (int[] entry : scored) {
			if (ranked.size() >= KEPT) {
				break;
			}
			ranked.add(spots.get(entry[1]));
		}
		return ranked;
	}

	/** Five readings, the middle and four at the compass points, one block of ground each: a steep spot is dropped cheaply. */
	private static boolean quickLevel(final ChunkGenerator generator, final RandomState randomState, final ServerLevel level, final int x, final int z, final int quickSlope) {
		int low = Integer.MAX_VALUE;
		int high = Integer.MIN_VALUE;
		int[][] points = {{0, 0}, {QUICK_REACH, 0}, {-QUICK_REACH, 0}, {0, QUICK_REACH}, {0, -QUICK_REACH}};
		for (int[] point : points) {
			int ground = generator.getFirstOccupiedHeight(x + point[0], z + point[1], Heightmap.Types.OCEAN_FLOOR_WG, level, randomState) - 1;
			low = Math.min(low, ground);
			high = Math.max(high, ground);
		}
		return high - low <= quickSlope;
	}
}
