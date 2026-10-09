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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

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

	/** A castle built in one pass, then built again chunk by chunk in a shuffled order, must come out the same. */
	@GameTest(maxTicks = 200)
	public void aCastleBuiltChunkByChunkEqualsOneBuiltWhole(final GameTestHelper helper) {
		ServerLevel level = TowerTestSupport.tower(helper);
		Direction[] facings = {Direction.NORTH, Direction.EAST, Direction.SOUTH};
		LostCastleVariant[] variants = {LostCastleVariant.CASTLE, LostCastleVariant.KEEP, LostCastleVariant.CRYPT};
		StringBuilder report = new StringBuilder();
		boolean same = true;
		for (int run = 0; run < facings.length; run++) {
			int area = TowerTestSupport.freshArea();
			// The offset of 5 and 7 puts the piece across three chunks in each direction.
			int west = Math.floorMod(area, 4096) * 64 + 5;
			int north = -200_000 - Math.floorMod(area / 4096, 1000) * 64 + 7;
			BoundingBox region = new BoundingBox(west - 2, 56, north - 2, west + LostRoomPiece.LENGTH + 1, 56 + 4 + LostRoomPiece.HEIGHT + 2, north + LostRoomPiece.LENGTH + 1);
			ground(level, region);
			int floor = LostRoomPiece.floorOver(west, north, west + LostRoomPiece.LENGTH - 1, north + LostRoomPiece.LENGTH - 1, 1, TowerLostRoomGameTest::hill);
			LostRoomPiece piece = new LostRoomPiece(west, floor, north, facings[run], variants[run]);
			BoundingBox box = piece.getBoundingBox();

			piece.build(level, BoundingBox.infinite());
			Snapshot whole = snapshot(level, box);

			ground(level, region);
			List<BoundingBox> chunks = new ArrayList<>();
			for (int cx = Math.floorDiv(box.minX(), 16); cx <= Math.floorDiv(box.maxX(), 16); cx++) {
				for (int cz = Math.floorDiv(box.minZ(), 16); cz <= Math.floorDiv(box.maxZ(), 16); cz++) {
					chunks.add(new BoundingBox(cx * 16, level.getMinY(), cz * 16, cx * 16 + 15, level.getMaxY(), cz * 16 + 15));
				}
			}
			Collections.shuffle(chunks, new Random(run));
			for (BoundingBox chunk : chunks) {
				piece.build(level, chunk);
			}
			Snapshot split = snapshot(level, box);

			int differing = 0;
			for (int i = 0; i < whole.states().size(); i++) {
				if (!whole.states().get(i).equals(split.states().get(i))) {
					differing++;
				}
			}
			boolean runSame = differing == 0 && java.util.Arrays.equals(whole.chestsByStorey(), split.chestsByStorey());
			same &= runSame;
			report.append(" [run ").append(run).append(' ').append(variants[run]).append(' ').append(facings[run])
				.append(" chunks=").append(chunks.size())
				.append(" chestsWhole=").append(whole.chests()).append(java.util.Arrays.toString(whole.chestsByStorey()))
				.append(" chestsSplit=").append(split.chests()).append(java.util.Arrays.toString(split.chestsByStorey()))
				.append(" differingBlocks=").append(differing).append(']');
		}
		TowerTestSupport.result("castleChunkSplit", "same=" + same + report);
		helper.assertTrue(same, "a castle built chunk by chunk differs from the same castle built whole:" + report);
		helper.succeed();
	}
}
