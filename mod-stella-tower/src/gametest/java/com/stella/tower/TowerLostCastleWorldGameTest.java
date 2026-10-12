package com.stella.tower;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

/**
 * The castle through the structure's own path, in the overworld test level: the start the structure draws over real
 * ground, the palette its biome gives, and the castle laid chunk by chunk as worldgen lays it.
 */
public class TowerLostCastleWorldGameTest {
	/** The first chunk of the test's own spots, far from any other test's. */
	private static final int FIRST_CHUNK = 90_000;

	private static Structure.GenerationContext context(final ServerLevel level, final Holder<Biome> biome, final ChunkPos at) {
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		return new Structure.GenerationContext(level.registryAccess(), generator, new FixedBiomeSource(biome), null, level.getChunkSource().randomState(),
			level.getServer().getStructureTemplateManager(), level.getSeed(), at, level, candidate -> true);
	}

	/**
	 * Catches a structure that draws no start over level ground, that gives a biome the wrong palette, that puts the top
	 * ceiling anywhere but the cover depth under the ground, or that lays a castle chunk by chunk which cannot be walked.
	 */
	@GameTest(maxTicks = 300)
	public void theStructureLaysADescendingCastleOverRealGroundInTheBiomesPalette(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		var biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
		Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(LostRooms.STRUCTURE);
		helper.assertTrue(structure instanceof LostRoomStructure, "the lost_room structure is not the castle");
		Map<ResourceKey<Biome>, LostCastlePalette> cases = new LinkedHashMap<>();
		cases.put(Biomes.SNOWY_PLAINS, LostCastlePalette.SNOW);
		cases.put(Biomes.DESERT, LostCastlePalette.SANDSTONE);
		cases.put(Biomes.PLAINS, LostCastlePalette.GREEN);
		int slot = 0;
		List<LostRoomData.Castle> expected = new ArrayList<>();
		StringBuilder report = new StringBuilder();
		for (Map.Entry<ResourceKey<Biome>, LostCastlePalette> entry : cases.entrySet()) {
			ChunkPos at = new ChunkPos(FIRST_CHUNK + slot * 8, FIRST_CHUNK + Math.floorMod(TowerTestSupport.freshArea(), 4096) * 8);
			slot++;
			Optional<Structure.GenerationStub> stub = structure.findValidGenerationPoint(context(level, biomes.getOrThrow(entry.getKey()), at));
			helper.assertTrue(stub.isPresent(), "the structure drew no castle over level ground for " + entry.getKey().identifier());
			List<StructurePiece> pieces = stub.get().getPiecesBuilder().build().pieces();
			helper.assertTrue(pieces.size() == 1 && pieces.get(0) instanceof LostRoomPiece, "the start is not one castle piece: " + pieces);
			LostRoomPiece piece = (LostRoomPiece) pieces.get(0);
			BoundingBox box = piece.getBoundingBox();
			int ground = generator.getFirstOccupiedHeight(box.minX(), box.minZ(), Heightmap.Types.OCEAN_FLOOR_WG, level, level.getChunkSource().randomState());
			helper.assertTrue(piece.palette() == entry.getValue(), entry.getKey().identifier() + " gave " + piece.palette() + ", not " + entry.getValue());
			helper.assertTrue(piece.floors() >= LostCastleGrid.MIN_FLOORS && piece.floors() <= LostCastleGrid.MAX_FLOORS, "the castle has " + piece.floors() + " floors");
			helper.assertTrue(box.minY() >= level.getMinY() + LostRoomStructure.FLOOR_MARGIN, "the castle reaches the bottom of the world: " + box);
			int ceiling = box.minY() + LostCastleGrid.topY(piece.floors());
			helper.assertTrue(ceiling == ground - LostCastleGrid.COVER, "the top ceiling at " + ceiling + " is not " + LostCastleGrid.COVER + " under the ground at " + ground);
			helper.assertTrue(piece.entranceTop().getY() == ground + 1, "the ring's floor at " + piece.entranceTop().getY() + " is not over the ground at " + ground);
			helper.assertTrue(piece.drop() >= LostCastleGrid.MIN_DROP && piece.drop() <= LostCastleGrid.MAX_DROP, "the drop " + piece.drop() + " is outside the bounds the stairs fit in");
			place(level, generator, piece);
			expected.add(new LostRoomData.Castle(level.dimension(), new BlockPos(box.minX(), box.minY(), box.minZ()), piece.facing(), false, piece.palette().ordinal(),
				piece.floors(), piece.drop()));
			Set<BlockPos> walked = TowerLostCastleSupport.walk(level, box, piece.entranceTop());
			BlockPos front = piece.worldPos(LostCastleBuilder.PORTAL_X, LostCastleGrid.feet(piece.floors(), piece.floors() - 1) + 1, LostCastleBuilder.PORTAL_Z + 2);
			helper.assertTrue(walked.contains(front), entry.getValue() + " castle: the portal is not walked to from the ring");
			report.append(" [").append(entry.getKey().identifier()).append(' ').append(piece.palette()).append(" floors=").append(piece.floors()).append(" ground=").append(ground)
				.append(" box=").append(box).append(" walked=").append(walked.size()).append(']');
		}
		LostCastles.wake(level.getServer());
		List<LostRoomData.Castle> recorded = LostRoomData.get(level.getServer()).castles().stream()
			.filter(castle -> expected.stream().anyMatch(want -> want.key().equals(castle.key()) && want.variant() == castle.variant()
				&& want.floors() == castle.floors() && want.drop() == castle.drop())).toList();
		TowerTestSupport.result("castleWorld", "recorded=" + recorded.size() + report);
		helper.assertTrue(recorded.size() == cases.size(), "only " + recorded.size() + " of " + cases.size() + " castles were recorded as the world laid them");
		helper.succeed();
	}

	/** Lays a piece the way worldgen does: once for each chunk its box touches, in order, each with that chunk's box. */
	private static void place(final ServerLevel level, final ChunkGenerator generator, final LostRoomPiece piece) {
		BoundingBox box = piece.getBoundingBox();
		for (int cx = Math.floorDiv(box.minX(), 16); cx <= Math.floorDiv(box.maxX(), 16); cx++) {
			for (int cz = Math.floorDiv(box.minZ(), 16); cz <= Math.floorDiv(box.maxZ(), 16); cz++) {
				BoundingBox chunk = new BoundingBox(cx * 16, level.getMinY(), cz * 16, cx * 16 + 15, level.getMaxY(), cz * 16 + 15);
				piece.postProcess(level, level.structureManager(), generator, level.getRandom(), chunk, new ChunkPos(cx, cz), BlockPos.ZERO);
			}
		}
	}
}
