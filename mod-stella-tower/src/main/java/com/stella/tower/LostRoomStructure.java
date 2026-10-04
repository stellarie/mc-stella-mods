package com.stella.tower;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/** A lost castle: one piece, on dry ground that is near level, its floor one block above the highest ground. */
public class LostRoomStructure extends Structure {
	public static final MapCodec<LostRoomStructure> CODEC = simpleCodec(LostRoomStructure::new);
	/** The most the ground may fall away under the castle's floor. */
	public static final int MAX_SLOPE = 6;
	/** How far apart the ground is read across the footprint; the edges are always read. */
	public static final int SAMPLE_STEP = 3;

	public LostRoomStructure(final Structure.StructureSettings settings) {
		super(settings);
	}

	@Override
	public Optional<Structure.GenerationStub> findGenerationPoint(final Structure.GenerationContext context) {
		Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(context.random());
		boolean alongX = facing.getAxis() == Direction.Axis.Z;
		int sizeX = alongX ? LostRoomPiece.LENGTH : LostRoomPiece.WIDTH;
		int sizeZ = alongX ? LostRoomPiece.WIDTH : LostRoomPiece.LENGTH;
		int west = context.chunkPos().getMiddleBlockX() - sizeX / 2;
		int north = context.chunkPos().getMiddleBlockZ() - sizeZ / 2;
		int[] low = {Integer.MAX_VALUE};
		boolean[] wet = {false};
		int floor = LostRoomPiece.floorOver(west, north, west + sizeX - 1, north + sizeZ - 1, SAMPLE_STEP, (x, z) -> {
			int surface = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
			int ground = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
			wet[0] |= surface != ground;
			low[0] = Math.min(low[0], ground);
			return ground;
		});
		// No castle on water or a steep slope.
		if (wet[0] || floor - 1 - low[0] > MAX_SLOPE) {
			return Optional.empty();
		}
		BlockPos at = new BlockPos(west, floor, north);
		return Optional.of(new Structure.GenerationStub(at, builder -> builder.addPiece(new LostRoomPiece(west, floor, north, facing))));
	}

	@Override
	public StructureType<?> type() {
		return LostRooms.STRUCTURE_TYPE;
	}
}
