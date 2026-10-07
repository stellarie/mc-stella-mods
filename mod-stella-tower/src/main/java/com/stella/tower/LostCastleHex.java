package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The hexagonal lost castle: {@link TowerHex#STOREYS} storeys of paths {@link TowerMaze#PATH} blocks wide
 * between walls {@link TowerMaze#WALL} block thick, the lost room's portal on the top storey, and a breach
 * in the ground storey's south wall for the way in. {@link TowerHex} holds the shape; this class lays it,
 * then ruins it: cracked and missing floor, rubble, cobwebs, candles, skulls and chains.
 */
public final class LostCastleHex {
	/** At least this many chests stand on every storey. */
	public static final int CHESTS_PER_STOREY = 4;
	/** How many candles, skulls, chains, floor gaps, rubble blocks, cobwebs and growths every storey is given. */
	public static final int CANDLES = 4;
	public static final int SKULLS = 4;
	public static final int CHAINS = 6;
	public static final int FLOOR_GAPS = 8;
	public static final int RUBBLE = 3;
	public static final int COBWEBS = 3;
	public static final int GROWTH = 2;
	/** One wall block in this many comes down as a crack or a mossy stone. */
	public static final int DECAY = 14;
	/** The breach in the ground storey's south wall: how wide, how high, and how many blocks of it still stand. */
	public static final int BREACH = 6;
	public static final int BREACH_HEIGHT = 3;
	public static final int BREACH_RUBBLE = 2;
	/** How many doors beyond the maze's own ways are opened, so a storey is a maze and not a warren. */
	public static final int EXTRA_DOORS = 2;

	private LostCastleHex() {
	}

	/** The first block of the breach in the ground storey's south wall, and its middle. */
	public static int breachLow() {
		return TowerHex.cellAt(TowerHex.COLS / 2);
	}

	public static int breachMiddle() {
		return breachLow() + BREACH / 2;
	}

	/** Lays the castle: its storeys, their ways, the portal on the top one, and the ruin over all of it. */
	static void build(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final RandomSource seed,
		final LostCastlePalette palette) {
		BlockState wall = palette.wall().defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		RandomSource draw = RandomSource.create(seed.nextLong() ^ 0x4845584CL);
		List<TowerHex.Cell> cells = TowerHex.cells();
		for (int storey = 0; storey < TowerHex.STOREYS; storey++) {
			int feet = TowerHex.floorAt(storey) + 1;
			int ceiling = TowerHex.ceilingAt(storey);
			int floor = TowerHex.floorAt(storey);
			for (int x = 0; x < LostRoomPiece.LENGTH; x++) {
				for (int z = 0; z < LostRoomPiece.WIDTH; z++) {
					TowerHex.Part part = TowerHex.kind(x, z);
					if (part == TowerHex.Part.OUT) {
						continue;
					}
					// A lattice cell is open from the storey's floor to the block under its ceiling; all else is stone.
					boolean open = TowerHex.inCell(x, z);
					for (int y = floor; y <= ceiling; y++) {
						piece.set(level, bb, open && y >= feet && y < ceiling ? air : wall, x, y, z);
					}
				}
			}
			carve(piece, level, bb, cells, draw, feet, ceiling - 1);
			stair(piece, level, bb, cells, storey);
		}
		// Every ceiling is cut after every storey is laid, so no storey's own stone closes the hole above it.
		for (int storey = 0; storey + 1 < TowerHex.STOREYS; storey++) {
			stairFill(piece, level, bb, cells, storey);
		}
		entrance(piece, level, bb, draw, palette);
		decay(piece, level, bb, draw);
		furnish(piece, level, bb, draw);
	}

	/** Opens the ways between the storey's cells: a maze over all of them, then a few doors more. */
	private static void carve(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final List<TowerHex.Cell> cells,
		final RandomSource draw, final int feet, final int head) {
		boolean[][] seen = new boolean[TowerHex.COLS][TowerHex.COLS];
		TowerHex.Cell first = cells.get(draw.nextInt(cells.size()));
		seen[first.cx()][first.cz()] = true;
		List<int[]> trail = new ArrayList<>();
		trail.add(new int[] {first.cx(), first.cz()});
		while (!trail.isEmpty()) {
			int[] at = trail.get(trail.size() - 1);
			List<int[]> ways = new ArrayList<>();
			if (at[0] + 1 < TowerHex.COLS && holds(cells, at[0] + 1, at[1]) && !seen[at[0] + 1][at[1]]) {
				ways.add(new int[] {at[0] + 1, at[1]});
			}
			if (at[1] + 1 < TowerHex.COLS && holds(cells, at[0], at[1] + 1) && !seen[at[0]][at[1] + 1]) {
				ways.add(new int[] {at[0], at[1] + 1});
			}
			if (ways.isEmpty()) {
				trail.remove(trail.size() - 1);
				continue;
			}
			int[] to = ways.get(draw.nextInt(ways.size()));
			door(piece, level, bb, at[0], at[1], to[0], to[1], feet, head);
			seen[to[0]][to[1]] = true;
			trail.add(new int[] {to[0], to[1]});
		}
		for (int extra = 0; extra < EXTRA_DOORS; extra++) {
			int cx = draw.nextInt(TowerHex.COLS);
			int cz = draw.nextInt(TowerHex.COLS);
			boolean alongX = draw.nextBoolean();
			if (alongX && holds(cells, cx, cz) && holds(cells, cx + 1, cz)) {
				door(piece, level, bb, cx, cz, cx + 1, cz, feet, head);
			} else if (!alongX && holds(cells, cx, cz) && holds(cells, cx, cz + 1)) {
				door(piece, level, bb, cx, cz, cx, cz + 1, feet, head);
			}
		}
	}

	private static boolean holds(final List<TowerHex.Cell> cells, final int cx, final int cz) {
		for (TowerHex.Cell cell : cells) {
			if (cell.cx() == cx && cell.cz() == cz) {
				return true;
			}
		}
		return false;
	}

	/** Opens one doorway through the wall line between two neighbouring cells. */
	private static void door(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final int cx, final int cz,
		final int nx, final int nz, final int feet, final int head) {
		BlockState air = Blocks.AIR.defaultBlockState();
		int top = Math.min(feet + TowerHex.DOOR - 1, head);
		if (nx != cx) {
			int x = (nx > cx ? TowerHex.cellAt(nx) : TowerHex.cellAt(cx)) - TowerMaze.WALL;
			int z = TowerHex.cellAt(cz);
			piece.fill(level, bb, x, feet, z, x + TowerMaze.WALL - 1, top, z + TowerHex.DOOR_WIDTH - 1, air);
		} else {
			int x = TowerHex.cellAt(cx);
			int z = (nz > cz ? TowerHex.cellAt(nz) : TowerHex.cellAt(cz)) - TowerMaze.WALL;
			piece.fill(level, bb, x, feet, z, x + TowerHex.DOOR_WIDTH - 1, top, z + TowerMaze.WALL - 1, air);
		}
	}

	/**
	 * Cuts one storey's stair out of its back cell: a shelf of steps two wide climbing one block at a time
	 * from the cell's floor to its ceiling, so the storey above is reached on foot from this one.
	 */
	private static void stair(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final List<TowerHex.Cell> cells, final int storey) {
		TowerHex.Cell back = backCell(cells);
		if (back == null) {
			return;
		}
		BlockState step = Blocks.DEEPSLATE_TILES.defaultBlockState();
		int feet = TowerHex.floorAt(storey) + 1;
		int x = TowerHex.cellAt(back.cx());
		int z = TowerHex.cellAt(back.cz()) + TowerMaze.PATH - TowerHex.DOOR_WIDTH;
		// The mouth of the climb: the cell's front, open so the first step is stepped down onto.
		piece.fill(level, bb, x + 1, feet, z, x + TowerMaze.PATH - 1, feet + TowerHex.DOOR - 1, z + TowerHex.DOOR_WIDTH - 1, Blocks.AIR.defaultBlockState());
		for (int rise = 0; rise < TowerHex.STOREY; rise++) {
			piece.fill(level, bb, x + 1 + rise, feet + rise, z, x + 1 + rise, feet + rise, z + TowerHex.DOOR_WIDTH - 1, step);
		}
	}

	/**
	 * Fills one storey's stair out of its own storey: the headroom over its steps, and the hole their top
	 * rises out of, so the way climbs from this storey into the one above.
	 */
	private static void stairFill(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final List<TowerHex.Cell> cells, final int storey) {
		TowerHex.Cell back = backCell(cells);
		if (back == null) {
			return;
		}
		BlockState air = Blocks.AIR.defaultBlockState();
		int feet = TowerHex.floorAt(storey) + 1;
		int x = TowerHex.cellAt(back.cx());
		int z = TowerHex.cellAt(back.cz()) + TowerMaze.PATH - TowerHex.DOOR_WIDTH;
		for (int rise = 0; rise < TowerHex.STOREY; rise++) {
			piece.fill(level, bb, x + 1 + rise, feet + rise, z, x + 1 + rise, feet + rise + TowerHex.DOOR - 1, z + TowerHex.DOOR_WIDTH - 1, air);
		}
		// The hole the climb rises out of, and the step over the wall line at the top of it.
		piece.fill(level, bb, x, TowerHex.ceilingAt(storey), z, x + TowerHex.STOREY, TowerHex.ceilingAt(storey), z + TowerHex.DOOR_WIDTH - 1, air);
	}

	/** The storey's back cell, where its stair stands. */
	private static TowerHex.Cell backCell(final List<TowerHex.Cell> cells) {
		for (TowerHex.Cell cell : cells) {
			if (cell.cz() == TowerHex.COLS - 1) {
				return cell;
			}
		}
		return null;
	}

	/**
	 * Breaks the ground storey's south wall over a way, not over the stone buttress beside it: a wide, low
	 * breach with fallen stone at its sides and a lamp over each end of it.
	 */
	private static void entrance(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final RandomSource draw,
		final LostCastlePalette palette) {
		TowerHex.Cell front = frontCell(TowerHex.cells());
		if (front == null) {
			return;
		}
		int low = TowerHex.cellAt(front.cx());
		int high = low + TowerMaze.PATH - 1;
		int z = TowerHex.cellAt(front.cz()) - 1;
		piece.fill(level, bb, low, 1, z, high, BREACH_HEIGHT, z, Blocks.AIR.defaultBlockState());
		piece.fill(level, bb, low, TowerHex.floorAt(0), z, high, TowerHex.floorAt(0), z, palette.wall().defaultBlockState());
		// The fallen stone keeps to the breach's sides, so its middle stays a way through.
		for (int i = 0; i < BREACH_RUBBLE; i++) {
			int x = i == 0 ? low : high;
			piece.set(level, bb, draw.nextBoolean() ? Blocks.COBBLESTONE.defaultBlockState() : palette.rubble().defaultBlockState(), x, 1, z);
		}
		piece.set(level, bb, TowerBlocks.TOWER_LAMP.defaultBlockState(), low, BREACH_HEIGHT + 1, z);
		piece.set(level, bb, TowerBlocks.TOWER_LAMP.defaultBlockState(), high, BREACH_HEIGHT + 1, z);
	}

	/** The middle cell of the ground storey's front row, where the breach is cut. */
	private static TowerHex.Cell frontCell(final List<TowerHex.Cell> cells) {
		for (TowerHex.Cell cell : cells) {
			if (cell.cx() == TowerHex.COLS / 2 && cell.cz() == 0) {
				return cell;
			}
		}
		return null;
	}

	/** The decay pass: the air in the walls closed up again, and the stone gone cracked and green. */
	private static void decay(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final RandomSource draw) {
		for (int storey = 0; storey < TowerHex.STOREYS; storey++) {
			int ceiling = TowerHex.ceilingAt(storey);
			for (int x = 0; x < LostRoomPiece.LENGTH; x++) {
				for (int z = 0; z < LostRoomPiece.WIDTH; z++) {
					if (TowerHex.kind(x, z) != TowerHex.Part.WALL) {
						continue;
					}
					for (int y = TowerHex.floorAt(storey); y <= ceiling; y++) {
						if (piece.get(level, bb, x, y, z).isAir()) {
							piece.set(level, bb, Blocks.COBBLESTONE.defaultBlockState(), x, y, z);
						} else if (draw.nextInt(DECAY) == 0) {
							piece.set(level, bb, draw.nextBoolean() ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), x, y, z);
						}
					}
				}
			}
		}
	}

	/** The furniture of every storey: chests, candles, skulls, chains, lamps, gaps and the lost room's portal. */
	private static void furnish(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final RandomSource draw) {
		List<TowerHex.Cell> cells = TowerHex.cells();
		for (int storey = 0; storey < TowerHex.STOREYS; storey++) {
			int feet = TowerHex.floorAt(storey) + 1;
			int ceiling = TowerHex.ceilingAt(storey);
			for (int i = 0; i < CHESTS_PER_STOREY; i++) {
				TowerHex.Cell cell = cells.get(i % cells.size());
				piece.chest(level, bb, spread(cell, 2, draw, 0), feet, spread(cell, 2, draw, 1), Direction.Plane.HORIZONTAL.getRandomDirection(draw));
			}
			for (int i = 0; i < CANDLES; i++) {
				place(piece, level, bb, draw, cells, feet,
					candle(draw), 2);
			}
			for (int i = 0; i < SKULLS; i++) {
				place(piece, level, bb, draw, cells, feet, Blocks.SKELETON_SKULL.defaultBlockState(), 3);
			}
			for (int i = 0; i < RUBBLE; i++) {
				place(piece, level, bb, draw, cells, feet, Blocks.COBBLESTONE.defaultBlockState(), 3);
			}
			for (int i = 0; i < GROWTH; i++) {
				place(piece, level, bb, draw, cells, feet,
					draw.nextBoolean() ? Blocks.BROWN_MUSHROOM.defaultBlockState() : Blocks.MOSS_CARPET.defaultBlockState(), 3);
			}
			for (int i = 0; i < COBWEBS; i++) {
				TowerHex.Cell cell = cells.get(draw.nextInt(cells.size()));
				int x = spread(cell, 3, draw, 0);
				int z = spread(cell, 3, draw, 1);
				if (piece.get(level, bb, x, feet, z).isAir()) {
					piece.set(level, bb, Blocks.COBWEB.defaultBlockState(), x, feet, z);
				}
			}
			for (int i = 0; i < CHAINS; i++) {
				TowerHex.Cell cell = cells.get(draw.nextInt(cells.size()));
				int x = spread(cell, 3, draw, 0);
				int z = spread(cell, 3, draw, 1);
				// A chain hangs from the ceiling onto open air, so no block is left holding nothing.
				if (!piece.get(level, bb, x, ceiling - 1, z).isAir()) {
					continue;
				}
				for (int drop = 1; drop <= 1 + draw.nextInt(2); drop++) {
					piece.set(level, bb, Blocks.IRON_CHAIN.defaultBlockState(), x, ceiling - drop, z);
				}
			}
			for (int i = 0; i < FLOOR_GAPS; i++) {
				TowerHex.Cell cell = cells.get(draw.nextInt(cells.size()));
				int x = spread(cell, 3, draw, 0);
				int z = spread(cell, 3, draw, 1);
				if (!standsOn(piece, level, bb, x, feet - 1, z)) {
					continue;
				}
				if (storey > 0 && draw.nextInt(2) == 0) {
					piece.set(level, bb, Blocks.AIR.defaultBlockState(), x, feet - 1, z);
				} else {
					piece.set(level, bb, draw.nextBoolean() ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), x, feet - 1, z);
				}
			}
			for (TowerHex.Cell cell : cells) {
				piece.set(level, bb, TowerBlocks.TOWER_LAMP.defaultBlockState(), cell.middle().getX(), ceiling - 1, cell.middle().getZ());
			}
		}
		// The lost room's portal stands in the middle of the top storey's middle way, where no doorway opens.
		TowerHex.Cell portal = portalCell(cells);
		int top = TowerHex.floorAt(TowerHex.STOREYS - 1) + 1;
		piece.portal(level, bb, portal.middle().getX(), top, portal.middle().getZ() - 2);
		piece.workstations(level, bb, portal.middle().getX() - 3, top, portal.middle().getZ() - 2, 1, 0, Direction.SOUTH);
	}

	/** The top storey's middle way, where the lost room's portal stands. */
	private static TowerHex.Cell portalCell(final List<TowerHex.Cell> cells) {
		for (TowerHex.Cell cell : cells) {
			if (cell.cx() == TowerHex.COLS / 2 && cell.cz() == TowerHex.COLS / 2) {
				return cell;
			}
		}
		return cells.get(0);
	}

	/** Puts one ruin block on open floor of a drawn cell, where the floor under it holds it up. */
	private static void place(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final RandomSource draw,
		final List<TowerHex.Cell> cells, final int feet, final BlockState state, final int reach) {
		TowerHex.Cell cell = cells.get(draw.nextInt(cells.size()));
		int x = spread(cell, reach, draw, 0);
		int z = spread(cell, reach, draw, 1);
		if (piece.get(level, bb, x, feet, z).isAir() && standsOn(piece, level, bb, x, feet - 1, z)) {
			piece.set(level, bb, state, x, feet, z);
		}
	}

	/** Whether the block at a spot holds a block up: a full block, and for the ruin's own kind, any sturdy face. */
	private static boolean standsOn(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final int x, final int y, final int z) {
		BlockState under = piece.get(level, bb, x, y, z);
		return under.isFaceSturdy(level, piece.worldPos(x, y, z), Direction.UP);
	}

	private static BlockState candle(final RandomSource draw) {
		return Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true).setValue(CandleBlock.CANDLES, 1 + draw.nextInt(4));
	}

	/** A spot of a cell's middle, spread within {@code reach} blocks of it along one side. */
	private static int spread(final TowerHex.Cell cell, final int reach, final RandomSource draw, final int side) {
		int middle = side == 0 ? cell.middle().getX() : cell.middle().getZ();
		return middle + draw.nextInt(2 * reach + 1) - reach;
	}
}
