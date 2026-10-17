package com.stella.end;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.pathfinder.Path;

/** The castle as blocks in the End: the floors, walls, rooms, doorways, stairs and crown a player walks, read back from the level. */
public class CastleBuildGameTest {
	/** A second castle under another seed, built far from the real one, so each gate run builds at least once. */
	static final BlockPos OTHER_MIN = new BlockPos(1024, EndCastle.BASE_Y, 1024);
	static final long OTHER_SEED = 424242L;
	private static final Set<Long> BUILT_OTHER = new HashSet<>();

	static CastleLayout other(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.end(helper);
		CastleLayout layout = CastleLayout.generate(OTHER_SEED);
		if (BUILT_OTHER.add(OTHER_SEED)) {
			CastleBuilder.build(end, layout, OTHER_MIN);
		}
		EndTestSupport.hold(end, OTHER_MIN);
		return layout;
	}

	@GameTest(environment = "mod-stella-end-test:build_floors", maxTicks = 20)
	public void everyWallOfEveryFloorStandsWholeAndOnlyStairsAndTheCourtyardOpenASlab(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		CastleLayout layout = EndCastles.layout(end);
		BlockPos min = EndCastle.min();
		StringBuilder bad = new StringBuilder();
		for (int floor = 1; floor <= EndCastle.FLOORS; floor++) {
			CastleLayout.FloorPlan plan = layout.floor(floor);
			int slab = EndCastle.slabY(floor);
			int holes = 0;
			int voids = 0;
			for (int x = 0; x < EndCastle.SIZE; x++) {
				for (int z = 0; z < EndCastle.SIZE; z++) {
					holes += EndTestSupport.state(end, min, x, slab, z).isAir() ? 1 : 0;
					voids += plan.kind(x, z) == CastleLayout.VOID ? 1 : 0;
					if (plan.kind(x, z) == CastleLayout.WALL) {
						for (int y = slab + 1; y < slab + EndCastle.FLOOR_HEIGHT; y++) {
							if (EndTestSupport.state(end, min, x, y, z).isAir()) {
								bad.append(" floor ").append(floor).append(" wall open at ").append(x).append(',').append(y).append(',').append(z);
								break;
							}
						}
					}
				}
			}
			// Only the courtyard and the climb of a stair from below open a slab.
			int expected = voids + plan.arrivals().size() * (CastleLayout.Flight.STEPS - 1 - CastleLayout.Flight.HOLE_FROM) * CastleLayout.Flight.WIDTH;
			if (holes != expected) {
				bad.append(" floor ").append(floor).append(" slab has ").append(holes).append(" open cells, expected ").append(expected);
			}
		}
		EndTestSupport.result("buildFloors", "floors=" + EndCastle.FLOORS + " bad=" + bad.length());
		helper.assertTrue(bad.isEmpty(), "footprint faults:" + bad);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:build_stairs", maxTicks = 20)
	public void everyFlightIsSixteenStepsOfThreeLanesWithHeadroomAndEndsLevelWithTheFloorAbove(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		CastleLayout layout = EndCastles.layout(end);
		StringBuilder bad = new StringBuilder();
		int flights = 0;
		for (int floor = 1; floor <= EndCastle.FLOORS; floor++) {
			for (CastleLayout.Flight flight : layout.floor(floor).ups()) {
				flights++;
				checkFlight(end, EndCastle.min(), flight, EndCastle.slabY(floor), "floor " + floor, bad);
			}
		}
		checkFlight(end, EndCastle.min(), layout.rest().exit(), EndCastle.REST_Y, "rest exit", bad);
		EndTestSupport.result("buildStairs", "flights=" + flights + " bad=" + bad.length());
		helper.assertTrue(flights == 9, "the plan holds " + flights + " flights, expected 8 between floors and 1 behind the arena door");
		helper.assertTrue(bad.isEmpty(), "stairs that do not climb:" + bad);
		helper.succeed();
	}

	/** Every step of a flight is a stair block that faces the way up, with room for the feet and head above it. */
	static void checkFlight(final ServerLevel end, final BlockPos min, final CastleLayout.Flight flight, final int slab, final String name, final StringBuilder bad) {
		for (int k = 0; k < CastleLayout.Flight.STEPS; k++) {
			for (int lane = 0; lane < CastleLayout.Flight.WIDTH; lane++) {
				int x = flight.x(k, lane);
				int z = flight.z(k, lane);
				int y = slab + 1 + k;
				BlockState step = EndTestSupport.state(end, min, x, y, z);
				if (!(step.getBlock() instanceof StairBlock) || step.getValue(StairBlock.FACING) != flight.direction() || step.getValue(StairBlock.HALF) != Half.BOTTOM) {
					bad.append(' ').append(name).append(" step ").append(k).append(" lane ").append(lane).append(" is ").append(step);
				}
				if (k < CastleLayout.Flight.STEPS - 1) {
					for (int head = 1; head <= 2; head++) {
						if (!EndTestSupport.state(end, min, x, y + head, z).isAir()) {
							bad.append(' ').append(name).append(" step ").append(k).append(" lane ").append(lane).append(" has no headroom at +").append(head);
						}
					}
				}
			}
		}
		// Beside the run on both sides, a stair block of the same flight would make it wider than three.
		int lastX = flight.x(CastleLayout.Flight.STEPS - 1, 1);
		int lastZ = flight.z(CastleLayout.Flight.STEPS - 1, 1);
		Direction across = flight.direction().getClockWise();
		for (int side : new int[] {-2, 2}) {
			BlockState beside = EndTestSupport.state(end, min, lastX + across.getStepX() * side, slab + CastleLayout.Flight.STEPS, lastZ + across.getStepZ() * side);
			if (beside.getBlock() instanceof StairBlock && beside.getValue(StairBlock.FACING) == flight.direction() && beside.is(Blocks.PURPUR_STAIRS)) {
				bad.append(' ').append(name).append(" is wider than three lanes");
			}
		}
	}

	@GameTest(environment = "mod-stella-end-test:build_walk", maxTicks = 200)
	public void aZombieFindsAPathUpEveryFlightToTheFloorAbove(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		CastleLayout layout = EndCastles.layout(end);
		List<Zombie> walkers = new ArrayList<>();
		List<BlockPos> goals = new ArrayList<>();
		List<String> names = new ArrayList<>();
		helper.startSequence()
			.thenExecute(() -> {
				for (int floor = 1; floor <= EndCastle.FLOORS - 1; floor++) {
					for (CastleLayout.Flight flight : layout.floor(floor).ups()) {
						BlockPos foot = EndTestSupport.world(EndCastle.min(), flight.x(-1, 1), EndCastle.standY(floor), flight.z(-1, 1));
						Zombie walker = EntityTypes.ZOMBIE.create(end, EntitySpawnReason.MOB_SUMMONED);
						// Claimed, so no other mod's spawn roll turns it elite, boss or baby: a big one cannot take a one-block stair.
						LevelBridge.claim(walker);
						walker.addTag(EndMobs.FLOOR_TAG + "0");
						walker.snapTo(foot.getX() + 0.5, foot.getY() + 2, foot.getZ() + 0.5, 0.0F, 0.0F);
						walker.setPersistenceRequired();
						helper.assertTrue(end.addFreshEntity(walker), "the walker was refused on floor " + floor);
						walkers.add(walker);
						goals.add(EndTestSupport.world(EndCastle.min(), flight.x(CastleLayout.Flight.STEPS, 1), EndCastle.standY(floor + 1), flight.z(CastleLayout.Flight.STEPS, 1)));
						names.add("floor " + floor + " " + flight.direction());
					}
				}
			})
			.thenWaitUntil(() -> {
				for (int i = 0; i < walkers.size(); i++) {
					helper.assertTrue(walkers.get(i).onGround(), "the walker of " + names.get(i) + " is not standing yet");
				}
			})
			.thenExecute(() -> {
				StringBuilder bad = new StringBuilder();
				for (int i = 0; i < walkers.size(); i++) {
					Path path = walkers.get(i).getNavigation().createPath(goals.get(i), 1);
					boolean reaches = path != null && path.canReach();
					EndTestSupport.result("buildWalk", names.get(i) + " reaches=" + reaches + " nodes=" + (path == null ? -1 : path.getNodeCount()) + " from=" + walkers.get(i).blockPosition()
						+ " goal=" + goals.get(i) + " end=" + (path == null ? null : path.getEndNode()));
					if (!reaches) {
						bad.append(' ').append(names.get(i)).append(" has no path up");
					}
					walkers.get(i).discard();
				}
				helper.assertTrue(walkers.size() == 8, "the walkers number " + walkers.size());
				helper.assertTrue(bad.isEmpty(), "flights a walker cannot climb:" + bad);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:build_connected", maxTicks = 40)
	public void theArenaDoorSealsTheStairToTheRestRoomUntilItOpensAndTheHatchLeadsToTheRoof(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		CastleLayout layout = EndCastles.layout(end);
		CastleLayout.FloorPlan arena = layout.floor(EndCastle.FLOORS);
		BlockPos landing = EndTestSupport.world(EndCastle.min(), arena.landingX(), EndCastle.standY(EndCastle.FLOORS), arena.landingZ());
		CastleLayout.Flight apse = CastleLayout.APSE;
		BlockPos foot = EndTestSupport.world(EndCastle.min(), apse.x(-1, 1), EndCastle.standY(EndCastle.FLOORS), apse.z(-1, 1));
		CastleLayout.Flight exit = layout.rest().exit();
		BlockPos hatch = EndTestSupport.world(EndCastle.min(), exit.x(CastleLayout.Flight.STEPS, 1), EndCastle.ROOF_Y + 1, exit.z(CastleLayout.Flight.STEPS, 1));
		boolean shutFoot = CastleWalk.reaches(CastleWalk.reach(end, landing), foot.getX(), foot.getY(), foot.getZ());
		boolean shutRoof;
		boolean openFoot;
		boolean openRoof;
		try {
			shutRoof = CastleWalk.reaches(CastleWalk.reach(end, landing), hatch.getX(), hatch.getY(), hatch.getZ());
			CastleBuilder.setExitDoor(end, layout, EndCastle.min(), true);
			Set<Long> opened = CastleWalk.reach(end, landing);
			openFoot = CastleWalk.reaches(opened, foot.getX(), foot.getY(), foot.getZ());
			openRoof = CastleWalk.reaches(opened, hatch.getX(), hatch.getY(), hatch.getZ());
		} finally {
			CastleBuilder.setExitDoor(end, layout, EndCastle.min(), false);
		}
		EndTestSupport.result("buildConnected", "shutFoot=" + shutFoot + " shutRoof=" + shutRoof + " openFoot=" + openFoot + " openRoof=" + openRoof);
		helper.assertTrue(!shutFoot && !shutRoof, "a walker passes the shut door: foot=" + shutFoot + " roof=" + shutRoof);
		helper.assertTrue(openFoot, "the open door does not lead to the foot of the stair at " + foot);
		helper.assertTrue(openRoof, "the stair behind the open door does not lead to the roof at " + hatch);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:build_arena", maxTicks = 20)
	public void theBossAnchorStandsOnOpenGroundAtTheCentreOfTheFloorSixArena(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		BlockPos anchor = EndCastle.arenaAnchor();
		StringBuilder bad = new StringBuilder();
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				BlockPos feet = anchor.offset(dx, 0, dz);
				boolean clear = end.getBlockState(feet).isAir() && end.getBlockState(feet.above()).isAir() && end.getBlockState(feet.above(2)).isAir();
				if (!clear || !end.getBlockState(feet.below()).isFaceSturdy(end, feet.below(), Direction.UP)) {
					bad.append(" blocked at ").append(feet);
				}
			}
		}
		int lx = anchor.getX() - EndCastle.MIN_X;
		int lz = anchor.getZ() - EndCastle.MIN_Z;
		boolean arena = EndCastles.layout(end).floor(EndCastle.FLOORS).room(lx, lz) == 1;
		EndTestSupport.result("buildArena", "anchor=" + anchor + " inArena=" + arena + " bad=" + bad.length());
		helper.assertTrue(arena, "the anchor is not in the arena room");
		helper.assertTrue(bad.isEmpty(), "the arena centre is not open ground:" + bad);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:build_top", maxTicks = 20)
	public void aRestRoomSixTallAndARooftopDiscFortySixAcrossCrownTheSixFloors(final GameTestHelper helper) {
		// The second castle: vanilla carves the exit portal pit into the real deck once the dragon fight starts.
		ServerLevel end = EndTestSupport.fresh(helper);
		CastleLayout layout = other(helper);
		BlockPos min = OTHER_MIN;
		CastleLayout.RestPlan rest = layout.rest();
		StringBuilder bad = new StringBuilder();
		int standing = EndCastle.REST_Y + 1;
		if (!EndTestSupport.state(end, min, rest.enderX(), standing, rest.enderZ()).is(Blocks.ENDER_CHEST)) {
			bad.append(" no ender chest");
		}
		if (!EndTestSupport.state(end, min, rest.tableX(), standing, rest.tableZ()).is(Blocks.CRAFTING_TABLE)) {
			bad.append(" no crafting table");
		}
		if (!EndTestSupport.state(end, min, rest.anvilX(), standing, rest.anvilZ()).is(Blocks.ANVIL)) {
			bad.append(" no anvil");
		}
		for (int i = 0; i < rest.chestCount(); i++) {
			BlockState chest = EndTestSupport.state(end, min, rest.chestX() + i, standing, rest.chestZ());
			if (!chest.is(Blocks.CHEST) || end.getBlockEntity(EndTestSupport.world(min, rest.chestX() + i, standing, rest.chestZ())) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity entity
				&& (entity.getLootTable() != null || !entity.isEmpty())) {
				bad.append(" chest ").append(i).append(" is not an empty chest");
			}
		}
		CastleLayout.Rect room = rest.room();
		for (int x = room.x0(); x <= room.x1(); x++) {
			for (int z = room.z0(); z <= room.z1(); z++) {
				for (int y = standing; y <= standing + 5; y++) {
					if (EndTestSupport.state(end, min, x, y, z).is(BlockTags.BEDS)) {
						bad.append(" a bed at ").append(x).append(',').append(y).append(',').append(z);
					}
				}
			}
		}
		if (!EndTestSupport.state(end, min, room.x0() + 8, standing + 6, room.z0() + 4).isSolid()
			|| !EndTestSupport.state(end, min, room.x0() + 8, standing + 5, room.z0() + 3).isAir()) {
			bad.append(" the rest room is not six tall inside");
		}
		int centre = EndCastle.SIZE / 2;
		int first = Integer.MAX_VALUE;
		int last = Integer.MIN_VALUE;
		for (int x = -EndCastle.ZONE_MARGIN; x < EndCastle.SIZE + EndCastle.ZONE_MARGIN; x++) {
			if (!EndTestSupport.state(end, min, x, EndCastle.ROOF_Y, centre).isAir()) {
				first = Math.min(first, x);
				last = Math.max(last, x);
			}
		}
		if (last - first != 2 * EndCastle.DISC_RADIUS) {
			bad.append(" the deck spans ").append(last - first).append(" blocks across its middle row");
		}
		int rim = centre + EndCastle.DISC_RADIUS - 1;
		int parapet = 0;
		for (int up = 1; up <= 6; up++) {
			parapet += EndTestSupport.state(end, min, rim, EndCastle.ROOF_Y + up, centre + 3).isAir() ? 0 : 1;
		}
		if (parapet < 3 || parapet > 4) {
			bad.append(" the parapet is ").append(parapet).append(" tall");
		}
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				for (int up = 1; up <= 8; up++) {
					if (!EndTestSupport.state(end, min, centre + dx, EndCastle.ROOF_Y + up, centre + dz).isAir()) {
						bad.append(" the portal centre is blocked at +").append(up);
					}
				}
			}
		}
		for (int distance = 5; distance <= 18; distance++) {
			for (int lane = -2; lane <= 2; lane++) {
				for (int up = 1; up <= 4; up++) {
					boolean clear = EndTestSupport.state(end, min, centre + distance, EndCastle.ROOF_Y + up, centre + lane).isAir()
						&& EndTestSupport.state(end, min, centre - distance, EndCastle.ROOF_Y + up, centre + lane).isAir()
						&& EndTestSupport.state(end, min, centre + lane, EndCastle.ROOF_Y + up, centre + distance).isAir()
						&& EndTestSupport.state(end, min, centre + lane, EndCastle.ROOF_Y + up, centre - distance).isAir();
					if (!clear) {
						bad.append(" a lane is blocked at ").append(distance).append(',').append(lane).append(",+").append(up);
					}
				}
			}
		}
		for (int ox : new int[] {1, EndCastle.SIZE - 2}) {
			for (int oz : new int[] {1, EndCastle.SIZE - 2}) {
				for (int y = EndCastle.slabY(5) + 1; y <= EndCastle.ROOF_Y + 8; y++) {
					if (EndTestSupport.state(end, min, ox, y, oz).isAir()) {
						bad.append(" a corner pillar is open at ").append(ox).append(',').append(y).append(',').append(oz);
						break;
					}
				}
			}
		}
		EndTestSupport.result("buildTop", "deckAcross=" + (last - first) + " parapet=" + parapet + " bad=" + bad.length());
		helper.assertTrue(bad.isEmpty(), "the top of the castle:" + bad);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:build_disc", maxTicks = 20)
	public void theRooftopPlayersAreThoseOverTheDiscNotOverTheKeepSquare(final GameTestHelper helper) {
		double deck = Rooftop.DECK_Y + 1.5;
		boolean wide = Rooftop.contains(-20.5, deck, 0.5);
		boolean rim = Rooftop.contains(EndCastle.DISC_RADIUS - 0.5, deck, 0.5);
		boolean beyond = Rooftop.contains(EndCastle.DISC_RADIUS + 1.5, deck, 0.5);
		boolean corner = Rooftop.contains(19.5, deck, 19.5);
		boolean below = Rooftop.contains(0.5, EndCastle.REST_Y + 2.0, 0.5);
		EndTestSupport.result("buildDisc", "wide=" + wide + " rim=" + rim + " beyond=" + beyond + " corner=" + corner + " below=" + below);
		helper.assertTrue(wide && rim, "a point over the disc outside the keep square is not on the rooftop");
		helper.assertTrue(!beyond && !corner, "a point past the rim of the disc is on the rooftop");
		helper.assertTrue(!below, "a point under the deck is on the rooftop");
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:build_portal", maxTicks = 700)
	public void theVanillaExitPortalStandsOnTheRooftopDeckAtTheOrigin(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		// The dragon fight makes the portal podium once a player stands in the End and its arena has loaded.
		EndGameTestInit.dragonFightRuns = true;
		EndTestSupport.joinOnFloor(helper, end, 1);
		BlockPos centre = new BlockPos(0, EndCastle.ROOF_Y, 0);
		helper.startSequence()
			.thenWaitUntil(() -> {
				int pillar = 0;
				for (int y = EndCastle.ROOF_Y + 1; y <= EndCastle.ROOF_Y + 4; y++) {
					pillar += end.getBlockState(centre.atY(y)).is(Blocks.BEDROCK) ? 1 : 0;
				}
				helper.assertTrue(pillar > 0, "vanilla has not built the exit portal yet");
			})
			.thenExecute(() -> {
				StringBuilder column = new StringBuilder();
				int lowest = Integer.MAX_VALUE;
				for (int y = EndCastle.BASE_Y; y <= EndCastle.ROOF_Y + 6; y++) {
					if (end.getBlockState(centre.atY(y)).is(Blocks.BEDROCK)) {
						lowest = Math.min(lowest, y);
						column.append(' ').append(y);
					}
				}
				EndTestSupport.result("buildPortal", "bedrockAtOrigin=" + column + " deck=" + EndCastle.ROOF_Y);
				EndTestSupport.fresh(helper);
				helper.assertTrue(lowest >= EndCastle.ROOF_Y - 1, "the exit portal base sits at " + lowest + ", below the deck at " + EndCastle.ROOF_Y);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:build_match", maxTicks = 40)
	public void theBuiltWorldMatchesThePlanForTheRealAndForAnotherSeed(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		CastleLayout otherLayout = other(helper);
		String real = mismatches(end, EndCastles.layout(end), EndCastle.min());
		String second = mismatches(end, otherLayout, OTHER_MIN);
		EndTestSupport.result("buildMatch", "real=" + real.length() + " other=" + second.length());
		// Different seeds must also differ as blocks, not only as plans.
		boolean differs = false;
		for (int x = 0; x < EndCastle.SIZE && !differs; x++) {
			for (int z = 0; z < EndCastle.SIZE; z++) {
				boolean a = EndTestSupport.state(end, EndCastle.min(), x, EndCastle.slabY(2) + 3, z).isAir();
				boolean b = EndTestSupport.state(end, OTHER_MIN, x, EndCastle.slabY(2) + 3, z).isAir();
				if (a != b) {
					differs = true;
					break;
				}
			}
		}
		helper.assertTrue(real.isEmpty(), "the real castle departs from its plan:" + real);
		helper.assertTrue(second.isEmpty(), "the second castle departs from its plan:" + second);
		helper.assertTrue(differs, "two seeds built the same doorways on floor 2");
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:build_once", maxTicks = 20)
	public void theCastleIsBuiltOnceAndNotOnEveryLoad(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		BlockPos marker = EndTestSupport.world(EndCastle.min(), 1, EndCastle.standY(1) + 8, 1);
		BlockState before = end.getBlockState(marker);
		end.setBlock(marker, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
		boolean again;
		BlockState after;
		try {
			again = EndCastles.ensureBuilt(end);
			after = end.getBlockState(marker);
		} finally {
			end.setBlock(marker, before, Block.UPDATE_CLIENTS);
		}
		EndTestSupport.result("buildOnce", "builtAgain=" + again + " marker=" + after.getBlock());
		helper.assertTrue(EndCastles.state(end).built(), "the End level does not record a built castle");
		helper.assertTrue(!again, "a second load built the castle again");
		helper.assertTrue(after.is(Blocks.GOLD_BLOCK), "a second load wiped what stood in the castle: " + after);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:build_immune", maxTicks = 20)
	public void everyCastleBlockResistsTheEnderDragon(final GameTestHelper helper) {
		// The second castle holds only what the builder placed: the real one also holds vanilla's exit portal.
		ServerLevel end = EndTestSupport.fresh(helper);
		other(helper);
		Set<Block> kinds = new HashSet<>();
		for (int y = EndCastle.BASE_Y; y <= EndCastle.ROOF_Y + EndCastle.TOWER_HEIGHT + 2; y++) {
			for (int x = -EndCastle.ZONE_MARGIN; x < EndCastle.SIZE + EndCastle.ZONE_MARGIN; x++) {
				for (int z = -EndCastle.ZONE_MARGIN; z < EndCastle.SIZE + EndCastle.ZONE_MARGIN; z++) {
					BlockState state = EndTestSupport.state(end, OTHER_MIN, x, y, z);
					if (!state.isAir()) {
						kinds.add(state.getBlock());
					}
				}
			}
		}
		StringBuilder bad = new StringBuilder();
		for (Block block : kinds) {
			if (!block.defaultBlockState().is(BlockTags.DRAGON_IMMUNE)) {
				bad.append(' ').append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block));
			}
		}
		EndTestSupport.result("buildImmune", "kinds=" + kinds.size() + " exposed=" + bad);
		helper.assertTrue(bad.isEmpty(), "blocks the dragon can break:" + bad);
		helper.succeed();
	}

	// -- Scans ---------------------------------------------------------------------------------------------------

	/**
	 * Every way the level departs from the plan, as text; empty when it matches. A wall stands whole, a doorway is
	 * clear up to its lintel, a spawn cell has a floor and head room, and each chest pair is where the plan puts it.
	 */
	static String mismatches(final ServerLevel end, final CastleLayout layout, final BlockPos min) {
		StringBuilder bad = new StringBuilder();
		for (int floor = 1; floor <= EndCastle.FLOORS; floor++) {
			CastleLayout.FloorPlan plan = layout.floor(floor);
			int slab = EndCastle.slabY(floor);
			for (int x = 0; x < EndCastle.SIZE; x++) {
				for (int z = 0; z < EndCastle.SIZE; z++) {
					byte kind = plan.kind(x, z);
					int cellX = x;
					int cellZ = z;
					if (kind == CastleLayout.WALL) {
						for (int y = slab + 1; y < slab + EndCastle.FLOOR_HEIGHT; y++) {
							if (EndTestSupport.state(end, min, x, y, z).isAir()) {
								bad.append(" floor ").append(floor).append(" wall ").append(x).append(',').append(z).append(" is open at +").append(y - slab);
								break;
							}
						}
					} else if (kind == CastleLayout.DOOR) {
						boolean barrier = plan.doors().stream().anyMatch(door -> door.contains(cellX, cellZ) && door.barrier());
						for (int y = slab + 1; y < slab + EndCastle.FLOOR_HEIGHT; y++) {
							BlockState state = EndTestSupport.state(end, min, x, y, z);
							boolean below = y <= slab + CastleLayout.DOOR_HEIGHT;
							boolean fault = below ? (barrier ? !state.is(Blocks.BARRIER) : !state.isAir()) : state.isAir();
							if (fault) {
								bad.append(" floor ").append(floor).append(" door ").append(x).append(',').append(z).append(" is ").append(state).append(" at +").append(y - slab);
								break;
							}
						}
					}
				}
			}
			for (int[] cell : plan.spawnCells()) {
				BlockPos feet = EndTestSupport.world(min, cell[0], EndCastle.standY(floor), cell[1]);
				boolean room = end.getBlockState(feet).isAir() && end.getBlockState(feet.above()).isAir() && end.getBlockState(feet.above(2)).isAir();
				if (!room || !end.getBlockState(feet.below()).isFaceSturdy(end, feet.below(), Direction.UP)) {
					bad.append(" floor ").append(floor).append(" spawn cell ").append(cell[0]).append(',').append(cell[1]).append(" is blocked");
				}
			}
			for (CastleLayout.Chest chest : plan.chests()) {
				if (!EndTestSupport.state(end, min, chest.x(), EndCastle.standY(floor), chest.z()).is(Blocks.CHEST)
					|| !EndTestSupport.state(end, min, chest.partnerX(), EndCastle.standY(floor), chest.partnerZ()).is(Blocks.CHEST)) {
					bad.append(" floor ").append(floor).append(" chest missing at ").append(chest.x()).append(',').append(chest.z());
				}
			}
		}
		return bad.toString();
	}
}
