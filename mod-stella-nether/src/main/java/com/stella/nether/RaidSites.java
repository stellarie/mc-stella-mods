package com.stella.nether;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/**
 * Picks where a run opens: midway between a fortress and a bastion that lie close together, so both are
 * within {@link RaidInstance#STRUCTURE_REACH} of the arrival. The raid nether's own structures are used, so
 * nothing is forced into the land. Each placement cell near the origin is read for its actual structure
 * start; the locate search is not used, because it answers with the first ring that holds a match rather
 * than the nearest one.
 */
public final class RaidSites {
	/** How far apart the pair may lie: midway, each is then within reach, with a margin for rounding. */
	static final int MAX_PAIR_GAP = 2 * RaidInstance.STRUCTURE_REACH - 32;
	/** Placement cells searched on each side of the origin's cell. */
	static final int CELL_RADIUS = 3;
	/** The nether complexes' cell, 27 chunks, as vanilla places fortresses and bastions. */
	static final int CELL_BLOCKS = 27 * 16;
	/** The farthest a site can lie from its origin: the far edge of the farthest cell searched. */
	public static final int MAX_SHIFT = (CELL_RADIUS + 1) * CELL_BLOCKS * 3 / 2;

	private record Found(boolean fortress, BlockPos at) {
	}

	/** Where a run opens, and the fortress start its Sovereign rises from. */
	public record Site(BlockPos arrival, BlockPos fortress) {
	}

	private RaidSites() {
	}

	static Site site(final ServerLevel raid, final BlockPos origin) {
		long began = System.nanoTime();
		Holder<Structure> fortress = structure(raid, BuiltinStructures.FORTRESS);
		Holder<Structure> bastion = structure(raid, BuiltinStructures.BASTION_REMNANT);
		ChunkGeneratorStructureState state = raid.getChunkSource().getGeneratorState();
		RandomSpreadStructurePlacement placement = null;
		for (StructurePlacement candidate : state.getPlacementsForStructure(fortress)) {
			if (candidate instanceof RandomSpreadStructurePlacement spread) {
				placement = spread;
			}
		}
		if (placement == null) {
			StellaNether.LOGGER.warn("[NETHER] the raid nether places no fortress; the run opens at its origin {}", origin);
			return new Site(origin, origin);
		}
		int spacing = placement.spacing();
		int cellX = Math.floorDiv(origin.getX() >> 4, spacing);
		int cellZ = Math.floorDiv(origin.getZ() >> 4, spacing);
		List<Found> found = new ArrayList<>();
		for (int dx = -CELL_RADIUS; dx <= CELL_RADIUS; dx++) {
			for (int dz = -CELL_RADIUS; dz <= CELL_RADIUS; dz++) {
				ChunkPos start = placement.getPotentialStructureChunk(state.getLevelSeed(), (cellX + dx) * spacing, (cellZ + dz) * spacing);
				ChunkAccess chunk = raid.getChunk(start.x(), start.z(), ChunkStatus.STRUCTURE_STARTS);
				BlockPos at = new BlockPos(start.getMiddleBlockX(), 0, start.getMiddleBlockZ());
				if (valid(chunk.getStartForStructure(fortress.value()))) {
					found.add(new Found(true, at));
				} else if (valid(chunk.getStartForStructure(bastion.value()))) {
					found.add(new Found(false, at));
				}
			}
		}
		Site best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Found f : found) {
			for (Found b : found) {
				if (!f.fortress() || b.fortress() || gap(f.at(), b.at()) > MAX_PAIR_GAP) {
					continue;
				}
				BlockPos mid = new BlockPos((f.at().getX() + b.at().getX()) / 2, RaidInstance.ARRIVAL_Y, (f.at().getZ() + b.at().getZ()) / 2);
				double distance = gap(mid, origin);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = new Site(mid, f.at());
				}
			}
		}
		if (best == null) {
			StellaNether.LOGGER.warn("[NETHER] no fortress and bastion pair near {}; the run opens at its origin", origin);
			return new Site(origin, origin);
		}
		StellaNether.LOGGER.info("[NETHER] raid site={} from origin={} structures={} ms={}", best.arrival(), origin, found.size(), (System.nanoTime() - began) / 1_000_000L);
		return best;
	}

	private static boolean valid(final StructureStart start) {
		return start != null && start.isValid();
	}

	private static Holder<Structure> structure(final ServerLevel raid, final ResourceKey<Structure> key) {
		return raid.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(key);
	}

	static double gap(final BlockPos a, final BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}
}
