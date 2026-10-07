package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * The shape a lost castle is built to, as pure geometry: a hexagon of six straight sides, the lattice of
 * ways {@link TowerMaze#PATH} blocks wide between walls {@link TowerMaze#WALL} block thick inside it, and
 * the storeys those ways stand on. Nothing here reads or writes a block, so the shape can be proved on its
 * own; {@link LostCastleHex} lays the castle to it.
 */
public final class TowerHex {
	/** How many storeys every castle stands: the lost room's portal is on the top one. */
	public static final int STOREYS = 3;
	/** From one storey's floor to the next: the next floor lies this far above it. */
	public static final int STOREY = 8;
	/** A storey's open height, from its floor to its ceiling, and the height of a doorway. */
	public static final int CLEAR = 7;
	public static final int DOOR = 4;
	/** The hexagon's reach along x and z from the piece's middle, and the cut of its six corners. */
	public static final int HALF_X = 15;
	public static final int HALF_Z = 13;
	/** The lattice's cells a side. */
	public static final int COLS = 3;
	/** A doorway is this wide: two blocks. */
	public static final int DOOR_WIDTH = 2;

	/** A way of a storey: the lattice cell it is, and its middle. */
	public record Cell(int cx, int cz, BlockPos middle) {
	}

	/** What the footprint holds at a spot. */
	public enum Part {
		/** Outside the hexagon: no block of a storey stands here. */
		OUT,
		/** Inside the hexagon: a wall, a floor, a ceiling or a part of the ruin, but never a way. */
		WALL,
		/** A way of a storey: open from its floor to its block under its ceiling. */
		WAY
	}

	private TowerHex() {
	}

	/** The block the piece's middle stands in. */
	public static int middleX() {
		return LostRoomPiece.LENGTH / 2;
	}

	public static int middleZ() {
		return LostRoomPiece.WIDTH / 2;
	}

	/** The hexagon's width at a row, in blocks. */
	public static int widthAt(final int z) {
		return Math.max(0, 2 * (HALF_X - Math.max(0, Math.abs(z - middleZ()) - (HALF_Z - HALF_X))) + 1);
	}

	/** The first block of the hexagon at a row, or -1 when the row lies outside it. */
	public static int lowAt(final int z) {
		int width = widthAt(z);
		return width == 0 ? -1 : middleX() - width / 2;
	}

	/** What a spot holds, from the hexagon alone: the shape the footprint is read by. */
	public static Part kind(final int x, final int z) {
		int dx = Math.abs(x - middleX());
		int dz = Math.abs(z - middleZ());
		if (dx > HALF_X || dz > HALF_Z || dx + Math.max(0, dz - (HALF_Z - HALF_X)) > HALF_X) {
			return Part.OUT;
		}
		return inCell(x, z) ? Part.WAY : Part.WALL;
	}

	/** Whether a spot lies in one of the lattice's cells, at any height of a storey. */
	public static boolean inCell(final int x, final int z) {
		for (int cx = 0; cx < COLS; cx++) {
			for (int cz = 0; cz < COLS; cz++) {
				if (inCell(x, z, cx, cz)) {
					return true;
				}
			}
		}
		return false;
	}

	static boolean inCell(final int x, final int z, final int cx, final int cz) {
		return x >= cellX(cx) && x < cellX(cx) + TowerMaze.PATH && z >= cellZ(cz) && z < cellZ(cz) + TowerMaze.PATH;
	}

	/**
	 * The first block of a lattice cell along x. The lattice is centred on the piece: the middle cell's
	 * middle is the piece's own middle, so every cell the hexagon holds is a whole six-wide way.
	 */
	public static int cellX(final int index) {
		return middleX() - TowerMaze.PATH / 2 + (index - COLS / 2) * TowerMaze.PITCH;
	}

	/** The first block of a lattice cell along z, centred on the piece's middle the same way. */
	public static int cellZ(final int index) {
		return middleZ() - TowerMaze.PATH / 2 + (index - COLS / 2) * TowerMaze.PITCH;
	}

	/** Whether a spot lies on a cell's middle pair both ways: the crossing every walker of a way uses. */
	public static boolean onCrossing(final int x, final int z) {
		return middlePair(x, cellX(0)) && middlePair(z, cellZ(0));
	}

	/** How far a column lies into a lattice cell along x, or {@link TowerMaze#PATH} when it misses every cell. */
	public static int intoCellX(final int x) {
		return intoCell(x, cellX(0));
	}

	/** How far a column lies into a lattice cell along z. */
	public static int intoCellZ(final int z) {
		return intoCell(z, cellZ(0));
	}

	private static int intoCell(final int at, final int start) {
		int inside = Math.floorMod(at - start, TowerMaze.PITCH);
		return inside < TowerMaze.PATH ? inside : TowerMaze.PATH;
	}

	private static boolean middlePair(final int at, final int start) {
		int inside = Math.floorMod(at - start, TowerMaze.PITCH);
		return inside == TowerMaze.PATH / 2 - 1 || inside == TowerMaze.PATH / 2;
	}

	/** The ways of one storey: every lattice cell whose middle the hexagon holds. */
	public static List<Cell> cells() {
		List<Cell> cells = new ArrayList<>();
		for (int cz = 0; cz < COLS; cz++) {
			for (int cx = 0; cx < COLS; cx++) {
				BlockPos middle = new BlockPos(cellX(cx) + TowerMaze.PATH / 2, 0, cellZ(cz) + TowerMaze.PATH / 2);
				if (kind(middle.getX(), middle.getZ()) == Part.WAY) {
					cells.add(new Cell(cx, cz, middle));
				}
			}
		}
		return cells;
	}

	/** A storey's floor, as a height from the piece's own floor. */
	public static int floorAt(final int storey) {
		return storey * STOREY;
	}

	/** A storey's ceiling: the block its ways end under, and the floor of the storey above it. */
	public static int ceilingAt(final int storey) {
		return floorAt(storey) + 1 + CLEAR;
	}
}
