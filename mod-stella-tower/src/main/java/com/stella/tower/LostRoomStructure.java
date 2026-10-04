package com.stella.tower;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/** A lost room's hut: one piece, on dry ground that is near level, at a chunk's middle. */
public class LostRoomStructure extends Structure {
	public static final MapCodec<LostRoomStructure> CODEC = simpleCodec(LostRoomStructure::new);
	/** The most the ground may rise across the hut's corners. */
	public static final int MAX_SLOPE = 2;

	public LostRoomStructure(final Structure.StructureSettings settings) {
		super(settings);
	}

	@Override
	public Optional<Structure.GenerationStub> findGenerationPoint(final Structure.GenerationContext context) {
		int x = context.chunkPos().getMiddleBlockX();
		int z = context.chunkPos().getMiddleBlockZ();
		int low = Integer.MAX_VALUE;
		int high = Integer.MIN_VALUE;
		for (int dx = 0; dx < LostRoomPiece.LENGTH; dx += LostRoomPiece.LENGTH - 1) {
			for (int dz = 0; dz < LostRoomPiece.LENGTH; dz += LostRoomPiece.LENGTH - 1) {
				int surface = context.chunkGenerator().getFirstOccupiedHeight(x + dx, z + dz, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
				int ground = context.chunkGenerator().getFirstOccupiedHeight(x + dx, z + dz, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
				// Water over the ground: no hut.
				if (surface != ground) {
					return Optional.empty();
				}
				low = Math.min(low, ground);
				high = Math.max(high, ground);
			}
		}
		if (high - low > MAX_SLOPE) {
			return Optional.empty();
		}
		return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG,
			builder -> builder.addPiece(new LostRoomPiece(context.random(), x, z)));
	}

	@Override
	public StructureType<?> type() {
		return LostRooms.STRUCTURE_TYPE;
	}
}
