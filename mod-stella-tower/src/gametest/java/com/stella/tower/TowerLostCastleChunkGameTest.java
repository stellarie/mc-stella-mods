package com.stella.tower;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** The lost castle is built one chunk at a time in a real world: the chunks must agree on one castle. */
public class TowerLostCastleChunkGameTest {
	/** What one build left in a piece's box: every block state, and what every chest holds, in a fixed order. */
	private record Snapshot(List<BlockState> states, List<String> chests) {
	}

	/** A site in an empty spot of the tower level, offset by 5 and 7 so the piece crosses four chunks each way. */
	private static LostRoomPiece site(final ServerLevel level, final Direction facing, final LostCastlePalette palette, final int floors) {
		int[] spot = TowerLostCastleSupport.freshSpot();
		return TowerLostCastleSupport.site(level, spot[0] + 5, spot[1] + 7, facing, palette, floors);
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
		List<String> chests = new ArrayList<>();
		for (BlockPos at : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState state = level.getBlockState(at);
			states.add(state);
			if (state.getBlock() instanceof ChestBlock && level.getBlockEntity(at) instanceof ChestBlockEntity chest) {
				StringBuilder items = new StringBuilder(at.toShortString()).append(':');
				for (int slot = 0; slot < chest.getContainerSize(); slot++) {
					items.append(chest.getItem(slot)).append(';');
				}
				chests.add(items.toString());
			}
		}
		return new Snapshot(states, chests);
	}

	private static int differing(final Snapshot a, final Snapshot b) {
		int differing = 0;
		for (int i = 0; i < a.states().size(); i++) {
			if (!a.states().get(i).equals(b.states().get(i))) {
				differing++;
				if (differing <= 3) {
					TowerTestSupport.LOG.info("[BEHAVIOR] test=castleChunkSplit DIFF index={} whole={} split={}", i, a.states().get(i), b.states().get(i));
				}
			}
		}
		return differing;
	}

	/**
	 * A castle built in one pass, then built again chunk by chunk in a shuffled order, must come out the same: the
	 * same blocks, and the same loot in every chest. Catches a choice made from a block already laid, a portal
	 * broken by the stone laid after it across a chunk seam, and a stair or wall cut in one chunk and not the next.
	 */
	@GameTest(maxTicks = 300)
	public void aCastleBuiltChunkByChunkEqualsOneBuiltWhole(final GameTestHelper helper) {
		ServerLevel level = TowerTestSupport.tower(helper);
		Direction[] facings = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
		int[] floors = {3, 5, 4, 6};
		StringBuilder report = new StringBuilder();
		boolean same = true;
		for (int run = 0; run < facings.length; run++) {
			LostCastlePalette palette = LostCastlePalette.values()[run % LostCastlePalette.values().length];
			LostRoomPiece piece = site(level, facings[run], palette, floors[run]);
			BoundingBox box = piece.getBoundingBox();

			piece.build(level, BoundingBox.infinite());
			Snapshot whole = snapshot(level, box);

			TowerLostCastleSupport.ground(level, piece);
			int chunks = buildByChunk(level, piece, run);
			Snapshot split = snapshot(level, box);

			int differing = differing(whole, split);
			boolean runSame = differing == 0 && whole.chests().equals(split.chests());
			same &= runSame;
			report.append(" [run ").append(run).append(' ').append(palette).append(' ').append(facings[run]).append(" floors=").append(floors[run])
				.append(" chunks=").append(chunks).append(" chests=").append(whole.chests().size()).append('/').append(split.chests().size())
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
	@GameTest(maxTicks = 300)
	public void nothingIsWrittenOutsideThePieceBox(final GameTestHelper helper) {
		ServerLevel level = TowerTestSupport.tower(helper);
		int touched = 0;
		for (Direction facing : new Direction[] {Direction.NORTH, Direction.WEST}) {
			LostRoomPiece piece = site(level, facing, LostCastlePalette.SNOW, 3);
			BoundingBox box = piece.getBoundingBox();
			List<BlockState> before = ring(level, box);
			buildByChunk(level, piece, facing.ordinal());
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
	@GameTest(maxTicks = 300)
	public void samePieceSeedGivesTheSameBlocks(final GameTestHelper helper) {
		ServerLevel level = TowerTestSupport.tower(helper);
		LostRoomPiece piece = site(level, Direction.WEST, LostCastlePalette.SANDSTONE, 3);
		piece.build(level, BoundingBox.infinite());
		Snapshot first = snapshot(level, piece.getBoundingBox());
		TowerLostCastleSupport.ground(level, piece);
		piece.build(level, BoundingBox.infinite());
		Snapshot second = snapshot(level, piece.getBoundingBox());
		LostRoomPiece loaded = new LostRoomPiece(piece.createTag(StructurePieceSerializationContext.fromLevel(level)));
		boolean samePlan = loaded.plan().equals(piece.plan()) && loaded.palette() == piece.palette() && loaded.floors() == piece.floors()
			&& loaded.drop() == piece.drop();
		int differing = differing(first, second);
		TowerTestSupport.result("castleSameSeed", "differingBlocks=" + differing + " samePlanAfterReload=" + samePlan);
		helper.assertTrue(differing == 0, differing + " blocks differ between two builds of one piece");
		helper.assertTrue(samePlan, "a piece loaded from its tag draws another castle");
		helper.succeed();
	}
}
