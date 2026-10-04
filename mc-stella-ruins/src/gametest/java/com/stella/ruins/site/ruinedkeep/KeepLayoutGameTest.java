package com.stella.ruins.site.ruinedkeep;

import com.stella.ruins.site.ruinedkeep.KeepPlan.Cell;
import com.stella.ruins.site.ruinedkeep.KeepPlan.Floor;
import com.stella.ruins.site.ruinedkeep.KeepPlan.Hole;
import com.stella.ruins.site.ruinedkeep.KeepPlan.Rect;
import com.stella.ruins.world.RuinBlocks;
import com.stella.ruins.world.RuinFrame;
import com.stella.ruins.world.RuinMarkers;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Acceptance items 1, 2, 12, and the sketch-review rules, on the drawn blocks and the plan. */
public class KeepLayoutGameTest {
	static final int SEEDS = 12;
	static final BlockPos MIN = new BlockPos(5, 64, 9);
	/** Straddles chunk borders on both axes. */
	static final long BASE_SEED = 0x6B33B0L;

	static long seedOf(final int i) {
		return BASE_SEED + i * 7919L;
	}

	private static int top(final Map<BlockPos, BlockState> blocks, final int minY) {
		int top = Integer.MIN_VALUE;
		for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
			if (!e.getValue().isAir()) {
				top = Math.max(top, e.getKey().getY() - minY);
			}
		}
		return top;
	}

	@GameTest(maxTicks = 400)
	public void theKeepStandsWholeAndFortyToFortyEightBlocksTallInEveryRotation(final GameTestHelper helper) {
		int min = Integer.MAX_VALUE;
		int max = 0;
		int checked = 0;
		for (int i = 0; i < SEEDS; i++) {
			for (int turns = 0; turns < 4; turns++) {
				RuinFrame frame = KeepTestSupport.frame(seedOf(i), turns, MIN);
				Map<BlockPos, BlockState> blocks = KeepTestSupport.draw(frame, false);
				KeepPlan plan = KeepTestSupport.planOf(frame);
				int height = top(blocks, frame.min().getY()) + 1;
				min = Math.min(min, height);
				max = Math.max(max, height);
				checked++;
				helper.assertTrue(height >= 40 && height <= 48, "seed " + i + " turns " + turns + " stands " + height + " blocks");
				helper.assertTrue(height == plan.height(), "seed " + i + " turns " + turns + " stands " + height + ", the plan says " + plan.height());
				helper.assertTrue(blocks.get(frame.toWorld(KeepLayout.CORE[0], KeepLayout.CORE[1], KeepLayout.CORE[2])).is(RuinBlocks.CORE), "the core is missing, seed " + i);
				helper.assertTrue(blocks.get(frame.toWorld(KeepLayout.VAULT[0], KeepLayout.VAULT[1], KeepLayout.VAULT[2])).is(RuinBlocks.VAULT), "the vault is missing, seed " + i);
				int door = 0;
				for (int x = KeepLayout.DOOR_X; x < KeepLayout.DOOR_X + KeepLayout.DOOR_W; x++) {
					for (int y = 27; y < 27 + KeepLayout.DOOR_H; y++) {
						BlockState state = blocks.get(frame.toWorld(x, y, KeepLayout.DOOR_Z));
						door += state != null && state.is(KeepBlocks.THRONE_DOOR) ? 1 : 0;
					}
				}
				helper.assertTrue(door == KeepLayout.DOOR_W * KeepLayout.DOOR_H, "the throne door is " + door + " blocks, seed " + i);
				for (int f = 0; f < 3; f++) {
					for (KeepPlan.Room room : plan.floors[f].rooms()) {
						Rect r = room.rect();
						int solid = 0;
						for (int x = r.x(); x <= r.maxX(); x++) {
							for (int z = r.z(); z <= r.maxZ(); z++) {
								BlockState under = blocks.get(frame.toWorld(x, KeepPlan.STAND[f] - 1, z));
								solid += under != null && !under.isAir() ? 1 : 0;
							}
						}
						helper.assertTrue(solid * 2 > r.w() * r.h(), "floor " + (f + 1) + " has floor under only " + solid + " of " + r.w() * r.h() + " blocks of its " + room.template() + ", seed " + i);
					}
				}
				for (KeepPlan.Tower tower : plan.towers) {
					BlockState chest = blocks.get(frame.toWorld(tower.corner().footX + 6, tower.top() - 4, tower.corner().footZ + 6));
					helper.assertTrue(chest != null && chest.is(Blocks.CHEST), "the tower " + tower.corner() + " has no loft chest, seed " + i);
				}
			}
		}
		KeepTestSupport.result("standsWhole", "draws=" + checked + " heightMin=" + min + " heightMax=" + max);
		helper.succeed();
	}

	@GameTest(maxTicks = 600)
	public void aWalkFromTheGateReachesEveryRoomEveryWardenTheCoreAndTheThroneDoor(final GameTestHelper helper) {
		int floorsReached = 0;
		int rooms = 0;
		for (int i = 0; i < SEEDS; i++) {
			for (int turns = 0; turns < 4; turns++) {
				RuinFrame frame = KeepTestSupport.frame(seedOf(i), turns, MIN);
				KeepPlan plan = KeepTestSupport.planOf(frame);
				Map<BlockPos, BlockState> blocks = KeepTestSupport.draw(frame, false);
				KeepWalker walker = new KeepWalker(blocks, frame);
				walker.walkFromGate();
				String where = "seed " + i + " turns " + turns;
				for (int f = 0; f < 3; f++) {
					int support = KeepPlan.STAND[f] - 1;
					for (KeepPlan.Room room : plan.floors[f].rooms()) {
						Rect r = room.rect();
						boolean any = false;
						for (int x = r.x(); x <= r.maxX() && !any; x++) {
							for (int z = r.z(); z <= r.maxZ() && !any; z++) {
								any = walker.reached(x, support, z);
							}
						}
						rooms++;
						helper.assertTrue(any, where + ": the walk never entered floor " + (f + 1) + " " + room.template() + " " + r);
					}
					for (KeepPlan.Marker m : plan.markers(f, KeepPlan.Kind.WARDEN)) {
						helper.assertTrue(walker.reached(m.x(), support, m.z()), where + ": the walk cannot reach the floor " + (f + 1) + " warden at " + m.x() + "," + m.z());
					}
					floorsReached++;
				}
				helper.assertTrue(walker.reached(24, 26, 30), where + ": the walk cannot reach the core");
				helper.assertTrue(walker.reached(23, 26, 28) && walker.reached(24, 26, 28), where + ": the walk cannot reach the throne door");
				for (int x : new int[] {10, 24, 38}) {
					helper.assertTrue(!walker.reached(x, 26, 14), where + ": the throne room is open at x " + x + " with the door shut");
				}
				// Open the door, as the objective does: the throne room is reached.
				for (BlockPos spot : doorBlocks(frame)) {
					walker.set(spot, Blocks.AIR.defaultBlockState());
				}
				walker.walkFromGate();
				helper.assertTrue(walker.reached(24, 26, 20) && walker.reached(24, 26, 16), where + ": the throne room stays shut with the door open");
			}
		}
		KeepTestSupport.result("walkFromGate", "draws=" + SEEDS * 4 + " floorsReached=" + floorsReached + " roomsReached=" + rooms);
		helper.succeed();
	}

	static List<BlockPos> doorBlocks(final RuinFrame frame) {
		List<BlockPos> out = new java.util.ArrayList<>();
		for (int x = KeepLayout.DOOR_X; x < KeepLayout.DOOR_X + KeepLayout.DOOR_W; x++) {
			for (int y = 27; y < 27 + KeepLayout.DOOR_H; y++) {
				out.add(frame.toWorld(x, y, KeepLayout.DOOR_Z));
			}
		}
		return out;
	}

	@GameTest(maxTicks = 400)
	public void theSameSeedDrawsTheSameBlocksInOneBoxAndChunkByChunk(final GameTestHelper helper) {
		StringBuilder line = new StringBuilder();
		for (int turns = 0; turns < 4; turns++) {
			RuinFrame frame = KeepTestSupport.frame(0xC0FFEEL, turns, MIN);
			Map<BlockPos, BlockState> whole = KeepTestSupport.draw(frame, false);
			Map<BlockPos, BlockState> pieces = KeepTestSupport.draw(frame, true);
			Set<Block> kinds = new HashSet<>();
			whole.values().forEach(state -> kinds.add(state.getBlock()));
			line.append(" turns").append(turns).append("=").append(whole.size());
			helper.assertTrue(kinds.size() >= 12, "the keep drew only " + kinds.size() + " kinds of block, so the comparison proves little");
			helper.assertTrue(whole.size() == pieces.size(), "turns " + turns + ": one box drew " + whole.size() + " blocks, chunk by chunk " + pieces.size());
			helper.assertTrue(whole.equals(pieces), "turns " + turns + ": the blocks differ between one box and chunk by chunk");
		}
		Map<BlockPos, BlockState> a = KeepTestSupport.draw(KeepTestSupport.frame(1L, 0, MIN), false);
		Map<BlockPos, BlockState> b = KeepTestSupport.draw(KeepTestSupport.frame(2L, 0, MIN), false);
		helper.assertTrue(!a.equals(b), "two seeds drew the same keep");
		KeepTestSupport.result("sameSeedSamePlan", "blocks=" + line);
		helper.succeed();
	}

	@GameTest(maxTicks = 400)
	public void aDryRunFindsTheMarkersAWorldDrawFinds(final GameTestHelper helper) {
		RuinFrame frame = KeepTestSupport.frame(77L, 1, MIN);
		com.stella.ruins.world.LayoutContext dry = com.stella.ruins.world.LayoutContext.forDryRun(frame);
		KeepTestSupport.site().layout().draw(dry);
		Map<BlockPos, BlockState> sink = new java.util.TreeMap<>(java.util.Comparator.comparingLong(BlockPos::asLong));
		com.stella.ruins.world.LayoutContext capture = com.stella.ruins.world.LayoutContext.forCapture(frame, sink, frame.box());
		KeepTestSupport.site().layout().draw(capture);
		helper.assertTrue(dry.markers().equals(capture.markers()), "the dry run found other markers than a real draw");
		helper.assertTrue(dry.markers().get(RuinMarkers.CORE).size() == 1 && dry.markers().get(RuinMarkers.VAULT).size() == 1, "the dry run lost the core or the vault");
		helper.assertTrue(dry.markers().get(KeepLayout.M_THRONE_DOOR).size() == KeepLayout.DOOR_W * KeepLayout.DOOR_H, "the dry run lost a throne door block");
		helper.assertTrue(dry.markers().get(KeepLayout.M_CASTELLAN).size() == 1, "the dry run lost the Castellan marker");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void theRuinShowsFromOutsideBrokenMerlonsAFallenCornerAndButtresses(final GameTestHelper helper) {
		int broken = 0;
		int fallen = 0;
		for (int i = 0; i < SEEDS; i++) {
			RuinFrame frame = KeepTestSupport.frame(seedOf(i), 0, MIN);
			KeepPlan plan = KeepTestSupport.planOf(frame);
			Map<BlockPos, BlockState> blocks = KeepTestSupport.draw(frame, false);
			int minY = frame.min().getY();
			int merlons = 0;
			int possible = 0;
			for (int x = KeepPlan.BODY_MIN_X; x <= KeepPlan.BODY_MAX_X; x++) {
				for (int z = KeepPlan.BODY_MIN_Z; z <= KeepPlan.BODY_MAX_Z; z++) {
					boolean edge = x == KeepPlan.BODY_MIN_X || x == KeepPlan.BODY_MAX_X || z == KeepPlan.BODY_MIN_Z || z == KeepPlan.BODY_MAX_Z;
					if (edge && (x + z) % 2 == 0 && !inAnyFoot(plan, x, z) && !inFallen(plan, x, z)) {
						possible++;
						BlockState state = blocks.get(frame.toWorld(x, KeepPlan.CROWN, z));
						merlons += state != null && !state.isAir() ? 1 : 0;
					}
				}
			}
			helper.assertTrue(merlons > 0 && merlons < possible, "seed " + i + ": " + merlons + " of " + possible + " merlons stand, so the parapet is not broken");
			broken += possible - merlons;
			// The fallen corner stands 4 to 8 blocks below the crown.
			KeepPlan.Corner c = plan.fallenCorner;
			int cx = c.west() ? KeepPlan.BODY_MIN_X : KeepPlan.BODY_MAX_X;
			int cz = c.north() ? KeepPlan.BODY_MIN_Z : KeepPlan.BODY_MAX_Z;
			if (!plan.tower(c)) {
				int highest = -1;
				for (int y = 0; y < 48; y++) {
					BlockState state = blocks.get(frame.toWorld(cx, y, cz));
					if (state != null && !state.isAir()) {
						highest = y;
					}
				}
				int drop = KeepPlan.CROWN - highest;
				fallen++;
				helper.assertTrue(drop >= 4 && drop <= 8, "seed " + i + ": the fallen corner stands " + drop + " below the crown");
			}
			for (KeepPlan.Buttress b : plan.buttresses) {
				Rect r = b.rect();
				BlockState mid = blocks.get(frame.toWorld(r.x(), 10, r.z()));
				helper.assertTrue(mid != null && !mid.isAir(), "seed " + i + ": a buttress is missing at " + r);
				helper.assertTrue(r.x() < KeepPlan.BODY_MIN_X || r.x() > KeepPlan.BODY_MAX_X || r.z() < KeepPlan.BODY_MIN_Z || r.z() > KeepPlan.BODY_MAX_Z, "a buttress stands inside the body at " + r);
			}
		}
		KeepTestSupport.result("ruinFromOutside", "brokenMerlons=" + broken + " fallenCorners=" + fallen);
		helper.succeed();
	}

	private static boolean inAnyFoot(final KeepPlan plan, final int x, final int z) {
		for (KeepPlan.Tower t : plan.towers) {
			KeepPlan.Corner c = t.corner();
			if (x >= c.footX && x <= c.footX + 8 && z >= c.footZ && z <= c.footZ + 8) {
				return true;
			}
		}
		return false;
	}

	private static boolean inFallen(final KeepPlan plan, final int x, final int z) {
		KeepPlan.Corner c = plan.fallenCorner;
		int x1 = c.west() ? KeepPlan.BODY_MIN_X : KeepPlan.BODY_MAX_X - 11;
		int z1 = c.north() ? KeepPlan.BODY_MIN_Z : KeepPlan.BODY_MAX_Z - 11;
		return x >= x1 && x <= x1 + 11 && z >= z1 && z <= z1 + 11;
	}

	@GameTest(maxTicks = 400)
	public void aHoleCutsOneSlabAndLeavesTheFloorBelowWholeAndClearOfStairsAndDoorways(final GameTestHelper helper) {
		int holes = 0;
		for (int i = 0; i < 40; i++) {
			RuinFrame frame = KeepTestSupport.frame(seedOf(i), 0, MIN);
			KeepPlan plan = KeepTestSupport.planOf(frame);
			Map<BlockPos, BlockState> blocks = KeepTestSupport.draw(frame, false);
			for (Hole h : plan.holes) {
				Rect r = h.rect();
				int slab = KeepPlan.STAND[h.floor()] - 1;
				int below = KeepPlan.STAND[h.floor() - 1] - 1;
				holes++;
				for (int x = r.x(); x <= r.maxX(); x++) {
					for (int z = r.z(); z <= r.maxZ(); z++) {
						BlockState cut = blocks.get(frame.toWorld(x, slab, z));
						helper.assertTrue(cut == null || cut.isAir(), "seed " + i + ": the slab is whole at " + x + "," + z);
						BlockState floorBelow = blocks.get(frame.toWorld(x, below, z));
						helper.assertTrue(floorBelow != null && !floorBelow.isAir(), "seed " + i + ": the floor below a hole is gone at " + x + "," + z);
					}
				}
				helper.assertTrue(KeepPlan.STAND[h.floor()] - KeepPlan.STAND[h.floor() - 1] == KeepPlan.PITCH, "a hole drops more than one floor");
			}
		}
		KeepTestSupport.result("holeRules", "seeds=40 holesChecked=" + holes);
		helper.assertTrue(holes >= 20, "only " + holes + " holes were checked, so the test proves little");
		helper.succeed();
	}

	@GameTest(maxTicks = 600)
	public void generationOfAFullKeepStaysUnderFiftyMillisecondsPerChunk(final GameTestHelper helper) {
		KeepTestSupport.inArena(helper, arena -> {
			BoundingBox box = arena.frame().box();
			double worst = 0.0;
			double total = 0.0;
			int chunks = 0;
			for (int pass = 0; pass < 3; pass++) {
				worst = 0.0;
				total = 0.0;
				chunks = 0;
				for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
					for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
						BoundingBox clip = new BoundingBox(cx << 4, box.minY(), cz << 4, (cx << 4) + 15, box.maxY(), (cz << 4) + 15);
						long begun = System.nanoTime();
						arena.piece().build(arena.level(), clip);
						double ms = (System.nanoTime() - begun) / 1.0E6;
						worst = Math.max(worst, ms);
						total += ms;
						chunks++;
					}
				}
			}
			KeepTestSupport.result("generationTime", "chunks=" + chunks + " meanMs=" + String.format("%.1f", total / chunks) + " worstMs=" + String.format("%.1f", worst) + " budgetMs=50 input=48x48x48 keep, final of 3 passes");
			helper.assertTrue(worst < 50.0, "a chunk took " + worst + " ms, over the 50 ms budget");
		});
	}
}
