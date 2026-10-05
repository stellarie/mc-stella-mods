package com.stella.ruins.site.meteorcrater;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** The plan of the crater: its ranges, its walls, its meteor, its markers and its ring, over many seeds. */
public class CraterPlanGameTest {
	static final int SEEDS = 200;

	static CraterPlan plan(final long seed) {
		return CraterPlan.derive(RandomSource.create(seed * 7919L + 13L));
	}

	@GameTest
	public void radiusDepthAndOreCountStayInRangeAndVaryOver200Seeds(final GameTestHelper helper) {
		Set<Integer> radii = new HashSet<>();
		Set<Integer> depths = new HashSet<>();
		Set<Integer> ores = new HashSet<>();
		Set<Integer> blobs = new HashSet<>();
		for (int seed = 0; seed < SEEDS; seed++) {
			CraterPlan plan = plan(seed);
			helper.assertTrue(plan.radius() >= CraterPlan.MIN_RADIUS && plan.radius() <= CraterPlan.MAX_RADIUS, "seed " + seed + " radius " + plan.radius());
			helper.assertTrue(plan.depth() >= CraterPlan.MIN_DEPTH && plan.depth() <= CraterPlan.MAX_DEPTH, "seed " + seed + " depth " + plan.depth());
			helper.assertTrue(plan.depth() <= plan.radius() - CraterPlan.FLAT, "seed " + seed + " depth " + plan.depth() + " is too steep for radius " + plan.radius());
			helper.assertTrue(plan.ore().size() >= CraterPlan.MIN_ORE && plan.ore().size() <= CraterPlan.MAX_ORE, "seed " + seed + " ore " + plan.ore().size());
			helper.assertTrue(plan.blob().size() >= CraterPlan.MIN_BLOB && plan.blob().size() <= CraterPlan.MAX_BLOB, "seed " + seed + " blob " + plan.blob().size());
			radii.add(plan.radius());
			depths.add(plan.depth());
			ores.add(plan.ore().size());
			blobs.add(plan.blob().hashCode());
		}
		CraterTestSupport.result("planRanges", "seeds=" + SEEDS + " radii=" + radii.size() + " depths=" + depths.size() + " oreCounts=" + ores.size() + " blobShapes=" + blobs.size());
		helper.assertTrue(radii.size() == CraterPlan.MAX_RADIUS - CraterPlan.MIN_RADIUS + 1, "radii " + radii);
		helper.assertTrue(depths.size() >= 8, "depths " + depths);
		helper.assertTrue(ores.size() >= 8, "ore counts " + ores);
		helper.assertTrue(blobs.size() >= SEEDS - 5, "only " + blobs.size() + " distinct meteor shapes in " + SEEDS + " seeds");
		helper.succeed();
	}

	@GameTest
	public void atLeastSixOreBlocksAreExposedAndEveryOreBlockIsPartOfTheMeteor(final GameTestHelper helper) {
		for (int seed = 0; seed < SEEDS; seed++) {
			CraterPlan plan = plan(seed);
			helper.assertTrue(plan.exposedOre() >= CraterPlan.MIN_EXPOSED, "seed " + seed + " exposes " + plan.exposedOre());
			for (CraterPlan.Spot spot : plan.ore()) {
				helper.assertTrue(plan.blobAt(spot.x(), spot.y(), spot.z()), "seed " + seed + " ore " + spot + " is outside the meteor");
				BlockState state = plan.stateAt(spot.x(), spot.y(), spot.z());
				helper.assertTrue(state != null && state.is(MeteorBlocks.STARMETAL_ORE), "seed " + seed + " ore " + spot + " draws " + state);
			}
		}
		helper.succeed();
	}

	@GameTest
	public void theMeteorSitsOnTheFloorAndLeavesTheNotchOpen(final GameTestHelper helper) {
		for (int seed = 0; seed < SEEDS; seed++) {
			CraterPlan plan = plan(seed);
			CraterPlan.Spot core = plan.core();
			helper.assertTrue(core.y() == plan.floorY() + 1 && plan.top(CraterPlan.CENTER, CraterPlan.CENTER) == plan.floorY(), "seed " + seed + " core height");
			for (CraterPlan.Blob blob : plan.blob()) {
				CraterPlan.Spot s = blob.spot();
				helper.assertTrue(s.y() >= plan.top(s.x(), s.z()) && s.y() <= plan.floorY() + CraterPlan.BLOB_HEIGHT, "seed " + seed + " blob block " + s);
				helper.assertTrue(Math.abs(s.x() - CraterPlan.CENTER) <= CraterPlan.BLOB_REACH && Math.abs(s.z() - CraterPlan.CENTER) <= CraterPlan.BLOB_REACH, "seed " + seed + " blob reach " + s);
				helper.assertTrue(!(s.x() == CraterPlan.CENTER && s.z() < CraterPlan.CENTER), "seed " + seed + " blob in the notch column " + s);
				helper.assertTrue(!(s.x() == core.x() && s.y() == core.y() && s.z() == core.z()), "seed " + seed + " blob on the core");
			}
			BlockState notch = plan.stateAt(CraterPlan.CENTER, core.y(), CraterPlan.NOTCH_Z);
			helper.assertTrue(notch != null && notch.isAir(), "seed " + seed + " notch is " + notch);
		}
		helper.succeed();
	}

	@GameTest
	public void theRouteFromTheSouthEdgeToTheNotchNeverStepsMoreThanOneBlock(final GameTestHelper helper) {
		for (int seed = 0; seed < SEEDS; seed++) {
			CraterPlan plan = plan(seed);
			for (int x = CraterPlan.CENTER - 1; x <= CraterPlan.CENTER + 1; x++) {
				for (int z = 0; z < CraterPlan.NOTCH_Z; z++) {
					int step = Math.abs(plan.top(x, z) - plan.top(x, z + 1));
					helper.assertTrue(step <= 1, "seed " + seed + " column " + x + " steps " + step + " at z " + z);
				}
			}
			helper.assertTrue(plan.top(CraterPlan.CENTER, 0) == CraterPlan.GROUND, "seed " + seed + " route starts at " + plan.top(CraterPlan.CENTER, 0));
			helper.assertTrue(plan.top(CraterPlan.CENTER, CraterPlan.NOTCH_Z) == plan.floorY(), "seed " + seed + " route ends at " + plan.top(CraterPlan.CENTER, CraterPlan.NOTCH_Z));
		}
		helper.succeed();
	}

	@GameTest
	public void theVaultSitsInANicheInTheNorthWallWithARoof(final GameTestHelper helper) {
		for (int seed = 0; seed < SEEDS; seed++) {
			CraterPlan plan = plan(seed);
			CraterPlan.Spot vault = plan.vault();
			int distance = vault.z() - CraterPlan.CENTER;
			helper.assertTrue(distance >= 5 && distance <= 16, "seed " + seed + " vault distance " + distance);
			for (int x = CraterPlan.CENTER - 1; x <= CraterPlan.CENTER + 1; x++) {
				for (int z = plan.nicheZ(); z <= plan.nicheZ() + 1; z++) {
					helper.assertTrue(plan.top(x, z) >= plan.floorY() + 3, "seed " + seed + " niche column " + x + "," + z + " has no roof");
					for (int y = plan.floorY() + 1; y <= plan.floorY() + 2; y++) {
						BlockState state = plan.stateAt(x, y, z);
						helper.assertTrue(state != null && state.isAir(), "seed " + seed + " niche cell " + x + "," + y + "," + z + " is " + state);
					}
				}
			}
			helper.assertTrue(vault.y() == plan.floorY() + 1 && vault.x() == CraterPlan.CENTER, "seed " + seed + " vault " + vault);
		}
		helper.succeed();
	}

	@GameTest
	public void everyMarkerStandsOnSolidGroundInsideTheBoxAndThereAreThreeToSixFires(final GameTestHelper helper) {
		for (int seed = 0; seed < SEEDS; seed++) {
			CraterPlan plan = plan(seed);
			helper.assertTrue(plan.golems().size() == CraterPlan.GOLEMS && plan.slimes().size() == CraterPlan.SLIMES, "seed " + seed + " markers " + plan.golems().size() + "/" + plan.slimes().size());
			for (CraterPlan.Spot spot : java.util.stream.Stream.concat(plan.golems().stream(), plan.slimes().stream()).toList()) {
				helper.assertTrue(spot.x() >= 0 && spot.x() < CraterPlan.SIZE_X && spot.z() >= 0 && spot.z() < CraterPlan.SIZE_Z, "seed " + seed + " marker outside " + spot);
				helper.assertTrue(spot.y() == plan.highest(spot.x(), spot.z()) + 1, "seed " + seed + " marker " + spot + " does not stand on the top of its column");
				helper.assertTrue(!plan.blobAt(spot.x(), spot.y(), spot.z()), "seed " + seed + " marker " + spot + " is inside the meteor");
			}
			helper.assertTrue(plan.fires().size() >= CraterPlan.MIN_FIRES && plan.fires().size() <= CraterPlan.MAX_FIRES, "seed " + seed + " fires " + plan.fires().size());
			for (CraterPlan.Fire fire : plan.fires()) {
				for (CraterPlan.Spot cell : fire.cells()) {
					helper.assertTrue(!CraterPlan.onRoute(cell.x(), cell.z()), "seed " + seed + " fire on the route " + cell);
				}
			}
		}
		helper.succeed();
	}

	@GameTest
	public void theRingAndTheRimNeverReachTheEdgeOfTheBox(final GameTestHelper helper) {
		for (int seed = 0; seed < SEEDS; seed++) {
			CraterPlan plan = plan(seed);
			for (int x = 0; x < CraterPlan.SIZE_X; x++) {
				for (int z = 0; z < CraterPlan.SIZE_Z; z++) {
					boolean edge = x == 0 || z == 0 || x == CraterPlan.SIZE_X - 1 || z == CraterPlan.SIZE_Z - 1;
					if (edge && plan.affected(x, z) && !CraterPlan.onRoute(x, z)) {
						helper.fail("seed " + seed + " the crater reaches the box edge at " + x + "," + z);
					}
					if (plan.rim(x, z) > CraterPlan.RIM_CAP + 1.0E-9) {
						helper.fail("seed " + seed + " rim " + plan.rim(x, z));
					}
				}
			}
			int scorched = 0;
			for (int x = 0; x < CraterPlan.SIZE_X; x++) {
				for (int z = 0; z < CraterPlan.SIZE_Z; z++) {
					if (plan.affected(x, z) && plan.surface(x, z) != CraterPlan.Surface.GRASS) {
						scorched++;
					}
				}
			}
			helper.assertTrue(scorched > 100, "seed " + seed + " has only " + scorched + " scorched columns");
		}
		helper.succeed();
	}

	@GameTest
	public void theLipIsHighestOnTheNorthAndTheGougeHasNone(final GameTestHelper helper) {
		int northLip = 0;
		int southLip = 0;
		for (int seed = 0; seed < SEEDS; seed++) {
			CraterPlan plan = plan(seed);
			int north = 0;
			int south = 0;
			int gouge = 0;
			for (int z = 0; z < CraterPlan.SIZE_Z; z++) {
				north = Math.max(north, plan.top(CraterPlan.CENTER, z) - CraterPlan.GROUND);
				if (z < CraterPlan.CENTER) {
					gouge = Math.max(gouge, plan.top(CraterPlan.CENTER, z) - CraterPlan.GROUND);
				}
			}
			for (int x = 0; x < CraterPlan.SIZE_X; x++) {
				south = Math.max(south, plan.top(x, CraterPlan.CENTER) - CraterPlan.GROUND);
			}
			helper.assertTrue(gouge <= 0, "seed " + seed + " the gouge rises " + gouge);
			helper.assertTrue(north <= 3 && north >= 1, "seed " + seed + " north lip " + north);
			northLip += north;
			southLip += south;
		}
		CraterTestSupport.result("lip", "northTotal=" + northLip + " eastWestTotal=" + southLip);
		helper.assertTrue(northLip > southLip, "the north lip is not the highest: " + northLip + " vs " + southLip);
		helper.succeed();
	}

	@GameTest
	public void theSamePlanComesFromTheSameSeedAndAnotherPlanFromAnotherSeed(final GameTestHelper helper) {
		long same = hash(plan(7));
		helper.assertTrue(same == hash(plan(7)), "the same seed drew two plans");
		Set<Long> hashes = new HashSet<>();
		for (int seed = 0; seed < 30; seed++) {
			hashes.add(hash(plan(seed)));
		}
		helper.assertTrue(hashes.size() == 30, "only " + hashes.size() + " different plans in 30 seeds");
		helper.succeed();
	}

	@GameTest
	public void everyPlanColumnIsWholeFromTheFoundationToTheTopOfTheBox(final GameTestHelper helper) {
		for (int seed = 0; seed < 40; seed++) {
			CraterPlan plan = plan(seed);
			for (int x = 0; x < CraterPlan.SIZE_X; x++) {
				for (int z = 0; z < CraterPlan.SIZE_Z; z++) {
					if (!plan.affected(x, z)) {
						helper.assertTrue(plan.stateAt(x, CraterPlan.MIN_Y, z) == null, "seed " + seed + " column " + x + "," + z + " is not affected but is drawn");
						continue;
					}
					for (int y = CraterPlan.MIN_Y; y < CraterPlan.SIZE_Y; y++) {
						BlockState state = plan.stateAt(x, y, z);
						helper.assertTrue(state != null, "seed " + seed + " hole at " + x + "," + y + "," + z);
						helper.assertTrue(state.getFluidState().isEmpty(), "seed " + seed + " fluid at " + x + "," + y + "," + z);
						helper.assertTrue(y > plan.top(x, z) || state.isAir() || !state.is(Blocks.WATER), "seed " + seed + " water in a column");
					}
				}
			}
		}
		helper.succeed();
	}

	private static long hash(final CraterPlan plan) {
		long h = plan.radius() * 31L + plan.depth();
		List<CraterPlan.Blob> blob = plan.blob();
		h = h * 31L + blob.hashCode();
		for (int x = 0; x < CraterPlan.SIZE_X; x++) {
			for (int z = 0; z < CraterPlan.SIZE_Z; z++) {
				h = h * 31L + plan.top(x, z) * 7L + plan.surface(x, z).ordinal();
			}
		}
		return h;
	}
}
