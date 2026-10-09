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

	/**
	 * One 2 x 2 corner of a way, where its floor is not the crossing. {@code x0}, {@code z0} is its low
	 * corner, {@code tuckX}, {@code tuckZ} the column that touches both walls of the corner, and
	 * {@code dx}, {@code dz} the steps from that column into the room. A usable nook has four columns of the
	 * hexagon and wall, not outside, behind both of its sides. A hub nook is one of the middle way's.
	 */
	public record Nook(int cx, int cz, int x0, int z0, int tuckX, int tuckZ, int dx, int dz, boolean usable, boolean hub) {
		/** The nook's four columns, the tuck column first. */
		public List<int[]> columns() {
			List<int[]> columns = new ArrayList<>();
			columns.add(new int[] {this.tuckX, this.tuckZ});
			columns.add(new int[] {this.tuckX + this.dx, this.tuckZ});
			columns.add(new int[] {this.tuckX, this.tuckZ + this.dz});
			columns.add(new int[] {this.tuckX + this.dx, this.tuckZ + this.dz});
			return columns;
		}

		public boolean holds(final int x, final int z) {
			return x >= this.x0 && x < this.x0 + 2 && z >= this.z0 && z < this.z0 + 2;
		}
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

	/**
	 * Whether a spot lies on the cross a way is walked by: its middle pair of columns, or of rows. Every
	 * doorway of a lattice cell opens onto that cross, so the ruin keeps off it and the way stays reached.
	 */
	public static boolean onCrossing(final int x, final int z) {
		return middlePair(x, cellX(0)) || middlePair(z, cellZ(0));
	}

	private static boolean middlePair(final int at, final int start) {
		int inside = Math.floorMod(at - start, TowerMaze.PITCH);
		return inside == TowerMaze.PATH / 2 - 1 || inside == TowerMaze.PATH / 2;
	}

	/** The ways of one storey: every lattice cell whose middle the hexagon holds. */
	public static List<Cell> cells() {
		return CELLS;
	}

	private static final List<Cell> CELLS = held();

	private static List<Cell> held() {
		List<Cell> cells = new ArrayList<>();
		for (int cz = 0; cz < COLS; cz++) {
			for (int cx = 0; cx < COLS; cx++) {
				BlockPos middle = new BlockPos(cellX(cx) + TowerMaze.PATH / 2, 0, cellZ(cz) + TowerMaze.PATH / 2);
				if (kind(middle.getX(), middle.getZ()) == Part.WAY) {
					cells.add(new Cell(cx, cz, middle));
				}
			}
		}
		return List.copyOf(cells);
	}

	/** The way a column belongs to, or null for a column no held way owns. */
	public static Cell cellOf(final int x, final int z) {
		for (Cell cell : cells()) {
			if (inCell(x, z, cell.cx(), cell.cz())) {
				return cell;
			}
		}
		return null;
	}

	/**
	 * Whether a column is a pocket: a fragment of a lattice cell that the hexagon cuts through but no held
	 * way owns. A pocket is sealed with wall, so nothing can stand in it.
	 */
	public static boolean isPocket(final int x, final int z) {
		return kind(x, z) == Part.WAY && cellOf(x, z) == null;
	}

	/** Whether a column is stone through every storey: the walls, and the pockets sealed into them. */
	public static boolean solid(final int x, final int z) {
		Part part = kind(x, z);
		return part == Part.WALL || part == Part.WAY && cellOf(x, z) == null;
	}

	/** The 2 x 2 corners of every held way, cell by cell, four corners to a cell. */
	public static List<Nook> nooks() {
		List<Nook> nooks = new ArrayList<>();
		List<Cell> cells = cells();
		int edge = TowerMaze.PATH - 2;
		for (Cell cell : cells) {
			boolean hub = cell.cx() == COLS / 2 && cell.cz() == COLS / 2;
			for (int iz = 0; iz < 2; iz++) {
				for (int ix = 0; ix < 2; ix++) {
					int x0 = cellX(cell.cx()) + (ix == 0 ? 0 : edge);
					int z0 = cellZ(cell.cz()) + (iz == 0 ? 0 : edge);
					int tuckX = ix == 0 ? x0 : x0 + 1;
					int tuckZ = iz == 0 ? z0 : z0 + 1;
					int dx = ix == 0 ? 1 : -1;
					int dz = iz == 0 ? 1 : -1;
					int wallX = ix == 0 ? cellX(cell.cx()) - TowerMaze.WALL : cellX(cell.cx()) + TowerMaze.PATH;
					int wallZ = iz == 0 ? cellZ(cell.cz()) - TowerMaze.WALL : cellZ(cell.cz()) + TowerMaze.PATH;
					boolean usable = true;
					for (int a = 0; a < 2; a++) {
						usable &= kind(x0 + a, z0) == Part.WAY && kind(x0 + a, z0 + 1) == Part.WAY;
						usable &= kind(wallX, z0 + a) == Part.WALL && kind(x0 + a, wallZ) == Part.WALL;
					}
					nooks.add(new Nook(cell.cx(), cell.cz(), x0, z0, tuckX, tuckZ, dx, dz, usable, hub));
				}
			}
		}
		return nooks;
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
