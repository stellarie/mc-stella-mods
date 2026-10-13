package com.stella.tower;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A lost castle goes down: the ring of ruins and the entrance stair on the surface, then three to six floors
 * under the ground, each twelve blocks from the one above, joined by stairs that change sides from floor to
 * floor. Every bound is read from the constant the castle ships with, so moving a constant moves its guard.
 */
public class TowerLostCastleDescentGameTest {
	private static final Direction[] FACINGS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	/** The runs of open blocks, bottom to top, in one column of a castle up to its top ceiling: world y, first and last. */
	private static List<int[]> runs(final TowerLostCastleSupport.Arena arena, final int x, final int z) {
		List<int[]> runs = new ArrayList<>();
		int start = -1;
		int top = LostCastleGrid.topY(arena.piece().floors());
		for (int y = 0; y <= top; y++) {
			boolean open = arena.at(x, y, z).isAir();
			if (open && start < 0) {
				start = y;
			}
			if (!open && start >= 0) {
				runs.add(new int[] {arena.box().minY() + start, arena.box().minY() + y - 1});
				start = -1;
			}
		}
		return runs;
	}

	/**
	 * Catches a castle that stands up from the ground, a floor count that is not the one built, a floor that is not
	 * twelve blocks from the one above it, and a top floor that is not under the entrance.
	 */
	@GameTest(maxTicks = 100)
	public void everyFloorLiesBelowTheEntranceAndTwelveBlocksFromTheOneAbove(final GameTestHelper helper) {
		List<String> faults = new ArrayList<>();
		StringBuilder report = new StringBuilder();
		for (int floors = LostCastleGrid.MIN_FLOORS; floors <= LostCastleGrid.MAX_FLOORS; floors++) {
			LostCastlePalette palette = LostCastlePalette.values()[floors % LostCastlePalette.values().length];
			TowerLostCastleSupport.Arena arena = TowerLostCastleSupport.castle(helper, palette, FACINGS[floors % FACINGS.length], floors);
			// A lane crossing of a side room, which no pillar, wall or prop stands on.
			List<int[]> runs = runs(arena, LostCastleGrid.cellLow(0) + LostCastleGrid.DOOR_FROM, LostCastleGrid.cellLow(1) + LostCastleGrid.DOOR_FROM);
			int entrance = arena.piece().entranceTop().getY();
			report.append(" [floors=").append(floors).append(" runs=").append(runs.size()).append(" entranceY=").append(entrance).append(']');
			if (runs.size() != floors) {
				faults.add(floors + " floors built, " + runs.size() + " found");
				continue;
			}
			for (int i = 0; i < runs.size(); i++) {
				int[] run = runs.get(i);
				if (run[1] - run[0] + 1 != LostCastleGrid.CLEAR) {
					faults.add("floor " + i + " of " + floors + " is " + (run[1] - run[0] + 1) + " open blocks high, not " + LostCastleGrid.CLEAR);
				}
				if (i > 0 && run[0] - runs.get(i - 1)[0] != LostCastleGrid.FLOOR_HEIGHT) {
					faults.add("floor " + i + " of " + floors + " is " + (run[0] - runs.get(i - 1)[0]) + " blocks from the one under it, not " + LostCastleGrid.FLOOR_HEIGHT);
				}
			}
			// Runs run bottom to top, so the last is the top floor.
			int topFeet = runs.get(runs.size() - 1)[0];
			if (topFeet >= entrance) {
				faults.add("the top floor's feet at " + topFeet + " are not under the entrance at " + entrance);
			}
			if (entrance - topFeet != arena.piece().drop()) {
				faults.add("the entrance is " + (entrance - topFeet) + " over the top floor, not the drop " + arena.piece().drop());
			}
			if (runs.get(0)[0] != arena.box().minY() + 1) {
				faults.add("the bottom floor does not stand on the castle's base slab");
			}
		}
		TowerTestSupport.result("castleDescent", "faults=" + faults + report);
		helper.assertTrue(faults.isEmpty(), "the castle does not descend as built: " + faults);
		helper.succeed();
	}

	/** Catches a floor count outside 3 to 6, or a count that never draws one of the four. */
	@GameTest(maxTicks = 20)
	public void floorCountIsThreeToSixOverASeededSample(final GameTestHelper helper) {
		int samples = 600;
		int[] counts = new int[LostCastleGrid.MAX_FLOORS + 2];
		int outside = 0;
		for (int seed = 0; seed < samples; seed++) {
			int floors = LostCastlePlan.floorCount(RandomSource.create(seed * 7919L + 13L));
			if (floors < LostCastleGrid.MIN_FLOORS || floors > LostCastleGrid.MAX_FLOORS) {
				outside++;
			} else {
				counts[floors]++;
			}
		}
		StringBuilder report = new StringBuilder();
		boolean every = true;
		for (int floors = LostCastleGrid.MIN_FLOORS; floors <= LostCastleGrid.MAX_FLOORS; floors++) {
			report.append(' ').append(floors).append('=').append(counts[floors]);
			every &= counts[floors] >= samples / (2 * (LostCastleGrid.MAX_FLOORS - LostCastleGrid.MIN_FLOORS + 1));
		}
		TowerTestSupport.result("castleFloorCount", "samples=" + samples + " outside=" + outside + report);
		helper.assertTrue(outside == 0, outside + " castles drew a floor count outside " + LostCastleGrid.MIN_FLOORS + " to " + LostCastleGrid.MAX_FLOORS);
		helper.assertTrue(every, "the floor count does not draw every number fairly:" + report);
		helper.succeed();
	}

	/**
	 * Catches an entrance stair that is not two wide, is not at the middle of the ring, has no soul torch on one
	 * side, or does not start at the ring's floor and end on the top floor.
	 */
	@GameTest(maxTicks = 100)
	public void theEntranceStairIsTwoWideAtTheRingCenterWithSoulTorchesOnBothSides(final GameTestHelper helper) {
		List<String> faults = new ArrayList<>();
		StringBuilder report = new StringBuilder();
		for (LostCastlePalette palette : LostCastlePalette.values()) {
			TowerLostCastleSupport.Arena arena = TowerLostCastleSupport.castle(helper, palette, FACINGS[palette.ordinal() % FACINGS.length], 4);
			int surface = arena.piece().surfaceFeet();
			int drop = arena.piece().drop();
			int mid = LostCastleGrid.MID;
			int minX = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE;
			int minZ = Integer.MAX_VALUE;
			int maxZ = Integer.MIN_VALUE;
			for (int x = mid - 14; x <= mid + 15; x++) {
				for (int z = mid - 14; z <= mid + 15; z++) {
					double away = Math.hypot(x + 0.5 - LostCastleGrid.SIZE / 2.0, z + 0.5 - LostCastleGrid.SIZE / 2.0);
					boolean stone = arena.at(x, surface, z).is(palette.wall()) || arena.at(x, surface, z).is(palette.worn());
					if (stone && away > LostCastleGrid.BENCH_RADIUS) {
						minX = Math.min(minX, x);
						maxX = Math.max(maxX, x);
						minZ = Math.min(minZ, z);
						maxZ = Math.max(maxZ, z);
					}
				}
			}
			for (int j = 1; j <= drop; j++) {
				int z = LostCastleGrid.ENTRY_Z + j;
				int steps = 0;
				List<Integer> at = new ArrayList<>();
				for (int x = mid - 8; x <= mid + 9; x++) {
					if (arena.at(x, surface - j, z).is(palette.stairs())) {
						steps++;
						at.add(x);
					}
				}
				if (steps != LostCastleGrid.STAIR_WIDTH || !at.equals(List.of(mid, mid + 1))) {
					faults.add(palette + " row " + j + " holds " + steps + " steps at x " + at + ", not " + LostCastleGrid.STAIR_WIDTH + " at the ring's middle");
				}
			}
			int west = 0;
			int east = 0;
			for (int z = LostCastleGrid.ENTRY_Z; z <= LostCastleGrid.ENTRY_Z + 4; z++) {
				west += arena.at(mid - 1, surface + 1, z).is(Blocks.SOUL_TORCH) ? 1 : 0;
				east += arena.at(mid + 2, surface + 1, z).is(Blocks.SOUL_TORCH) ? 1 : 0;
			}
			if (west < 2 || east < 2) {
				faults.add(palette + " lights the stair with " + west + " soul torches on one side and " + east + " on the other");
			}
			// The ring is centred on the stair: its wall reaches as far one way as the other.
			boolean centred = Math.abs((minX + maxX) - (2 * mid + 1)) <= 2 && Math.abs((minZ + maxZ) - (2 * mid + 1)) <= 2;
			if (!centred) {
				faults.add(palette + " ring wall spans x " + minX + ".." + maxX + " and z " + minZ + ".." + maxZ + ", not round the stair at " + mid);
			}
			if (maxX - minX < 14 || maxX - minX > 24) {
				faults.add(palette + " ring is " + (maxX - minX + 1) + " wide, not a small ring");
			}
			report.append(" [").append(palette).append(" drop=").append(drop).append(" torches=").append(west).append('/').append(east)
				.append(" ringX=").append(minX).append("..").append(maxX).append(" ringZ=").append(minZ).append("..").append(maxZ).append(']');
		}
		TowerTestSupport.result("castleEntrance", "faults=" + faults + report);
		helper.assertTrue(faults.isEmpty(), "the entrance stair is wrong: " + faults);
		helper.succeed();
	}

	/** The open columns of a floor's slab: the shaft of the stair that goes down from that floor. */
	private static List<int[]> holes(final TowerLostCastleSupport.Arena arena, final int floor) {
		List<int[]> holes = new ArrayList<>();
		int y = LostCastleGrid.slabY(arena.piece().floors(), floor);
		for (int x = 0; x < LostCastleGrid.SIZE; x++) {
			for (int z = 0; z < LostCastleGrid.SIZE; z++) {
				if (arena.at(x, y, z).isAir()) {
					holes.add(new int[] {x, z});
				}
			}
		}
		return holes;
	}

	/**
	 * Catches stairs that are not two wide, that sit on the same side as the stairs above, or that leave a
	 * floor unreachable: every room of every floor is walked to from the ring, and the ring from the portal.
	 */
	@GameTest(maxTicks = 200)
	public void stairsChangeSideFromFloorToFloorAndEveryRoomIsWalkable(final GameTestHelper helper) {
		List<String> faults = new ArrayList<>();
		StringBuilder report = new StringBuilder();
		for (int floors : new int[] {LostCastleGrid.MAX_FLOORS, LostCastleGrid.MIN_FLOORS}) {
			TowerLostCastleSupport.Arena arena = TowerLostCastleSupport.castle(helper, LostCastlePalette.GREEN, FACINGS[floors % FACINGS.length], floors);
			LostRoomPiece piece = arena.piece();
			int entranceLanding = LostCastleGrid.ENTRY_Z + piece.drop() + 1;
			boolean entranceSouth = entranceLanding > LostCastleGrid.MID;
			List<Boolean> sides = new ArrayList<>();
			for (int floor = 0; floor < floors - 1; floor++) {
				List<int[]> holes = holes(arena, floor);
				Set<Integer> rows = new HashSet<>();
				boolean north = holes.stream().allMatch(hole -> hole[1] < LostCastleGrid.MID);
				boolean south = holes.stream().allMatch(hole -> hole[1] > LostCastleGrid.MID);
				holes.forEach(hole -> rows.add(hole[1]));
				if (rows.size() != LostCastleGrid.STAIR_WIDTH || holes.isEmpty() || !(north || south)) {
					faults.add(floors + " floors: floor " + floor + " stair opens " + rows.size() + " rows, " + holes.size() + " columns");
				}
				sides.add(north);
			}
			if (!sides.get(0) == entranceSouth) {
				faults.add(floors + " floors: the stair of floor 0 is on the entrance's side");
			}
			for (int floor = 1; floor < sides.size(); floor++) {
				if (sides.get(floor).equals(sides.get(floor - 1))) {
					faults.add(floors + " floors: the stair of floor " + floor + " is on the side of the stair above it");
				}
			}
			BoundingBox box = arena.box();
			Set<BlockPos> fromRing = TowerLostCastleSupport.walk(arena.level(), box, piece.entranceTop());
			int unreached = 0;
			for (int floor = 0; floor < floors; floor++) {
				for (int cz = 0; cz < LostCastleGrid.COLS; cz++) {
					for (int cx = 0; cx < LostCastleGrid.COLS; cx++) {
						boolean hub = floor == floors - 1 && cx == 1 && cz == 1;
						int offset = hub ? 1 : LostCastleGrid.DOOR_FROM;
						BlockPos spot = arena.spot(floor, LostCastleGrid.cellLow(cx) + offset, LostCastleGrid.cellLow(cz) + offset);
						if (!fromRing.contains(spot)) {
							unreached++;
							faults.add(floors + " floors: floor " + floor + " room " + cx + "," + cz + " is not walked to from the ring");
						}
					}
				}
			}
			BlockPos front = piece.worldPos(LostCastleBuilder.PORTAL_X, LostCastleGrid.feet(floors, floors - 1) + 1, LostCastleBuilder.PORTAL_Z + 2);
			boolean reachesPortal = fromRing.contains(front);
			Set<BlockPos> fromPortal = TowerLostCastleSupport.walk(arena.level(), box, front);
			boolean back = fromPortal.contains(piece.entranceTop());
			if (!reachesPortal || !back) {
				faults.add(floors + " floors: the portal is " + (reachesPortal ? "" : "not reached from the ring, ") + (back ? "" : "and the ring is not reached from it"));
			}
			report.append(" [floors=").append(floors).append(" sides=").append(sides).append(" reached=").append(fromRing.size()).append(" unreached=")
				.append(unreached).append(" portal=").append(reachesPortal).append(" back=").append(back).append(']');
		}
		TowerTestSupport.result("castleStairs", "faults=" + faults + report);
		helper.assertTrue(faults.isEmpty(), "the stairs do not join the floors: " + faults);
		helper.succeed();
	}

	/** Catches a castle that has a block that burns or scalds a walker: no lava, fire or magma stands in it. */
	@GameTest(maxTicks = 100)
	public void noLavaFireOrMagmaStandsInACastle(final GameTestHelper helper) {
		TowerLostCastleSupport.Arena arena = TowerLostCastleSupport.castle(helper, LostCastlePalette.SANDSTONE, Direction.WEST, 3);
		BoundingBox box = arena.box();
		int banned = 0;
		for (BlockPos at : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState state = arena.level().getBlockState(at);
			boolean fire = state.is(Blocks.FIRE) || state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.TNT)
				|| state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.WITHER_ROSE);
			banned += fire ? 1 : 0;
		}
		TowerTestSupport.result("castleBanned", "blocks=" + banned);
		helper.assertTrue(banned == 0, banned + " blocks that harm a walker stand in the castle");
		helper.succeed();
	}
}
