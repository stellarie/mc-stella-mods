package com.stella.ruins.world;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/** Items 1 and 13, and the piece that carries a site: the real Structure path, the chunk budget and the village exclusion. */
public class StructureGameTest {
	/** The budget for drawing one chunk of the largest layout, in milliseconds. */
	static final double CHUNK_BUDGET_MS = 50.0;

	static ResourceKey<Structure> key(final String id) {
		return ResourceKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath(WorldTestInit.NAMESPACE, id));
	}

	/** Runs the structure's own generation for one chunk, as worldgen would. */
	static StructureStart generate(final ServerLevel level, final String id, final ChunkPos chunk) {
		Holder<Structure> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(key(id));
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState randomState = level.getChunkSource().randomState();
		return holder.value().generate(holder, level.dimension(), level.registryAccess(), generator, generator.getBiomeSource(), randomState.createClimateSampler(SamplerContext.builder().enableCaches().build()), randomState,
			level.getServer().getStructureTemplateManager(), level.getSeed(), chunk, 0, level, biome -> true);
	}

	@GameTest
	public void aSitePlacedThroughTheStructurePathStandsWhole(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ChunkPos chunk = WorldTestSupport.freshChunk();
		List<ChunkPos> forced = new ArrayList<>();
		for (int cx = chunk.x() - 3; cx <= chunk.x() + 3; cx++) {
			for (int cz = chunk.z() - 3; cz <= chunk.z() + 3; cz++) {
				level.setChunkForced(cx, cz, true);
				forced.add(new ChunkPos(cx, cz));
			}
		}
		try {
			StructureStart start = generate(level, "test_surface", chunk);
			helper.assertTrue(start.isValid(), "the structure produced no start on open ground");
			helper.assertTrue(start.getPieces().size() == 1 && start.getPieces().get(0) instanceof RuinPiece, "the start does not hold one ruin piece");
			RuinPiece piece = (RuinPiece) start.getPieces().get(0);
			RuinFrame frame = piece.frame();
			helper.assertTrue(frame.site().equals("test_surface"), "the piece names site " + frame.site());
			ChunkGenerator generator = level.getChunkSource().getGenerator();
			BoundingBox box = start.getBoundingBox();
			int chunksPlaced = 0;
			for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
				for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
					BoundingBox chunkBox = new BoundingBox(cx << 4, level.getMinY(), cz << 4, (cx << 4) + 15, level.getMaxY(), (cz << 4) + 15);
					start.placeInChunk(level, level.structureManager(), generator, RandomSource.create(1L), chunkBox, new ChunkPos(cx, cz));
					chunksPlaced++;
				}
			}
			Map<BlockPos, BlockState> expected = new TreeMap<>(java.util.Comparator.comparingLong(BlockPos::asLong));
			WorldTestInit.surface.layout().draw(LayoutContext.forCapture(frame, expected, frame.box()));
			int wrong = 0;
			BlockPos firstWrong = null;
			for (Map.Entry<BlockPos, BlockState> entry : expected.entrySet()) {
				if (level.getBlockState(entry.getKey()) != entry.getValue()) {
					wrong++;
					firstWrong = firstWrong == null ? entry.getKey() : firstWrong;
				}
			}
			LayoutContext markers = LayoutContext.forDryRun(frame);
			WorldTestInit.surface.layout().draw(markers);
			BlockPos corePos = markers.markers().get(RuinMarkers.CORE).get(0);
			BlockPos vaultPos = markers.markers().get(RuinMarkers.VAULT).get(0);
			boolean coreStands = level.getBlockState(corePos).is(RuinBlocks.CORE) && level.getBlockEntity(corePos) instanceof RuinCoreBlockEntity core && frame.equals(core.frame());
			boolean vaultStands = level.getBlockState(vaultPos).is(RuinBlocks.VAULT) && level.getBlockState(vaultPos).getValue(RuinVaultBlock.LOCKED);
			boolean chestRolls = false;
			for (BlockPos pos : expected.keySet()) {
				if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest && WorldTestInit.CHEST_LOOT.equals(chest.getLootTable())) {
					chestRolls = true;
				}
			}
			WorldTestSupport.result("structurePath", "chunks=" + chunksPlaced + " blocks=" + expected.size() + " wrong=" + wrong + " core=" + coreStands + " vault=" + vaultStands
				+ " chestTable=" + chestRolls + " turns=" + frame.turns() + " min=" + frame.min().toShortString());
			helper.assertTrue(expected.size() > 2000, "the plan drew only " + expected.size() + " blocks");
			helper.assertTrue(wrong == 0, wrong + " blocks differ from the plan, first at " + firstWrong);
			helper.assertTrue(coreStands, "the core is missing or does not know its frame");
			helper.assertTrue(vaultStands, "the vault is missing or not locked");
			helper.assertTrue(chestRolls, "no chest carries its loot table");
			helper.assertTrue(chunksPlaced >= 4, "the site did not cross chunk borders, so the test proves little");
		} finally {
			forced.forEach(c -> level.setChunkForced(c.x(), c.z(), false));
		}
		helper.succeed();
	}

	@GameTest
	public void theLargestLayoutDrawsEachChunkWithinTheBudget(final GameTestHelper helper) {
		RuinFrame frame = RuinFrame.of(WorldTestInit.large, new BlockPos(5, 64, 9), Rotation.NONE, 4242L);
		BoundingBox box = frame.box();
		SiteLayout layout = WorldTestInit.large.layout();
		layout.draw(LayoutContext.forCapture(frame, new TreeMap<>(java.util.Comparator.comparingLong(BlockPos::asLong)), box));
		double total = 0.0;
		double worst = 0.0;
		int chunks = 0;
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				BoundingBox clip = new BoundingBox(cx << 4, box.minY(), cz << 4, (cx << 4) + 15, box.maxY(), (cz << 4) + 15);
				long start = System.nanoTime();
				layout.draw(LayoutContext.forCapture(frame, new java.util.HashMap<>(), clip));
				double millis = (System.nanoTime() - start) / 1_000_000.0;
				total += millis;
				worst = Math.max(worst, millis);
				chunks++;
			}
		}
		WorldTestSupport.result("largestLayoutDraw", String.format("size=%dx%dx%d chunks=%d meanMs=%.2f worstMs=%.2f budgetMs=%.0f", frame.sizeX(), frame.sizeY(), frame.sizeZ(), chunks, total / chunks, worst, CHUNK_BUDGET_MS));
		helper.assertTrue(frame.sizeX() == RuinSite.MAX_X && frame.sizeY() == RuinSite.MAX_Y && frame.sizeZ() == RuinSite.MAX_Z, "the layout is not the largest allowed box");
		helper.assertTrue(chunks >= 16, "the largest layout crossed only " + chunks + " chunks");
		helper.assertTrue(total / chunks <= CHUNK_BUDGET_MS, "a chunk took " + (total / chunks) + " ms on average, over " + CHUNK_BUDGET_MS);
		helper.succeed();
	}

	@GameTest
	public void aPieceSurvivesItsOwnSaveAndLoad(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinFrame frame = RuinFrame.of(WorldTestInit.surface, new BlockPos(-37, 71, 1203), Rotation.COUNTERCLOCKWISE_90, 0xABCDEF123L);
		RuinPiece piece = new RuinPiece(frame);
		CompoundTag tag = piece.createTag(StructurePieceSerializationContext.fromLevel(level));
		StructurePiece loaded = RuinsWorld.PIECE.load(StructurePieceSerializationContext.fromLevel(level), tag);
		helper.assertTrue(loaded instanceof RuinPiece, "the piece type did not load a ruin piece");
		helper.assertTrue(((RuinPiece) loaded).frame().equals(frame), "the frame changed over a save: " + ((RuinPiece) loaded).frame() + " versus " + frame);
		helper.assertTrue(loaded.getBoundingBox().equals(piece.getBoundingBox()), "the box changed over a save");
		helper.succeed();
	}

	/**
	 * True when the placement excludes villages by at least 8 chunks. The exclusion names a tag, because each set
	 * also excludes the other ruin sets and one placement carries one exclusion zone. A tag holds villages, so the
	 * village exclusion still stands and the tag is what carries it.
	 */
	static boolean excludesVillages(final JsonElement placement) {
		return RuinSitePlanGameTest.excludesVillages(placement);
	}

	@GameTest
	public void everyRuinsStructureSetExcludesVillages(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
		List<String> missing = new ArrayList<>();
		List<String> holders = new ArrayList<>();
		int ruinSets = 0;
		for (Holder.Reference<StructureSet> holder : level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).listElements().toList()) {
			StructureSet set = holder.value();
			if (set.structures().stream().noneMatch(entry -> entry.structure().value() instanceof RuinStructure)) {
				continue;
			}
			ruinSets++;
			holders.add(holder.key().identifier().toString());
			JsonElement placement = StructurePlacement.CODEC.encodeStart(ops, set.placement()).getOrThrow();
			if (!excludesVillages(placement)) {
				missing.add(holder.key().identifier() + " " + placement);
			}
		}
		boolean merged = RuinSitePlanGameTest.sites(level) != null && !RuinSitePlanGameTest.plans(level).isEmpty();
		WorldTestSupport.result("villageExclusion", "ruinSets=" + ruinSets + " holders=" + holders + " mergedSet=" + merged
			+ " thisModSets=" + RuinSitePlanGameTest.ruinSetIds(level) + " missing=" + missing.size());
		helper.assertTrue(ruinSets >= 1, "no ruin structure set was found, so the check proves little");
		helper.assertTrue(merged, "no " + RuinSitePlanGameTest.SITES + " set holds the ruin structures");
		helper.assertTrue(missing.isEmpty(), "structure sets without a village exclusion of 8 chunks: " + missing);
		helper.succeed();
	}

	@GameTest
	public void theVillageExclusionCheckRefusesAWeakerPlacement(final GameTestHelper helper) {
		String strong = "{\"exclusion_zone\":{\"chunk_count\":8,\"other_set\":\"minecraft:villages\"}}";
		String tooClose = "{\"exclusion_zone\":{\"chunk_count\":7,\"other_set\":\"minecraft:villages\"}}";
		String otherSet = "{\"exclusion_zone\":{\"chunk_count\":9,\"other_set\":\"minecraft:ancient_cities\"}}";
		String tag = "{\"exclusion_zone\":{\"chunk_count\":8,\"other_set\":\"#mod-stella-ruins:sites\"}}";
		String none = "{\"salt\":1}";
		helper.assertTrue(excludesVillages(JsonParser.parseString(strong)), "the check refused a correct exclusion");
		helper.assertTrue(!excludesVillages(JsonParser.parseString(tooClose)), "the check accepted 7 chunks");
		helper.assertTrue(!excludesVillages(JsonParser.parseString(otherSet)), "the check accepted another set");
		helper.assertTrue(!excludesVillages(JsonParser.parseString(tag)), "the check accepted a tag, and the codec takes a set id");
		helper.assertTrue(!excludesVillages(JsonParser.parseString(none)), "the check accepted no exclusion");
		helper.succeed();
	}
}
