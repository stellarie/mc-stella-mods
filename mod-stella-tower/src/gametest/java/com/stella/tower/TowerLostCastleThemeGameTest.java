package com.stella.tower;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** The rooms of a built castle: one theme each, its props present, the ways clear, and nothing that hurts. */
public class TowerLostCastleThemeGameTest {
	private interface Reading {
		String read(TowerLostRoomGameTest.Arena arena);
	}

	private static String overCastles(final GameTestHelper helper, final Reading reading) {
		for (LostCastleVariant variant : LostCastleVariant.values()) {
			for (Direction facing : Direction.Plane.HORIZONTAL) {
				TowerLostRoomGameTest.Arena arena = TowerLostRoomGameTest.castle(helper, variant, facing);
				String fault = reading.read(arena);
				if (!fault.isEmpty()) {
					return variant + " " + facing + ": " + fault;
				}
			}
		}
		return "";
	}

	/** Whether every room holds at least the blocks of every prop of its theme, counted from the theme's own recipe. */
	static String signatures(final TowerLostRoomGameTest.Arena arena) {
		ServerLevel level = arena.level();
		LostRoomPiece piece = arena.piece();
		for (LostCastlePlan.FloorPlan floor : piece.plan().floors()) {
			Set<LostRoomTheme> seen = EnumSet.noneOf(LostRoomTheme.class);
			int feet = TowerHex.floorAt(floor.storey()) + 1;
			int ceiling = TowerHex.ceilingAt(floor.storey());
			for (LostCastlePlan.Room room : floor.rooms()) {
				if (!seen.add(room.theme())) {
					return "storey " + floor.storey() + " repeats " + room.theme();
				}
				Map<LostRoomTheme.Prop, Integer> found = new EnumMap<>(LostRoomTheme.Prop.class);
				for (int x = TowerHex.cellX(room.cx()); x < TowerHex.cellX(room.cx()) + TowerMaze.PATH; x++) {
					for (int z = TowerHex.cellZ(room.cz()); z < TowerHex.cellZ(room.cz()) + TowerMaze.PATH; z++) {
						for (int y = feet; y < ceiling; y++) {
							BlockState state = level.getBlockState(piece.worldPos(x, y, z));
							for (LostRoomTheme.Prop prop : room.theme().recipe()) {
								if (prop.matches(state, floor.storey() == TowerHex.STOREYS - 1)) {
									found.merge(prop, 1, Integer::sum);
								}
							}
						}
					}
				}
				Map<LostRoomTheme.Prop, Integer> wanted = new EnumMap<>(LostRoomTheme.Prop.class);
				for (LostRoomTheme.Prop prop : room.theme().recipe()) {
					wanted.merge(prop, prop.blocks(), Integer::sum);
				}
				for (Map.Entry<LostRoomTheme.Prop, Integer> entry : wanted.entrySet()) {
					if (found.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
						return "storey " + floor.storey() + " room " + room.cx() + "," + room.cz() + " " + room.theme() + " holds "
							+ found.getOrDefault(entry.getKey(), 0) + " of " + entry.getKey() + ", not " + entry.getValue();
					}
				}
			}
			if (seen.size() != TowerHex.cells().size()) {
				return "storey " + floor.storey() + " holds " + seen.size() + " themes";
			}
		}
		return "";
	}

	@GameTest(maxTicks = 400)
	public void everyRoomHoldsItsThemeAndNoThemeRepeatsOnAStorey(final GameTestHelper helper) {
		String fault = overCastles(helper, TowerLostCastleThemeGameTest::signatures);
		TowerTestSupport.result("castleThemes", "fault=" + fault);
		helper.assertTrue(fault.isEmpty(), fault);
		helper.succeed();
	}

	/** Whether every crossing of every room is clear to head height, bar the stair, the portal hall and the lamp. */
	static String clear(final TowerLostRoomGameTest.Arena arena) {
		ServerLevel level = arena.level();
		LostRoomPiece piece = arena.piece();
		int checked = 0;
		for (int storey = 0; storey < TowerHex.STOREYS; storey++) {
			int feet = TowerHex.floorAt(storey) + 1;
			boolean top = storey == TowerHex.STOREYS - 1;
			for (TowerHex.Cell cell : TowerHex.cells()) {
				for (int x = TowerHex.cellX(cell.cx()); x < TowerHex.cellX(cell.cx()) + TowerMaze.PATH; x++) {
					for (int z = TowerHex.cellZ(cell.cz()); z < TowerHex.cellZ(cell.cz()) + TowerMaze.PATH; z++) {
						if (!TowerHex.onCrossing(x, z) || TowerHex.kind(x, z) != TowerHex.Part.WAY || LostCastleHex.inStairLane(x, z)
							|| top && LostCastleHex.inPortalHall(x, LostCastleHex.portalSpot().getY(), z)) {
							continue;
						}
						checked++;
						for (int y = feet; y < feet + 3; y++) {
							BlockPos at = piece.worldPos(x, y, z);
							if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
								return "storey " + storey + " crossing " + x + "," + z + " is blocked at " + y + " by " + level.getBlockState(at).getBlock();
							}
						}
					}
				}
			}
		}
		return checked == 0 ? "no crossing was read" : "";
	}

	@GameTest(maxTicks = 400)
	public void propsLeaveEveryCrossingClear(final GameTestHelper helper) {
		String fault = overCastles(helper, TowerLostCastleThemeGameTest::clear);
		TowerTestSupport.result("castleCrossings", "fault=" + fault);
		helper.assertTrue(fault.isEmpty(), fault);
		helper.succeed();
	}

	private static boolean banned(final BlockState state) {
		return state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.CACTUS)
			|| state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.TNT)
			|| state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT);
	}

	/** Whether the castle holds none of the blocks that burn, blast or hurt a walker. */
	static String hazards(final TowerLostRoomGameTest.Arena arena) {
		ServerLevel level = arena.level();
		BoundingBox box = arena.piece().getBoundingBox();
		for (BlockPos at : BlockPos.betweenClosed(box.minX(), box.minY() - 1, box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (banned(level.getBlockState(at))) {
				return "a banned block " + level.getBlockState(at).getBlock() + " stands at " + at;
			}
		}
		return "";
	}

	@GameTest(maxTicks = 400)
	public void noBannedBlockStandsInACastle(final GameTestHelper helper) {
		String fault = overCastles(helper, TowerLostCastleThemeGameTest::hazards);
		TowerTestSupport.result("castleBanned", "fault=" + fault);
		helper.assertTrue(fault.isEmpty(), fault);
		helper.succeed();
	}
}
