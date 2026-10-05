package com.stella.ruins.site.fallenwatchtower;

import com.stella.ruins.world.LayoutContext;
import com.stella.ruins.world.RuinBlocks;
import com.stella.ruins.world.RuinFrame;
import com.stella.ruins.world.RuinMarkers;
import com.stella.ruins.world.RuinSite;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** The layout as the game draws it: the same blocks in every chunk order, inside the box, over a foundation. */
public class WatchtowerLayoutGameTest {
	static final BlockPos MIN = new BlockPos(5, 64, 9);
	static final long SEED = 0x5E17A3C1L;
	static final int WARM_UP = 6;
	static final int TIMED = 24;
	static final double BUDGET_MS = 50.0;

	static RuinFrame frame(final long seed, final int turns) {
		return RuinFrame.of(TowerTestSupport.site(), MIN, Rotation.values()[turns], seed);
	}

	static Map<BlockPos, BlockState> draw(final RuinFrame frame, final boolean chunked) {
		RuinSite site = TowerTestSupport.site();
		Map<BlockPos, BlockState> sink = new TreeMap<>(Comparator.comparingLong(BlockPos::asLong));
		BoundingBox box = frame.box();
		if (!chunked) {
			site.layout().draw(LayoutContext.forCapture(frame, sink, box));
			return sink;
		}
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				BoundingBox clip = new BoundingBox(cx << 4, box.minY(), cz << 4, (cx << 4) + 15, box.maxY(), (cz << 4) + 15);
				site.layout().draw(LayoutContext.forCapture(frame, sink, clip));
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
		StringBuilder line = new StringBuilder();
		for (int i = 0; i < 6; i++) {
			for (int turns = 0; turns < 4; turns++) {
				RuinFrame frame = frame(SEED + i * 101L, turns);
				Map<BlockPos, BlockState> whole = draw(frame, false);
				Map<BlockPos, BlockState> pieces = draw(frame, true);
				if (turns == 0) {
					line.append(' ').append(Long.toHexString(hash(whole))).append('/').append(whole.size());
				}
				helper.assertTrue(hash(whole) == hash(pieces) && whole.size() == pieces.size(), "seed " + i + " turns " + turns + ": the blocks differ between one box and chunk by chunk");
			}
		}
		TowerTestSupport.result("sameSeedSamePlan", "hashes=" + line);
		helper.succeed();
	}

	@GameTest
	public void twoSeedsDrawTwoTowers(final GameTestHelper helper) {
		java.util.Set<Long> hashes = new java.util.HashSet<>();
		for (int i = 0; i < 12; i++) {
			hashes.add(hash(draw(frame(SEED + i * 977L, 0), false)));
		}
		helper.assertTrue(hashes.size() == 12, "only " + hashes.size() + " of 12 seeds drew a different tower");
		helper.succeed();
	}

	@GameTest
	public void everyBlockLiesInsideTheBoxAndTheFoundationReachesDownToTheGround(final GameTestHelper helper) {
		for (int turns = 0; turns < 4; turns++) {
			RuinFrame frame = frame(SEED + 7L, turns);
			BoundingBox box = frame.box();
			Map<BlockPos, BlockState> blocks = draw(frame, false);
			for (BlockPos pos : blocks.keySet()) {
				helper.assertTrue(box.isInside(pos), "turns " + turns + ": a block at " + pos + " lies outside the box " + box);
			}
			int below = frame.foundation();
			for (int[] corner : new int[][] {{5, 5}, {15, 5}, {5, 15}, {15, 15}, {10, 10}}) {
				for (int y = -below; y <= -1; y++) {
					BlockState state = blocks.get(frame.toWorld(corner[0], y, corner[1]));
					helper.assertTrue(state != null && state.is(Blocks.STONE_BRICKS), "turns " + turns + ": the foundation at " + corner[0] + "," + y + "," + corner[1] + " is " + state);
				}
			}
			int top = 0;
			for (BlockPos pos : blocks.keySet()) {
				top = Math.max(top, pos.getY() - frame.min().getY());
			}
			helper.assertTrue(top <= WatchtowerPlan.SIZE_Y - 1, "turns " + turns + ": a block stands " + top + " above the floor");
		}
		helper.succeed();
	}

	@GameTest
	public void theBlocksAreThePlanAndTheCoreAndVaultAreOnTheRoof(final GameTestHelper helper) {
		RuinFrame frame = frame(SEED + 11L, 0);
		WatchtowerPlan plan = WatchtowerPlan.derive(frame.seed());
		Map<BlockPos, BlockState> blocks = draw(frame, false);
		int checked = 0;
		for (int y = 0; y < WatchtowerPlan.SIZE_Y; y++) {
			for (int z = 0; z < WatchtowerPlan.SIZE_Z; z++) {
				for (int x = 0; x < WatchtowerPlan.SIZE_X; x++) {
					byte id = plan.cell(x, y, z);
					if (id == WatchtowerPlan.AIR || id == WatchtowerPlan.CORE || id == WatchtowerPlan.VAULT || id == WatchtowerPlan.CHEST) {
						continue;
					}
					BlockState want = WatchtowerLayout.stateOf(id, plan.dir(x, y, z)).rotate(frame.rotation());
					BlockState got = blocks.get(frame.toWorld(x, y, z));
					helper.assertTrue(want.equals(got), "at " + x + "," + y + "," + z + " the plan says " + want + " and the layout drew " + got);
					checked++;
				}
			}
		}
		int[] core = plan.core();
		helper.assertTrue(blocks.get(frame.toWorld(core[0], core[1], core[2])).is(RuinBlocks.CORE), "the core is not on the roof");
		int[] vault = plan.vault();
		helper.assertTrue(blocks.get(frame.toWorld(vault[0], vault[1], vault[2])).is(RuinBlocks.VAULT), "the vault is not on the roof");
		TowerTestSupport.result("layoutIsPlan", "cells=" + checked + " floors=" + plan.floors);
		helper.succeed();
	}

	@GameTest
	public void aDryRunGivesTheMarkersOfThePlan(final GameTestHelper helper) {
		RuinFrame frame = frame(SEED + 13L, 1);
		WatchtowerPlan plan = WatchtowerPlan.derive(frame.seed());
		LayoutContext dry = LayoutContext.forDryRun(frame);
		TowerTestSupport.site().layout().draw(dry);
		Map<String, List<BlockPos>> marks = dry.markers();
		for (String kind : new String[] {WatchtowerPlan.ARCHER, WatchtowerPlan.CROSSBOW, WatchtowerPlan.CAPTAIN}) {
			List<BlockPos> want = new ArrayList<>();
			for (WatchtowerPlan.Marker marker : plan.markers(kind)) {
				want.add(frame.toWorld(marker.x(), marker.y(), marker.z()));
			}
			helper.assertTrue(want.equals(marks.getOrDefault(kind, List.of())) && !want.isEmpty(), kind + " markers " + marks.get(kind) + " want " + want);
		}
		helper.assertTrue(marks.get(RuinMarkers.CORE).size() == 1 && marks.get(RuinMarkers.VAULT).size() == 1, "the dry run lost the core or the vault");
		helper.assertTrue(marks.get(WatchtowerMobs.BOUND_MIN).size() == 1 && marks.get(WatchtowerMobs.BOUND_MAX).size() == 1, "the dry run lost the shaft bounds");
		helper.succeed();
	}

	@GameTest(maxTicks = 400)
	public void drawingTheTowerCostsUnderFiftyMillisecondsAChunkOnAverage(final GameTestHelper helper) {
		RuinSite site = TowerTestSupport.site();
		RuinFrame frame = frame(SEED + 17L, 0);
		BoundingBox box = frame.box();
		List<BoundingBox> chunks = new ArrayList<>();
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				chunks.add(new BoundingBox(cx << 4, box.minY(), cz << 4, (cx << 4) + 15, box.maxY(), (cz << 4) + 15));
			}
		}
		long total = 0L;
		int draws = 0;
		for (int round = 0; round < WARM_UP + TIMED; round++) {
			for (BoundingBox clip : chunks) {
				Map<BlockPos, BlockState> sink = new java.util.HashMap<>();
				long begun = System.nanoTime();
				site.layout().draw(LayoutContext.forCapture(frame, sink, clip));
				long spent = System.nanoTime() - begun;
				if (round >= WARM_UP) {
					total += spent;
					draws++;
				}
			}
		}
		double mean = total / 1.0e6 / draws;
		TowerTestSupport.result("layoutTime", "chunks=" + chunks.size() + " draws=" + draws + " meanMs=" + mean + " budgetMs=" + BUDGET_MS + " scale=21x44x21");
		helper.assertTrue(mean < BUDGET_MS, "the mean draw took " + mean + " ms a chunk, over " + BUDGET_MS);
		helper.succeed();
	}
}
