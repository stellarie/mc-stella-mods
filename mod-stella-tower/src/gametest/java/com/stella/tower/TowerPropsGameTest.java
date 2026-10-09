package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;

/** The things the dead left: on every biome floor, on the dais, and in the lost rooms, where they change nothing else. */
public class TowerPropsGameTest {
	private static final long SEED = 0x70F5_0011_0005L;
	/** The blocks a prop is made of. */
	private static final Set<Block> PROPS = Set.of(Blocks.SKELETON_SKULL, Blocks.SKELETON_WALL_SKULL, Blocks.WITHER_SKELETON_SKULL, Blocks.ZOMBIE_HEAD,
		Blocks.BARREL, Blocks.CHIPPED_ANVIL, Blocks.GRINDSTONE, Blocks.BONE_BLOCK, Blocks.CANDLE, Blocks.COBWEB);

	/** Catches a floor with too few props, too many barrels, or props on the arrival pad, on the stair, or on the road's margin. */
	@GameTest(maxTicks = 200)
	public void everyBiomeFloorHoldsItsPropsAndNoneInTheWay(final GameTestHelper helper) {
		StringBuilder report = new StringBuilder();
		int thin = 0;
		int barrelly = 0;
		int stray = 0;
		for (FloorBiome biome : FloorBiome.values()) {
			int seen = 0;
			int floors = 0;
			int walls = 0;
			for (int floor = 2; seen < 2 && floor < 3000; floor++) {
				boolean hall = biome == FloorBiome.GREAT_HALL;
				if (hall != (floor % TowerElites.BOSS_EVERY == 0) || !hall && FloorBiomes.of(SEED, floor) != biome) {
					continue;
				}
				seen++;
				TowerPlan plan = BiomeGen.plan(SEED, floor);
				int[] placed = plan.biome().placed();
				int barrels = 0;
				for (int i = 0; i < placed.length; i += 4) {
					byte code = (byte) placed[i + 3];
					if (!BiomeBlocks.isProp(code)) {
						continue;
					}
					int x = placed[i];
					int z = placed[i + 2];
					boolean high = placed[i + 1] - plan.biome().feet()[x][z] >= 2;
					floors += high ? 0 : 1;
					walls += high ? 1 : 0;
					barrels += code == BiomeBlocks.BARREL ? 1 : 0;
					boolean onThePad = Math.abs(x - plan.arrivalX()) <= 2 && Math.abs(z - plan.arrivalZ()) <= 2 && !high;
					stray += onThePad || plan.biome().inDais(plan, x, z) ? 1 : 0;
				}
				barrelly += barrels > 30 ? 1 : 0;
			}
			thin += seen == 0 || floors < 30 * seen || walls < 8 * seen ? 1 : 0;
			report.append(biome).append(" floor=").append(floors).append(" wall=").append(walls).append("; ");
		}
		TowerTestSupport.result("biomeProps", report + "thin=" + thin + " barrelly=" + barrelly + " stray=" + stray);
		helper.assertTrue(thin == 0, thin + " biomes hold fewer props than their share: " + report);
		helper.assertTrue(barrelly == 0 && stray == 0, "barrelly=" + barrelly + " stray=" + stray);
		helper.succeed();
	}

	/** Catches a dais without its candles and banners. */
	@GameTest(maxTicks = 400)
	public void theDaisHoldsItsCandlesAndBanners(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerElites.BOSS_EVERY != 0);
		TowerFloors.ensure(tower, floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		BlockPos origin = plan.origin();
		int s = plan.biome().steps();
		int f = plan.portalFacing();
		int candles = 0;
		for (int k = 1; k < s; k++) {
			for (int z : new int[] {plan.portalZ() - 3 - k, plan.portalZ() + 4 + k}) {
				candles += tower.getBlockState(origin.offset(plan.portalX() + f * (2 + k), s - k, z)).is(Blocks.CANDLE) ? 1 : 0;
			}
		}
		int banners = 0;
		for (int z : new int[] {plan.portalZ() - 1, plan.portalZ() + 2}) {
			banners += tower.getBlockState(origin.offset(plan.portalX() - f * 2, s + 3, z)).getBlock() instanceof WallBannerBlock ? 1 : 0;
		}
		TowerTestSupport.result("daisProps", "floor=" + floor + " candles=" + candles + "/" + 2 * (s - 1) + " banners=" + banners);
		helper.assertTrue(candles == 2 * (s - 1), "the dais holds " + candles + " of " + 2 * (s - 1) + " candles");
		helper.assertTrue(banners == 2, "the dais holds " + banners + " of 2 banners");
		helper.succeed();
	}

	/**
	 * Catches a lost room's prop pass that changes the room: every cell of the built room is the plan's block, except
	 * where the plan leaves air and a prop, a trap, a chest, a station or the portal stands. The props keep off the
	 * traps, the chests, the gates, the portal's approach and the arrival, and the walk to the portal still stands.
	 */
	@GameTest(maxTicks = 400)
	public void aLostRoomGainsPropsAndNothingElseChanges(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floorProps = 0;
		int wallProps = 0;
		int changed = 0;
		int crowded = 0;
		int stuck = 0;
		for (int level : new int[] {45, 120, 310}) {
			ServerPlayer player = TowerLostRoomGameTest.withLevel(helper, level);
			LostRoomData.Instance instance = LostRooms.open(tower, TowerLostRoomGameTest.freshHut(), TowerLostRoomGameTest.anywhere(helper), List.of(player));
			TowerTestSupport.leave(helper, player);
			int room = instance.room();
			TowerPlan plan = LostRooms.plan(tower.getSeed(), room);
			TowerVolume volume = TowerVolume.of(plan);
			TowerTheme theme = plan.theme();
			BlockPos origin = TowerLayout.origin(room);
			List<int[]> fixtures = new ArrayList<>();
			for (TowerTraps.Trap trap : TowerTraps.layout(tower.getSeed(), LostRooms.looksFloor(room), plan)) {
				fixtures.add(new int[] {trap.x(), trap.z()});
			}
			for (TowerPlan.ChestSpot spot : plan.chestSpots()) {
				for (TowerPlan.Spot cell : spot.cells()) {
					fixtures.add(new int[] {cell.x(), cell.z()});
				}
			}
			for (TowerPlan.Gate gate : plan.gates()) {
				for (TowerPlan.Spot cell : gate.cells()) {
					fixtures.add(new int[] {cell.x(), cell.z()});
				}
			}
			for (int x = 0; x < TowerLayout.SIZE; x++) {
				for (int z = 0; z < TowerLayout.SIZE; z++) {
					for (int y = TowerVolume.LOW + 1; y < TowerVolume.HIGH; y++) {
						BlockState world = tower.getBlockState(origin.offset(x, y, z));
						BlockState planned = TowerFloors.stateOf(volume, theme, plan, x, y, z);
						if (world == planned) {
							continue;
						}
						Block block = world.getBlock();
						boolean prop = PROPS.contains(block) && planned.isAir();
						boolean fixture = block instanceof TowerTrapBlock || block == TowerBlocks.TOWER_CHEST || block == TowerBlocks.TOWER_PORTAL
							|| block == Blocks.CRAFTING_TABLE || block == Blocks.FURNACE || block == Blocks.ANVIL;
						if (prop) {
							boolean high = y >= 3;
							floorProps += high ? 0 : 1;
							wallProps += high ? 1 : 0;
							for (int[] fixtureAt : fixtures) {
								crowded += Math.max(Math.abs(x - fixtureAt[0]), Math.abs(z - fixtureAt[1])) <= TowerProps.KEEP_OFF ? 1 : 0;
							}
							crowded += Math.max(Math.abs(x - plan.portalX()), Math.abs(z - plan.portalZ())) <= TowerProps.PORTAL_CLEAR
								|| Math.max(Math.abs(x - plan.arrivalX()), Math.abs(z - plan.arrivalZ())) <= TowerProps.ARRIVAL_CLEAR ? 1 : 0;
						} else if (!fixture) {
							changed++;
						}
					}
				}
			}
			boolean reaches = false;
			int[] dist = TowerWalk.distances(TowerWalk.world(tower, room), plan.arrivalX(), 0, plan.arrivalZ());
			for (int[] goal : TowerVolume.portalGoals(plan)) {
				reaches |= dist[TowerWalk.index(goal[0], goal[1], goal[2])] >= 0;
			}
			stuck += reaches ? 0 : 1;
		}
		TowerTestSupport.result("lostRoomProps", "floorProps=" + floorProps + " wallProps=" + wallProps + " changedOtherwise=" + changed + " crowded=" + crowded + " stuck=" + stuck);
		helper.assertTrue(floorProps >= 3 * 40 && wallProps >= 3 * 20, "the rooms hold " + floorProps + " floor props and " + wallProps + " wall props");
		helper.assertTrue(changed == 0, changed + " cells of the rooms differ from the plan beyond props, traps, chests, stations and the portal");
		helper.assertTrue(crowded == 0, crowded + " props lie too near a trap, a chest, a gate, the portal or the arrival");
		helper.assertTrue(stuck == 0, stuck + " rooms have no walk to the portal with the props in");
		helper.succeed();
	}
}
