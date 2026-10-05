package com.stella.ruins.site.spidernest;

import com.stella.ruins.world.LayoutContext;
import com.stella.ruins.world.Placement;
import com.stella.ruins.world.RuinBlocks;
import com.stella.ruins.world.RuinFrame;
import com.stella.ruins.world.RuinMarkers;
import com.stella.ruins.world.RuinSite;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.util.RandomSource;

/** Items 1 and 2, and the sketch review additions that live in the plan: the mouth, the lichen, the lane. */
public class NestPlanGameTest {
	static final int SEEDS = 24;
	static final int VARIETY_SEEDS = 200;
	/** What the webs may add to a greedy walk that the plan judged on the web-free grid. Measured over 200 seeds at 3.7 s. */
	static final double WEB_SLACK = 5.0;
	static final BlockState STONE = Blocks.STONE.defaultBlockState();

	/** Draws the nest into a map over solid stone, so the tunnel lining and the shell pass have rock to work on. */
	static Map<BlockPos, BlockState> capture(final RuinFrame frame, final BoundingBox clip) {
		Map<BlockPos, BlockState> sink = new HashMap<>();
		BoundingBox box = frame.box();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int y = box.minY(); y <= box.maxY(); y++) {
				for (int z = box.minZ(); z <= box.maxZ(); z++) {
					sink.put(new BlockPos(x, y, z), STONE);
				}
			}
		}
		NestTestSupport.site().layout().draw(LayoutContext.forCapture(frame, sink, clip));
		return sink;
	}

	static RuinFrame frame(final long seed, final Rotation rotation) {
		return RuinFrame.of(NestTestSupport.site(), new BlockPos(5000, 40, -3000), rotation, seed);
	}

	static BlockState at(final Map<BlockPos, BlockState> sink, final RuinFrame frame, final int x, final int y, final int z) {
		return sink.get(frame.toWorld(x, y, z + NestPlan.EXTENSION));
	}

	@GameTest(maxTicks = 1200)
	public void theNestStandsWholeOverManySeedsAndEveryRotation(final GameTestHelper helper) {
		int sacsSeen = 0;
		int drawn = 0;
		for (int s = 0; s < SEEDS; s++) {
			long seed = 0x5B1DE70L + s * 7919L;
			NestPlan plan = NestPlan.of(seed);
			for (Rotation rotation : Rotation.values()) {
				RuinFrame frame = frame(seed, rotation);
				Map<BlockPos, BlockState> sink = capture(frame, frame.box());
				String where = "seed " + seed + " " + rotation;
				helper.assertTrue(plan.sacs().size() >= 6 && plan.sacs().size() <= 10, where + ": " + plan.sacs().size() + " sacs");
				NestPlan.Spot core = plan.core();
				NestPlan.Spot vault = plan.vault();
				helper.assertTrue(at(sink, frame, core.x(), core.y(), core.z()).is(RuinBlocks.CORE), where + ": the core is missing");
				helper.assertTrue(at(sink, frame, vault.x(), vault.y(), vault.z()).is(RuinBlocks.VAULT), where + ": the vault is missing");
				int sacBlocks = 0;
				for (BlockState state : sink.values()) {
					if (state.is(EggSacBlock.BLOCK)) {
						sacBlocks++;
					}
				}
				helper.assertTrue(sacBlocks == plan.sacs().size(), where + ": " + sacBlocks + " sac blocks for " + plan.sacs().size() + " planned");
				for (NestPlan.Sac sac : plan.sacs()) {
					helper.assertTrue(at(sink, frame, sac.x, sac.y, sac.z).is(EggSacBlock.BLOCK), where + ": sac " + sac.id + " is missing");
				}
				for (int x = plan.mouthX() - 1; x <= plan.mouthX() + 1; x++) {
					for (int y = NestPlan.TUNNEL_FLOOR; y < NestPlan.TUNNEL_FLOOR + NestPlan.TUNNEL_HEIGHT; y++) {
						for (int z = NestPlan.TUNNEL_START; z < 0; z++) {
							helper.assertTrue(at(sink, frame, x, y, z).isAir(), where + ": the mouth tunnel is blocked at " + x + "," + y + "," + z);
						}
					}
				}
				helper.assertTrue(at(sink, frame, plan.pitX(), plan.pitFloor() + 3, plan.pitZ()).isAir(), where + ": the brood pit is not open");
				sacsSeen += plan.sacs().size();
				drawn++;
			}
		}
		NestTestSupport.result("standsWhole", "nests=" + drawn + " sacs=" + sacsSeen);
		helper.assertTrue(drawn == SEEDS * 4, "drew " + drawn);
		helper.succeed();
	}

	@GameTest(maxTicks = 1200)
	public void everyChunkDrawsTheSameNestAsOneWholeBox(final GameTestHelper helper) {
		for (long seed : new long[] {11L, 4242L, 90_001L}) {
			for (Rotation rotation : new Rotation[] {Rotation.NONE, Rotation.CLOCKWISE_90}) {
				RuinFrame frame = frame(seed, rotation);
				BoundingBox box = frame.box();
				Map<BlockPos, BlockState> whole = capture(frame, box);
				Map<BlockPos, BlockState> pieces = new HashMap<>();
				for (int x = box.minX(); x <= box.maxX(); x++) {
					for (int y = box.minY(); y <= box.maxY(); y++) {
						for (int z = box.minZ(); z <= box.maxZ(); z++) {
							pieces.put(new BlockPos(x, y, z), STONE);
						}
					}
				}
				for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
					for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
						BoundingBox chunk = new BoundingBox(cx << 4, box.minY(), cz << 4, (cx << 4) + 15, box.maxY(), (cz << 4) + 15);
						NestTestSupport.site().layout().draw(LayoutContext.forCapture(frame, pieces, chunk));
					}
				}
				int differing = 0;
				for (Map.Entry<BlockPos, BlockState> entry : whole.entrySet()) {
					if (!entry.getValue().equals(pieces.get(entry.getKey()))) {
						differing++;
					}
				}
				NestTestSupport.result("chunkwise", "seed=" + seed + " " + rotation + " cells=" + whole.size() + " differing=" + differing);
				helper.assertTrue(differing == 0, "seed " + seed + " " + rotation + ": " + differing + " cells differ between chunk-by-chunk and one box");
			}
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 1200)
	public void theMouthTunnelLiningHugsRockAndSkipsCaveAir(final GameTestHelper helper) {
		RuinFrame frame = frame(0x77AA, Rotation.NONE);
		NestPlan plan = NestPlan.of(0x77AA);
		int z = NestPlan.TUNNEL_START + 4;
		Map<BlockPos, BlockState> rock = capture(frame, frame.box());
		BlockState lining = at(rock, frame, plan.mouthX(), NestPlan.TUNNEL_FLOOR + NestPlan.TUNNEL_HEIGHT, z);
		helper.assertTrue(lining.is(Blocks.STONE) || lining.is(Blocks.DEEPSLATE), "no lining over rock above the tunnel: " + lining);
		Map<BlockPos, BlockState> open = new HashMap<>();
		NestTestSupport.site().layout().draw(LayoutContext.forCapture(frame, open, frame.box()));
		helper.assertTrue(!open.containsKey(frame.toWorld(plan.mouthX(), NestPlan.TUNNEL_FLOOR + NestPlan.TUNNEL_HEIGHT, z + NestPlan.EXTENSION)), "the lining stood in open cave air");
		helper.assertTrue(open.get(frame.toWorld(plan.mouthX(), NestPlan.TUNNEL_FLOOR, z + NestPlan.EXTENSION)).isAir(), "the tunnel floor cell was not air");
		helper.assertTrue(NestPlan.EXTENSION <= 16 && NestPlan.TUNNEL_START == -16, "the tunnel is longer than 16 blocks");
		NestTestSupport.result("tunnelLining", "overRock=" + lining + " inAir=absent length=" + NestPlan.EXTENSION);
		helper.succeed();
	}

	@GameTest(maxTicks = 1200)
	public void theShellSealsFluidOutOfTheWalls(final GameTestHelper helper) {
		RuinFrame frame = frame(0x5E11, Rotation.NONE);
		NestPlan plan = NestPlan.of(0x5E11);
		Map<BlockPos, BlockState> sink = new HashMap<>();
		BoundingBox box = frame.box();
		int wet = 0;
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int y = box.minY(); y <= box.maxY(); y++) {
				for (int z = box.minZ(); z <= box.maxZ(); z++) {
					sink.put(new BlockPos(x, y, z), Blocks.WATER.defaultBlockState());
				}
			}
		}
		NestTestSupport.site().layout().draw(LayoutContext.forCapture(frame, sink, box));
		for (int z = 0; z < NestPlan.NEST_Z; z++) {
			for (int x = 0; x < NestPlan.SIZE_X; x++) {
				for (int y = 0; y < NestPlan.SIZE_Y; y++) {
					if (plan.cell(x, y, z) == NestPlan.AIR) {
						for (int[] d : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}}) {
							int nx = x + d[0];
							int ny = y + d[1];
							int nz = z + d[2];
							if (nx >= 0 && nx < NestPlan.SIZE_X && ny >= 0 && ny < NestPlan.SIZE_Y && nz >= 0 && nz < NestPlan.NEST_Z && plan.cell(nx, ny, nz) == NestPlan.ROCK) {
								BlockState wall = at(sink, frame, nx, ny, nz);
								if (!wall.getFluidState().isEmpty()) {
									wet++;
								}
							}
						}
					}
				}
			}
		}
		NestTestSupport.result("shell", "wetWallCells=" + wet);
		helper.assertTrue(wet == 0, wet + " wall cells next to the cave still hold water");
		helper.succeed();
	}

	@GameTest(maxTicks = 1200)
	public void everyPlanOverTwoHundredSeedsPassesTheWholeChecklist(final GameTestHelper helper) {
		List<String> failures = new ArrayList<>();
		int retried = 0;
		double slowest = 0;
		double greedyWorst = 0;
		for (int s = 1; s <= VARIETY_SEEDS; s++) {
			NestPlan plan = NestPlan.of(s);
			failures.addAll(NestPlanChecks.check(plan));
			NestPlan again = NestPlan.withWebPercent(s, plan.webPercent);
			if (again.gridHash() != plan.gridHash()) {
				failures.add("seed " + s + ": the same seed drew two grids");
			}
			retried += plan.pickAttempts() > 1 ? 1 : 0;
			slowest = Math.max(slowest, plan.route().timed());
			greedyWorst = Math.max(greedyWorst, plan.greedy().timed());
		}
		NestTestSupport.result("checklist", "seeds=" + VARIETY_SEEDS + " failures=" + failures.size() + " repicked=" + retried + " expertWorst=" + slowest + " greedyWorst=" + greedyWorst);
		helper.assertTrue(failures.isEmpty(), failures.size() + " failures, first: " + failures.stream().limit(5).toList());
		helper.assertTrue(greedyWorst <= NestPlan.GREEDY_BUDGET + WEB_SLACK, "the greedy walk reached " + greedyWorst + " s, over the budget " + NestPlan.GREEDY_BUDGET + " plus the web slack");
		helper.assertTrue(retried > 0, "no plan picked its sacs again, so the walk budget did nothing");
		helper.succeed();
	}

	@GameTest(maxTicks = 1200)
	public void chamberShapeWebDensityAndSacPositionsVaryAcrossSeeds(final GameTestHelper helper) {
		Set<Integer> chamberCounts = new HashSet<>();
		Set<Integer> sacCounts = new HashSet<>();
		Set<String> signatures = new HashSet<>();
		Set<String> sacSets = new HashSet<>();
		Set<Integer> webRolls = new HashSet<>();
		Set<Integer> floors = new HashSet<>();
		double lowest = 1.0;
		double highest = 0.0;
		for (int s = 0; s < VARIETY_SEEDS; s++) {
			NestPlan plan = NestPlan.of(1000L + s);
			chamberCounts.add(plan.blobCount);
			sacCounts.add(plan.sacs().size());
			signatures.add(plan.signature());
			webRolls.add(plan.webPercent);
			floors.add(plan.heartFloor);
			StringBuilder positions = new StringBuilder();
			for (NestPlan.Sac sac : plan.sacs()) {
				positions.append(sac.x).append(',').append(sac.y).append(',').append(sac.z).append(sac.ceiling ? 'c' : 'w').append(';');
			}
			sacSets.add(positions.toString());
			lowest = Math.min(lowest, plan.webRatio());
			highest = Math.max(highest, plan.webRatio());
		}
		NestTestSupport.result("variety", "seeds=" + VARIETY_SEEDS + " chambers=" + chamberCounts + " sacCounts=" + sacCounts + " signatures=" + signatures.size()
			+ " sacSets=" + sacSets.size() + " webRolls=" + webRolls.size() + " webShare=" + lowest + ".." + highest);
		helper.assertTrue(chamberCounts.equals(Set.of(3, 4, 5)), "chamber counts seen: " + chamberCounts);
		helper.assertTrue(sacCounts.equals(Set.of(6, 7, 8, 9, 10)), "sac counts seen: " + sacCounts);
		helper.assertTrue(signatures.size() >= 190, "only " + signatures.size() + " distinct nests in " + VARIETY_SEEDS);
		helper.assertTrue(sacSets.size() >= 190, "only " + sacSets.size() + " distinct sac layouts");
		helper.assertTrue(webRolls.size() >= 18, "only " + webRolls.size() + " distinct web rolls");
		helper.assertTrue(lowest >= 0.14 && highest <= 0.36, "web share ran " + lowest + " to " + highest + ", outside 15 to 35 percent");
		helper.assertTrue(floors.equals(Set.of(2, 3)), "heart floors seen: " + floors);
		helper.succeed();
	}

	@GameTest(maxTicks = 1200)
	public void theWebShareFollowsTheRollAndTheLowShareIsInsideTheHighShare(final GameTestHelper helper) {
		for (long seed : new long[] {3L, 77L, 1234L}) {
			NestPlan low = NestPlan.withWebPercent(seed, 15);
			NestPlan high = NestPlan.withWebPercent(seed, 35);
			helper.assertTrue(Math.abs(low.webRatio() - 0.15) < 0.01 && Math.abs(high.webRatio() - 0.35) < 0.01, "shares " + low.webRatio() + " and " + high.webRatio());
			helper.assertTrue(low.webCells() < high.webCells(), "the high roll holds fewer webs than the low");
			int outside = 0;
			for (int y = 0; y < NestPlan.SIZE_Y; y++) {
				for (int z = 0; z < NestPlan.NEST_Z; z++) {
					for (int x = 0; x < NestPlan.SIZE_X; x++) {
						if (low.cell(x, y, z) == NestPlan.WEB && high.cell(x, y, z) != NestPlan.WEB) {
							outside++;
						}
					}
				}
			}
			helper.assertTrue(outside == 0, "seed " + seed + ": " + outside + " webs of the 15 percent roll are missing from the 35 percent roll");
			helper.assertTrue(NestPlanChecks.check(low).isEmpty() && NestPlanChecks.check(high).isEmpty(), "a fixed web share broke the plan: " + NestPlanChecks.check(high));
		}
		helper.succeed();
	}

	@GameTest
	public void theLayoutHasFourToEightLichenPatchesAndTheSacsCarryTheirLight(final GameTestHelper helper) {
		Set<Integer> patchCounts = new HashSet<>();
		for (long seed = 1; seed <= 200; seed++) {
			NestPlan plan = NestPlan.of(seed);
			for (NestPlan.Lichen cell : plan.lichens()) {
				helper.assertTrue(plan.cell(cell.x(), cell.y(), cell.z()) == NestPlan.AIR, "seed " + seed + ": lichen in a filled cell");
			}
			helper.assertTrue(plan.lichenPatches() >= 4 && plan.lichenPatches() <= 8, "seed " + seed + ": " + plan.lichenPatches() + " lichen patches");
			helper.assertTrue(plan.lichens().size() >= plan.lichenPatches(), "seed " + seed + ": fewer lichen cells than patches");
			patchCounts.add(plan.lichenPatches());
		}
		helper.assertTrue(patchCounts.size() >= 4, "patch counts seen: " + patchCounts);
		helper.assertTrue(EggSacBlock.BLOCK.defaultBlockState().getLightEmission() == 4, "an egg sac does not glow at level 4");
		NestTestSupport.result("lichen", "patchCounts=" + patchCounts + " sacLight=" + EggSacBlock.BLOCK.defaultBlockState().getLightEmission());
		helper.succeed();
	}

	@GameTest
	public void theSiteKeepsItsCoverBoundsAndTheMouthWithinTheBox(final GameTestHelper helper) {
		RuinSite site = NestTestSupport.site();
		helper.assertTrue(site.placement().equals(Placement.cave(-32, 48, 8)), "the placement is " + site.placement());
		helper.assertTrue(site.sizeX() == 30 && site.sizeY() == 14 && site.sizeZ() == 30 + 16, "the bounds are " + site.sizeX() + "x" + site.sizeY() + "x" + site.sizeZ());
		int accepted = 0;
		for (int surface : new int[] {10, 20, 30, 60, 120}) {
			for (int seed = 0; seed < 40; seed++) {
				Optional<BlockPos> floor = site.placement().choose(flat(surface), RandomSource.create(seed), 0, 0, 30, 14, 46);
				if (floor.isPresent()) {
					accepted++;
					int roof = floor.get().getY() + site.sizeY() - 1;
					helper.assertTrue(surface - roof >= 8, "surface " + surface + " roof " + roof + " leaves " + (surface - roof) + " blocks of cover");
				}
			}
		}
		helper.assertTrue(accepted > 20, "only " + accepted + " tries were accepted");
		helper.assertTrue(site.placement().choose(flat(-25), RandomSource.create(1), 0, 0, 30, 14, 46).isEmpty(), "a shallow surface took a nest");
		helper.succeed();
	}

	private static Placement.Probe flat(final int surface) {
		return new Placement.Probe() {
			@Override
			public int groundY(final int x, final int z) {
				return surface;
			}

			@Override
			public int surfaceY(final int x, final int z) {
				return surface;
			}

			@Override
			public BlockState stateAt(final int x, final int y, final int z) {
				return Blocks.STONE.defaultBlockState();
			}

			@Override
			public int minY() {
				return -64;
			}

			@Override
			public int maxY() {
				return 320;
			}
		};
	}

	@GameTest
	public void theMarkersMatchThePlan(final GameTestHelper helper) {
		for (int s = 0; s < 20; s++) {
			long seed = 6150L + s;
			RuinFrame frame = frame(seed, Rotation.CLOCKWISE_90);
			NestPlan plan = NestPlan.of(seed);
			LayoutContext dry = LayoutContext.forDryRun(frame);
			NestTestSupport.site().layout().draw(dry);
			Map<String, List<BlockPos>> marks = dry.markers();
			helper.assertTrue(marks.get(NestMarkers.SAC).size() == plan.sacs().size(), "sac markers: " + marks.get(NestMarkers.SAC).size());
			helper.assertTrue(marks.get(RuinMarkers.SPAWN).size() == NestPlan.WEAVERS, "weaver markers: " + marks.get(RuinMarkers.SPAWN).size());
			helper.assertTrue(marks.get(NestMarkers.CRAWLER).size() == NestPlan.CRAWLERS, "crawler markers: " + marks.get(NestMarkers.CRAWLER).size());
			helper.assertTrue(marks.get(NestMarkers.MATRIARCH).size() == 1 && marks.get(NestMarkers.ENTRANCE).size() == 1, "matriarch or entrance marker count");
			helper.assertTrue(marks.get(RuinMarkers.CORE).size() == 1 && marks.get(RuinMarkers.VAULT).size() == 1, "core or vault marker count");
			for (NestPlan.Sac sac : plan.sacs()) {
				helper.assertTrue(marks.get(NestMarkers.SAC).get(sac.id - 1).equals(frame.toWorld(sac.x, sac.y, sac.z + NestPlan.EXTENSION)), "sac marker " + sac.id + " is off its plan");
			}
			NestPlan.Spot mother = plan.matriarch();
			helper.assertTrue(marks.get(NestMarkers.MATRIARCH).get(0).equals(frame.toWorld(mother.x(), mother.y(), mother.z() + NestPlan.EXTENSION)), "matriarch marker is off the pit");
		}
		helper.succeed();
	}
}
