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
	public void theShellTheFloorsAndTheRoofAreVaultBlocks(final GameTestHelper helper) {
		Capture capture = VaultTestSupport.capture(VaultPlanGameTest.seedOf(1), Rotation.NONE);
		for (int[] spot : new int[][] {{0, 5, 30}, {55, 12, 40}, {20, 16, 20}, {30, 22, 50}, {10, 0, 10}, {10, 1, 60}}) {
			helper.assertTrue(capture.local(spot[0], spot[1], spot[2]).is(VaultBlocks.WALL) || capture.local(spot[0], spot[1], spot[2]).is(VaultBlocks.FLOOR),
				"spot " + java.util.Arrays.toString(spot) + " is " + capture.local(spot[0], spot[1], spot[2]));
		}
		helper.assertTrue(capture.local(10, 8, 40).is(VaultBlocks.FLOOR) || capture.local(10, 8, 40).isAir(), "the slab is not a vault floor");
		helper.assertTrue(capture.local(0, 0, 0).is(VaultBlocks.FLOOR) && capture.local(55, 23, 63).is(VaultBlocks.WALL), "the corners are not vault blocks");
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
			helper.assertTrue(capture.markers().get(VaultMarkers.WRAITH).size() == VaultPlan.WRAITHS, "seed " + seed + " wraith markers");
			helper.assertTrue(capture.markers().get(VaultMarkers.GUARDIAN).size() == 8, "seed " + seed + " guardian markers " + capture.markers().get(VaultMarkers.GUARDIAN).size());
			helper.assertTrue(capture.markers().get(VaultMarkers.WARDEN).size() == 1, "seed " + seed + " warden markers");
			for (BlockPos spot : capture.markers().get(VaultMarkers.GUARDIAN)) {
				helper.assertTrue(capture.at(spot).isAir() && capture.at(spot.above()).isAir() && !capture.at(spot.below()).isAir(), "seed " + seed + " guardian marker " + spot + " is not a standing spot");
			}
			for (BlockPos spot : capture.markers().get(VaultMarkers.WRAITH)) {
				helper.assertTrue(capture.at(spot).isAir() && capture.at(spot.above()).isAir(), "seed " + seed + " wraith marker " + spot + " is inside a block");
			}
		}
		helper.succeed();
	}

	static final double CHUNK_BUDGET_MS = 50.0;

	/** The layout cost alone: one capture per chunk, as the framework's own budget test measures it. */
	@GameTest
	public void theLayoutCostsLessThanTheBudgetPerChunkOnAverage(final GameTestHelper helper) {
		com.stella.ruins.world.RuinFrame frame = VaultTestSupport.frame(VaultPlanGameTest.seedOf(3), Rotation.NONE);
		net.minecraft.world.level.levelgen.structure.BoundingBox box = frame.box();
		com.stella.ruins.world.SiteLayout layout = VaultTestSupport.site().layout();
		layout.draw(com.stella.ruins.world.LayoutContext.forCapture(frame, new java.util.HashMap<>(), box));
		double total = 0.0;
		double worst = 0.0;
		int chunks = 0;
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				net.minecraft.world.level.levelgen.structure.BoundingBox clip = new net.minecraft.world.level.levelgen.structure.BoundingBox(cx << 4, box.minY(), cz << 4, (cx << 4) + 15, box.maxY(), (cz << 4) + 15);
				long start = System.nanoTime();
				layout.draw(com.stella.ruins.world.LayoutContext.forCapture(frame, new java.util.HashMap<>(), clip));
				double millis = (System.nanoTime() - start) / 1_000_000.0;
				total += millis;
				worst = Math.max(worst, millis);
				chunks++;
			}
		}
		VaultTestSupport.result("layoutCost", String.format("size=%dx%dx%d chunks=%d meanMs=%.2f worstMs=%.2f budgetMs=%.0f", frame.sizeX(), frame.sizeY(), frame.sizeZ(), chunks, total / chunks, worst, CHUNK_BUDGET_MS));
		helper.assertTrue(chunks >= 16, "the vault crossed only " + chunks + " chunks");
		helper.assertTrue(total / chunks <= CHUNK_BUDGET_MS, "a chunk took " + (total / chunks) + " ms on average, over " + CHUNK_BUDGET_MS);
		helper.succeed();
	}

	/** The draw into a live level, where every write also updates lighting. The mean must hold the budget; the worst chunk is logged. */
	@GameTest(maxTicks = 6000)
	public void theLiveDrawCostsLessThanTheBudgetPerChunkOnAverage(final GameTestHelper helper) {
		VaultTestSupport.Arena arena = VaultTestSupport.arena(helper.getLevel(), Rotation.NONE, VaultPlanGameTest.seedOf(3), false);
		net.minecraft.world.level.levelgen.structure.BoundingBox box = arena.frame().box();
		double worst = 0.0;
		double total = 0.0;
		int chunks = 0;
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				net.minecraft.world.level.levelgen.structure.BoundingBox clip = new net.minecraft.world.level.levelgen.structure.BoundingBox(
					Math.max(box.minX(), cx << 4), box.minY(), Math.max(box.minZ(), cz << 4),
					Math.min(box.maxX(), (cx << 4) + 15), box.maxY(), Math.min(box.maxZ(), (cz << 4) + 15));
				long started = System.nanoTime();
				arena.piece().build(arena.level(), clip);
				double took = (System.nanoTime() - started) / 1_000_000.0;
				worst = Math.max(worst, took);
				total += took;
				chunks++;
			}
		}
		arena.release();
		VaultTestSupport.result("liveDraw", String.format("chunks=%d meanMs=%.2f worstMs=%.2f budgetMs=%.0f blocks=%d", chunks, total / chunks, worst, CHUNK_BUDGET_MS, VaultPlan.SIZE_X * VaultPlan.SIZE_Y * VaultPlan.SIZE_Z));
		helper.assertTrue(total / chunks <= CHUNK_BUDGET_MS, "a chunk took " + (total / chunks) + " ms on average, over " + CHUNK_BUDGET_MS);
		helper.succeed();
	}
}
