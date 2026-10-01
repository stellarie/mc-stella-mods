package com.stella.mobs.entity.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Finds a light-emitting block near a spawn spot. The distance is the straight line between block centers.
 * A section without any emitting block is skipped whole, so most spots cost a few palette checks.
 */
public final class LightSources {
	/** The slime rules keep this many blocks away from any light source. */
	public static final int RADIUS = 7;

	private LightSources() {
	}

	/** True when a block with a light emission above 0 lies within the radius of the spot. Unloaded chunks count as dark. */
	public static boolean near(final ServerLevel level, final BlockPos spot, final int radius) {
		int reach = radius * radius;
		for (int sectionX = SectionPos.blockToSectionCoord(spot.getX() - radius); sectionX <= SectionPos.blockToSectionCoord(spot.getX() + radius); sectionX++) {
			for (int sectionZ = SectionPos.blockToSectionCoord(spot.getZ() - radius); sectionZ <= SectionPos.blockToSectionCoord(spot.getZ() + radius); sectionZ++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(sectionX, sectionZ);
				if (chunk == null) {
					continue;
				}
				for (int sectionY = SectionPos.blockToSectionCoord(spot.getY() - radius); sectionY <= SectionPos.blockToSectionCoord(spot.getY() + radius); sectionY++) {
					int index = chunk.getSectionIndexFromSectionY(sectionY);
					if (index < 0 || index >= chunk.getSectionsCount()) {
						continue;
					}
					LevelChunkSection section = chunk.getSection(index);
					if (section.hasOnlyAir() || !section.maybeHas(state -> state.getLightEmission() > 0)) {
						continue;
					}
					if (emitterInSection(section, sectionX, sectionY, sectionZ, spot, reach)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static boolean emitterInSection(
		final LevelChunkSection section, final int sectionX, final int sectionY, final int sectionZ, final BlockPos spot, final int reach
	) {
		int baseX = SectionPos.sectionToBlockCoord(sectionX);
		int baseY = SectionPos.sectionToBlockCoord(sectionY);
		int baseZ = SectionPos.sectionToBlockCoord(sectionZ);
		for (int dy = 0; dy < 16; dy++) {
			int y = baseY + dy - spot.getY();
			for (int dz = 0; dz < 16; dz++) {
				int z = baseZ + dz - spot.getZ();
				for (int dx = 0; dx < 16; dx++) {
					int x = baseX + dx - spot.getX();
					if (x * x + y * y + z * z > reach) {
						continue;
					}
					BlockState state = section.getBlockState(dx, dy, dz);
					if (state.getLightEmission() > 0) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
