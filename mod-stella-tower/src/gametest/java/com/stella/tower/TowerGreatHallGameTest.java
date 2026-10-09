package com.stella.tower;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** The boss floor is a great hall with the portal on a stair of eight steps and no safe room; floor 1 holds the workstations. */
public class TowerGreatHallGameTest {
	private static final long SEED = 0x6A1B_4A11_0004L;

	/** Catches a boss floor that is not a hall, has a safe zone or stations, or whose walk to the portal fails. */
	@GameTest(maxTicks = 200)
	public void everyBossFloorIsAHallWithAnEightStepStairAndNoSafeRoom(final GameTestHelper helper) {
		int halls = 0;
		int wrong = 0;
		int stuck = 0;
		int zones = 0;
		int stations = 0;
		int shortest = Integer.MAX_VALUE;
		for (int floor = 10; floor <= 400; floor += 10) {
			TowerPlan plan = BiomeGen.plan(SEED, floor);
			halls++;
			wrong += plan.biome().biome() == FloorBiome.GREAT_HALL && plan.biome().steps() == 8 && plan.biome().ceiling() != null ? 0 : 1;
			zones += plan.safeZones().size();
			stations += plan.stations().size();
			TowerVolume volume = TowerVolume.of(plan);
			stuck += volume.reaches(plan) ? 0 : 1;
			int[] dist = TowerWalk.distances(volume, plan.arrivalX(), 0, plan.arrivalZ());
			for (int[] goal : TowerVolume.portalGoals(plan)) {
				int steps = dist[TowerWalk.index(goal[0], goal[1], goal[2])];
				if (steps >= 0) {
					shortest = Math.min(shortest, steps);
				}
			}
		}
		TowerTestSupport.result("hallPlans", "halls=" + halls + " wrong=" + wrong + " stuck=" + stuck + " zones=" + zones + " stations=" + stations + " shortest=" + shortest);
		helper.assertTrue(wrong == 0, wrong + " boss plans are not great halls of eight steps under a vault");
		helper.assertTrue(stuck == 0, stuck + " halls have no walk from the arrival to the portal");
		helper.assertTrue(zones == 0 && stations == 0, "a boss floor holds " + zones + " safe zones and " + stations + " stations");
		helper.assertTrue(shortest >= 40, "the shortest walk down the nave is " + shortest);
		helper.succeed();
	}

	/** Catches a floor 1 without its workstations in the safe zone, or an ordinary floor with some. */
	@GameTest(maxTicks = 100)
	public void floorOneHoldsTheCraftingTableTheFurnaceAndTheAnvilInItsSafeZone(final GameTestHelper helper) {
		int rows = 0;
		int inZone = 0;
		int solid = 0;
		int strays = 0;
		for (long seed = SEED; seed < SEED + 12; seed++) {
			TowerPlan plan = BiomeGen.plan(seed, 1);
			List<TowerPlan.Station> stations = plan.stations();
			rows += stations.size() == 3 ? 1 : 0;
			TowerVolume volume = TowerVolume.of(plan);
			for (TowerPlan.Station station : stations) {
				inZone += plan.inSafeZone(station.x(), 0, station.z()) ? 1 : 0;
				solid += volume.kind(station.x(), 0, station.z()) == TowerVolume.STATION ? 1 : 0;
				strays += station.x() == plan.arrivalX() && station.z() == plan.arrivalZ() ? 1 : 0;
			}
			boolean reaches = volume.reaches(plan);
			helper.assertTrue(reaches, "floor 1 of seed " + seed + " has no walk to the portal with its stations in");
			java.util.Map<BlockPos, BlockState> blocks = TowerFloors.workstations(plan);
			boolean table = blocks.values().stream().anyMatch(state -> state.is(Blocks.CRAFTING_TABLE));
			boolean furnace = blocks.values().stream().anyMatch(state -> state.is(Blocks.FURNACE));
			boolean anvil = blocks.values().stream().anyMatch(state -> state.is(Blocks.ANVIL));
			helper.assertTrue(table && furnace && anvil, "floor 1 of seed " + seed + " lacks one of the three blocks");
			helper.assertTrue(BiomeGen.plan(seed, 2).stations().isEmpty() && BiomeGen.plan(seed, 7).stations().isEmpty(), "a floor above the first holds stations");
		}
		TowerTestSupport.result("floorOneStations", "rows=" + rows + "/12 inZone=" + inZone + " solid=" + solid + " onTheArrival=" + strays);
		helper.assertTrue(rows == 12 && inZone == 36 && solid == 36 && strays == 0, "rows=" + rows + " inZone=" + inZone + " solid=" + solid + " onTheArrival=" + strays);
		helper.succeed();
	}

	/** Catches a built hall that is not as planned: a stair of eight, a gallery, a vault, the boss mist, a boss on level ground. */
	@GameTest(maxTicks = 600)
	public void aBuiltHallHasItsStairItsGalleryItsVaultAndAFreeBoss(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerElites.BOSS_EVERY == 0);
		TowerFloors.ensure(tower, floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		BlockPos origin = plan.origin();
		int s = plan.biome().steps();
		int f = plan.portalFacing();
		int stairs = 0;
		for (int k = 1; k < s; k++) {
			stairs += tower.getBlockState(origin.offset(plan.portalX() + f * (2 + k), s - k - 1, plan.portalZ())).is(TowerBlocks.TOWER_STAIR) ? 1 : 0;
		}
		int gallery = 0;
		for (int x = 3; x < TowerLayout.SIZE - 3; x++) {
			gallery += tower.getBlockState(origin.offset(x, 6, 10)).is(Blocks.CRACKED_STONE_BRICKS) ? 1 : 0;
		}
		int ceiling = plan.biome().ceiling()[30][31];
		boolean vault = tower.getBlockState(origin.offset(30, plan.roofY(), 30)).is(TowerBlocks.TOWER_WALL)
			&& !tower.getBlockState(origin.offset(30, ceiling, 31)).isAir() && tower.getBlockState(origin.offset(30, ceiling - 1, 31)).isAir();
		boolean mist = tower.getBiome(origin.offset(32, 2, 32)).is(TowerBuffs.BOSS_BIOME);
		int[] model = TowerWalk.distances(TowerVolume.of(plan), plan.arrivalX(), 0, plan.arrivalZ());
		int[] world = TowerWalk.distances(TowerWalk.world(tower, floor), plan.arrivalX(), 0, plan.arrivalZ());
		int off = 0;
		for (int i = 0; i < model.length; i++) {
			off += (model[i] >= 0) != (world[i] >= 0) ? 1 : 0;
		}
		Mob boss = TowerElites.rise(tower, floor, TowerRank.BOSS);
		boolean onGround = boss != null && boss.onGround() || boss != null && Math.abs(boss.getY() - TowerLayout.BASE_Y) < 1.5;
		TowerTestSupport.result("hallBuilt", "floor=" + floor + " steps=" + s + " stairs=" + stairs + "/" + (s - 1) + " gallery=" + gallery + " vault=" + vault + " mist=" + mist
			+ " off=" + off + " boss=" + (boss != null) + " atBase=" + onGround);
		if (boss != null) {
			boss.discard();
		}
		helper.assertTrue(s == 8 && stairs == s - 1, "the stair holds " + stairs + " of " + (s - 1) + " steps of eight");
		helper.assertTrue(gallery > 40, "the gallery slab holds " + gallery + " blocks in a row");
		helper.assertTrue(vault, "the roof is not rock");
		helper.assertTrue(mist, "the hall is not wrapped in the boss mist");
		helper.assertTrue(off == 0, "the built hall disagrees with its model on " + off + " spots");
		helper.assertTrue(boss != null && onGround, "the boss did not rise on level ground");
		helper.succeed();
	}
}
