package com.stella.tower;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** The castle's footprint: an octagon over the whole 3 x 3 lattice, with nine rooms and four cut corners. */
public class TowerLostCastleOctagonGameTest {
	private static int ways(final int cx, final int cz) {
		int count = 0;
		for (int dx = 0; dx < TowerMaze.PATH; dx++) {
			for (int dz = 0; dz < TowerMaze.PATH; dz++) {
				count += TowerHex.kind(TowerHex.cellX(cx) + dx, TowerHex.cellZ(cz) + dz) == TowerHex.Part.WAY ? 1 : 0;
			}
		}
		return count;
	}

	/** Nine rooms, four corners cut but each keeping its floor, the other five whole. */
	@GameTest(maxTicks = 20)
	public void theFootprintHoldsNineRoomsAndKeepsEveryCornerRoomBig(final GameTestHelper helper) {
		helper.assertTrue(TowerHex.cells().size() == TowerHex.COLS * TowerHex.COLS, "the footprint holds " + TowerHex.cells().size() + " rooms, not nine");
		StringBuilder report = new StringBuilder();
		int whole = TowerMaze.PATH * TowerMaze.PATH;
		for (int cz = 0; cz < TowerHex.COLS; cz++) {
			for (int cx = 0; cx < TowerHex.COLS; cx++) {
				boolean corner = (cx == 0 || cx == TowerHex.COLS - 1) && (cz == 0 || cz == TowerHex.COLS - 1);
				int columns = ways(cx, cz);
				report.append(" ").append(cx).append(cz).append('=').append(columns);
				helper.assertTrue(corner ? columns >= TowerHex.MIN_CORNER_COLUMNS && columns < whole : columns == whole,
					"room " + cx + "," + cz + " keeps " + columns + " floor columns");
			}
		}
		TowerTestSupport.result("castleOctagonRooms", "columns=" + report);
		helper.succeed();
	}

	/** No way column touches the outside, and the shape is the same turned about the lattice's middle. */
	@GameTest(maxTicks = 20)
	public void theOctagonIsClosedAndSymmetric(final GameTestHelper helper) {
		int left = TowerHex.cellX(0) - TowerMaze.WALL;
		int right = TowerHex.cellX(TowerHex.COLS - 1) + TowerMaze.PATH;
		int top = TowerHex.cellZ(0) - TowerMaze.WALL;
		int bottom = TowerHex.cellZ(TowerHex.COLS - 1) + TowerMaze.PATH;
		int open = 0;
		int unsymmetric = 0;
		int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int x = 0; x < LostRoomPiece.LENGTH; x++) {
			for (int z = 0; z < LostRoomPiece.WIDTH; z++) {
				TowerHex.Part part = TowerHex.kind(x, z);
				if (part == TowerHex.Part.WAY) {
					for (int[] side : sides) {
						open += TowerHex.kind(x + side[0], z + side[1]) == TowerHex.Part.OUT ? 1 : 0;
					}
				}
				unsymmetric += part != TowerHex.kind(left + right - x, z) || part != TowerHex.kind(x, top + bottom - z) ? 1 : 0;
			}
		}
		TowerTestSupport.result("castleOctagonShape", "wayColumnsTouchingOut=" + open + " unsymmetric=" + unsymmetric);
		helper.assertTrue(open == 0, open + " way sides touch the outside");
		helper.assertTrue(unsymmetric == 0, unsymmetric + " columns differ from their mirror");
		helper.succeed();
	}
}
