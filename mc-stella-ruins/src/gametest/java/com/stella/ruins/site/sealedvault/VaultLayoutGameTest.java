package com.stella.ruins.site.sealedvault;

import com.stella.ruins.site.sealedvault.VaultPlan.Cell;
import com.stella.ruins.site.sealedvault.VaultPlan.Trap;
import com.stella.ruins.site.sealedvault.VaultPlan.TrapKind;
import com.stella.ruins.site.sealedvault.VaultTestSupport.Capture;
import com.stella.ruins.site.sealedvault.VaultTestSupport.Walker;
import com.stella.ruins.world.Placement;
import com.stella.ruins.world.RuinBlocks;
import com.stella.ruins.world.RuinMarkers;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/** Item 1 and item 3 at the block level: the vault stands whole and every room can be walked to. */
public class VaultLayoutGameTest {
	static final int SEEDS = 12;

	private static void checkWhole(final GameTestHelper helper, final long seed, final Rotation rotation) {
		Capture capture = VaultTestSupport.capture(seed, rotation);
		VaultPlan plan = VaultTestSupport.plan(seed);
		String where = "seed " + seed + " " + rotation;
		helper.assertTrue(capture.at(capture.markers().get(RuinMarkers.CORE).get(0)).is(RuinBlocks.CORE), where + ": the core is missing");
		helper.assertTrue(capture.at(capture.markers().get(RuinMarkers.VAULT).get(0)).is(RuinBlocks.VAULT), where + ": the vault is missing");
		List<BlockPos> seals = capture.markers().get(VaultMarkers.SEAL);
		helper.assertTrue(seals.size() == VaultPlan.SEALS, where + ": " + seals.size() + " seals");
		for (BlockPos seal : seals) {
			helper.assertTrue(capture.at(seal).is(VaultBlocks.SEAL) && capture.at(seal.below()).is(Blocks.POLISHED_BLACKSTONE), where + ": a seal does not sit on its plinth at " + seal);
		}
		BlockPos entrance = capture.markers().get(VaultMarkers.ENTRANCE).get(0);
		Walker walker = new Walker(capture, 2);
		Set<BlockPos> reach = walker.reach(entrance);
		helper.assertTrue(reach.contains(entrance), where + ": the entrance is not standable");
		int rooms = 0;
		for (int level = 0; level < VaultPlan.LEVELS; level++) {
			for (int c = 0; c < VaultPlan.CELLS; c++) {
				if (plan.isOpen(level, c)) {
					helper.assertTrue(walker.reaches(reach, level, c), where + ": level " + level + " room " + c + " cannot be walked to");
					rooms++;
				}
			}
		}
		helper.assertTrue(walker.reaches(reach, 0, VaultPlan.CENTER_CELL), where + ": the inner vault cannot be walked to");
		VaultTestSupport.result("vaultWhole", where + " rooms=" + (rooms + 1) + " reachSpots=" + reach.size());
	}

	@GameTest(maxTicks = 1200)
	public void theVaultStandsWholeOverTwelveSeedsAndEveryRotation(final GameTestHelper helper) {
		for (int n = 0; n < SEEDS; n++) {
			checkWhole(helper, VaultPlanGameTest.seedOf(n), Rotation.values()[n % 4]);
		}
		for (Rotation rotation : Rotation.values()) {
			checkWhole(helper, VaultPlanGameTest.seedOf(0), rotation);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 1200)
	public void aTallWalkerFromTheLairReachesTheCoreAndEveryCandidateThroughTheStairs(final GameTestHelper helper) {
		for (int n = 0; n < SEEDS; n++) {
			long seed = VaultPlanGameTest.seedOf(n);
			Rotation rotation = Rotation.values()[n % 4];
			Capture capture = VaultTestSupport.capture(seed, rotation);
			VaultPlan plan = VaultTestSupport.plan(seed);
			Walker tall = new Walker(capture, 5);
			BlockPos lair = capture.markers().get(VaultMarkers.WARDEN).get(0);
			Set<BlockPos> reach = tall.reach(lair);
			String where = "seed " + seed + " " + rotation;
			helper.assertTrue(reach.contains(lair), where + ": the lair is not standable for a tall walker");
			helper.assertTrue(tall.reaches(reach, 1, VaultPlan.CENTER_CELL), where + ": the Warden cannot reach the core room");
			for (Cell candidate : plan.candidates()) {
				helper.assertTrue(tall.reaches(reach, candidate.level(), candidate.cell()), where + ": the Warden cannot reach " + candidate);
			}
			for (int stair : plan.stairs()) {
				helper.assertTrue(tall.reaches(reach, 1, stair) && tall.reaches(reach, 0, stair), where + ": the Warden cannot use stair " + stair);
			}
			VaultTestSupport.result("wardenReach", where + " spots=" + reach.size());
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 1200)
	public void aBodyOnRealStairShapesWalksFromTheEntranceDownToTheInnerVault(final GameTestHelper helper) {
		for (int n = 0; n < SEEDS; n++) {
			long seed = VaultPlanGameTest.seedOf(n);
			Rotation rotation = Rotation.values()[n % 4];
			Capture capture = VaultTestSupport.capture(seed, rotation);
			StairWalker walker = new StairWalker(capture);
			StairWalker.Spot start = walker.spotAt(capture.markers().get(VaultMarkers.ENTRANCE).get(0));
			String where = "seed " + seed + " " + rotation;
			helper.assertTrue(start != null, where + ": nobody can stand at the entrance");
			Set<StairWalker.Spot> reach = walker.reach(start);
			helper.assertTrue(walker.reachesRoom(reach, 1, VaultPlan.CENTER_CELL), where + ": the core room cannot be walked to");
			helper.assertTrue(walker.reachesRoom(reach, 0, VaultPlan.CENTER_CELL), where + ": the inner vault cannot be walked to on real stair shapes");
			VaultTestSupport.result("innerVaultWalk", where + " spots=" + reach.size());
		}
		helper.succeed();
	}

	@GameTest
	public void theCavePlacementKeepsTheBottomAboveMinusSixtyAndTheTopUnderMinusSixteen(final GameTestHelper helper) {
		Placement placement = VaultTestSupport.site().placement();
		Placement.Probe probe = new Placement.Probe() {
			@Override
			public int groundY(final int x, final int z) {
				return 70;
			}

			@Override
			public int surfaceY(final int x, final int z) {
				return 70;
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
		RandomSource random = RandomSource.create(20261005L);
		int lowest = Integer.MAX_VALUE;
		int highest = Integer.MIN_VALUE;
		for (int i = 0; i < 400; i++) {
			Optional<BlockPos> spot = placement.choose(probe, random, 1000, 1000, VaultPlan.SIZE_X, VaultPlan.SIZE_Y, VaultPlan.SIZE_Z);
			helper.assertTrue(spot.isPresent(), "the cave placement refused a deep, dry spot");
			int bottom = spot.get().getY();
			int top = bottom + VaultPlan.SIZE_Y - 1;
			lowest = Math.min(lowest, bottom);
			highest = Math.max(highest, top);
			helper.assertTrue(bottom > -60, "the vault bottom is " + bottom + ", not above -60");
			helper.assertTrue(top <= SealedVaultSite.MAX_Y, "the vault top is " + top + ", above " + SealedVaultSite.MAX_Y);
		}
		VaultTestSupport.result("placement", "bottomMin=" + lowest + " topMax=" + highest + " bounds=" + VaultPlan.SIZE_X + "x" + VaultPlan.SIZE_Y + "x" + VaultPlan.SIZE_Z);
		helper.assertTrue(lowest >= SealedVaultSite.MIN_Y, "the lowest bottom " + lowest + " is under the site minimum");
		helper.succeed();
	}

	@GameTest
	public void theSkinRoundEveryRoomIsVaultBlocksAndTheBuriedMassStaysRock(final GameTestHelper helper) {
		long seed = VaultPlanGameTest.seedOf(1);
		Capture capture = VaultTestSupport.capture(seed, Rotation.NONE);
		VaultPlan plan = VaultTestSupport.plan(seed);
		for (int level = 0; level < VaultPlan.LEVELS; level++) {
			for (int c = 0; c < VaultPlan.CELLS; c++) {
				if (!plan.isOpen(level, c) || plan.isStair(c) || c == VaultPlan.CENTER_CELL) {
					continue;
				}
				int ax = VaultPlan.cellMinX(c % VaultPlan.GRID);
				int az = VaultPlan.cellMinZ(c / VaultPlan.GRID);
				int y = VaultPlan.stand(level);
				BlockState roof = capture.local(ax + 1, level == 0 ? VaultLayout.SLAB : 15, az + 1);
				BlockState floor = capture.local(ax + 1, y - 1, az + 1);
				helper.assertTrue(roof.is(VaultBlocks.WALL) || roof.is(VaultBlocks.FLOOR) || roof.is(VaultBlocks.PIT_COVER) || roof.isAir(), "level " + level + " cell " + c + " roof is " + roof);
				helper.assertTrue(floor.is(VaultBlocks.FLOOR) || floor.is(VaultBlocks.WALL) || floor.is(Blocks.POLISHED_BLACKSTONE_BRICKS), "level " + level + " cell " + c + " floor is " + floor);
			}
		}
		helper.assertTrue(capture.local(VaultPlan.OFFSET, 10, VaultPlan.cellMinZ(3) + 1).is(VaultBlocks.WALL), "the west skin is not a vault wall");
		helper.assertTrue(capture.local(0, 5, 30).is(Blocks.STONE) && capture.local(55, 12, 40).is(Blocks.STONE) && capture.local(30, 22, 50).is(Blocks.STONE), "the buried mass was written");
		helper.assertTrue(capture.local(VaultPlan.cellMinX(2), VaultLayout.SLAB, VaultPlan.cellMinZ(1)).is(VaultBlocks.FLOOR) || plan.isStair(8) || plan.isStair(9), "the slab under a room is not a vault floor");
		helper.succeed();
	}

	@GameTest
	public void theGateIsOneWideAndTheTunnelReachesTheFaceInTwelveBlocks(final GameTestHelper helper) {
		for (int n = 0; n < 7; n++) {
			long seed = VaultPlanGameTest.seedOf(n);
			Capture capture = VaultTestSupport.capture(seed, Rotation.NONE);
			VaultPlan plan = VaultTestSupport.plan(seed);
			int ax = VaultPlan.cellMinX(plan.entrance());
			int gateZ = VaultPlan.EXTENSION + VaultPlan.OFFSET;
			for (int z = 0; z < gateZ; z++) {
				helper.assertTrue(capture.local(ax + 3, 9, z).isAir() && capture.local(ax + 3, 11, z).isAir(), "seed " + seed + ": the tunnel is closed at z" + z);
				helper.assertTrue(capture.local(ax + 2, 9, z).is(VaultBlocks.WALL) && capture.local(ax + 4, 9, z).is(VaultBlocks.WALL), "seed " + seed + ": the tunnel is wider than 1 at z" + z);
				helper.assertTrue(capture.local(ax + 3, 12, z).is(VaultBlocks.WALL), "seed " + seed + ": the tunnel is higher than 3 at z" + z);
			}
			for (int x = ax + 2; x <= ax + 4; x++) {
				for (int y = 9; y <= 13; y++) {
					helper.assertTrue(capture.local(x, y, gateZ).isAir(), "seed " + seed + ": the gate is shut at " + x + "," + y);
				}
			}
			helper.assertTrue(capture.local(ax + 1, 10, gateZ).is(Blocks.CRYING_OBSIDIAN) && capture.local(ax + 5, 10, gateZ).is(Blocks.CRYING_OBSIDIAN), "seed " + seed + ": the gate has no frame");
			helper.assertTrue(gateZ + 1 <= 12, "the tunnel plus the gate is " + (gateZ + 1) + " blocks, over 12");
		}
		helper.succeed();
	}

	@GameTest
	public void everyTrapCorridorHoldsItsPlatesAndItsMachinery(final GameTestHelper helper) {
		int arrows = 0;
		int drops = 0;
		for (int n = 0; n < 60; n++) {
			long seed = VaultPlanGameTest.seedOf(n);
			Capture capture = VaultTestSupport.capture(seed, Rotation.values()[n % 4]);
			VaultPlan plan = VaultTestSupport.plan(seed);
			helper.assertTrue(capture.markers().get(VaultMarkers.PLATE).size() == plan.traps().size(), "seed " + seed + " marks " + capture.markers().get(VaultMarkers.PLATE).size() + " plates for " + plan.traps().size() + " traps");
			for (Trap trap : plan.traps()) {
				int ax = VaultPlan.cellMinX(trap.at().i());
				int az = VaultPlan.cellMinZ(trap.at().j());
				int y = VaultPlan.stand(trap.level());
				int plates = 0;
				for (int u = 0; u <= 5; u++) {
					for (int v = 0; v <= 5; v++) {
						BlockState state = capture.local(ax + u, y, az + v);
						if (state.is(VaultBlocks.ARROW_PLATE) || state.is(VaultBlocks.DROP_PLATE)) {
							plates++;
							helper.assertTrue(state.is(trap.kind() == TrapKind.ARROW ? VaultBlocks.ARROW_PLATE : VaultBlocks.DROP_PLATE), "seed " + seed + " has the wrong plate kind");
						}
					}
				}
				helper.assertTrue(plates == 6, "seed " + seed + " trap " + trap + " has " + plates + " plates");
				if (trap.kind() == TrapKind.ARROW) {
					arrows++;
					int vents = 0;
					for (int u = -1; u <= 6; u++) {
						for (int v = -1; v <= 6; v++) {
							if (capture.local(ax + u, y + 1, az + v).is(VaultBlocks.VENT)) {
								vents++;
							}
						}
					}
					helper.assertTrue(vents == 4, "seed " + seed + " arrow trap has " + vents + " vents");
				} else {
					drops++;
					int covers = 0;
					int dais = 0;
					for (int u = 0; u <= 5; u++) {
						for (int v = 0; v <= 5; v++) {
							covers += capture.local(ax + u, VaultLayout.SLAB, az + v).is(VaultBlocks.PIT_COVER) ? 1 : 0;
							dais += capture.local(ax + u, 5, az + v).is(VaultBlocks.FLOOR) ? 1 : 0;
						}
					}
					helper.assertTrue(covers == 18, "seed " + seed + " drop pit has " + covers + " covers");
					helper.assertTrue(dais == 18, "seed " + seed + " drop landing has " + dais + " dais blocks");
				}
			}
		}
		VaultTestSupport.result("trapGeometry", "arrowTraps=" + arrows + " dropTraps=" + drops);
		helper.assertTrue(arrows > 0 && drops > 0, "the 60 seeds covered " + arrows + " arrow and " + drops + " drop traps");
		helper.succeed();
	}

	@GameTest
	public void theRosterMarkersMatchThePlan(final GameTestHelper helper) {
		for (int n = 0; n < 40; n++) {
			long seed = VaultPlanGameTest.seedOf(n);
			Capture capture = VaultTestSupport.capture(seed, Rotation.values()[n % 4]);
			int guardians = VaultPlan.CANDIDATES_PER_LEVEL * 2 + 2 + VaultPlan.GUARDIAN_ROOMS;
			helper.assertTrue(capture.markers().get(VaultMarkers.GUARDIAN).size() == guardians, "seed " + seed + " guardian markers " + capture.markers().get(VaultMarkers.GUARDIAN).size());
			helper.assertTrue(capture.markers().get(VaultMarkers.WARDEN).size() == 1, "seed " + seed + " warden markers");
			helper.assertTrue(capture.markers().get(VaultMarkers.ARCHER).size() == 1, "seed " + seed + " archer markers");
			for (BlockPos spot : capture.markers().get(VaultMarkers.GUARDIAN)) {
				helper.assertTrue(capture.at(spot).isAir() && capture.at(spot.above()).isAir() && !capture.at(spot.below()).isAir(), "seed " + seed + " guardian marker " + spot + " is not a standing spot");
			}
			BlockPos archer = capture.markers().get(VaultMarkers.ARCHER).get(0);
			helper.assertTrue(capture.at(archer).isAir() && capture.at(archer.above()).isAir() && !capture.at(archer.below()).isAir(), "seed " + seed + " the archer marker is not a standing spot");
		}
		helper.succeed();
	}

	@GameTest
	public void everyTreasureRoomHoldsAChestWithAClearTop(final GameTestHelper helper) {
		for (int n = 0; n < 40; n++) {
			long seed = VaultPlanGameTest.seedOf(n);
			Capture capture = VaultTestSupport.capture(seed, Rotation.values()[n % 4]);
			VaultPlan plan = VaultTestSupport.plan(seed);
			int chests = 0;
			for (Cell cell : plan.treasures()) {
				BlockPos at = capture.world(VaultPlan.cellMinX(cell.i()) + 4, VaultPlan.stand(cell.level()), VaultPlan.cellMinZ(cell.j()) + 1);
				helper.assertTrue(capture.at(at).is(Blocks.CHEST), "seed " + seed + " has no chest in " + cell + ", it holds " + capture.at(at));
				helper.assertTrue(capture.at(at.above()).isAir(), "seed " + seed + " the chest in " + cell + " has a block over it");
				chests++;
			}
			VaultTestSupport.result("treasureChests", "seed=" + seed + " chests=" + chests);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 1200)
	public void theInnerVaultIsReachedFromItsNeighbourOnTheLowerLevelWithoutAStair(final GameTestHelper helper) {
		for (int n = 0; n < SEEDS; n++) {
			long seed = VaultPlanGameTest.seedOf(n);
			Rotation rotation = Rotation.values()[n % 4];
			Capture capture = VaultTestSupport.capture(seed, rotation);
			VaultPlan plan = VaultTestSupport.plan(seed);
			String where = "seed " + seed + " " + rotation;
			StairWalker walker = new StairWalker(capture, capture.world(0, VaultLayout.SLAB, 0).getY());
			int ax = VaultPlan.cellMinX(plan.centerDoorCell() % VaultPlan.GRID);
			int az = VaultPlan.cellMinZ(plan.centerDoorCell() / VaultPlan.GRID);
			StairWalker.Spot start = null;
			for (int u = 0; u <= 5 && start == null; u++) {
				for (int v = 0; v <= 5 && start == null; v++) {
					start = walker.spotAt(capture.world(ax + u, VaultPlan.stand(0), az + v));
				}
			}
			helper.assertTrue(start != null, where + ": nobody can stand in the room beside the inner vault");
			helper.assertTrue(walker.reachesRoom(walker.reach(start), 0, VaultPlan.CENTER_CELL), where + ": the inner vault cannot be walked to from room " + plan.centerDoorCell() + " on its own level");
		}
		helper.succeed();
	}

	static final double CHUNK_BUDGET_MS = 50.0;

	/** The layout cost alone: one capture per chunk, as the framework's own budget test measures it, after a warm-up and with the median of three draws. */
	@GameTest
	public void theLayoutCostsLessThanTheBudgetPerChunkOnAverage(final GameTestHelper helper) {
		com.stella.ruins.world.RuinFrame frame = VaultTestSupport.frame(VaultPlanGameTest.seedOf(3), Rotation.NONE);
		net.minecraft.world.level.levelgen.structure.BoundingBox box = frame.box();
		com.stella.ruins.world.SiteLayout layout = VaultTestSupport.site().layout();
		java.util.List<net.minecraft.world.level.levelgen.structure.BoundingBox> clips = new java.util.ArrayList<>();
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				clips.add(new net.minecraft.world.level.levelgen.structure.BoundingBox(cx << 4, box.minY(), cz << 4, (cx << 4) + 15, box.maxY(), (cz << 4) + 15));
			}
		}
		for (net.minecraft.world.level.levelgen.structure.BoundingBox clip : clips) {
			layout.draw(com.stella.ruins.world.LayoutContext.forCapture(frame, new java.util.HashMap<>(), clip));
		}
		double[][] samples = new double[clips.size()][3];
		for (int pass = 0; pass < 3; pass++) {
			for (int k = 0; k < clips.size(); k++) {
				long start = System.nanoTime();
				layout.draw(com.stella.ruins.world.LayoutContext.forCapture(frame, new java.util.HashMap<>(), clips.get(k)));
				samples[k][pass] = (System.nanoTime() - start) / 1_000_000.0;
			}
		}
		java.util.List<Double> medians = new java.util.ArrayList<>();
		for (double[] chunk : samples) {
			double[] sorted = chunk.clone();
			java.util.Arrays.sort(sorted);
			medians.add(sorted[1]);
		}
		double mean = medians.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
		double max = medians.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
		VaultTestSupport.result("layoutCost", String.format("size=%dx%dx%d chunks=%d meanOfMediansMs=%.2f maxMs=%.2f budgetMs=%.0f", frame.sizeX(), frame.sizeY(), frame.sizeZ(), clips.size(), mean, max, CHUNK_BUDGET_MS));
		helper.assertTrue(clips.size() >= 16, "the vault crossed only " + clips.size() + " chunks");
		helper.assertTrue(mean <= CHUNK_BUDGET_MS, "the mean of the chunk medians is " + mean + " ms, over " + CHUNK_BUDGET_MS);
		helper.succeed();
	}

	static final int TIMED_DRAWS = 3;

	/**
	 * The draw into a live level. The first pass writes into fresh rock and is only logged. An untimed pass warms the code.
	 * Then every chunk is drawn three times, the median of each chunk counts, and the mean of the medians must hold the
	 * budget. A slow chunk on a loaded machine is noise, so the max and the p95 are logged, not asserted.
	 */
	@GameTest(maxTicks = 6000)
	public void theLiveDrawCostsLessThanTheBudgetPerChunkOnAverage(final GameTestHelper helper) {
		VaultTestSupport.Arena arena = VaultTestSupport.arena(helper.getLevel(), Rotation.NONE, VaultPlanGameTest.seedOf(3), false);
		net.minecraft.world.level.levelgen.structure.BoundingBox box = arena.frame().box();
		java.util.List<net.minecraft.world.level.levelgen.structure.BoundingBox> clips = new java.util.ArrayList<>();
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				clips.add(new net.minecraft.world.level.levelgen.structure.BoundingBox(
					Math.max(box.minX(), cx << 4), box.minY(), Math.max(box.minZ(), cz << 4),
					Math.min(box.maxX(), (cx << 4) + 15), box.maxY(), Math.min(box.maxZ(), (cz << 4) + 15)));
			}
		}
		double firstTotal = 0.0;
		double firstWorst = 0.0;
		for (net.minecraft.world.level.levelgen.structure.BoundingBox clip : clips) {
			long started = System.nanoTime();
			arena.piece().build(arena.level(), clip);
			double took = (System.nanoTime() - started) / 1_000_000.0;
			firstTotal += took;
			firstWorst = Math.max(firstWorst, took);
		}
		for (net.minecraft.world.level.levelgen.structure.BoundingBox clip : clips) {
			arena.piece().build(arena.level(), clip);
		}
		double[][] samples = new double[clips.size()][TIMED_DRAWS];
		for (int pass = 0; pass < TIMED_DRAWS; pass++) {
			for (int k = 0; k < clips.size(); k++) {
				long started = System.nanoTime();
				arena.piece().build(arena.level(), clips.get(k));
				samples[k][pass] = (System.nanoTime() - started) / 1_000_000.0;
			}
		}
		arena.release();
		java.util.List<Double> medians = new java.util.ArrayList<>();
		for (double[] chunk : samples) {
			double[] sorted = chunk.clone();
			java.util.Arrays.sort(sorted);
			medians.add(sorted[sorted.length / 2]);
		}
		double mean = medians.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
		java.util.List<Double> ordered = new java.util.ArrayList<>(medians);
		java.util.Collections.sort(ordered);
		double max = ordered.get(ordered.size() - 1);
		double p95 = ordered.get((int) Math.ceil(ordered.size() * 0.95) - 1);
		VaultTestSupport.result("liveDraw", String.format("chunks=%d draws=%d meanOfMediansMs=%.2f p95Ms=%.2f maxMs=%.2f budgetMs=%.0f firstWriteMeanMs=%.2f firstWriteWorstMs=%.2f blocks=%d",
			clips.size(), TIMED_DRAWS, mean, p95, max, CHUNK_BUDGET_MS, firstTotal / clips.size(), firstWorst, VaultPlan.SIZE_X * VaultPlan.SIZE_Y * VaultPlan.SIZE_Z));
		helper.assertTrue(mean <= CHUNK_BUDGET_MS, "the mean of the chunk medians is " + mean + " ms, over " + CHUNK_BUDGET_MS);
		helper.succeed();
	}
}
