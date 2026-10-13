package com.stella.tower;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** The biome floor: its plan walks from arrival to portal, it fits its volume, it is built as planned, and it is painted. */
public class TowerBiomeFloorGameTest {
	private static final long SEED = 0x7A11_B10E_0001L;

	private static TowerPlan drawn(final long seed, final int floor) {
		return BiomeGen.plan(seed, floor);
	}

	/** Catches a draw that leaves the portal out of a body's reach, or falls back to the plain yard too often. */
	@GameTest(maxTicks = 100)
	public void everyDrawnBiomeFloorWalksFromTheArrivalToThePortal(final GameTestHelper helper) {
		int floors = 150;
		int fallbacks = 0;
		int stuck = 0;
		int shortest = Integer.MAX_VALUE;
		int longest = 0;
		long started = System.nanoTime();
		for (int floor = 1; floor <= floors; floor++) {
			TowerPlan plan = drawn(SEED + floor * 31L, floor);
			TowerVolume volume = TowerVolume.of(plan);
			fallbacks += plan.attempt() == TowerPlan.FALLBACK ? 1 : 0;
			boolean reaches = plan.isBiome() && plan.archetype() == TowerArchetype.BIOME && volume.reaches(plan);
			stuck += reaches ? 0 : 1;
			int[] dist = TowerWalk.distances(volume, plan.arrivalX(), 0, plan.arrivalZ());
			for (int[] goal : TowerVolume.portalGoals(plan)) {
				int steps = dist[TowerWalk.index(goal[0], goal[1], goal[2])];
				if (steps >= 0) {
					shortest = Math.min(shortest, steps);
					longest = Math.max(longest, steps);
				}
			}
		}
		double millis = (System.nanoTime() - started) / 1.0E6 / floors;
		TowerTestSupport.result("biomeWalks", "floors=" + floors + " stuck=" + stuck + " fallbacks=" + fallbacks + " steps=" + shortest + ".." + longest
			+ " millisPerPlan=" + millis);
		helper.assertTrue(stuck == 0, stuck + " of " + floors + " biome plans have no walk from arrival to portal");
		helper.assertTrue(fallbacks * 200 <= floors, fallbacks + " of " + floors + " floors fell back to the plain yard");
		helper.assertTrue(shortest >= 30, "the shortest walk is " + shortest + " steps: the spine does not wind");
		helper.succeed();
	}

	/** Catches a floor with no hub zone, or a great hall with one, or an arrival outside its hub. */
	@GameTest(maxTicks = 100)
	public void everyFloorButAGreatHallHoldsOneSafeZoneRoundItsArrival(final GameTestHelper helper) {
		int withZone = 0;
		List<Integer> wrong = new ArrayList<>();
		for (int floor = 1; floor <= 60; floor++) {
			TowerPlan plan = drawn(SEED, floor);
			int zones = plan.safeZones().size();
			withZone += zones > 0 ? 1 : 0;
			boolean hall = floor % TowerElites.BOSS_EVERY == 0;
			if (zones != (hall ? 0 : 1) || !hall && !plan.inSafeZone(plan.arrivalX(), 0, plan.arrivalZ())) {
				wrong.add(floor);
			}
		}
		TowerTestSupport.result("safeZones", "withZone=" + withZone + " wrong=" + wrong);
		helper.assertTrue(wrong.isEmpty(), "floors with a wrong safe zone: " + wrong);
		helper.succeed();
	}

	/** Catches a player left unsheltered at the arrival of a floor above the first: it is in the hub. */
	@GameTest(maxTicks = 100)
	public void aPlayerAtTheArrivalOfALaterFloorIsSheltered(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerElites.BOSS_EVERY != 0);
		TowerFloors.ensure(tower, floor);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		boolean sheltered = TowerSafety.sheltered(player);
		TowerTestSupport.leave(helper, player);
		TowerTestSupport.result("laterArrival", "floor=" + floor + " sheltered=" + sheltered);
		helper.assertTrue(sheltered, "a player at floor " + floor + "'s arrival is not sheltered");
		helper.succeed();
	}

	/** Catches a light gap: every spot a body reaches has a light within six blocks. */
	@GameTest(maxTicks = 100)
	public void everySpotABodyReachesIsLit(final GameTestHelper helper) {
		int dark = 0;
		int spots = 0;
		for (int floor = 2; floor <= 21; floor++) {
			TowerPlan plan = drawn(SEED + floor, floor);
			TowerVolume volume = TowerVolume.of(plan);
			List<int[]> lamps = new ArrayList<>();
			for (int x = 0; x < plan.width(); x++) {
				for (int z = 0; z < plan.depth(); z++) {
					for (int y = plan.low(); y < plan.roofY(); y++) {
						if (volume.kind(x, y, z) == TowerVolume.BIOME_BLOCK && BiomeBlocks.lights(volume.code(x, y, z))) {
							lamps.add(new int[] {x, y, z});
						}
					}
				}
			}
			int[] dist = TowerWalk.distances(volume, plan.arrivalX(), 0, plan.arrivalZ());
			for (int x = 0; x < plan.width(); x++) {
				for (int z = 0; z < plan.depth(); z++) {
					for (int y = TowerWalk.LOW; y <= TowerWalk.HIGH; y++) {
						if (dist[TowerWalk.index(x, y, z)] < 0) {
							continue;
						}
						spots++;
						boolean lit = false;
						for (int[] lamp : lamps) {
							int dx = lamp[0] - x;
							int dy = lamp[1] - y;
							int dz = lamp[2] - z;
							lit |= dx * dx + dy * dy + dz * dz <= BiomeGen.LIGHT_REACH * BiomeGen.LIGHT_REACH;
						}
						dark += lit ? 0 : 1;
						if (!lit) {
							TowerTestSupport.result("darkSpot", "floor=" + floor + " biome=" + plan.biome().biome() + " at " + x + "," + y + "," + z + " attempt=" + plan.attempt());
						}
					}
				}
			}
		}
		TowerTestSupport.result("biomeLight", "spots=" + spots + " dark=" + dark);
		helper.assertTrue(spots > 0 && dark == 0, dark + " of " + spots + " reachable spots have no light within " + BiomeGen.LIGHT_REACH + " blocks");
		helper.succeed();
	}

	/** Catches a floor that writes outside its volume: sentinels set round it and above and below stay. */
	@GameTest(maxTicks = 400)
	public void aBiomeFloorWritesNothingOutsideItsVolume(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshBiomeFloor();
		TowerPlan plan = TowerPlan.at(tower, floor);
		BlockPos origin = plan.origin();
		List<BlockPos> sentinels = new ArrayList<>();
		int width = plan.width();
		int depth = plan.depth();
		int[][] sides = {{-2, 10}, {width + 1, 20}, {30, -2}, {40, depth + 1}, {-2, -2}, {width + 1, depth + 1}};
		for (int[] side : sides) {
			for (int y : new int[] {plan.low() - 1, 0, plan.roofY() / 2, plan.roofY() + 1}) {
				sentinels.add(origin.offset(side[0], y, side[1]));
			}
		}
		for (int x : new int[] {5, width / 2, width - 5}) {
			for (int z : new int[] {5, depth / 2, depth - 5}) {
				sentinels.add(origin.offset(x, plan.low() - 1, z));
				sentinels.add(origin.offset(x, plan.roofY() + 1, z));
			}
		}
		for (BlockPos at : sentinels) {
			tower.setBlock(at, Blocks.GLASS.defaultBlockState(), 2);
		}
		TowerFloors.ensure(tower, floor);
		int lost = 0;
		for (BlockPos at : sentinels) {
			lost += tower.getBlockState(at).is(Blocks.GLASS) ? 0 : 1;
		}
		TowerTestSupport.result("biomeFits", "floor=" + floor + " sentinels=" + sentinels.size() + " lost=" + lost + " low=" + plan.low() + " roof=" + plan.roofY());
		helper.assertTrue(lost == 0, lost + " of " + sentinels.size() + " sentinels outside the floor's volume were overwritten");
		helper.succeed();
	}

	/** Catches a built floor the plan's model disagrees with, step for step, and a portal that is not lit and walkable. */
	@GameTest(maxTicks = 400)
	public void aBuiltBiomeFloorIsWalkedAsItWasPlanned(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int disagree = 0;
		int stuck = 0;
		int unlit = 0;
		List<Integer> floors = new ArrayList<>();
		floors.add(TowerTestSupport.freshBiomeFloor());
		floors.add(TowerTestSupport.freshBiomeFloor());
		floors.add(TowerTestSupport.freshBiomeFloorWhere(f -> f % 5 == 0 && f % 10 != 0));
		for (int floor : floors) {
			TowerFloors.ensure(tower, floor);
			TowerPlan plan = TowerPlan.at(tower, floor);
			TowerVolume volume = TowerVolume.of(plan);
			int[] model = TowerWalk.distances(volume, plan.arrivalX(), 0, plan.arrivalZ());
			int[] world = TowerWalk.distances(TowerWalk.world(tower, floor), plan.arrivalX(), 0, plan.arrivalZ());
			int off = 0;
			for (int i = 0; i < model.length; i++) {
				off += (model[i] >= 0) != (world[i] >= 0) ? 1 : 0;
			}
			disagree += off;
			boolean reaches = false;
			for (int[] goal : TowerVolume.portalGoals(plan)) {
				reaches |= world[TowerWalk.index(goal[0], goal[1], goal[2])] >= 0;
			}
			stuck += reaches ? 0 : 1;
			for (BlockPos inside : plan.portalInside()) {
				unlit += tower.getBlockState(inside).is(TowerBlocks.TOWER_PORTAL) ? 0 : 1;
			}
			TowerTestSupport.result("biomeBuilt", "floor=" + floor + " off=" + off + " reaches=" + reaches + " chests=" + plan.chestSpots().size()
				+ " attempt=" + plan.attempt());
		}
		helper.assertTrue(disagree == 0, "the built floors disagree with their models on " + disagree + " spots");
		helper.assertTrue(stuck == 0, stuck + " built floors have no walk to the portal");
		helper.assertTrue(unlit == 0, unlit + " portal blocks of the built floors are not lit");
		helper.succeed();
	}

	/** Catches a roof that lets a body out: no random teleport lands above the room's solid roof. The roof itself: {@link TowerEnclosedGameTest}. */
	@GameTest(maxTicks = 200)
	public void aRoofIsClosedToTravel(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		long seed = tower.getSeed();
		int floor = TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerElites.BOSS_EVERY != 0 && FloorBiomes.of(seed, f).openRoof());
		TowerFloors.ensure(tower, floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		Vec3 arrival = TowerLayout.arrival(tower, floor);
		Pig pig = EntityTypes.PIG.create(tower, EntitySpawnReason.COMMAND);
		pig.snapTo(arrival);
		tower.addFreshEntity(pig);
		boolean above = pig.randomTeleport(arrival.x + 1.0, TowerLayout.BASE_Y + plan.roofY() + 1.0, arrival.z, false, BlockTags.CONSUMABLE_DOES_NOT_TELEPORT_TO);
		pig.discard();
		TowerTestSupport.result("roofTravel", "floor=" + floor + " teleportedAbove=" + above);
		helper.assertFalse(above, "a random teleport landed above the room");
		helper.succeed();
	}

	/** Catches a floor painted with no biome, or a biome file the game would not load. */
	@GameTest(maxTicks = 400)
	public void aBuiltBiomeFloorIsPaintedWithItsBiome(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		// Not a boss floor: that keeps its mist.
		int floor = TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerElites.BOSS_EVERY != 0);
		TowerFloors.ensure(tower, floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		boolean painted = TowerBiomes.painted(tower, floor, plan.biome().biome());
		TowerTestSupport.result("biomePaint", "floor=" + floor + " biome=" + plan.biome().biome() + " painted=" + painted);
		helper.assertTrue(painted, "the floor's sample columns do not hold " + plan.biome().biome().key().identifier());
		helper.succeed();
	}

	@GameTest
	public void everyBiomeFileFogStartsBeforeItEnds(final GameTestHelper helper) {
		for (FloorBiome biome : FloorBiome.values()) {
			String path = "/data/mod-stella-tower/worldgen/biome/tower_" + biome.id() + ".json";
			try (InputStreamReader reader = new InputStreamReader(TowerBiomeFloorGameTest.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
				JsonObject attributes = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("attributes");
				double start = attributes.get("minecraft:visual/fog_start_distance").getAsDouble();
				double end = attributes.get("minecraft:visual/fog_end_distance").getAsDouble();
				TowerTestSupport.result("biomeFile", biome + " fog=" + start + ".." + end);
				helper.assertTrue(start >= 0.0 && start < end, biome + " fogs from " + start + " to " + end);
				// The marsh is the extreme fog: a body sees about six blocks.
				helper.assertTrue(biome != FloorBiome.MISTBOUND_MARSH || end <= 6.0, "the marsh fogs out to " + end + " blocks");
			} catch (java.io.IOException | NullPointerException e) {
				helper.fail("biome file " + path + " could not be read: " + e);
			}
		}
		helper.succeed();
	}

	/** Catches a mob raised near the arrival, on the stair, or in the air: each spawn stands on the ground, off the stair and 16 away. */
	@GameTest(maxTicks = 400)
	public void aBiomeFloorRaisesNoMobNearTheArrivalOrOnTheStair(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshBiomeFloor();
		TowerFloors.ensure(tower, floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		BlockPos origin = plan.origin();
		net.minecraft.util.RandomSource random = tower.getRandom();
		int placed = 0;
		int near = 0;
		int onStair = 0;
		int inAir = 0;
		for (int i = 0; i < 200; i++) {
			Mob mob = EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.EVENT);
			mob.snapTo(Vec3.atBottomCenterOf(origin.offset(32, 0, 32)));
			if (!TowerMobs.placeInRoom(tower, floor, mob, random)) {
				mob.discard();
				continue;
			}
			placed++;
			int x = mob.blockPosition().getX() - origin.getX();
			int z = mob.blockPosition().getZ() - origin.getZ();
			near += mob.position().distanceTo(plan.arrivalPos()) < TowerMobs.BIOME_ARRIVAL_CLEAR ? 1 : 0;
			onStair += plan.biome().inDais(plan, x, z) ? 1 : 0;
			inAir += plan.standY(x, z) < TowerWalk.LOW || Math.abs(mob.blockPosition().getY() - TowerLayout.BASE_Y - plan.standY(x, z)) > 0 ? 1 : 0;
			mob.discard();
		}
		TowerTestSupport.result("biomeSpawns", "floor=" + floor + " placed=" + placed + " near=" + near + " onStair=" + onStair + " notOnTheGround=" + inAir);
		helper.assertTrue(placed > 100, "only " + placed + " of 200 mobs found a spot");
		helper.assertTrue(near == 0 && onStair == 0 && inAir == 0, "near=" + near + " onStair=" + onStair + " notOnTheGround=" + inAir);
		helper.succeed();
	}

	/** Catches an elite floor with no guard post on level ground, or too few chests, or no guaranteed chest by the stair. */
	@GameTest(maxTicks = 400)
	public void anEliteBiomeFloorKeepsItsGuardAndItsChests(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshBiomeFloorWhere(f -> f % 5 == 0 && f % 10 != 0);
		TowerFloors.ensure(tower, floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		BlockPos post = TowerElites.post(plan);
		BlockPos origin = plan.origin();
		int footX = plan.portalX() + plan.portalFacing() * (2 + plan.biome().steps());
		long guaranteed = plan.chestSpots().stream().filter(TowerPlan.ChestSpot::guaranteed).count();
		TowerPlan.ChestSpot spot = plan.chestSpots().stream().filter(TowerPlan.ChestSpot::guaranteed).findFirst().orElse(null);
		int away = spot == null ? -1 : Math.max(Math.abs(spot.x() - footX), Math.abs(spot.z() - plan.portalZ()));
		boolean level = plan.standY(post.getX() - origin.getX(), post.getZ() - origin.getZ()) == 0;
		int placed = 0;
		for (TowerPlan.ChestSpot each : plan.chestSpots()) {
			placed += tower.getBlockState(origin.offset(each.x(), each.y(), each.z())).is(TowerBlocks.TOWER_CHEST) ? 1 : 0;
		}
		TowerTestSupport.result("biomeElite", "floor=" + floor + " chests=" + plan.chestSpots().size() + " placed=" + placed + " guaranteed=" + guaranteed
			+ " awayFromFoot=" + away + " postLevel=" + level);
		helper.assertTrue(plan.chestSpots().size() >= TowerChestSpots.MIN_SPOTS + 1, "only " + plan.chestSpots().size() + " chests");
		helper.assertTrue(placed == plan.chestSpots().size(), placed + " of " + plan.chestSpots().size() + " chests stand in the world");
		helper.assertTrue(guaranteed == 1 && away >= TowerChestSpots.NEAR_STAIR_MIN && away <= TowerChestSpots.NEAR_STAIR_MAX,
			"the guaranteed chest: count " + guaranteed + ", " + away + " from the stair's foot");
		helper.assertTrue(level, "the guard's post is not on level ground at the base");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void anOrdinaryBiomeFloorHoldsSixChestsAndItsTraps(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshBiomeFloorWhere(f -> f % 5 != 0 && TrapTheme.of(f) != TrapTheme.NONE);
		TowerFloors.ensure(tower, floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		BlockPos origin = plan.origin();
		List<TowerTraps.Trap> traps = TowerTraps.layout(tower.getSeed(), floor, plan);
		int standing = 0;
		for (TowerTraps.Trap trap : traps) {
			standing += tower.getBlockState(origin.offset(trap.x(), trap.y() - 1, trap.z())).getBlock() instanceof TowerTrapBlock ? 1 : 0;
		}
		TowerTestSupport.result("biomeTraps", "floor=" + floor + " chests=" + plan.chestSpots().size() + " traps=" + traps.size() + " standing=" + standing);
		helper.assertTrue(plan.chestSpots().size() >= TowerChestSpots.MIN_SPOTS, "only " + plan.chestSpots().size() + " chests");
		helper.assertTrue(!traps.isEmpty() && standing == traps.size(), standing + " of " + traps.size() + " traps stand on the ground");
		helper.succeed();
	}
}
