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
 * Picks where a run opens: beside a fortress that has a bastion close by, 48 blocks from the fortress start toward it.
 * The raid nether's own structures are used, so
 * nothing is forced into the land. Each placement cell near the origin is read for its actual structure
 * start; the locate search is not used, because it answers with the first ring that holds a match rather
 * than the nearest one.
 */
public final class RaidSites {
	/** How far apart the fortress and its bastion may lie. */
	static final int MAX_PAIR_GAP = 2 * RaidInstance.STRUCTURE_REACH - 32;
	/** How far from the fortress start a run opens; inside the 64 blocks the run promises. */
	static final int ARRIVAL_STANDOFF = 48;
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
				BlockPos mid = standoff(f.at(), b.at());
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
		BehaviorLog.info("[NETHER] raid site={} from origin={} structures={} ms={}", best.arrival(), origin, found.size(), (System.nanoTime() - began) / 1_000_000L);
		return best;
	}

	/** A spot a fixed standoff from the fortress start, toward the bastion, so the run opens beside the fortress and clear of its core. */
	static BlockPos standoff(final BlockPos fortress, final BlockPos bastion) {
		double gap = gap(fortress, bastion);
		double step = Math.min(ARRIVAL_STANDOFF, gap / 2.0);
		double scale = gap < 1.0E-6 ? 0.0 : step / gap;
		return new BlockPos(
			fortress.getX() + (int) Math.round((bastion.getX() - fortress.getX()) * scale), RaidInstance.ARRIVAL_Y,
			fortress.getZ() + (int) Math.round((bastion.getZ() - fortress.getZ()) * scale));
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
