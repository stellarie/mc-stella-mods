package com.stella.ruins.site.lostminerscamp;

import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

/** Acceptance items 1 and 2: the camp stands whole, and the random parts vary. */
public class CampPlanGameTest {
	private static final int SEEDS = 24;
	private static final int VARIETY_SEEDS = 200;

	private static CampPlan plan(final long seed) {
		return CampPlan.derive(RandomSource.create(seed));
	}

	@GameTest
	public void theWalkConnectsTheMouthToTheCamp(final GameTestHelper helper) {
		int checked = 0;
		for (int seed = 0; seed < SEEDS; seed++) {
			CampPlan plan = plan(seed);
			var centre = plan.centre();
			helper.assertTrue(!centre.isEmpty(), "seed " + seed + " drew no tunnel");
			CampPlan.Spot first = centre.get(0);
			helper.assertTrue(first.z() == 0, "seed " + seed + " starts the tunnel at z=" + first.z());
			CampPlan.Spot last = centre.get(centre.size() - 1);
			helper.assertTrue(last.z() == CampPlan.CAMP_Z - 1, "seed " + seed + " stopped the walk at z=" + last.z());
			for (int i = 1; i < centre.size(); i++) {
				CampPlan.Spot a = centre.get(i - 1);
				CampPlan.Spot b = centre.get(i);
				// The floor steps down the ramp, so the walk is adjacent in x and z and drops at most one.
				helper.assertTrue(Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z()) == 1, "seed " + seed + " tears the tunnel between " + a + " and " + b);
				helper.assertTrue(a.y() - b.y() >= 0 && a.y() - b.y() <= 1, "seed " + seed + " steps the floor by " + (a.y() - b.y()) + " between " + a + " and " + b);
			}
			for (CampPlan.Spot spot : centre) {
				helper.assertTrue(spot.x() >= 2 && spot.x() <= CampPlan.SIZE_X - 3, "seed " + seed + " puts the tunnel wall outside the box at x=" + spot.x());
				helper.assertTrue(spot.z() >= 0 && spot.z() < CampPlan.CAMP_Z, "seed " + seed + " puts the tunnel outside the box at z=" + spot.z());
				helper.assertTrue(spot.y() >= CampPlan.FLOOR - CampPlan.MAX_DROP && spot.y() <= CampPlan.FLOOR,
					"seed " + seed + " drops the floor to y=" + spot.y());
			}
			checked++;
		}
		MinerTestSupport.result("campWalk", "seeds=" + checked);
		helper.succeed();
	}

	@GameTest
	public void theCampHoldsWhatItPromises(final GameTestHelper helper) {
		int checked = 0;
		for (int seed = 0; seed < SEEDS; seed++) {
			CampPlan plan = plan(seed);
			helper.assertTrue(plan.core().z() >= CampPlan.CAMP_Z, "seed " + seed + " puts the core outside the camp");
			helper.assertTrue(plan.vault().z() >= CampPlan.CAMP_Z, "seed " + seed + " puts the vault outside the camp");
			helper.assertTrue(plan.tent().z() >= CampPlan.CAMP_Z, "seed " + seed + " puts the tent outside the camp");
			helper.assertTrue(plan.fire().z() >= CampPlan.CAMP_Z, "seed " + seed + " puts the fire outside the camp");
			helper.assertTrue(plan.miners().size() == CampPlan.MINERS + 1, "seed " + seed + " drew " + plan.miners().size() + " miner spots");
			helper.assertTrue(plan.rats().size() == CampPlan.RATS, "seed " + seed + " drew " + plan.rats().size() + " rat spots");
			CampPlan.Spot lectern = plan.lectern();
			helper.assertTrue(Blocks.LECTERN.defaultBlockState().getBlock() == plan.stateAt(lectern.x(), lectern.y() + 1, lectern.z()).getBlock(),
				"seed " + seed + " left no lectern at the dead end");
			helper.assertTrue(plan.stateAt(plan.vault().x(), plan.vault().y(), plan.vault().z()).getBlock() == Blocks.BARREL,
				"seed " + seed + " left no supply pile at the vault");
			helper.assertTrue(plan.stateAt(plan.core().x(), plan.core().y(), plan.core().z()).getBlock() == Blocks.OAK_FENCE,
				"seed " + seed + " left no tally post at the core");
			checked++;
		}
		MinerTestSupport.result("campWhole", "seeds=" + checked);
		helper.succeed();
	}

	@GameTest
	public void theRandomPartsVaryOverTwoHundredSeeds(final GameTestHelper helper) {
		Set<Integer> bends = new HashSet<>();
		Set<Integer> drops = new HashSet<>();
		Set<Integer> collapses = new HashSet<>();
		Set<Integer> lecterns = new HashSet<>();
		Set<Integer> names = new HashSet<>();
		Set<Integer> days = new HashSet<>();
		int firstLectern = plan(0).lectern().x();
		boolean moved = false;
		for (int seed = 0; seed < VARIETY_SEEDS; seed++) {
			CampPlan plan = plan(seed);
			bends.add(plan.bends());
			drops.add(plan.dropDepth());
			collapses.add(plan.collapses());
			lecterns.add(plan.lectern().x());
			names.add(plan.journalName());
			days.add(plan.journalDays());
			moved |= plan.lectern().x() != firstLectern;
			helper.assertTrue(plan.lectern().x() >= 2 && plan.lectern().x() <= CampPlan.SIZE_X - 3,
				"seed " + seed + " puts the lectern outside the box at x=" + plan.lectern().x());
			helper.assertTrue(plan.lectern().z() == CampPlan.CAMP_Z - 1, "seed " + seed + " leaves the lectern off the dead end at z=" + plan.lectern().z());
			helper.assertTrue(plan.bends() >= CampPlan.MIN_BENDS && plan.bends() <= CampPlan.MAX_BENDS, "seed " + seed + " rolled " + plan.bends() + " bends");
			helper.assertTrue(plan.dropDepth() >= CampPlan.MIN_DROP && plan.dropDepth() <= CampPlan.MAX_DROP, "seed " + seed + " rolled a drop of " + plan.dropDepth());
			helper.assertTrue(plan.collapses() >= CampPlan.MIN_COLLAPSES && plan.collapses() <= CampPlan.MAX_COLLAPSES, "seed " + seed + " rolled " + plan.collapses() + " collapses");
			helper.assertTrue(plan.journalName() >= 0 && plan.journalName() < CampPlan.NAMES, "seed " + seed + " rolled the name " + plan.journalName());
			helper.assertTrue(plan.journalDays() >= 3 && plan.journalDays() <= 30, "seed " + seed + " rolled " + plan.journalDays() + " days");
		}
		MinerTestSupport.result("campVariety", "seeds=" + VARIETY_SEEDS + " bends=" + bends.size() + " drops=" + drops.size() + " collapses=" + collapses.size()
			+ " lecterns=" + lecterns.size() + " names=" + names.size() + " days=" + days.size());
		helper.assertTrue(bends.size() >= CampPlan.MAX_BENDS - CampPlan.MIN_BENDS, "the bend count did not vary: " + bends);
		helper.assertTrue(drops.size() == CampPlan.MAX_DROP - CampPlan.MIN_DROP + 1, "the drop depth did not vary: " + drops);
		helper.assertTrue(collapses.size() == CampPlan.MAX_COLLAPSES - CampPlan.MIN_COLLAPSES + 1, "the collapse count did not vary: " + collapses);
		helper.assertTrue(moved, "the lectern never moved off x=" + firstLectern);
		helper.assertTrue(names.size() == CampPlan.NAMES, "the journal name did not vary: " + names.size());
		helper.assertTrue(days.size() >= 20, "the day count barely varied: " + days.size());
		helper.succeed();
	}
}
