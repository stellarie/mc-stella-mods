package com.stella.ruins.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Item 2: a layout draws the same plan whatever the chunk order, and the seed changes the plan. */
public class LayoutSeedGameTest {
	/** Straddles chunk borders on both axes, so a torn plan would show. */
	static final BlockPos MIN = new BlockPos(5, 64, 9);

	static RuinFrame frame(final long seed, final int turns) {
		return RuinFrame.of(WorldTestInit.surface, MIN, Rotation.values()[turns], seed);
	}

	/** Draws the layout through one box, or chunk by chunk, into a map of world positions. */
	static Map<BlockPos, BlockState> draw(final SiteLayout layout, final RuinFrame frame, final boolean chunked) {
		Map<BlockPos, BlockState> sink = new TreeMap<>(java.util.Comparator.comparingLong(BlockPos::asLong));
		BoundingBox box = frame.box();
		if (!chunked) {
			layout.draw(LayoutContext.forCapture(frame, sink, box));
			return sink;
		}
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				BoundingBox clip = new BoundingBox(cx << 4, box.minY(), cz << 4, (cx << 4) + 15, box.maxY(), (cz << 4) + 15);
				layout.draw(LayoutContext.forCapture(frame, sink, clip));
			}
		}
		return sink;
	}

	static long hash(final Map<BlockPos, BlockState> blocks) {
		long hash = 17L;
		for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
			hash = hash * 31L + entry.getKey().asLong();
			hash = hash * 31L + Block.getId(entry.getValue());
		}
		return hash;
	}

	@GameTest
	public void theSameSeedDrawsTheSameBlocksInOneBoxAndChunkByChunk(final GameTestHelper helper) {
		SiteLayout layout = WorldTestInit.surface.layout();
		StringBuilder line = new StringBuilder();
		for (int turns = 0; turns < 4; turns++) {
			RuinFrame frame = frame(0xC0FFEEL, turns);
			Map<BlockPos, BlockState> whole = draw(layout, frame, false);
			Map<BlockPos, BlockState> pieces = draw(layout, frame, true);
			Set<Block> kinds = new HashSet<>();
			whole.values().forEach(state -> kinds.add(state.getBlock()));
			line.append(" turns").append(turns).append("=").append(Long.toHexString(hash(whole))).append("/").append(whole.size());
			helper.assertTrue(kinds.size() >= 5, "the layout drew only " + kinds.size() + " kinds of block, so the comparison proves little");
			helper.assertTrue(whole.size() == pieces.size(), "turns " + turns + ": one box drew " + whole.size() + " blocks, chunk by chunk " + pieces.size());
			helper.assertTrue(hash(whole) == hash(pieces), "turns " + turns + ": the blocks differ between one box and chunk by chunk");
		}
		WorldTestSupport.result("sameSeedSamePlan", "hashes=" + line);
		helper.succeed();
	}

	@GameTest
	public void differentSeedsDrawDifferentBlocks(final GameTestHelper helper) {
		SiteLayout layout = WorldTestInit.surface.layout();
		Set<Long> hashes = new HashSet<>();
		int seeds = 8;
		for (long seed = 1; seed <= seeds; seed++) {
			hashes.add(hash(draw(layout, frame(seed, 0), false)));
		}
		WorldTestSupport.result("differentSeedsDiffer", "distinct=" + hashes.size() + "/" + seeds);
		helper.assertTrue(hashes.size() == seeds, "only " + hashes.size() + " of " + seeds + " seeds drew a different plan");
		helper.succeed();
	}

	@GameTest
	public void aLayoutThatSharesOneRandomSourceTearsAtChunkBorders(final GameTestHelper helper) {
		// The control: this layout reads a random source that outlives each call, the mistake item 2 guards against.
		RandomSource shared = RandomSource.create(99L);
		SiteLayout torn = ctx -> {
			for (int x = 0; x < ctx.sizeX(); x++) {
				for (int z = 0; z < ctx.sizeZ(); z++) {
					ctx.set(x, 1, z, shared.nextBoolean() ? Blocks.STONE.defaultBlockState() : Blocks.DIRT.defaultBlockState());
				}
			}
		};
		RuinFrame frame = frame(5L, 0);
		long whole = hash(draw(torn, frame, false));
		long pieces = hash(draw(torn, frame, true));
		WorldTestSupport.result("tornLayoutControl", "whole=" + Long.toHexString(whole) + " pieces=" + Long.toHexString(pieces));
		helper.assertTrue(whole != pieces, "the comparison cannot tell a torn layout from a whole one");
		helper.succeed();
	}

	@GameTest
	public void aDryRunWritesNothingAndFindsTheMarkersAWorldDrawFinds(final GameTestHelper helper) {
		RuinFrame frame = frame(77L, 1);
		LayoutContext dry = LayoutContext.forDryRun(frame);
		WorldTestInit.surface.layout().draw(dry);
		Map<BlockPos, BlockState> sink = new TreeMap<>(java.util.Comparator.comparingLong(BlockPos::asLong));
		LayoutContext capture = LayoutContext.forCapture(frame, sink, frame.box());
		WorldTestInit.surface.layout().draw(capture);
		helper.assertTrue(dry.markers().equals(capture.markers()), "the dry run found other markers than a real draw");
		helper.assertTrue(dry.markers().get(RuinMarkers.SPAWN).size() == 4, "the dry run lost a spawn marker");
		helper.assertTrue(dry.markers().get(RuinMarkers.CORE).size() == 1 && dry.markers().get(RuinMarkers.VAULT).size() == 1, "the dry run lost the core or the vault");
		helper.assertTrue(dry.get(1, 1, 1).isAir(), "a dry run read a block");
		helper.succeed();
	}

	@GameTest
	public void everyBlockLandsInsideTheSiteBox(final GameTestHelper helper) {
		for (int turns = 0; turns < 4; turns++) {
			RuinFrame frame = frame(31L, turns);
			BoundingBox box = frame.box();
			List<BlockPos> outside = new ArrayList<>();
			for (BlockPos pos : draw(WorldTestInit.surface.layout(), frame, false).keySet()) {
				if (!box.isInside(pos)) {
					outside.add(pos);
				}
			}
			helper.assertTrue(outside.isEmpty(), "turns " + turns + ": " + outside.size() + " blocks fell outside the box, first " + (outside.isEmpty() ? "" : outside.get(0)));
			helper.assertTrue(box.getXSpan() == frame.worldSizeX() && box.getZSpan() == frame.worldSizeZ(), "the box does not turn with the frame");
		}
		helper.succeed();
	}

	@GameTest
	public void aQuarterTurnSendsLocalNorthToTheDirectionTheBlocksFace(final GameTestHelper helper) {
		for (int turns = 0; turns < 4; turns++) {
			RuinFrame frame = frame(1L, turns);
			Rotation rotation = Rotation.values()[turns];
			Direction expected = rotation.rotate(Direction.NORTH);
			BlockPos here = frame.toWorld(10, 1, 10);
			BlockPos northOfHere = frame.toWorld(10, 1, 9);
			helper.assertTrue(northOfHere.subtract(here).equals(expected.getUnitVec3i()), "turns " + turns + ": local north steps " + northOfHere.subtract(here) + ", not " + expected);
			Map<BlockPos, BlockState> sink = new TreeMap<>(java.util.Comparator.comparingLong(BlockPos::asLong));
			LayoutContext ctx = LayoutContext.forCapture(frame, sink, frame.box());
			ctx.placeChest(10, 1, 10, Direction.NORTH, WorldTestInit.CHEST_LOOT);
			BlockState chest = sink.get(here);
			helper.assertTrue(chest != null && chest.getValue(ChestBlock.FACING) == expected, "turns " + turns + ": the chest faces " + (chest == null ? "nowhere" : chest.getValue(ChestBlock.FACING)) + ", not " + expected);
		}
		helper.succeed();
	}
}
