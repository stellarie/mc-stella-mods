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
	/** The chest blocks of the ground, a middle and the top storey: a double chest is two blocks, and the top storey keeps its workstation chest. */
	public static final int CHEST_BLOCKS_GROUND = 2;
	public static final int CHEST_BLOCKS_MIDDLE = 3;
	public static final int CHEST_BLOCKS_TOP = 3;
	/** No castle holds fewer double chests than this. */
	public static final int MIN_DOUBLES = 2;
	/** Every chest block is stocked with this many loot rolls. */
	public static final int CHEST_ROLLS = 15;
	private static final long DECAY_SALT = 0x4445434159L;
	private static final long MAZE_SALT = 0x4D415A45L;
	private static final long GATE_SALT = 0x47415445L;
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

	/** The chest blocks a storey holds, by its place in the castle. */
	public static int chestBlocksAt(final int storey) {
		return storey == 0 ? CHEST_BLOCKS_GROUND : storey == TowerHex.STOREYS - 1 ? CHEST_BLOCKS_TOP : CHEST_BLOCKS_MIDDLE;
	}

	/** The chest blocks a whole castle holds. */
	public static int castleChestBlocks() {
		int sum = 0;
		for (int storey = 0; storey < TowerHex.STOREYS; storey++) {
			sum += chestBlocksAt(storey);
		}
		return sum;
	}

	/** A number from a seed and a spot alone, so no chunk of a castle can draw it differently from another. */
	static int hash(final long seed, final int x, final int y, final int z) {
		long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		h ^= h >>> 33;
		return (int) (h >>> 32);
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

	/**
	 * Lays the castle: its storeys, their ways, the portal on the top one, and the ruin over all of it. Every
	 * choice comes from the seed and the plan, and none from a block already laid, so a castle built one
	 * chunk at a time is the castle built whole.
	 */
	static void build(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final long ruinSeed,
		final LostCastlePalette palette, final LostCastlePlan plan) {
		BlockState wall = palette.wall().defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		List<TowerHex.Cell> cells = TowerHex.cells();
		for (int storey = 0; storey < TowerHex.STOREYS; storey++) {
			int feet = TowerHex.floorAt(storey) + 1;
			int ceiling = TowerHex.ceilingAt(storey);
			int floor = TowerHex.floorAt(storey);
			for (int x = 0; x < LostRoomPiece.LENGTH; x++) {
				for (int z = 0; z < LostRoomPiece.WIDTH; z++) {
					if (TowerHex.kind(x, z) == TowerHex.Part.OUT) {
						continue;
					}
					// A way is a cell the hexagon holds: the cut fragments of the corner cells are sealed.
					boolean open = TowerHex.cellOf(x, z) != null;
					for (int y = floor; y <= ceiling; y++) {
						piece.set(level, bb, open && y >= feet && y < ceiling ? air : wall, x, y, z);
					}
				}
			}
			// The stone decays before this storey's ways are cut, so no door and no stair is closed again.
			decay(piece, level, bb, ruinSeed, storey);
			carve(piece, level, bb, cells, RandomSource.create(ruinSeed ^ MAZE_SALT ^ (storey * 0x9E3779B1L)), feet, ceiling - 1);
			if (storey + 1 < TowerHex.STOREYS) {
				stair(piece, level, bb, cells, storey);
			}
		}
		// Every ceiling is cut after every storey is laid, so no storey's own stone closes the hole above it.
		for (int storey = 0; storey + 1 < TowerHex.STOREYS; storey++) {
			stairFill(piece, level, bb, cells, storey);
		}
		entrance(piece, level, bb, RandomSource.create(ruinSeed ^ GATE_SALT), palette);
		dress(piece, level, bb, plan);
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
			int z = TowerHex.cellZ(cz) + TowerMaze.PATH / 2 - 1;
			piece.fill(level, bb, x, feet, z, x + TowerMaze.WALL - 1, top, z + TowerHex.DOOR_WIDTH - 1, air);
		} else {
			int x = TowerHex.cellX(cx) + TowerMaze.PATH / 2 - 1;
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

	/** One storey's decay pass: its stone gone cracked and green, each block by its own spot and the seed. */
	private static void decay(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final long ruinSeed, final int storey) {
		int ceiling = TowerHex.ceilingAt(storey);
		for (int x = 0; x < LostRoomPiece.LENGTH; x++) {
			for (int z = 0; z < LostRoomPiece.WIDTH; z++) {
				if (!TowerHex.solid(x, z)) {
					continue;
				}
				for (int y = TowerHex.floorAt(storey); y <= ceiling; y++) {
					int h = hash(ruinSeed ^ DECAY_SALT, x, y, z);
					if (Math.floorMod(h, DECAY) == 0) {
						piece.set(level, bb, (h >>> 8 & 1) == 0 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), x, y, z);
					}
				}
			}
		}
	}

	/** The furniture of every storey as the plan lays it: chests, ruin and lamps, then the lost room's portal. */
	private static void dress(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final LostCastlePlan plan) {
		List<TowerHex.Cell> cells = TowerHex.cells();
		for (LostCastlePlan.FloorPlan floor : plan.floors()) {
			int feet = TowerHex.floorAt(floor.storey()) + 1;
			int ceiling = TowerHex.ceilingAt(floor.storey());
			for (LostCastlePlan.Chest chest : floor.chests()) {
				piece.chestSite(level, bb, feet, chest);
			}
			for (LostCastlePlan.Scatter item : floor.scatter()) {
				switch (item.kind()) {
					case CANDLE -> piece.set(level, bb, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true).setValue(CandleBlock.CANDLES, item.arg()), item.x(), feet, item.z());
					case SKULL -> piece.set(level, bb, Blocks.SKELETON_SKULL.defaultBlockState(), item.x(), feet, item.z());
					case RUBBLE -> piece.set(level, bb, Blocks.COBBLESTONE.defaultBlockState(), item.x(), feet, item.z());
					case GROWTH -> piece.set(level, bb, item.arg() == 0 ? Blocks.BROWN_MUSHROOM.defaultBlockState() : Blocks.MOSS_CARPET.defaultBlockState(), item.x(), feet, item.z());
					case COBWEB -> piece.set(level, bb, Blocks.COBWEB.defaultBlockState(), item.x(), feet, item.z());
					case CHAIN -> {
						for (int drop = 1; drop <= item.arg(); drop++) {
							piece.set(level, bb, Blocks.IRON_CHAIN.defaultBlockState(), item.x(), ceiling - drop, item.z());
						}
					}
					case GAP -> piece.set(level, bb, switch (item.arg()) {
						case 0 -> Blocks.AIR.defaultBlockState();
						case 1 -> Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
						default -> Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
					}, item.x(), feet - 1, item.z());
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

	/** Whether a spot lies in the corner of the back cell a stair climbs through, which the ruin keeps off. */
	static boolean inStairLane(final int x, final int z) {
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

	/** Whether a spot is one the portal or its workstations take, so no chest is counted there and lost. */
	static boolean inPortalHall(final int x, final int y, final int z) {
		BlockPos spot = portalSpot();
		return y == spot.getY() && Math.abs(x - spot.getX()) <= PORTAL_STEP
			&& z >= spot.getZ() - 1 && z <= spot.getZ() + PORTAL_STEP + 1;
	}
}
