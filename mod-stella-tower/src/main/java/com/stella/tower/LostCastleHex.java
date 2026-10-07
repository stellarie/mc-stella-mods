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
	/** At least this many chests stand on every storey: oniichan asked for six a floor. */
	public static final int CHESTS_PER_STOREY = 6;
	/** The tries a ruin block or a chest takes to find a spot that keeps a way crossable. */
	private static final int SPOT_TRIES = 12;
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
	/** How far the lost room's portal stands in front of its cell's middle, for the walker coming in. */
	private static final int PORTAL_STEP = 2;

	private LostCastleHex() {
	}

	/** The first block of the breach in the ground storey's south wall, and its middle. */
	public static int breachLow() {
		return TowerHex.cellX(TowerHex.COLS / 2);
	}

	public static int breachMiddle() {
		return breachLow() + BREACH / 2;
	}

	/** The wall the breach is cut through: one row in front of the ground storey's front cell. */
	public static int breachRow() {
		return TowerHex.cellZ(0) - 1;
	}

	/** The breach's first block that the hexagon's own wall holds, where its stone and its lamp stand. */
	public static int entranceLow() {
		return Math.max(breachLow(), TowerHex.lowAt(breachRow()));
	}

	/** The breach's last block that the hexagon's own wall holds. */
	public static int entranceHigh() {
		return Math.min(breachLow() + BREACH - 1, TowerHex.lowAt(breachRow()) + TowerHex.widthAt(breachRow()) - 1);
	}

	/** Whether a spot is the breach's own doorway and the wall over its ends, which the entrance owns. */
	public static boolean inBreach(final int x, final int y, final int z) {
		return z == breachRow() && y >= 1 && y <= BREACH_HEIGHT + 1 && x >= entranceLow() - 1 && x <= entranceHigh() + 1;
	}

	/** Whether a spot is the breach's own floor row beside the doorway, the one place fallen stone may stay. */
	public static boolean inBreachSide(final int x, final int y, final int z) {
		return inBreach(x, y, z) && y == 1 && (x == entranceLow() || x == entranceHigh());
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
					// A way is a lattice cell the hexagon holds, which is the one test TowerHex.cells() reads:
					// the lattice alone would open the two corner cells as shafts through the outer wall.
					boolean open = part == TowerHex.Part.WAY;
					for (int y = floor; y <= ceiling; y++) {
						piece.set(level, bb, open && y >= feet && y < ceiling ? air : wall, x, y, z);
					}
				}
			}
			// The stone decays before this storey's ways are cut, so no door and no stair is closed again.
			decay(piece, level, bb, draw, storey);
			carve(piece, level, bb, cells, draw, feet, ceiling - 1);
			if (storey + 1 < TowerHex.STOREYS) {
				stair(piece, level, bb, cells, storey);
			}
		}
		// Every ceiling is cut after every storey is laid, so no storey's own stone closes the hole above it.
		for (int storey = 0; storey + 1 < TowerHex.STOREYS; storey++) {
			stairFill(piece, level, bb, cells, storey);
		}
		entrance(piece, level, bb, draw, palette);
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
			// Every neighbour of the cell counts, so the maze reaches every way the hexagon holds.
			if (at[0] + 1 < TowerHex.COLS && holds(cells, at[0] + 1, at[1]) && !seen[at[0] + 1][at[1]]) {
				ways.add(new int[] {at[0] + 1, at[1]});
			}
			if (at[0] > 0 && holds(cells, at[0] - 1, at[1]) && !seen[at[0] - 1][at[1]]) {
				ways.add(new int[] {at[0] - 1, at[1]});
			}
			if (at[1] + 1 < TowerHex.COLS && holds(cells, at[0], at[1] + 1) && !seen[at[0]][at[1] + 1]) {
				ways.add(new int[] {at[0], at[1] + 1});
			}
			if (at[1] > 0 && holds(cells, at[0], at[1] - 1) && !seen[at[0]][at[1] - 1]) {
				ways.add(new int[] {at[0], at[1] - 1});
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
			int x = (nx > cx ? TowerHex.cellX(nx) : TowerHex.cellX(cx)) - TowerMaze.WALL;
			int z = TowerHex.cellZ(cz);
			piece.fill(level, bb, x, feet, z, x + TowerMaze.WALL - 1, top, z + TowerHex.DOOR_WIDTH - 1, air);
		} else {
			int x = TowerHex.cellX(cx);
			int z = (nz > cz ? TowerHex.cellZ(nz) : TowerHex.cellZ(cz)) - TowerMaze.WALL;
			piece.fill(level, bb, x, feet, z, x + TowerHex.DOOR_WIDTH - 1, top, z + TowerMaze.WALL - 1, air);
		}
	}

	/**
	 * Cuts one storey's stair out of its back cell: a shelf of steps climbing one block at a time from the
	 * cell's floor to the storey above, so the way up is walked. The storey's height fits two rows of a cell.
	 */
	private static void stair(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final List<TowerHex.Cell> cells, final int storey) {
		TowerHex.Cell back = backCell(cells);
		if (back == null) {
			return;
		}
		BlockState step = Blocks.DEEPSLATE_TILES.defaultBlockState();
		int feet = TowerHex.floorAt(storey) + 1;
		for (int rise = 0; rise < TowerHex.STOREY; rise++) {
			int[] at = stairStep(back, rise);
			piece.set(level, bb, step, at[0], feet + rise, at[1]);
		}
	}

	/** One step of a storey's stair: the first {@link TowerMaze#PATH} along the cell's back row, the rest back along the row before it. */
	public static int[] stairStep(final TowerHex.Cell back, final int rise) {
		int along = rise < TowerMaze.PATH ? rise : 2 * TowerMaze.PATH - rise - 1;
		int row = rise < TowerMaze.PATH ? 0 : 1;
		return new int[] {TowerHex.cellX(back.cx()) + along, TowerHex.cellZ(back.cz()) + TowerMaze.PATH - 1 - row};
	}

	/**
	 * Fills one storey's stair out of its own storey: the room over every step, so the climb passes this
	 * storey's ceiling and reaches the way above it.
	 */
	private static void stairFill(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final List<TowerHex.Cell> cells, final int storey) {
		TowerHex.Cell back = backCell(cells);
		if (back == null) {
			return;
		}
		BlockState air = Blocks.AIR.defaultBlockState();
		int feet = TowerHex.floorAt(storey) + 1;
		for (int rise = 0; rise < TowerHex.STOREY; rise++) {
			int[] at = stairStep(back, rise);
			piece.fill(level, bb, at[0], feet + rise + 1, at[1], at[0], feet + rise + TowerHex.DOOR - 1, at[1], air);
		}
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

	/** Whether a column is one of the steps of a storey's stair, whose floor has to hold the walker up. */
	private static boolean onStair(final List<TowerHex.Cell> cells, final int storey, final int x, final int z) {
		TowerHex.Cell back = backCell(cells);
		if (back == null || storey + 1 >= TowerHex.STOREYS) {
			return false;
		}
		for (int rise = 0; rise < TowerHex.STOREY; rise++) {
			int[] at = stairStep(back, rise);
			if (at[0] == x && at[1] == z) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Breaks the ground storey's south wall over a way, not over the stone buttress beside it: a wide, low
	 * breach with fallen stone at its sides and a lamp over each end of it.
	 */
	private static void entrance(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final RandomSource draw,
		final LostCastlePalette palette) {
		int row = breachRow();
		int low = breachLow();
		int high = low + BREACH - 1;
		// The doorway is the front cell's whole mouth, and only the columns the hexagon holds take stone.
		int insideLow = entranceLow();
		int insideHigh = entranceHigh();
		piece.fill(level, bb, low, 1, row, high, BREACH_HEIGHT, row, Blocks.AIR.defaultBlockState());
		piece.fill(level, bb, insideLow, TowerHex.floorAt(0), row, insideHigh, TowerHex.floorAt(0), row, palette.wall().defaultBlockState());
		// The fallen stone keeps to the breach's sides, so its middle stays a way through.
		for (int i = 0; i < BREACH_RUBBLE; i++) {
			int x = i == 0 ? insideLow : insideHigh;
			// The castle's own rubble stone leads, so the breach names the environment it fell in.
			BlockState stone = i == 0 || draw.nextBoolean() ? palette.rubble().defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
			piece.set(level, bb, stone, x, 1, row);
		}
		piece.set(level, bb, TowerBlocks.TOWER_LAMP.defaultBlockState(), insideLow, BREACH_HEIGHT + 1, row);
		piece.set(level, bb, TowerBlocks.TOWER_LAMP.defaultBlockState(), insideHigh, BREACH_HEIGHT + 1, row);
	}

	/** One storey's decay pass: the air in its walls closed up again, and its stone gone cracked and green. */
	private static void decay(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final RandomSource draw, final int storey) {
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

	/** The furniture of every storey: chests, candles, skulls, chains, lamps, gaps and the lost room's portal. */
	private static void furnish(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final RandomSource draw) {
		List<TowerHex.Cell> cells = TowerHex.cells();
		for (int storey = 0; storey < TowerHex.STOREYS; storey++) {
			int feet = TowerHex.floorAt(storey) + 1;
			int ceiling = TowerHex.ceilingAt(storey);
			for (int i = 0; i < CHESTS_PER_STOREY; i++) {
				chest(piece, level, bb, draw, cells, feet);
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
				if (roomForRuin(x, feet, z) && piece.get(level, bb, x, feet, z).isAir()) {
					piece.set(level, bb, Blocks.COBWEB.defaultBlockState(), x, feet, z);
				}
			}
			for (int i = 0; i < CHAINS; i++) {
				TowerHex.Cell cell = cells.get(draw.nextInt(cells.size()));
				int x = spread(cell, 3, draw, 0);
				int z = spread(cell, 3, draw, 1);
				// A chain hangs into the room a walker needs, so it keeps off the crossing, the stair and the portal.
				if (!roomForRuin(x, feet, z)) {
					continue;
				}
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
				// A gap under the crossing would take the floor out from under the walker who uses it.
				if (TowerHex.onCrossing(x, z)) {
					continue;
				}
				// A gap in the floor over a stair step would break the climb out of the storey below.
				if (storey > 0 && onStair(cells, storey - 1, x, z)) {
					continue;
				}
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
		BlockPos spot = portalSpot();
		piece.portal(level, bb, spot.getX(), spot.getY(), spot.getZ());
		piece.workstations(level, bb, spot.getX() - 2, spot.getY(), spot.getZ() - 1, 0, 1, Direction.EAST);
	}

	/**
	 * The lost room's portal: its lower block, in the middle of the top storey's middle way and one step in
	 * from that middle, so a walker enters it on the way from the cell's lower z.
	 */
	public static BlockPos portalSpot() {
		TowerHex.Cell cell = portalCell(TowerHex.cells());
		return new BlockPos(cell.middle().getX(), TowerHex.floorAt(TowerHex.STOREYS - 1) + 1, cell.middle().getZ() - PORTAL_STEP);
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

	/** Puts one ruin block on open floor of a drawn cell, where the floor under it holds it up and the way stays crossable. */
	private static void place(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final RandomSource draw,
		final List<TowerHex.Cell> cells, final int feet, final BlockState state, final int reach) {
		for (int attempt = 0; attempt < SPOT_TRIES; attempt++) {
			TowerHex.Cell cell = cells.get(draw.nextInt(cells.size()));
			int x = spread(cell, reach, draw, 0);
			int z = spread(cell, reach, draw, 1);
			// The middle pair of a cell is the crossing every walker uses, and the ruin keeps to the way.
			if (!roomForRuin(x, feet, z)) {
				continue;
			}
			if (piece.get(level, bb, x, feet, z).isAir() && standsOn(piece, level, bb, x, feet - 1, z)) {
				piece.set(level, bb, state, x, feet, z);
				return;
			}
		}
	}

	/** A spot a ruin block may stand on: inside a way, off its crossing, and clear of the stair and the portal. */
	private static boolean roomForRuin(final int x, final int feet, final int z) {
		return TowerHex.kind(x, z) == TowerHex.Part.WAY && !TowerHex.onCrossing(x, z) && !inStairLane(x, z)
			&& !inPortalHall(x, feet, z);
	}

	/** Whether a spot lies in the corner of the back cell a stair climbs through, which the ruin keeps off. */
	private static boolean inStairLane(final int x, final int z) {
		for (TowerHex.Cell cell : TowerHex.cells()) {
			if (cell.cz() != TowerHex.COLS - 1) {
				continue;
			}
			int lowX = TowerHex.cellX(cell.cx());
			int lowZ = TowerHex.cellZ(cell.cz()) + TowerMaze.PATH - TowerHex.DOOR_WIDTH - 2;
			if (x >= lowX && x < lowX + TowerMaze.PATH && z >= lowZ) {
				return true;
			}
		}
		return false;
	}

	/** Stocks one chest on a storey: a drawn spot of a way, or the first open one, so the count holds. */
	private static void chest(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final RandomSource draw,
		final List<TowerHex.Cell> cells, final int feet) {
		int first = draw.nextInt(cells.size());
		for (int cell = 0; cell < cells.size(); cell++) {
			TowerHex.Cell at = cells.get((first + cell) % cells.size());
			for (int attempt = 0; attempt < SPOT_TRIES; attempt++) {
				int x = spread(at, 2, draw, 0);
				int z = spread(at, 2, draw, 1);
				if (chestAt(piece, level, bb, draw, x, feet, z)) {
					return;
				}
			}
		}
		for (TowerHex.Cell at : cells) {
			for (int x = 0; x < LostRoomPiece.LENGTH; x++) {
				for (int z = 0; z < LostRoomPiece.WIDTH; z++) {
					if (chestAt(piece, level, bb, draw, x, feet, z)) {
						return;
					}
				}
			}
		}
	}

	/** Puts a stocked chest at a spot when the spot is open floor of a way, off its crossing and out of the hall. */
	private static boolean chestAt(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final RandomSource draw,
		final int x, final int feet, final int z) {
		if (!roomForRuin(x, feet, z) || !piece.get(level, bb, x, feet, z).isAir() || !standsOn(piece, level, bb, x, feet - 1, z)) {
			return false;
		}
		piece.chest(level, bb, x, feet, z, Direction.Plane.HORIZONTAL.getRandomDirection(draw));
		return true;
	}

	/** Whether a spot is one the portal or its workstations take, so no chest is counted there and lost. */
	private static boolean inPortalHall(final int x, final int y, final int z) {
		BlockPos spot = portalSpot();
		return y == spot.getY() && Math.abs(x - spot.getX()) <= PORTAL_STEP
			&& z >= spot.getZ() - 1 && z <= spot.getZ() + PORTAL_STEP + 1;
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
