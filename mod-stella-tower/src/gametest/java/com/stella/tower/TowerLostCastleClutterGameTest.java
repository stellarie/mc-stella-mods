package com.stella.tower;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The clutter pass, the turret counts and the light of the top storey. The plan is read over many seeds, and
 * built castles are read block by block, so a plan that never reaches the world still fails.
 */
public class TowerLostCastleClutterGameTest {
	/** Seeds read for each variant. */
	private static final int SEEDS = 2000;

	private static long seed(final int index) {
		return index * 0x9E3779B97F4A7C15L + 0x2545F4914F6CDD1DL;
	}

	private static long key(final int x, final int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	/** The columns of every nook a turret or a chest of the storey stands in. */
	private static Set<Long> reserved(final LostCastlePlan.FloorPlan floor) {
		Set<Long> columns = new HashSet<>();
		for (TowerHex.Nook nook : TowerHex.nooks()) {
			boolean taken = floor.turrets().stream().anyMatch(turret -> turret.nook().equals(nook))
				|| floor.chests().stream().anyMatch(chest -> nook.holds(chest.x(), chest.z()));
			if (taken) {
				for (int[] column : nook.columns()) {
					columns.add(key(column[0], column[1]));
				}
			}
		}
		return columns;
	}

	/** The columns a floor prop or a ruin block of the storey stands on. */
	private static Set<Long> floorTaken(final LostCastlePlan.FloorPlan floor) {
		Set<Long> columns = new HashSet<>();
		for (LostCastlePlan.Room room : floor.rooms()) {
			for (LostCastlePlan.Furnishing prop : room.props()) {
				columns.add(key(prop.x(), prop.z()));
				columns.add(key(prop.x2(), prop.z2()));
			}
		}
		for (LostCastlePlan.Scatter item : floor.scatter()) {
			columns.add(key(item.x(), item.z()));
		}
		return columns;
	}

	/** The columns something hangs from the storey's ceiling in. */
	private static Set<Long> hung(final LostCastlePlan.FloorPlan floor) {
		Set<Long> columns = new HashSet<>();
		for (LostCastlePlan.Scatter item : floor.scatter()) {
			if (item.kind() == LostCastlePlan.ScatterKind.CHAIN) {
				columns.add(key(item.x(), item.z()));
			}
		}
		for (LostCastlePlan.Room room : floor.rooms()) {
			for (LostCastlePlan.Furnishing prop : room.props()) {
				if (prop.prop() == LostRoomTheme.Prop.CHAIN_RACK) {
					columns.add(key(prop.x(), prop.z()));
				}
			}
		}
		return columns;
	}

	/** The columns the floor above is gapped in, which leave no stone over a ceiling block. */
	private static Set<Long> gapped(final LostCastlePlan plan, final int storey) {
		Set<Long> columns = new HashSet<>();
		if (storey + 1 < plan.floors().size()) {
			for (LostCastlePlan.Scatter item : plan.floors().get(storey + 1).scatter()) {
				if (item.kind() == LostCastlePlan.ScatterKind.GAP) {
					columns.add(key(item.x(), item.z()));
				}
			}
		}
		return columns;
	}

	/** What is wrong with the counts of one plan's clutter, or nothing. */
	static String countFault(final LostCastlePlan plan) {
		for (LostCastlePlan.FloorPlan floor : plan.floors()) {
			int floorSlabs = 0;
			int ceilingSlabs = 0;
			for (LostCastlePlan.Clutter item : floor.clutter()) {
				floorSlabs += item.kind() == LostCastlePlan.ClutterKind.FLOOR_SLAB ? 1 : 0;
				ceilingSlabs += item.kind() == LostCastlePlan.ClutterKind.CEILING_SLAB ? 1 : 0;
			}
			if (floorSlabs < 1 || ceilingSlabs < 1) {
				return "storey " + floor.storey() + " holds " + floorSlabs + " floor slabs and " + ceilingSlabs + " ceiling slabs";
			}
			for (LostCastlePlan.Room room : floor.rooms()) {
				int count = 0;
				for (LostCastlePlan.Clutter item : floor.clutter()) {
					TowerHex.Cell cell = TowerHex.cellOf(item.x(), item.z());
					count += cell != null && cell.cx() == room.cx() && cell.cz() == room.cz() ? 1 : 0;
				}
				boolean exempt = LostCastlePlan.clutterExempt(floor.storey(), room.cx(), room.cz());
				if (count > LostCastleHex.CLUTTER_MAX || !exempt && count < LostCastleHex.CLUTTER_MIN) {
					return "storey " + floor.storey() + " room " + room.cx() + "," + room.cz() + " holds " + count + " pieces of clutter, not "
						+ LostCastleHex.CLUTTER_MIN + " to " + LostCastleHex.CLUTTER_MAX;
				}
			}
		}
		return "";
	}

	/** What is wrong with where one plan puts its clutter, or nothing. */
	static String placeFault(final LostCastlePlan plan) {
		int[] court = LostCastleVariant.court();
		for (LostCastlePlan.FloorPlan floor : plan.floors()) {
			int storey = floor.storey();
			boolean top = storey == TowerHex.STOREYS - 1;
			Set<Long> reserved = reserved(floor);
			Set<Long> taken = floorTaken(floor);
			Set<Long> hung = hung(floor);
			Set<Long> gapped = gapped(plan, storey);
			Set<Long> front = new HashSet<>();
			for (LostCastlePlan.Turret turret : floor.turrets()) {
				for (int[] column : LostCastlePlan.turretFront(turret)) {
					front.add(key(column[0], column[1]));
				}
			}
			Set<Long> lowSeen = new HashSet<>();
			Set<Long> propSeen = new HashSet<>();
			Set<Long> props = new HashSet<>();
			floor.rooms().forEach(room -> room.props().stream().filter(prop -> LostCastlePlan.carriesCandle(prop.prop()))
				.forEach(prop -> props.add(key(prop.x(), prop.z()))));
			Set<Long> highSeen = new HashSet<>();
			for (LostCastlePlan.Clutter item : floor.clutter()) {
				int x = item.x();
				int z = item.z();
				long at = key(x, z);
				String where = "storey " + storey + " " + item.kind() + " at " + x + "," + z;
				if (TowerHex.kind(x, z) != TowerHex.Part.WAY || TowerHex.cellOf(x, z) == null) {
					return where + " is not on a way";
				}
				if (TowerHex.onCrossing(x, z)) {
					return where + " stands on a crossing or in a doorway";
				}
				if (LostCastleHex.inStairLane(x, z)) {
					return where + " stands in the stair lane";
				}
				if (top && LostCastleHex.inPortalHall(x, LostCastleHex.portalSpot().getY(), z)) {
					return where + " stands in the portal hall";
				}
				if (storey == 0 && x >= court[0] && x <= court[2] && z >= court[1] && z <= court[3]) {
					return where + " stands on the yard path";
				}
				if (front.contains(at) || reserved.contains(at)) {
					return where + " stands in a turret or a chest nook or before a turret's open face";
				}
				if (item.kind() == LostCastlePlan.ClutterKind.PROP_CANDLE) {
					if (!props.contains(at) || !propSeen.add(at)) {
						return where + " stands on no prop, or on one that already carries a candle";
					}
				} else if (item.kind().high()) {
					if (hung.contains(at) || gapped.contains(at) || !highSeen.add(at)) {
						return where + " hangs where a chain hangs, over a floor gap, or twice";
					}
				} else if (taken.contains(at) || !lowSeen.add(at)) {
					return where + " stands on a prop, on ruin, or twice";
				}
			}
		}
		return "";
	}

	/** The number of pieces of clutter a plan holds, by kind, to show the draw in the run log. */
	private static Map<LostCastlePlan.ClutterKind, Integer> census(final LostCastlePlan plan) {
		Map<LostCastlePlan.ClutterKind, Integer> census = new EnumMap<>(LostCastlePlan.ClutterKind.class);
		plan.floors().forEach(floor -> floor.clutter().forEach(item -> census.merge(item.kind(), 1, Integer::sum)));
		return census;
	}

	@GameTest(maxTicks = 200)
	public void everyRoomHoldsClutterAndEveryStoreyHoldsBothSlabs(final GameTestHelper helper) {
		int bad = 0;
		int dark = 0;
		int unlit = 0;
		Map<String, Integer> shortfalls = new java.util.TreeMap<>();
		int rooms = 0;
		String first = "";
		Map<LostCastlePlan.ClutterKind, Integer> total = new EnumMap<>(LostCastlePlan.ClutterKind.class);
		for (LostCastleVariant variant : LostCastleVariant.values()) {
			for (int i = 0; i < SEEDS; i++) {
				LostCastlePlan plan = LostCastlePlan.of(seed(i), variant);
				String fault = countFault(plan);
				if (!fault.isEmpty()) {
					shortfalls.merge(fault.contains(" pieces") ? fault.substring(0, fault.indexOf(" pieces")) : fault, 1, Integer::sum);
					bad++;
					first = first.isEmpty() ? variant + "/" + i + ": " + fault : first;
				}
				census(plan).forEach((kind, count) -> total.merge(kind, count, Integer::sum));
				LostCastlePlan.FloorPlan roof = plan.floors().get(TowerHex.STOREYS - 1);
				for (LostCastlePlan.Room room : roof.rooms()) {
					boolean lit = roof.clutter().stream().anyMatch(item -> (item.kind() == LostCastlePlan.ClutterKind.CANDLE || item.kind() == LostCastlePlan.ClutterKind.PROP_CANDLE)
						&& TowerHex.cellOf(item.x(), item.z()).cx() == room.cx() && TowerHex.cellOf(item.x(), item.z()).cz() == room.cz());
					boolean anyLight = lit || room.props().stream().anyMatch(prop -> prop.prop() == LostRoomTheme.Prop.CANDLE
						|| prop.prop() == LostRoomTheme.Prop.ALTAR || prop.prop() == LostRoomTheme.Prop.SOUL_LANTERN)
						|| roof.scatter().stream().anyMatch(item -> item.kind() == LostCastlePlan.ScatterKind.CANDLE
						&& TowerHex.cellOf(item.x(), item.z()).cx() == room.cx() && TowerHex.cellOf(item.x(), item.z()).cz() == room.cz());
					unlit += anyLight || LostCastlePlan.clutterExempt(roof.storey(), room.cx(), room.cz()) ? 0 : 1;
					dark += lit ? 0 : 1;
					rooms++;
				}
			}
		}
		TowerTestSupport.result("castleClutterCounts", "plans=" + SEEDS * LostCastleVariant.values().length + " bad=" + bad
			+ " min=" + LostCastleHex.CLUTTER_MIN + " max=" + LostCastleHex.CLUTTER_MAX + " kinds=" + total + " topRoomsWithoutClutterCandle=" + dark + "/" + rooms + " topRoomsWithNoCandleAtAll=" + unlit
			+ " shortfalls=" + shortfalls + " first=" + first);
		helper.assertTrue(total.values().stream().mapToInt(Integer::intValue).sum() > 0, "no plan holds any clutter");
		helper.assertTrue(bad == 0, bad + " plans break the clutter counts; first: " + first);
		helper.assertTrue(unlit == 0, unlit + " rooms of the top storey hold no candle at all");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void clutterNeverStandsOnACrossingADoorwayTheStairTheHallTheYardOrBeforeATurret(final GameTestHelper helper) {
		int bad = 0;
		int pieces = 0;
		String first = "";
		for (LostCastleVariant variant : LostCastleVariant.values()) {
			for (int i = 0; i < SEEDS; i++) {
				LostCastlePlan plan = LostCastlePlan.of(seed(i), variant);
				String fault = placeFault(plan);
				pieces += plan.floors().stream().mapToInt(floor -> floor.clutter().size()).sum();
				if (!fault.isEmpty()) {
					bad++;
					first = first.isEmpty() ? variant + "/" + i + ": " + fault : first;
				}
				if (!plan.equals(LostCastlePlan.of(seed(i), variant))) {
					bad++;
					first = first.isEmpty() ? variant + "/" + i + ": the plan differs on a second draw" : first;
				}
			}
		}
		TowerTestSupport.result("castleClutterPlace", "pieces=" + pieces + " bad=" + bad + " first=" + first);
		helper.assertTrue(pieces > 0, "no plan holds any clutter");
		helper.assertTrue(bad == 0, bad + " plans put clutter where it may not stand; first: " + first);
		helper.succeed();
	}

	/** Whether the block in the world is the one a piece of clutter names. */
	private static boolean shows(final LostCastlePlan.Clutter item, final BlockState state) {
		return switch (item.kind()) {
			case FLOOR_SLAB -> state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
			case CEILING_SLAB -> state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.TOP;
			case CARPET -> state.getBlock() instanceof CarpetBlock;
			case TRAPDOOR -> state.getBlock() instanceof TrapDoorBlock;
			case FLOWER_POT -> state.is(Blocks.FLOWER_POT);
			case BONE_BLOCK -> state.is(Blocks.BONE_BLOCK);
			case SKULL -> state.is(Blocks.SKELETON_SKULL);
			case CANDLE, PROP_CANDLE -> state.is(Blocks.CANDLE);
			case COBWEB -> state.is(Blocks.COBWEB);
		};
	}

	/** Whether every piece of clutter a built castle's plan names stands in the world, and how many were read. */
	private static int built(final TowerLostRoomGameTest.Arena arena, final List<String> faults) {
		ServerLevel level = arena.level();
		LostRoomPiece piece = arena.piece();
		int read = 0;
		for (LostCastlePlan.FloorPlan floor : piece.plan().floors()) {
			int feet = TowerHex.floorAt(floor.storey()) + 1;
			int ceiling = TowerHex.ceilingAt(floor.storey());
			for (LostCastlePlan.Clutter item : floor.clutter()) {
				read++;
				BlockState state = level.getBlockState(piece.worldPos(item.x(), item.kind().heightAt(feet, ceiling), item.z()));
				if (!shows(item, state)) {
					faults.add("storey " + floor.storey() + " " + item.kind() + " at " + item.x() + "," + item.z() + " is " + state.getBlock());
				}
			}
		}
		return read;
	}

	@GameTest(maxTicks = 400)
	public void builtCastlesShowTheirClutterInTheWorld(final GameTestHelper helper) {
		java.util.ArrayList<String> faults = new java.util.ArrayList<>();
		int read = 0;
		for (LostCastleVariant variant : LostCastleVariant.values()) {
			for (Direction facing : Direction.Plane.HORIZONTAL) {
				read += built(TowerLostRoomGameTest.castle(helper, variant, facing), faults);
			}
		}
		TowerTestSupport.result("castleClutterBuilt", "read=" + read + " faults=" + faults.size() + " first=" + (faults.isEmpty() ? "" : faults.get(0)));
		helper.assertTrue(read > 0, "no built castle holds any planned clutter");
		helper.assertTrue(faults.isEmpty(), faults.size() + " pieces of planned clutter are missing in the world; first: " + (faults.isEmpty() ? "" : faults.get(0)));
		helper.succeed();
	}

	/** The turrets of every storey: two on the ground, two on the middle, eight on the top, each pair in two rooms. */
	@GameTest(maxTicks = 200)
	public void turretsAreTwoTwoAndEight(final GameTestHelper helper) {
		int bad = 0;
		String first = "";
		int[] wanted = {2, 2, 8};
		for (LostCastleVariant variant : LostCastleVariant.values()) {
			for (int i = 0; i < SEEDS; i++) {
				LostCastlePlan plan = LostCastlePlan.of(seed(i), variant);
				for (LostCastlePlan.FloorPlan floor : plan.floors()) {
					Set<Long> rooms = new HashSet<>();
					floor.turrets().forEach(turret -> rooms.add(key(turret.nook().cx(), turret.nook().cz())));
					String fault = floor.turrets().size() != wanted[floor.storey()] ? "holds " + floor.turrets().size() + " turrets, not " + wanted[floor.storey()]
						: LostCastlePlan.turretsAt(floor.storey()) != wanted[floor.storey()] ? "turretsAt says " + LostCastlePlan.turretsAt(floor.storey())
						: rooms.size() != wanted[floor.storey()] ? "its turrets share a room" : "";
					if (!fault.isEmpty()) {
						bad++;
						first = first.isEmpty() ? variant + "/" + i + " storey " + floor.storey() + " " + fault : first;
					}
				}
			}
		}
		TowerTestSupport.result("castleTurretCounts", "wanted=2,2,8 bad=" + bad + " first=" + first);
		helper.assertTrue(bad == 0, bad + " storeys hold the wrong turrets; first: " + first);
		helper.succeed();
	}

	/** Every block of the top storey that gives light, by name, with the portal's own blocks left out. */
	static Map<String, Integer> lights(final TowerLostRoomGameTest.Arena arena) {
		ServerLevel level = arena.level();
		LostRoomPiece piece = arena.piece();
		Map<String, Integer> found = new LinkedHashMap<>();
		int top = TowerHex.STOREYS - 1;
		for (int x = 0; x < LostRoomPiece.LENGTH; x++) {
			for (int z = 0; z < LostRoomPiece.WIDTH; z++) {
				for (int y = TowerHex.floorAt(top); y <= TowerHex.ceilingAt(top); y++) {
					BlockPos at = piece.worldPos(x, y, z);
					BlockState state = level.getBlockState(at);
					if (state.getLightEmission() > 0 && !state.is(Blocks.CANDLE) && !state.is(TowerBlocks.TOWER_PORTAL) && !state.is(TowerBlocks.TOWER_FRAME)) {
						found.merge(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), 1, Integer::sum);
					}
				}
			}
		}
		return found;
	}

	@GameTest(maxTicks = 400)
	public void candlesAreTheOnlyLightOnTheTopStorey(final GameTestHelper helper) {
		Map<String, Integer> others = new LinkedHashMap<>();
		int candles = 0;
		int castles = 0;
		for (LostCastleVariant variant : LostCastleVariant.values()) {
			for (Direction facing : Direction.Plane.HORIZONTAL) {
				TowerLostRoomGameTest.Arena arena = TowerLostRoomGameTest.castle(helper, variant, facing);
				castles++;
				lights(arena).forEach((name, count) -> others.merge(name, count, Integer::sum));
				int top = TowerHex.STOREYS - 1;
				for (int x = 0; x < LostRoomPiece.LENGTH; x++) {
					for (int z = 0; z < LostRoomPiece.WIDTH; z++) {
						for (int y = TowerHex.floorAt(top); y <= TowerHex.ceilingAt(top); y++) {
							BlockState state = arena.level().getBlockState(arena.piece().worldPos(x, y, z));
							candles += state.is(Blocks.CANDLE) && state.getValue(net.minecraft.world.level.block.CandleBlock.LIT) ? 1 : 0;
						}
					}
				}
			}
		}
		TowerTestSupport.result("castleTopStoreyLight", "castles=" + castles + " litCandles=" + candles + " otherLights=" + others);
		helper.assertTrue(candles > 0, "the top storey holds no lit candle");
		helper.assertTrue(others.isEmpty(), "the top storey holds a light that is not a candle: " + others);
		helper.succeed();
	}
}
