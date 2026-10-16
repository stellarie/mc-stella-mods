package com.stella.end;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/** The castle as blocks in the End: the floors, walls, rooms, doorways and stairs a player walks, read back from the level. */
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
	public void sixFloorsStandInAThirtyTwoByThirtyTwoByHeightSixteenFootprint(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		BlockPos min = EndCastle.min();
		StringBuilder bad = new StringBuilder();
		for (int floor = 1; floor <= EndCastle.FLOORS; floor++) {
			int slab = EndCastle.slabY(floor);
			int holes = 0;
			for (int x = 0; x < EndCastle.SIZE; x++) {
				for (int z = 0; z < EndCastle.SIZE; z++) {
					holes += EndTestSupport.state(end, min, x, slab, z).isAir() ? 1 : 0;
					boolean edge = x == 0 || z == 0 || x == EndCastle.SIZE - 1 || z == EndCastle.SIZE - 1;
					for (int y = slab + 1; y < slab + EndCastle.FLOOR_HEIGHT; y++) {
						boolean wall = !EndTestSupport.state(end, min, x, y, z).isAir();
						if (edge && !wall) {
							bad.append(" floor ").append(floor).append(" wall open at ").append(x).append(',').append(y).append(',').append(z);
						}
					}
					if (x == 0 && !EndTestSupport.state(end, min, -1, slab + 2, z).isAir()) {
						bad.append(" floor ").append(floor).append(" spills west at z ").append(z);
					}
					if (z == 0 && !EndTestSupport.state(end, min, x, slab + 2, -1).isAir()) {
						bad.append(" floor ").append(floor).append(" spills north at x ").append(x);
					}
					if (x == EndCastle.SIZE - 1 && !EndTestSupport.state(end, min, EndCastle.SIZE, slab + 2, z).isAir()) {
						bad.append(" floor ").append(floor).append(" spills east at z ").append(z);
					}
					if (z == EndCastle.SIZE - 1 && !EndTestSupport.state(end, min, x, slab + 2, EndCastle.SIZE).isAir()) {
						bad.append(" floor ").append(floor).append(" spills south at x ").append(x);
					}
				}
			}
			// Only the stairwell from below opens a slab.
			int expected = floor == 1 ? 0 : CastleLayout.Shaft.CEILING_HOLES;
			if (holes != expected) {
				bad.append(" floor ").append(floor).append(" slab has ").append(holes).append(" open cells, expected ").append(expected);
			}
		}
		EndTestSupport.result("buildFloors", "floors=" + EndCastle.FLOORS + " bad=" + bad.length());
		helper.assertTrue(bad.isEmpty(), "footprint faults:" + bad);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:build_rooms", maxTicks = 20)
	public void everyFloorHoldsAtLeastFourRoomsInTheWorld(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		StringBuilder bad = new StringBuilder();
		StringBuilder counts = new StringBuilder();
		for (int floor = 1; floor <= EndCastle.FLOORS; floor++) {
			// Above a doorway's lintel only walls divide a floor, so the open cells of this layer fall into its rooms.
			int rooms = components(end, EndCastle.min(), EndCastle.slabY(floor) + CastleLayout.DOOR_HEIGHT + 4, false, 0);
			counts.append(' ').append(rooms);
			if (rooms < 4) {
				bad.append(" floor ").append(floor).append(" holds ").append(rooms);
			}
		}
		EndTestSupport.result("buildRooms", "roomsPerFloor=" + counts);
		helper.assertTrue(bad.isEmpty(), "floors with too few rooms:" + bad);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:build_connected", maxTicks = 20)
	public void theRoomsOfEveryFloorJoinThroughDoorwaysInTheWorld(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		CastleLayout layout = EndCastles.layout(end);
		StringBuilder bad = new StringBuilder();
		// The barrier door cuts the exit chamber off floor 6 until a later rule opens it.
		for (int floor = 1; floor <= EndCastle.FLOORS; floor++) {
			int pieces = components(end, EndCastle.min(), EndCastle.standY(floor), true, floor);
			int expected = floor == EndCastle.FLOORS ? 2 : 1;
			if (pieces != expected) {
				bad.append(" floor ").append(floor).append(" falls into ").append(pieces).append(" pieces, expected ").append(expected);
			}
		}
		int whole;
		try {
			CastleBuilder.setExitDoor(end, layout, EndCastle.min(), true);
			whole = components(end, EndCastle.min(), EndCastle.standY(EndCastle.FLOORS), true, EndCastle.FLOORS);
		} finally {
			CastleBuilder.setExitDoor(end, layout, EndCastle.min(), false);
		}
		if (whole != 1) {
			bad.append(" floor 6 with its door open falls into ").append(whole).append(" pieces");
		}
		EndTestSupport.result("buildConnected", "bad=" + bad.length() + " floor6DoorOpen=" + whole);
		helper.assertTrue(bad.isEmpty(), "unjoined floors:" + bad);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:build_stairs", maxTicks = 20)
	public void aSpiralStairClimbsFromEveryFloorToTheNext(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		BlockPos min = EndCastle.min();
		StringBuilder bad = new StringBuilder();
		for (int floor = 1; floor <= EndCastle.FLOORS - 1; floor++) {
			CastleLayout.Shaft shaft = EndCastles.layout(end).floor(floor).up();
			for (int k = 0; k < CastleLayout.Shaft.STEPS; k++) {
				int y = EndCastle.slabY(floor) + 1 + k;
				BlockState step = EndTestSupport.state(end, min, shaft.stepX(k), y, shaft.stepZ(k));
				if (!(step.getBlock() instanceof StairBlock) || step.getValue(StairBlock.FACING) != shaft.stepFacing(k) || step.getValue(StairBlock.HALF) != Half.BOTTOM) {
					bad.append(" floor ").append(floor).append(" step ").append(k).append(" is ").append(step);
				}
				// A climber on the step stands in the two layers above its block.
				for (int head = 1; head <= 2; head++) {
					if (!end.getBlockState(EndTestSupport.world(min, shaft.stepX(k), y + head, shaft.stepZ(k))).isAir()) {
						bad.append(" floor ").append(floor).append(" step ").append(k).append(" has no headroom at +").append(head);
					}
				}
			}
			// The top step ends level with the next floor's standing surface.
			BlockState top = EndTestSupport.state(end, min, shaft.topX(), EndCastle.slabY(floor + 1), shaft.topZ());
			if (top.isAir()) {
				bad.append(" floor ").append(floor).append(" has no deck at the top");
			}
		}
		EndTestSupport.result("buildStairs", "floors=" + (EndCastle.FLOORS - 1) + " bad=" + bad.length());
		helper.assertTrue(bad.isEmpty(), "stairs that do not climb:" + bad);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:build_walk", maxTicks = 200)
	public void aWalkerFindsAPathFromEveryFloorToTheNextOverTheStairs(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		CastleLayout layout = EndCastles.layout(end);
		net.minecraft.world.entity.monster.zombie.Zombie[] walkers = new net.minecraft.world.entity.monster.zombie.Zombie[EndCastle.FLOORS];
		helper.startSequence()
			.thenExecute(() -> {
				for (int floor = 1; floor <= EndCastle.FLOORS - 1; floor++) {
					// Beside the first step: a zombie's search reaches 35 blocks, and the far corner is farther.
					CastleLayout.Shaft up = layout.floor(floor).up();
					BlockPos at = EndTestSupport.world(EndCastle.min(), up.stepX(0) - 1, EndCastle.standY(floor), up.stepZ(0));
					net.minecraft.world.entity.monster.zombie.Zombie walker =
						net.minecraft.world.entity.EntityTypes.ZOMBIE.create(end, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
					// Claimed, so no other mod's spawn roll turns it elite, boss or baby: a big one cannot take a one-block stair.
					LevelBridge.claim(walker);
					walker.addTag(EndMobs.FLOOR_TAG + "0");
					walker.snapTo(at.getX() + 0.5, at.getY() + 2, at.getZ() + 0.5, 0.0F, 0.0F);
					walker.setPersistenceRequired();
					helper.assertTrue(end.addFreshEntity(walker), "the walker was refused on floor " + floor);
					walkers[floor - 1] = walker;
				}
			})
			.thenWaitUntil(() -> {
				for (int floor = 1; floor <= EndCastle.FLOORS - 1; floor++) {
					helper.assertTrue(walkers[floor - 1].onGround(), "the walker of floor " + floor + " is not standing yet");
				}
			})
			.thenExecute(() -> {
				StringBuilder bad = new StringBuilder();
				for (int floor = 1; floor <= EndCastle.FLOORS - 1; floor++) {
					CastleLayout.FloorPlan above = layout.floor(floor + 1);
					BlockPos goal = EndTestSupport.world(EndCastle.min(), above.landingX(), EndCastle.standY(floor + 1), above.landingZ());
					net.minecraft.world.level.pathfinder.Path path = walkers[floor - 1].getNavigation().createPath(goal, 1);
					boolean reaches = path != null && path.canReach();
					EndTestSupport.result("buildWalk", "floor=" + floor + " reaches=" + reaches + " nodes=" + (path == null ? -1 : path.getNodeCount())
						+ " from=" + walkers[floor - 1].blockPosition() + " goal=" + goal + " end=" + (path == null ? null : path.getEndNode()));
					if (!reaches) {
						bad.append(" floor ").append(floor).append(" has no path up");
					}
					walkers[floor - 1].discard();
				}
				helper.assertTrue(bad.isEmpty(), "floors a walker cannot climb:" + bad);
			})
			.thenSucceed();
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
				if (!clear || !end.getBlockState(feet.below()).isFaceSturdy(end, feet.below(), net.minecraft.core.Direction.UP)) {
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
	public void aRestAreaAndARooftopCrownTheSixFloors(final GameTestHelper helper) {
		// The second castle: vanilla carves the exit portal pit into the real deck once the dragon fight starts.
		ServerLevel end = EndTestSupport.fresh(helper);
		CastleLayout layout = other(helper);
		BlockPos min = OTHER_MIN;
		CastleLayout.RestPlan rest = layout.rest();
		StringBuilder bad = new StringBuilder();
		int standing = EndCastle.REST_Y + 1;
		if (!EndTestSupport.state(end, min, rest.chestX(), standing, rest.chestZ()).is(Blocks.ENDER_CHEST)) {
			bad.append(" no ender chest");
		}
		if (!EndTestSupport.state(end, min, rest.tableX(), standing, rest.tableZ()).is(Blocks.CRAFTING_TABLE)) {
			bad.append(" no crafting table");
		}
		if (!EndTestSupport.state(end, min, rest.anvilX(), standing, rest.anvilZ()).is(Blocks.ANVIL)) {
			bad.append(" no anvil");
		}
		if (!EndTestSupport.state(end, min, rest.bedX(), standing, rest.bedZ()).is(net.minecraft.tags.BlockTags.BEDS)) {
			bad.append(" no bed");
		}
		// The rest room is 12 by 12 with walls: a wall at its far edge, air inside, and nothing of it past that.
		if (EndTestSupport.state(end, min, EndCastle.REST_SIZE - 1, standing + 2, 5).isAir()
			|| !EndTestSupport.state(end, min, EndCastle.REST_SIZE - 2, standing + 2, 5).isAir()) {
			bad.append(" rest room is not 12 wide");
		}
		int deck = 0;
		for (int x = 0; x < EndCastle.SIZE; x++) {
			for (int z = 0; z < EndCastle.SIZE; z++) {
				deck += EndTestSupport.state(end, min, x, EndCastle.ROOF_Y, z).isAir() ? 0 : 1;
			}
		}
		// The deck is the whole footprint but the cells its stairwell opens.
		if (deck != EndCastle.SIZE * EndCastle.SIZE - CastleLayout.Shaft.CEILING_HOLES) {
			bad.append(" deck has ").append(deck).append(" cells");
		}
		if (EndTestSupport.state(end, min, 0, EndCastle.ROOF_Y + 1, 0).isAir() || !EndTestSupport.state(end, min, 15, EndCastle.ROOF_Y + 1, 15).isAir()) {
			bad.append(" no low parapet round a clear deck");
		}
		EndTestSupport.result("buildTop", "bad=" + bad.length());
		helper.assertTrue(bad.isEmpty(), "the top of the castle:" + bad);
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
				boolean a = EndTestSupport.state(end, EndCastle.min(), x, EndCastle.slabY(2) + 10, z).isAir();
				boolean b = EndTestSupport.state(end, OTHER_MIN, x, EndCastle.slabY(2) + 10, z).isAir();
				if (a != b) {
					differs = true;
					break;
				}
			}
		}
		helper.assertTrue(real.isEmpty(), "the real castle departs from its plan:" + real);
		helper.assertTrue(second.isEmpty(), "the second castle departs from its plan:" + second);
		helper.assertTrue(differs, "two seeds built the same walls on floor 2");
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
		for (int y = EndCastle.BASE_Y; y <= EndCastle.ROOF_Y + 2; y++) {
			for (int x = 0; x < EndCastle.SIZE; x++) {
				for (int z = 0; z < EndCastle.SIZE; z++) {
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
	 * Counts the pieces the open cells of one layer fall into. A walking scan wants a floor under the feet and air at
	 * the feet and the head; a room scan wants only air in the layer.
	 */
	static int components(final ServerLevel end, final BlockPos min, final int y, final boolean walking, final int floor) {
		int size = EndCastle.SIZE;
		boolean[] open = new boolean[size * size];
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				BlockPos at = EndTestSupport.world(min, x, y, z);
				boolean air = end.getBlockState(at).isAir();
				if (walking) {
					boolean headroom = end.getBlockState(at.above()).isAir();
					boolean ground = end.getBlockState(at.below()).isFaceSturdy(end, at.below(), net.minecraft.core.Direction.UP);
					open[x * size + z] = air && headroom && ground;
				} else {
					open[x * size + z] = air;
				}
			}
		}
		boolean[] seen = new boolean[size * size];
		int pieces = 0;
		int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int start = 0; start < size * size; start++) {
			if (!open[start] || seen[start]) {
				continue;
			}
			pieces++;
			ArrayDeque<Integer> queue = new ArrayDeque<>();
			queue.add(start);
			seen[start] = true;
			while (!queue.isEmpty()) {
				int at = queue.poll();
				for (int[] step : steps) {
					int x = at / size + step[0];
					int z = at % size + step[1];
					if (x >= 0 && z >= 0 && x < size && z < size && open[x * size + z] && !seen[x * size + z]) {
						seen[x * size + z] = true;
						queue.add(x * size + z);
					}
				}
			}
		}
		return pieces;
	}

	/** The first block above a slab at a cell, named with its height, for a failure message. */
	static String firstSolid(final ServerLevel end, final BlockPos min, final int x, final int slab, final int z) {
		for (int y = slab + 1; y < slab + EndCastle.FLOOR_HEIGHT; y++) {
			BlockState state = EndTestSupport.state(end, min, x, y, z);
			if (!state.isAir()) {
				return state + " at +" + (y - slab);
			}
		}
		return "none";
	}

	/** Every way the level departs from the plan, as text; empty when it matches. */
	static String mismatches(final ServerLevel end, final CastleLayout layout, final BlockPos min) {
		StringBuilder bad = new StringBuilder();
		for (int floor = 1; floor <= EndCastle.FLOORS; floor++) {
			CastleLayout.FloorPlan plan = layout.floor(floor);
			int slab = EndCastle.slabY(floor);
			for (int x = 0; x < EndCastle.SIZE; x++) {
				for (int z = 0; z < EndCastle.SIZE; z++) {
					boolean shaft = (plan.up() != null && plan.up().contains(x, z)) || (plan.arrival() != null && plan.arrival().contains(x, z));
					int cellX = x;
					int cellZ = z;
					boolean chest = plan.chests().stream().anyMatch(spot -> spot.x() == cellX && spot.z() == cellZ);
					byte kind = plan.kind(x, z);
					int solidLayers = 0;
					for (int y = slab + 1; y < slab + EndCastle.FLOOR_HEIGHT; y++) {
						solidLayers += EndTestSupport.state(end, min, x, y, z).isAir() ? 0 : 1;
					}
					if (kind == CastleLayout.WALL && solidLayers != EndCastle.FLOOR_HEIGHT - 1) {
						bad.append(" floor ").append(floor).append(" wall ").append(x).append(',').append(z).append(" has ").append(solidLayers).append(" layers");
					} else if (kind == CastleLayout.OPEN && !shaft && !chest && solidLayers != 0) {
						bad.append(" floor ").append(floor).append(" room cell ").append(x).append(',').append(z).append(" holds ").append(solidLayers).append(" layers, first ")
							.append(firstSolid(end, min, x, slab, z));
					} else if (kind == CastleLayout.DOOR) {
						// A doorway is clear up to the lintel, and the wall stands above it.
						int lintel = EndCastle.FLOOR_HEIGHT - 1 - CastleLayout.DOOR_HEIGHT;
						boolean barrier = plan.doors().stream().anyMatch(door -> door.contains(cellX, cellZ) && door.barrier());
						int low = 0;
						for (int y = slab + 1; y <= slab + CastleLayout.DOOR_HEIGHT; y++) {
							low += EndTestSupport.state(end, min, x, y, z).isAir() ? 0 : 1;
						}
						if (solidLayers != lintel + (barrier ? CastleLayout.DOOR_HEIGHT : 0) || (!barrier && low != 0)) {
							bad.append(" floor ").append(floor).append(" door ").append(x).append(',').append(z).append(" has ").append(solidLayers).append(" layers");
						}
					}
				}
			}
			for (CastleLayout.Chest chest : plan.chests()) {
				if (!EndTestSupport.state(end, min, chest.x(), EndCastle.standY(floor), chest.z()).is(Blocks.CHEST)) {
					bad.append(" floor ").append(floor).append(" chest missing at ").append(chest.x()).append(',').append(chest.z());
				}
			}
		}
		return bad.toString();
	}
}
