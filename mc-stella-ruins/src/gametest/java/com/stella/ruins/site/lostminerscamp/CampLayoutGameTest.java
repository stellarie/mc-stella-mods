package com.stella.ruins.site.lostminerscamp;

import com.stella.ruins.world.RuinBlocks;
import com.stella.ruins.world.RuinMarkers;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/** Acceptance item 1: the camp stands whole over 24 seeds and every rotation. */
public class CampLayoutGameTest {
	private static final int SEEDS = 24;
	private static final int TURNS = 4;

	@GameTest
	public void theLayoutDrawsEveryPlannedBlockInAllFourRotations(final GameTestHelper helper) {
		int cells = 0;
		for (int seed = 0; seed < SEEDS; seed++) {
			CampPlan plan = CampPlan.derive(RandomSource.create(seed));
			for (int turns = 0; turns < TURNS; turns++) {
				MinerTestSupport.Captured drawn = MinerTestSupport.capture(seed, turns);
				Rotation rotation = Rotation.values()[turns];
				for (Map.Entry<Integer, BlockState> cell : plan.blocks().entrySet()) {
					CampPlan.Spot spot = CampPlan.unpack(cell.getKey());
					if (spot.equals(plan.core()) || spot.equals(plan.vault())) {
						// The core and the vault replace the block the plan puts there.
						continue;
					}
					BlockState want = cell.getValue().rotate(rotation);
					BlockState got = drawn.state(spot.x(), spot.y(), spot.z());
					helper.assertTrue(want.getBlock() == got.getBlock(),
						"seed " + seed + " turn " + turns + " drew " + got + " at " + spot + ", not " + want);
					cells++;
				}
			}
		}
		MinerTestSupport.result("campDraw", "seeds=" + SEEDS + " turns=" + TURNS + " cells=" + cells);
		helper.succeed();
	}

	@GameTest
	public void theTunnelReachesTheCampFromTheMouthInEveryRotation(final GameTestHelper helper) {
		int walked = 0;
		int dugMost = 0;
		for (int seed = 0; seed < SEEDS; seed++) {
			CampPlan plan = CampPlan.derive(RandomSource.create(seed));
			for (int turns = 0; turns < TURNS; turns++) {
				MinerTestSupport.Captured drawn = MinerTestSupport.capture(seed, turns);
				MinerTestSupport.Walk walk = new MinerTestSupport.Walk(drawn);
				CampPlan.Spot mouth = plan.mouth();
				Set<BlockPos> seen = walk.reach(mouth.x(), mouth.y(), mouth.z());
				CampPlan.Spot tent = plan.tent();
				int budget = plan.collapses() * CampPlan.TUNNEL_WIDTH * 2;
				helper.assertTrue(seen.contains(new BlockPos(tent.x(), tent.y(), tent.z())),
					"seed " + seed + " turn " + turns + " walks nowhere into the tent from the mouth: " + seen.size()
						+ " spots reached to z " + deepest(seen) + ", " + walk.dug().size() + " rubble blocks dug of " + budget);
				CampPlan.Spot lectern = plan.lectern();
				helper.assertTrue(walk.beside(seen, new BlockPos(lectern.x(), lectern.y() + 1, lectern.z())),
					"seed " + seed + " turn " + turns + " cannot stand at the lectern");
				CampPlan.Spot core = plan.core();
				helper.assertTrue(walk.beside(seen, new BlockPos(core.x(), core.y(), core.z())),
					"seed " + seed + " turn " + turns + " cannot stand at the tally post");
				helper.assertTrue(walk.dug().size() <= budget,
					"seed " + seed + " turn " + turns + " dug " + walk.dug().size() + " rubble blocks, more than the " + budget + " the collapses hold");
				dugMost = Math.max(dugMost, walk.dug().size());
				walked++;
			}
		}
		MinerTestSupport.result("campWalk", "seeds=" + SEEDS + " turns=" + TURNS + " walked=" + walked + " rubbleDug=" + dugMost);
		helper.succeed();
	}

	/** The far end of a reach, for a failure that says where the walk stopped. */
	private static int deepest(final Set<BlockPos> seen) {
		int z = Integer.MIN_VALUE;
		for (BlockPos pos : seen) {
			z = Math.max(z, pos.getZ());
		}
		return z;
	}

	@GameTest
	public void theCampStandsWholeWithItsTentRailsFloorAndMarkers(final GameTestHelper helper) {
		int checked = 0;
		for (int seed = 0; seed < SEEDS; seed++) {
			CampPlan plan = CampPlan.derive(RandomSource.create(seed));
			MinerTestSupport.Captured drawn = MinerTestSupport.capture(seed, 0);
			CampPlan.Spot lectern = plan.lectern();
			helper.assertTrue(drawn.state(lectern.x(), lectern.y() + 1, lectern.z()).is(Blocks.LECTERN), "seed " + seed + " left no lectern");
			helper.assertTrue(drawn.markers(CampMarkers.LECTERN).equals(List.of(drawn.world(lectern.x(), lectern.y() + 1, lectern.z()))),
				"seed " + seed + " marks another lectern: " + drawn.markers(CampMarkers.LECTERN));
			CampPlan.Spot core = plan.core();
			helper.assertTrue(drawn.state(core.x(), core.y(), core.z()).is(RuinBlocks.CORE), "seed " + seed + " left no tally post core");
			helper.assertTrue(drawn.markers(RuinMarkers.CORE).equals(List.of(drawn.world(core.x(), core.y(), core.z()))),
				"seed " + seed + " marks another core");
			helper.assertTrue(drawn.markers(RuinMarkers.VAULT).equals(List.of(drawn.world(plan.vault().x(), plan.vault().y(), plan.vault().z()))),
				"seed " + seed + " marks another vault");
			CampPlan.Spot tent = plan.tent();
			helper.assertTrue(drawn.state(tent.x() - 2, tent.y(), tent.z() - 2).is(Blocks.WOOL.white()), "seed " + seed + " has no tent wall");
			helper.assertTrue(drawn.state(tent.x() - 1, tent.y() + 2, tent.z() - 1).is(Blocks.WOOL.white()), "seed " + seed + " has no tent roof");
			helper.assertTrue(drawn.state(tent.x(), tent.y(), tent.z()).isAir(), "seed " + seed + " fills the tent");
			helper.assertTrue(drawn.state(tent.x(), tent.y(), tent.z() - 2).isAir(), "seed " + seed + " walls the tent door");
			CampPlan.Spot fire = plan.fire();
			helper.assertTrue(drawn.state(fire.x(), fire.y() - 1, fire.z()).is(Blocks.COARSE_DIRT), "seed " + seed + " has no camp floor");
			helper.assertTrue(drawn.state(core.x() + 1, core.y() - 1, core.z()).isAir(), "seed " + seed + " leaves the camp uncleared");
			List<CampPlan.Spot> centre = plan.centre();
			int railEnd = centre.size() - CampPlan.RAIL_STOP;
			int rails = 0;
			int lateRails = 0;
			for (int i = 0; i < centre.size(); i++) {
				CampPlan.Spot spot = centre.get(i);
				if (drawn.state(spot.x(), spot.y(), spot.z()).is(Blocks.RAIL)) {
					rails++;
					if (i >= railEnd) {
						lateRails++;
					}
				}
			}
			helper.assertTrue(rails > 0 && rails <= railEnd, "seed " + seed + " drew " + rails + " rail blocks of " + railEnd);
			helper.assertTrue(lateRails == 0, "seed " + seed + " runs the rails to the lectern");
			CampPlan.Spot mouth = plan.mouth();
			helper.assertTrue(drawn.state(mouth.x(), mouth.y() + 1, mouth.z()).isAir(), "seed " + seed + " plugs the mouth");
			helper.assertTrue(drawn.markers(CampMarkers.MOUTH).equals(List.of(drawn.world(mouth.x(), mouth.y(), mouth.z()))), "seed " + seed + " marks another mouth");
			helper.assertTrue(drawn.markers(CampMarkers.MINER).size() == CampPlan.MINERS + 1, "seed " + seed + " marks " + drawn.markers(CampMarkers.MINER).size() + " miner spots");
			helper.assertTrue(drawn.markers(CampMarkers.RAT).size() == CampPlan.RATS, "seed " + seed + " marks " + drawn.markers(CampMarkers.RAT).size() + " rat spots");
			int spawns = drawn.markers(RuinMarkers.SPAWN).size();
			helper.assertTrue(spawns == CampPlan.MINERS + 1 + CampPlan.RATS, "seed " + seed + " marks " + spawns + " roster spots");
			checked++;
		}
		MinerTestSupport.result("campWhole", "seeds=" + checked + " tents=" + SEEDS + " rails=yes floors=yes marks=" + (CampPlan.MINERS + 1 + CampPlan.RATS));
		helper.succeed();
	}
}
