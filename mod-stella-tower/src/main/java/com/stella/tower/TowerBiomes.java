package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Paints a biome floor's chunks with its data biome, which carries the floor's fog and air. The client reads the
 * biome at the camera, so painting the chunks on the server is all a floor needs; a boss floor keeps its mist
 * ({@link TowerBuffs#mist}).
 */
public final class TowerBiomes {
	/** The columns a floor is checked at, as offsets from its inner corner: the four near corners and the middle. */
	static final int[][] SAMPLES = {{2, 2}, {61, 2}, {2, 61}, {61, 61}, {32, 32}};

	private TowerBiomes() {
	}

	static void paintChunk(final ServerLevel tower, final int cx, final int cz, final FloorBiome biome) {
		Holder<Biome> holder = tower.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(biome.key());
		ChunkAccess chunk = tower.getChunk(cx, cz);
		chunk.fillBiomesFromNoise((x, y, z) -> holder);
		chunk.markUnsaved();
		tower.getChunkSource().chunkMap.resendBiomesForChunks(List.of(chunk));
	}

	/** Paints every chunk of the floor's room and walls. */
	public static void paint(final ServerLevel tower, final int floor, final FloorBiome biome) {
		BlockPos origin = TowerLayout.origin(floor);
		Holder<Biome> holder = tower.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(biome.key());
		List<ChunkAccess> chunks = new ArrayList<>();
		for (int cx = (origin.getX() - 1) >> 4; cx <= (origin.getX() + TowerLayout.SIZE) >> 4; cx++) {
			for (int cz = (origin.getZ() - 1) >> 4; cz <= (origin.getZ() + TowerLayout.SIZE) >> 4; cz++) {
				ChunkAccess chunk = tower.getChunk(cx, cz);
				chunk.fillBiomesFromNoise((x, y, z) -> holder);
				chunk.markUnsaved();
				chunks.add(chunk);
			}
		}
		tower.getChunkSource().chunkMap.resendBiomesForChunks(chunks);
	}

	/** Whether the floor's sample columns hold its biome. */
	public static boolean painted(final ServerLevel tower, final int floor, final FloorBiome biome) {
		BlockPos origin = TowerLayout.origin(floor);
		for (int[] at : SAMPLES) {
			if (!tower.getBiome(origin.offset(at[0], 2, at[1])).is(biome.key())) {
				return false;
			}
		}
		return true;
	}

	/** Paints a floor a player stands on that was built without its biome, or lost it. */
	static void repair(final ServerLevel tower, final int floor) {
		TowerPlan plan = TowerPlan.atQuiet(tower, floor);
		if (plan.isBiome() && !TowerBuffs.isBossFloor(floor) && !painted(tower, floor, plan.biome().biome())) {
			paint(tower, floor, plan.biome().biome());
		}
	}
}
