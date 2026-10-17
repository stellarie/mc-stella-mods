package com.stella.end;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** The rooftop: a roof over the hatch, and three or four ruined rooms off the dive lanes. */
public class CastleRoofGameTest {
	@GameTest(environment = "mod-stella-end-test:roof_hatch", maxTicks = 40)
	public void theHatchHasARoofOverEveryCellOfItsHoleAndRoomToStandUnderIt(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		CastleLayout layout = CastleBuildGameTest.other(helper);
		CastleLayout.Flight exit = layout.rest().exit();
		StringBuilder bad = new StringBuilder();
		int cells = 0;
		for (int k = CastleLayout.Flight.HOLE_FROM; k < CastleLayout.Flight.STEPS - 1; k++) {
			for (int lane = 0; lane < CastleLayout.Flight.WIDTH; lane++) {
				cells++;
				int x = exit.x(k, lane);
				int z = exit.z(k, lane);
				int roof = -1;
				for (int y = EndCastle.ROOF_Y + 1; y <= EndCastle.ROOF_Y + 12 && roof < 0; y++) {
					BlockState state = EndTestSupport.state(end, CastleBuildGameTest.OTHER_MIN, x, y, z);
					if (!state.isAir()) {
						roof = y - EndCastle.ROOF_Y;
					}
				}
				if (roof < 0) {
					bad.append(" hole cell ").append(x).append(',').append(z).append(" is open to the sky");
				} else if (roof < 3) {
					bad.append(" hole cell ").append(x).append(',').append(z).append(" has a roof only ").append(roof).append(" over the deck");
				}
			}
		}
		EndTestSupport.result("roofHatch", "cells=" + cells + " bad=" + bad.length());
		helper.assertTrue(bad.isEmpty(), "the hatch is not roofed:" + bad);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:roof_ruin_plan", maxTicks = 60)
	public void theRoofPlanHoldsThreeOrFourRuinsOfSixBlocksOrLessOffTheLanesTheHatchAndTheParapetForEverySeed(final GameTestHelper helper) {
		StringBuilder bad = new StringBuilder();
		int seeds = 300;
		int[] counts = new int[6];
		for (long seed = 0; seed < seeds; seed++) {
			CastleLayout.RoofPlan roof = CastleLayout.generate(seed).roof();
			int count = roof.ruins().size();
			counts[Math.min(count, counts.length - 1)]++;
			if (count != 3 && count != 4) {
				bad.append(" seed ").append(seed).append(" holds ").append(count).append(" ruins");
			}
			for (int i = 0; i < roof.ruins().size(); i++) {
				CastleLayout.Ruin ruin = roof.ruins().get(i);
				CastleLayout.Rect b = ruin.bounds();
				if (ruin.height() > CastleLayout.RUIN_HEIGHT || ruin.height() < 1) {
					bad.append(" seed ").append(seed).append(" ruin ").append(i).append(" is ").append(ruin.height()).append(" tall");
				}
				for (int x = b.x0(); x <= b.x1(); x++) {
					for (int z = b.z0(); z <= b.z1(); z++) {
						int dx = x - CastleLayout.ROOF_CENTRE;
						int dz = z - CastleLayout.ROOF_CENTRE;
						if (Math.abs(dx) <= CastleLayout.LANE_HALF || Math.abs(dz) <= CastleLayout.LANE_HALF) {
							bad.append(" seed ").append(seed).append(" ruin ").append(i).append(" stands in a lane at ").append(x).append(',').append(z);
						}
						if (dx * dx + dz * dz >= CastleLayout.PARAPET_FROM) {
							bad.append(" seed ").append(seed).append(" ruin ").append(i).append(" reaches the parapet at ").append(x).append(',').append(z);
						}
						if (roof.hatchHouse().x0() - 1 <= x && x <= roof.hatchHouse().x1() + 1 && roof.hatchHouse().z0() - 1 <= z && z <= roof.hatchHouse().z1() + 1) {
							bad.append(" seed ").append(seed).append(" ruin ").append(i).append(" touches the hatch house");
						}
					}
				}
				for (int j = i + 1; j < roof.ruins().size(); j++) {
					CastleLayout.Rect c = roof.ruins().get(j).bounds();
					if (b.x0() <= c.x1() && c.x0() <= b.x1() && b.z0() <= c.z1() && c.z0() <= b.z1()) {
						bad.append(" seed ").append(seed).append(" ruins ").append(i).append(" and ").append(j).append(" overlap");
					}
				}
			}
		}
		EndTestSupport.result("roofRuinPlan", "seeds=" + seeds + " threeRuins=" + counts[3] + " fourRuins=" + counts[4] + " bad=" + bad.length());
		helper.assertTrue(bad.isEmpty(), "roof plans off the rule:" + bad);
		helper.assertTrue(counts[3] > 0 && counts[4] > 0, "the seeds gave only one count of ruins: " + counts[3] + " threes and " + counts[4] + " fours");
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:roof_ruin_built", maxTicks = 60)
	public void everyPlannedRuinStandsOnTheDeckInRuinsAndNothingOfItRisesOverItsHeight(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		CastleLayout layout = CastleBuildGameTest.other(helper);
		StringBuilder bad = new StringBuilder();
		int walls = 0;
		helper.assertTrue(layout.roof().ruins().size() >= 3, "the roof plan holds " + layout.roof().ruins().size() + " ruins");
		for (CastleLayout.Ruin ruin : layout.roof().ruins()) {
			CastleLayout.Rect b = ruin.bounds();
			int standing = 0;
			int broken = 0;
			for (int x = b.x0(); x <= b.x1(); x++) {
				for (int z = b.z0(); z <= b.z1(); z++) {
					boolean edge = x == b.x0() || x == b.x1() || z == b.z0() || z == b.z1();
					int top = 0;
					for (int up = 1; up <= CastleLayout.RUIN_HEIGHT + 6; up++) {
						if (!EndTestSupport.state(end, CastleBuildGameTest.OTHER_MIN, x, EndCastle.ROOF_Y + up, z).isAir()) {
							top = up;
						}
					}
					if (top > ruin.height()) {
						bad.append(" ruin at ").append(b.x0()).append(',').append(b.z0()).append(" rises ").append(top).append(" over a planned ").append(ruin.height());
					}
					if (edge) {
						standing += top > 0 ? 1 : 0;
						broken += top < ruin.height() ? 1 : 0;
					}
				}
			}
			walls += standing;
			if (standing < 8) {
				bad.append(" ruin at ").append(b.x0()).append(',').append(b.z0()).append(" has only ").append(standing).append(" wall columns");
			}
			if (broken < 2) {
				bad.append(" ruin at ").append(b.x0()).append(',').append(b.z0()).append(" is not broken");
			}
		}
		EndTestSupport.result("roofRuinBuilt", "ruins=" + layout.roof().ruins().size() + " wallColumns=" + walls + " bad=" + bad.length());
		EndTestSupport.fresh(helper);
		helper.assertTrue(bad.isEmpty(), "ruins off the plan:" + bad);
		helper.succeed();
	}
}
