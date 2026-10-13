package com.stella.tower;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;

/** The hub at every floor's arrival, the level of the first floor, how crowded a floor is, and where the breeze first rises. */
public class TowerHubGameTest {
	private static final long SEED = 0x7A11_B10E_00A5L;

	private static boolean ring(final TowerMaze.Zone zone, final int x, final int z) {
		return x == zone.x() - 1 || x == zone.x() + BiomeGen.HUB || z == zone.z() - 1 || z == zone.z() + BiomeGen.HUB;
	}

	private static boolean isDoor(final byte code) {
		return code == BiomeBlocks.DOOR_EAST_LOWER || code == BiomeBlocks.DOOR_EAST_UPPER || code == BiomeBlocks.DOOR_WEST_LOWER || code == BiomeBlocks.DOOR_WEST_UPPER;
	}

	/** Catches a floor with no hub, a hub with a gap in its wall or its roof, a second door, or a door off the arrival's line. */
	@GameTest(maxTicks = 200)
	public void everyFloorButAGreatHallStartsInAClosedRoomWithOneDoor(final GameTestHelper helper) {
		int hubs = 0;
		int doors = 0;
		int lamps = 0;
		for (int floor = 1; floor <= 60; floor++) {
			TowerPlan plan = BiomeGen.plan(SEED, floor);
			boolean hall = floor % TowerElites.BOSS_EVERY == 0;
			helper.assertTrue(plan.safeZones().size() == (hall ? 0 : 1), "floor " + floor + " holds " + plan.safeZones().size() + " safe zones");
			if (hall) {
				continue;
			}
			hubs++;
			TowerMaze.Zone zone = plan.safeZones().get(0);
			TowerVolume volume = TowerVolume.of(plan);
			helper.assertTrue(plan.inSafeZone(plan.arrivalX(), 0, plan.arrivalZ()), "floor " + floor + ": the arrival is outside the hub");
			int toward = plan.portalX() >= plan.arrivalX() ? 1 : -1;
			int doorX = toward == 1 ? zone.x() + BiomeGen.HUB : zone.x() - 1;
			int found = 0;
			for (int x = zone.x() - 1; x <= zone.x() + BiomeGen.HUB; x++) {
				for (int z = zone.z() - 1; z <= zone.z() + BiomeGen.HUB; z++) {
					helper.assertTrue(volume.kind(x, BiomeGen.HUB_HEIGHT, z) != TowerVolume.AIR, "floor " + floor + ": a gap in the hub's roof at " + x + "," + z);
					for (int y = 0; y < BiomeGen.HUB_HEIGHT; y++) {
						boolean wall = ring(zone, x, z);
						boolean air = volume.kind(x, y, z) == TowerVolume.AIR;
						if (wall) {
							byte code = volume.code(x, y, z);
							if (isDoor(code)) {
								found++;
								helper.assertTrue(x == doorX && z == plan.arrivalZ() && y < 2, "floor " + floor + ": a door at " + x + "," + y + "," + z + ", not on the arrival's line");
							} else {
								helper.assertTrue(!air, "floor " + floor + ": a gap in the hub's wall at " + x + "," + y + "," + z);
								lamps += code == BiomeBlocks.SEA_LANTERN ? 1 : 0;
							}
						} else if (y >= 1 && y < BiomeGen.HUB_HEIGHT - 1 && volume.kind(x, y, z) != TowerVolume.AIR && volume.kind(x, y, z) != TowerVolume.FRAME
							&& volume.kind(x, y, z) != TowerVolume.BACK_FILL && volume.kind(x, y, z) != TowerVolume.SOUL_SOIL && volume.kind(x, y, z) != TowerVolume.SOUL_FIRE) {
							helper.fail("floor " + floor + ": something stands in the hub at " + x + "," + y + "," + z);
						}
					}
				}
			}
			helper.assertTrue(found == 2, "floor " + floor + " has " + found + " door blocks, not a lower and an upper");
			doors += found / 2;
		}
		TowerTestSupport.result("hubs", "floors=60 hubs=" + hubs + " doors=" + doors + " wallLamps=" + lamps + " hub=" + BiomeGen.HUB + "x" + BiomeGen.HUB + "x" + BiomeGen.HUB_HEIGHT);
		helper.assertTrue(lamps >= hubs * 16, "the hubs hold " + lamps + " wall lamps for " + hubs + " hubs");
		helper.succeed();
	}

	/** Catches a hub with no crafting table, furnace or anvil, or with one standing in its wall. */
	@GameTest(maxTicks = 200)
	public void everyHubHoldsACraftingTableAFurnaceAndAnAnvilOnFreeFloor(final GameTestHelper helper) {
		for (int floor = 1; floor <= 60; floor++) {
			if (floor % TowerElites.BOSS_EVERY == 0) {
				continue;
			}
			TowerPlan plan = BiomeGen.plan(SEED, floor);
			TowerMaze.Zone zone = plan.safeZones().get(0);
			List<TowerPlan.Station> stations = plan.stations();
			helper.assertTrue(stations.size() == 3 && stations.stream().map(TowerPlan.Station::kind).distinct().count() == 3, "floor " + floor + " holds " + stations.size() + " stations");
			TowerVolume volume = TowerVolume.of(plan);
			for (TowerPlan.Station station : stations) {
				helper.assertTrue(zone.contains(station.x(), station.z()) && !ring(zone, station.x(), station.z()), "floor " + floor + ": a station outside the hub's floor");
				helper.assertTrue(volume.kind(station.x(), 1, station.z()) == TowerVolume.AIR, "floor " + floor + ": a station has a block over it");
			}
		}
		helper.succeed();
	}

	/** Catches a floor above the first without its way back inside the hub, or a first floor that has one. */
	@GameTest(maxTicks = 200)
	public void theBackPortalStandsInsideTheHubFromFloorTwo(final GameTestHelper helper) {
		int placed = 0;
		for (int floor = 1; floor <= 60; floor++) {
			if (floor % TowerElites.BOSS_EVERY == 0) {
				continue;
			}
			TowerPlan plan = BiomeGen.plan(SEED, floor);
			TowerMaze.Zone zone = plan.safeZones().get(0);
			TowerBackPortals.Spot spot = TowerVolume.of(plan).backPortalSpot();
			if (floor == 1) {
				helper.assertTrue(spot == null, "floor 1 has a way back");
				continue;
			}
			helper.assertTrue(spot != null, "floor " + floor + " has no room for its way back");
			boolean inside = zone.contains(spot.x(), spot.z()) && zone.contains(spot.x(), spot.z() + TowerBackPortals.FRAME_WIDTH - 1);
			helper.assertTrue(inside, "floor " + floor + ": the way back stands outside the hub at " + spot);
			placed++;
		}
		TowerTestSupport.result("hubBackPortals", "placed=" + placed);
		helper.succeed();
	}

	/** Catches a built hub that is open, door-less, or short of a station, and a mob raised in or at it. */
	@GameTest(maxTicks = 1200)
	public void aBuiltHubIsClosedHoldsItsStationsAndRaisesNoMobInside(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerElites.BOSS_EVERY != 0 && f % TowerElites.ELITE_EVERY != 0);
		TowerFloors.ensure(tower, floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		TowerMaze.Zone zone = plan.safeZones().get(0);
		BlockPos origin = plan.origin();
		int toward = plan.portalX() >= plan.arrivalX() ? 1 : -1;
		BlockPos door = origin.offset(toward == 1 ? zone.x() + BiomeGen.HUB : zone.x() - 1, 0, plan.arrivalZ());
		helper.assertTrue(tower.getBlockState(door).is(Blocks.DARK_OAK_DOOR) && tower.getBlockState(door.above()).is(Blocks.DARK_OAK_DOOR), "the hub's door is " + tower.getBlockState(door));
		int sealed = 0;
		for (int x = zone.x() - 1; x <= zone.x() + BiomeGen.HUB; x++) {
			for (int z = zone.z() - 1; z <= zone.z() + BiomeGen.HUB; z++) {
				BlockState roof = tower.getBlockState(origin.offset(x, BiomeGen.HUB_HEIGHT, z));
				helper.assertTrue(!roof.isAir(), "the built hub has a gap in its roof at " + x + "," + z);
				sealed++;
			}
		}
		for (TowerPlan.Station station : plan.stations()) {
			BlockState state = tower.getBlockState(origin.offset(station.x(), 0, station.z()));
			boolean right = switch (station.kind()) {
				case 0 -> state.is(Blocks.CRAFTING_TABLE);
				case 1 -> state.is(Blocks.FURNACE);
				default -> state.is(Blocks.ANVIL);
			};
			helper.assertTrue(right, "station " + station.kind() + " built as " + state);
		}
		helper.assertTrue(floor < 2 || TowerBackPortals.find(tower, floor) != null, "the built hub has no way back");
		int inside = 0;
		for (int i = 0; i < 200; i++) {
			Mob mob = TowerMobs.spawnOne(tower, floor);
			if (mob != null) {
				BlockPos at = mob.blockPosition().subtract(origin);
				boolean in = at.getX() >= zone.x() - 1 && at.getX() <= zone.x() + BiomeGen.HUB && at.getZ() >= zone.z() - 1 && at.getZ() <= zone.z() + BiomeGen.HUB;
				inside += in ? 1 : 0;
				mob.discard();
			}
		}
		TowerTestSupport.result("builtHub", "floor=" + floor + " roofBlocks=" + sealed + " raisedInside=" + inside);
		helper.assertTrue(inside == 0, inside + " mobs were raised in the hub");
		helper.succeed();
	}

	/** Catches a first floor that is not level 30, and a curve that no longer climbs by the rule. */
	@GameTest
	public void theFirstFloorIsLevelThirtyAndTheCurveClimbsFromThere(final GameTestHelper helper) {
		helper.assertTrue(TowerMobs.FIRST_FLOOR_LEVEL == 30 && TowerMobs.levelOf(1) == 30, "floor 1 is level " + TowerMobs.levelOf(1));
		helper.assertTrue(TowerMobs.levelOf(2) == 31 && TowerMobs.levelOf(11) == 50, "floor 11 is level " + TowerMobs.levelOf(11));
		for (int floor = 2; floor <= 200; floor++) {
			helper.assertTrue(TowerMobs.levelOf(floor) > TowerMobs.levelOf(floor - 1), "floor " + floor + " is no higher than the floor below");
		}
		helper.succeed();
	}

	/** Catches a floor that is full from the first, or a count that falls, or one that passes the base cap. */
	@GameTest
	public void aFloorFillsUpFromTwentyFourToItsCapAsTheTowerClimbs(final GameTestHelper helper) {
		helper.assertTrue(TowerMobs.capOf(1) == TowerMobs.FIRST_FLOOR_CAP && TowerMobs.FIRST_FLOOR_CAP < TowerMobs.FLOOR_CAP, "floor 1 holds " + TowerMobs.capOf(1));
		int full = -1;
		for (int floor = 1; floor <= 200; floor++) {
			helper.assertTrue(TowerMobs.capOf(floor) <= TowerMobs.FLOOR_CAP && TowerMobs.capOf(floor) >= TowerMobs.capOf(Math.max(1, floor - 1)), "floor " + floor + " holds " + TowerMobs.capOf(floor));
			if (full < 0 && TowerMobs.capOf(floor) == TowerMobs.FLOOR_CAP) {
				full = floor;
			}
		}
		TowerTestSupport.result("floorCaps", "floor1=" + TowerMobs.capOf(1) + " floor10=" + TowerMobs.capOf(10) + " full=" + TowerMobs.capOf(200) + " fullFrom=" + full);
		helper.assertTrue(full > 10, "a floor is full from floor " + full + ": no ramp");
		helper.succeed();
	}

	/** Catches a breeze in a pool at or below floor 20, and a pool that never gets one above it. */
	@GameTest
	public void theBreezeFirstRisesOnFloorTwentyOne(final GameTestHelper helper) {
		int above = 0;
		for (FloorBiome biome : FloorBiome.DRAWN) {
			List<EntityType<? extends Mob>> pool = BiomePools.of(biome);
			for (int floor : new int[] {1, 10, 20}) {
				helper.assertTrue(!TowerMobs.forFloor(pool, floor).contains(EntityTypes.BREEZE), biome + " raises a breeze on floor " + floor);
				helper.assertTrue(TowerMobs.forFloor(pool, floor).size() >= pool.size() - 1 && TowerMobs.forFloor(pool, floor).size() >= 4, biome + " lost more than the breeze");
			}
			above += TowerMobs.forFloor(pool, 21).equals(pool) && pool.contains(EntityTypes.BREEZE) ? 1 : 0;
		}
		helper.assertTrue(above > 0, "no biome raises a breeze on floor 21");
		helper.assertTrue(!TowerMobs.pool(20).contains(EntityTypes.BREEZE) && TowerMobs.pool(21).contains(EntityTypes.BREEZE), "the older pool's breeze floor is not 21");
		helper.succeed();
	}
}
