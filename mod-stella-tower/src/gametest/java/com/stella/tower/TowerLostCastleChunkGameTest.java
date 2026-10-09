package com.stella.tower;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** The lost castle is built one chunk at a time in a real world: the chunks must agree on one castle. */
public class TowerLostCastleChunkGameTest {
	/** What one build left in a piece's box: every block state, and the chest blocks by storey. */
	private record Snapshot(List<BlockState> states, int[] chestsByStorey) {
		int chests() {
			int sum = 0;
			for (int count : this.chestsByStorey) {
				sum += count;
			}
			return sum;
		}
	}

	/** A castle placed over a hill, not yet built: the ground it stands on and the piece. */
	private record Site(BoundingBox region, LostRoomPiece piece) {
	}

	private static void ground(final ServerLevel level, final BoundingBox region) {
		for (int x = region.minX(); x <= region.maxX(); x++) {
			for (int z = region.minZ(); z <= region.maxZ(); z++) {
				for (int y = 56; y <= region.maxY(); y++) {
					BlockState state = y <= TowerLostRoomGameTest.hill(x, z) ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
					level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	/** A site in an empty spot of the tower level, offset by 5 and 7 so the piece crosses three chunks each way. */
	private static Site site(final ServerLevel level, final Direction facing, final LostCastleVariant variant) {
		int area = TowerTestSupport.freshArea();
		int west = Math.floorMod(area, 4096) * 64 + 5;
		int north = -200_000 - Math.floorMod(area / 4096, 1000) * 64 + 7;
		BoundingBox region = new BoundingBox(west - 2, 56, north - 2, west + LostRoomPiece.LENGTH + 1, 56 + 4 + LostRoomPiece.HEIGHT + 2, north + LostRoomPiece.LENGTH + 1);
		ground(level, region);
		int floor = LostRoomPiece.floorOver(west, north, west + LostRoomPiece.LENGTH - 1, north + LostRoomPiece.LENGTH - 1, 1, TowerLostRoomGameTest::hill);
		return new Site(region, new LostRoomPiece(west, floor, north, facing, variant));
	}

	/** Builds the piece one 16 x 16 chunk box at a time, in a shuffled order, as worldgen does. */
	private static int buildByChunk(final ServerLevel level, final LostRoomPiece piece, final long order) {
		BoundingBox box = piece.getBoundingBox();
		List<BoundingBox> chunks = new ArrayList<>();
		for (int cx = Math.floorDiv(box.minX(), 16); cx <= Math.floorDiv(box.maxX(), 16); cx++) {
			for (int cz = Math.floorDiv(box.minZ(), 16); cz <= Math.floorDiv(box.maxZ(), 16); cz++) {
				chunks.add(new BoundingBox(cx * 16, level.getMinY(), cz * 16, cx * 16 + 15, level.getMaxY(), cz * 16 + 15));
			}
		}
		Collections.shuffle(chunks, new Random(order));
		for (BoundingBox chunk : chunks) {
			piece.build(level, chunk);
		}
		return chunks.size();
	}

	private static Snapshot snapshot(final ServerLevel level, final BoundingBox box) {
		List<BlockState> states = new ArrayList<>();
		int[] chests = new int[TowerHex.STOREYS];
		for (BlockPos at : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState state = level.getBlockState(at);
			states.add(state);
			if (state.getBlock() instanceof ChestBlock) {
				int storey = 0;
				while (storey + 1 < chests.length && at.getY() >= box.minY() + TowerHex.floorAt(storey + 1)) {
					storey++;
				}
				chests[storey]++;
			}
		}
		return new Snapshot(states, chests);
	}

	private static int differing(final Snapshot a, final Snapshot b) {
		int differing = 0;
		for (int i = 0; i < a.states().size(); i++) {
			if (!a.states().get(i).equals(b.states().get(i))) {
				differing++;
			}
		}
		return differing;
	}

	/** A castle built in one pass, then built again chunk by chunk in a shuffled order, must come out the same. */
	@GameTest(maxTicks = 200)
	public void aCastleBuiltChunkByChunkEqualsOneBuiltWhole(final GameTestHelper helper) {
		ServerLevel level = TowerTestSupport.tower(helper);
		Direction[] facings = {Direction.NORTH, Direction.EAST, Direction.SOUTH};
		LostCastleVariant[] variants = {LostCastleVariant.CASTLE, LostCastleVariant.KEEP, LostCastleVariant.CRYPT};
		StringBuilder report = new StringBuilder();
		boolean same = true;
		for (int run = 0; run < facings.length; run++) {
			Site site = site(level, facings[run], variants[run]);
			LostRoomPiece piece = site.piece();
			BoundingBox box = piece.getBoundingBox();

			piece.build(level, BoundingBox.infinite());
			Snapshot whole = snapshot(level, box);

			ground(level, site.region());
			int chunks = buildByChunk(level, piece, run);
			Snapshot split = snapshot(level, box);

			int differing = differing(whole, split);
			boolean runSame = differing == 0 && Arrays.equals(whole.chestsByStorey(), split.chestsByStorey());
			same &= runSame;
			report.append(" [run ").append(run).append(' ').append(variants[run]).append(' ').append(facings[run])
				.append(" chunks=").append(chunks)
				.append(" chestsWhole=").append(whole.chests()).append(Arrays.toString(whole.chestsByStorey()))
				.append(" chestsSplit=").append(split.chests()).append(Arrays.toString(split.chestsByStorey()))
				.append(" differingBlocks=").append(differing).append(']');
		}
		TowerTestSupport.result("castleChunkSplit", "same=" + same + report);
		helper.assertTrue(same, "a castle built chunk by chunk differs from the same castle built whole:" + report);
		helper.succeed();
	}

	/** The blocks around the piece's box, its sides and the layer over it, which no chunk of the castle may touch. */
	private static List<BlockState> ring(final ServerLevel level, final BoundingBox box) {
		List<BlockState> ring = new ArrayList<>();
		for (BlockPos at : BlockPos.betweenClosed(box.minX() - 1, box.minY(), box.minZ() - 1, box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1)) {
			if (!box.isInside(at)) {
				ring.add(level.getBlockState(at));
			}
		}
		return ring;
	}

	/** No chunk writes a block outside the piece's box, and no half of a double chest lands beyond it. */
	@GameTest(maxTicks = 200)
	public void nothingIsWrittenOutsideThePieceBox(final GameTestHelper helper) {
		ServerLevel level = TowerTestSupport.tower(helper);
		int touched = 0;
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			Site site = site(level, facing, LostCastleVariant.KEEP);
			BoundingBox box = site.piece().getBoundingBox();
			List<BlockState> before = ring(level, box);
			buildByChunk(level, site.piece(), facing.ordinal());
			List<BlockState> after = ring(level, box);
			for (int i = 0; i < before.size(); i++) {
				touched += before.get(i).equals(after.get(i)) ? 0 : 1;
			}
		}
		TowerTestSupport.result("castleRing", "ringBlocksChanged=" + touched);
		helper.assertTrue(touched == 0, touched + " blocks outside the piece box changed");
		helper.succeed();
	}

	/** One seed gives one castle: the same piece builds the same blocks twice, and a saved piece draws the same plan. */
	@GameTest(maxTicks = 200)
	public void samePieceSeedGivesTheSameBlocks(final GameTestHelper helper) {
		ServerLevel level = TowerTestSupport.tower(helper);
		Site site = site(level, Direction.WEST, LostCastleVariant.MAZE);
		LostRoomPiece piece = site.piece();
		piece.build(level, BoundingBox.infinite());
		Snapshot first = snapshot(level, piece.getBoundingBox());
		ground(level, site.region());
		piece.build(level, BoundingBox.infinite());
		Snapshot second = snapshot(level, piece.getBoundingBox());
		LostRoomPiece loaded = new LostRoomPiece(piece.createTag(StructurePieceSerializationContext.fromLevel(level)));
		boolean samePlan = loaded.plan().equals(piece.plan());
		int differing = differing(first, second);
		TowerTestSupport.result("castleSameSeed", "differingBlocks=" + differing + " samePlanAfterReload=" + samePlan);
		helper.assertTrue(differing == 0, differing + " blocks differ between two builds of one piece");
		helper.assertTrue(samePlan, "a piece loaded from its tag draws another plan");
		helper.succeed();
	}
}
