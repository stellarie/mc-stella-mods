package com.stella.tower;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * A lost castle: one piece, on dry ground that is near level. Its floors go down from the ground, so the
 * start asks for depth under the lowest ground of its footprint, and its palette follows the biome at the
 * middle of its ring.
 */
public class LostRoomStructure extends Structure {
	public static final MapCodec<LostRoomStructure> CODEC = simpleCodec(LostRoomStructure::new);
	/** How far apart the ground is read across the footprint; the edges are always read. */
	public static final int SAMPLE_STEP = 3;
	/** The blocks left between the bottom slab and the world's floor. */
	public static final int FLOOR_MARGIN = 4;

	public LostRoomStructure(final Structure.StructureSettings settings) {
		super(settings);
	}

	/** The floors a castle may have over ground whose lowest top block is at {@code lowestGround}, in a world that starts at {@code minY}. */
	public static int maxFloors(final int lowestGround, final int minY) {
		return (lowestGround - LostCastleGrid.COVER - (minY + FLOOR_MARGIN)) / LostCastleGrid.FLOOR_HEIGHT;
	}

	@Override
	public Optional<Structure.GenerationStub> findGenerationPoint(final Structure.GenerationContext context) {
		Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(context.random());
		int size = LostCastleGrid.SIZE;
		int west = context.chunkPos().getMiddleBlockX() - size / 2;
		int north = context.chunkPos().getMiddleBlockZ() - size / 2;
		int[] low = {Integer.MAX_VALUE};
		int[] high = {Integer.MIN_VALUE};
		boolean[] wet = {false};
		for (int x = west; ; x = Math.min(west + size - 1, x + SAMPLE_STEP)) {
			for (int z = north; ; z = Math.min(north + size - 1, z + SAMPLE_STEP)) {
				int surface = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
				int ground = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
				wet[0] |= surface != ground;
				low[0] = Math.min(low[0], ground);
				high[0] = Math.max(high[0], ground);
				if (z == north + size - 1) {
					break;
				}
			}
			if (x == west + size - 1) {
				break;
			}
		}
		// No castle on water or a steep slope.
		if (wet[0] || high[0] - low[0] > LostCastleGrid.MAX_SLOPE) {
			return Optional.empty();
		}
		int floors = Math.min(LostCastlePlan.floorCount(context.random()), maxFloors(low[0], context.heightAccessor().getMinY()));
		if (floors < LostCastleGrid.MIN_FLOORS) {
			return Optional.empty();
		}
		int middleX = west + size / 2;
		int middleZ = north + size / 2;
		Holder<Biome> biome = context.biomeResolver().getNoiseBiome(QuartPos.fromBlock(middleX), QuartPos.fromBlock(high[0]), QuartPos.fromBlock(middleZ));
		LostRoomPiece piece = LostRoomPiece.over(west, north, facing, LostCastlePalette.of(biome), floors, low[0], high[0]);
		BlockPos at = new BlockPos(middleX, high[0] + 1, middleZ);
		return Optional.of(new Structure.GenerationStub(at, builder -> builder.addPiece(piece)));
	}

	@Override
	public StructureType<?> type() {
		return LostRooms.STRUCTURE_TYPE;
	}
}
