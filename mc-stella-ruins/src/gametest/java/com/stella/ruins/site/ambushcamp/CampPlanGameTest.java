package com.stella.ruins.site.ambushcamp;

import com.stella.ruins.world.LayoutContext;
import com.stella.ruins.world.RuinBlocks;
import com.stella.ruins.world.RuinFrame;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Acceptance items 1 and 2, and the plan rules from the sketch: the camp stands whole, varies, and is playable. */
public class CampPlanGameTest {
	static final int SEEDS = 24;
	static final int VARIETY_SEEDS = 200;
	static final int PLAYABLE_SEEDS = 3000;
	/** The budget for one chunk of the layout, after a warm-up. */
	static final double CHUNK_BUDGET_MS = 50.0;

	static RuinFrame frame(final long seed, final Rotation rotation) {
		return RuinFrame.of(CampTestSupport.site(), new BlockPos(5000, 64, -3000), rotation, seed);
	}

	static Map<BlockPos, BlockState> draw(final RuinFrame frame, final boolean chunked) {
		Map<BlockPos, BlockState> sink = new TreeMap<>(java.util.Comparator.comparingLong(BlockPos::asLong));
		BoundingBox box = frame.box();
		if (!chunked) {
			CampTestSupport.site().layout().draw(LayoutContext.forCapture(frame, sink, box));
			return sink;
		}
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				BoundingBox clip = new BoundingBox(cx << 4, box.minY(), cz << 4, (cx << 4) + 15, box.maxY(), (cz << 4) + 15);
				CampTestSupport.site().layout().draw(LayoutContext.forCapture(frame, sink, clip));
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

	private static BlockState at(final Map<BlockPos, BlockState> sink, final RuinFrame frame, final int x, final int y, final int z) {
		return sink.getOrDefault(frame.toWorld(x, y, z), Blocks.BEDROCK.defaultBlockState());
	}

	@GameTest(maxTicks = 600)
	public void theCampStandsWholeOverManySeedsAndEveryRotation(final GameTestHelper helper) {
		int tentsSeen = 0;
		int drawn = 0;
		for (int s = 0; s < SEEDS; s++) {
			long seed = 0xAB5C0DE0L + s * 7919L;
			for (Rotation rotation : Rotation.values()) {
				RuinFrame frame = frame(seed, rotation);
				CampPlan plan = CampPlan.derive(RandomSource.create(seed));
				Map<BlockPos, BlockState> sink = draw(frame, false);
				String where = "seed " + seed + " " + rotation;
				helper.assertTrue(plan.tentCount() >= 3 && plan.tentCount() <= 6, where + ": " + plan.tentCount() + " tents");
				helper.assertTrue(at(sink, frame, plan.core().x(), 1, plan.core().z()).is(RuinBlocks.CORE), where + ": the core is missing");
				helper.assertTrue(at(sink, frame, plan.vault().x(), 1, plan.vault().z()).is(RuinBlocks.VAULT), where + ": the vault is missing");
				helper.assertTrue(at(sink, frame, plan.hub().x(), 1, plan.hub().z()).is(Blocks.CAMPFIRE), where + ": the campfire is missing");
				for (CampPlan.Tent tent : plan.tents()) {
					helper.assertTrue(at(sink, frame, tent.cx(), 4, tent.cz()).is(CampLayout.wool(tent.color())), where + ": tent " + tent.id() + " has no ridge");
					helper.assertTrue(at(sink, frame, tent.door().x(), 1, tent.door().z()).isAir() && at(sink, frame, tent.door().x(), 2, tent.door().z()).isAir(), where + ": tent " + tent.id() + " has no door");
					helper.assertTrue(at(sink, frame, tent.marker().x(), 1, tent.marker().z()).isAir() && at(sink, frame, tent.marker().x(), 3, tent.marker().z()).isAir(), where + ": tent " + tent.id() + " has no room for the Chief");
				}
				helper.assertTrue(plan.gaps().size() >= 2 && plan.gaps().size() <= 4, where + ": " + plan.gaps().size() + " gaps");
				for (CampPlan.Gap gap : plan.gaps()) {
					for (CampPlan.Cell cell : gap.cells()) {
						helper.assertTrue(at(sink, frame, cell.x(), 1, cell.z()).isAir() && at(sink, frame, cell.x(), 2, cell.z()).isAir(), where + ": a gap is closed at " + cell);
					}
				}
				int logs = 0;
				for (int x = 0; x < CampPlan.SIZE_X; x++) {
					for (int z = 0; z < CampPlan.SIZE_Z; z++) {
						if (at(sink, frame, x, 1, z).is(Blocks.SPRUCE_LOG)) {
							logs++;
						}
					}
				}
				helper.assertTrue(logs >= 60, where + ": the palisade has only " + logs + " logs at ground level");
				for (CampPlan.Tower tower : plan.towers()) {
					helper.assertTrue(at(sink, frame, tower.cx(), 5, tower.cz()).is(Blocks.SPRUCE_PLANKS), where + ": a tower has no platform");
					helper.assertTrue(at(sink, frame, tower.cx() + 2 * tower.ldx(), 3, tower.cz() + 2 * tower.ldz()).is(Blocks.LADDER), where + ": a tower has no ladder");
				}
				tentsSeen += plan.tentCount();
				drawn++;
			}
		}
		CampTestSupport.result("standsWhole", "camps=" + drawn + " tents=" + tentsSeen);
		helper.assertTrue(drawn == SEEDS * 4, "drew " + drawn);
		helper.succeed();
	}

	@GameTest(maxTicks = 600)
	public void theSameSeedDrawsTheSameBlocksInOneBoxAndChunkByChunk(final GameTestHelper helper) {
		StringBuilder line = new StringBuilder();
		for (int turns = 0; turns < 4; turns++) {
			// A frame that straddles chunk borders on both axes.
			RuinFrame frame = RuinFrame.of(CampTestSupport.site(), new BlockPos(5, 64, 9), Rotation.values()[turns], 0xC0FFEEL + turns);
			Map<BlockPos, BlockState> whole = draw(frame, false);
			Map<BlockPos, BlockState> pieces = draw(frame, true);
			Set<Block> kinds = new HashSet<>();
			whole.values().forEach(state -> kinds.add(state.getBlock()));
			line.append(" turns").append(turns).append("=").append(Long.toHexString(hash(whole))).append("/").append(whole.size());
			helper.assertTrue(kinds.size() >= 15, "the camp drew only " + kinds.size() + " kinds of block");
			helper.assertTrue(whole.size() == pieces.size(), "turns " + turns + ": one box drew " + whole.size() + " blocks, chunk by chunk " + pieces.size());
			helper.assertTrue(hash(whole) == hash(pieces), "turns " + turns + ": the blocks differ between one box and chunk by chunk");
		}
		CampTestSupport.result("sameSeedSamePlan", "hashes=" + line);
		helper.succeed();
	}

	@GameTest
	public void tentCountLayoutGapsAndTheChiefsTentVaryAcrossSeeds(final GameTestHelper helper) {
		Set<Integer> tentCounts = new HashSet<>();
		Set<Integer> gapCounts = new HashSet<>();
		Set<String> signatures = new HashSet<>();
		Set<String> tentLayouts = new HashSet<>();
		Set<String> gapLayouts = new HashSet<>();
		Set<String> colourSets = new HashSet<>();
		Set<String> towerPairs = new HashSet<>();
		Set<String> fires = new HashSet<>();
		Map<Integer, Set<Integer>> chiefTents = new HashMap<>();
		RandomSource chiefRandom = RandomSource.create(5000L);
		for (int s = 0; s < VARIETY_SEEDS; s++) {
			CampPlan plan = CampPlan.derive(RandomSource.create(1000L + s));
			tentCounts.add(plan.tentCount());
			gapCounts.add(plan.gaps().size());
			signatures.add(plan.signature());
			StringBuilder layout = new StringBuilder();
			StringBuilder colours = new StringBuilder();
			for (CampPlan.Tent tent : plan.tents()) {
				layout.append(tent.cx()).append(',').append(tent.cz()).append(';');
				colours.append(tent.color()).append(',');
			}
			tentLayouts.add(layout.toString());
			colourSets.add(colours.toString());
			StringBuilder gaps = new StringBuilder();
			for (CampPlan.Gap gap : plan.gaps()) {
				gaps.append(gap.side()).append(gap.start()).append('w').append(gap.width()).append(gap.main() ? "!" : "").append(';');
			}
			gapLayouts.add(gaps.toString());
			StringBuilder towers = new StringBuilder();
			plan.towers().stream().map(CampPlan.Tower::corner).sorted().forEach(towers::append);
			towerPairs.add(towers.toString());
			fires.add(plan.hub().x() + "," + plan.hub().z());
			int pick = ChiefObjective.pick(chiefRandom, plan.tentCount(), -1);
			chiefTents.computeIfAbsent(plan.tentCount(), k -> new HashSet<>()).add(pick);
		}
		CampTestSupport.result("variety", "seeds=" + VARIETY_SEEDS + " tentCounts=" + tentCounts + " gapCounts=" + gapCounts + " signatures=" + signatures.size()
			+ " tentLayouts=" + tentLayouts.size() + " gapLayouts=" + gapLayouts.size() + " colourSets=" + colourSets.size() + " towerPairs=" + towerPairs
			+ " fires=" + fires.size() + " chiefTents=" + chiefTents);
		helper.assertTrue(tentCounts.equals(Set.of(3, 4, 5, 6)), "tent counts seen: " + tentCounts);
		helper.assertTrue(gapCounts.equals(Set.of(2, 3, 4)), "gap counts seen: " + gapCounts);
		helper.assertTrue(signatures.size() >= 195, "only " + signatures.size() + " distinct camps in " + VARIETY_SEEDS);
		helper.assertTrue(tentLayouts.size() >= 190, "only " + tentLayouts.size() + " distinct tent layouts");
		helper.assertTrue(gapLayouts.size() >= 150, "only " + gapLayouts.size() + " distinct palisade gap layouts");
		helper.assertTrue(colourSets.size() >= 180, "only " + colourSets.size() + " distinct colour sets");
		helper.assertTrue(towerPairs.size() == 6, "tower corner pairs seen: " + towerPairs);
		helper.assertTrue(fires.size() >= 30, "only " + fires.size() + " campfire spots");
		for (Map.Entry<Integer, Set<Integer>> entry : chiefTents.entrySet()) {
			helper.assertTrue(entry.getValue().size() == entry.getKey(), "with " + entry.getKey() + " tents the Chief hid in " + entry.getValue());
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 1200)
	public void everyPlanIsPlayable(final GameTestHelper helper) {
		int redrawn = 0;
		int mostDraws = 0;
		for (int s = 0; s < PLAYABLE_SEEDS; s++) {
			long seed = 77_000L + s;
			CampPlan plan = CampPlan.derive(RandomSource.create(seed));
			String where = "seed " + seed;
			helper.assertTrue(plan.isConnected() && plan.floodsEverything(), where + ": a walk from the trail misses the core, the vault, a door or a ladder");
			redrawn += plan.draws() > 1 ? 1 : 0;
			mostDraws = Math.max(mostDraws, plan.draws());
			helper.assertTrue(plan.towers().size() == 2 && plan.piles().size() >= 2 && plan.rack().size() == 3 && plan.seats().size() == 4, where + ": a part is missing");
			helper.assertTrue(plan.patrol().size() == CampPlan.patrolFor(plan.tentCount()), where + ": " + plan.patrol().size() + " patrol markers for " + plan.tentCount() + " tents");
			helper.assertTrue(CampPlan.patrolFor(plan.tentCount()) + plan.tentCount() - 1 == CampPlan.BANDITS, where + ": the bandits do not add up");
			helper.assertTrue(plan.core().equals(new CampPlan.Cell(plan.hub().x(), plan.hub().z() - 2)), where + ": the core is not at the fire");
			for (CampPlan.Cell cell : plan.patrol()) {
				helper.assertTrue(plan.walkable(cell.x(), cell.z()) && plan.kind(cell.x(), 2, cell.z()) == CampPlan.AIR, where + ": a patrol marker is blocked at " + cell);
			}
			for (CampPlan.Tent a : plan.tents()) {
				helper.assertTrue((a.dx() * (plan.hub().x() - a.cx()) + a.dz() * (plan.hub().z() - a.cz())) > 0, where + ": tent " + a.id() + " turns its back on the fire");
				helper.assertTrue(plan.kind(a.marker().x(), 1, a.marker().z()) == CampPlan.AIR && plan.kind(a.marker().x(), 3, a.marker().z()) == CampPlan.AIR, where + ": tent " + a.id() + " has no standing room");
				for (CampPlan.Tent b : plan.tents()) {
					helper.assertTrue(a == b || Math.abs(a.cx() - b.cx()) >= 2 * CampPlan.TENT_HALF + 2 || Math.abs(a.cz() - b.cz()) >= 2 * CampPlan.TENT_HALF + 2, where + ": tents " + a.id() + " and " + b.id() + " touch");
				}
			}
			for (CampPlan.Gap gap : plan.gaps()) {
				helper.assertTrue(gap.width() >= 2 && gap.width() <= 3, where + ": gap width " + gap.width());
			}
			long mains = plan.gaps().stream().filter(CampPlan.Gap::main).count();
			helper.assertTrue(mains == 1, where + ": " + mains + " trail gaps");
			for (int x = 0; x < CampPlan.SIZE_X; x++) {
				for (int z = 0; z < CampPlan.SIZE_Z; z++) {
					for (int y = 0; y < CampPlan.SIZE_Y; y++) {
						if (y == 0) {
							helper.assertTrue(plan.kind(x, 0, z) >= CampPlan.GRASS && plan.kind(x, 0, z) <= CampPlan.COBBLE, where + ": the floor course has a hole at " + x + "," + z);
						}
					}
				}
			}
		}
		CampTestSupport.result("playable", "seeds=" + PLAYABLE_SEEDS + " redrawn=" + redrawn + " mostDraws=" + mostDraws);
		helper.assertTrue(mostDraws < CampPlan.MAX_DRAWS, "a plan used every draw");
		helper.succeed();
	}

	@GameTest
	public void everyTentHasTheSameShapeAndFurniture(final GameTestHelper helper) {
		for (int s = 0; s < 400; s++) {
			CampPlan plan = CampPlan.derive(RandomSource.create(9000L + s));
			int[] first = null;
			for (CampPlan.Tent tent : plan.tents()) {
				int[] counts = new int[32];
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						for (int y = 1; y <= 5; y++) {
							counts[plan.kind(tent.cx() + dx, y, tent.cz() + dz)]++;
						}
					}
				}
				if (first == null) {
					first = counts;
				}
				helper.assertTrue(java.util.Arrays.equals(first, counts), "seed " + (9000 + s) + ": tent " + tent.id() + " differs from tent 1 in its blocks, so a player could tell the Chief's tent by its look");
			}
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 1200)
	public void theLayoutDrawsAChunkWithinTheBudgetOnAverage(final GameTestHelper helper) {
		for (int i = 0; i < 20; i++) {
			drawOneChunk(i);
		}
		int draws = 200;
		long begun = System.nanoTime();
		for (int i = 0; i < draws; i++) {
			drawOneChunk(100 + i);
		}
		double mean = (System.nanoTime() - begun) / 1_000_000.0 / draws;
		CampTestSupport.result("drawTime", "draws=" + draws + " meanMsPerChunk=" + mean + " budgetMs=" + CHUNK_BUDGET_MS);
		helper.assertTrue(mean < CHUNK_BUDGET_MS, "a chunk took " + mean + " ms on average, over the " + CHUNK_BUDGET_MS + " ms budget");
		helper.succeed();
	}

	private static void drawOneChunk(final int seed) {
		RuinFrame frame = frame(seed, Rotation.NONE);
		BoundingBox box = frame.box();
		BoundingBox clip = new BoundingBox(box.minX(), box.minY(), box.minZ(), box.minX() + 15, box.maxY(), box.minZ() + 15);
		CampTestSupport.site().layout().draw(LayoutContext.forCapture(frame, new HashMap<>(), clip));
	}

	@GameTest
	public void theChiefsTentNeverRepeatsTheLastOneAndCoversEveryOtherTent(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(20261005L);
		for (int tents = 3; tents <= 6; tents++) {
			for (int last = -1; last < tents; last++) {
				Set<Integer> seen = new HashSet<>();
				for (int i = 0; i < 400; i++) {
					int pick = ChiefObjective.pick(random, tents, last);
					helper.assertTrue(pick >= 0 && pick < tents, "pick " + pick + " is no tent of " + tents);
					helper.assertTrue(pick != last, "the Chief hid in tent " + last + " twice");
					seen.add(pick);
				}
				helper.assertTrue(seen.size() == (last < 0 ? tents : tents - 1), "tents " + tents + " last " + last + " covered " + seen);
			}
		}
		helper.succeed();
	}
}
