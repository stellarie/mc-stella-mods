package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The five shapes a lost castle takes, chosen by its seed. Each keeps the gate at the front middle, the
 * workstations in the portal's own room, four loot chests or more, a yard for the roaming mobs, and a walk
 * from the gate to the portal and back.
 * <p>
 * Spots are in the piece's own frame (see {@link LostRoomPiece}). A layout holds what other code needs
 * before the castle is built: its portal, its yard, and its lowest layer. Everything else a builder draws
 * from the ruin random, after the layout's own draws, so a chunk always draws the same castle.
 */
public enum LostCastleVariant {
	/** The ruined castle: a curtain wall, towers, a courtyard, and two halls. */
	CASTLE,
	/** A keep of two or three floors joined by stairs, with broken balconies; the portal on the top floor. */
	KEEP,
	/** A maze of four-wide ways inside the walls; the portal in the room at its middle. */
	MAZE,
	/** A ruin with a water moat and dry pits crossed by bridges; the portal in a room on a ringed island. */
	FLOODED,
	/** A sunken crypt: a mausoleum over stairs down to two vaulted halls; the portal in the lowest. */
	CRYPT;

	public static final int STOREY = 5;
	public static final int MAZE_CELLS = 5;
	public static final int MAZE_PITCH = 5;
	public static final int MAZE_X = 3;
	public static final int CRYPT_HALL = -5;
	public static final int CRYPT_LOW = -11;
	private static final long LAYOUT_SALT = 0x4C41594FL;

	/** The castle of the furnace: blackstone, burnt through. */
	public static final LostCastlePalette INFERNO = new LostCastlePalette(TrapTheme.INFERNO, Blocks.POLISHED_BLACKSTONE, Blocks.BASALT, Blocks.MAGMA_BLOCK);
	/** The castle of the frost: deepslate under a crown of ice. */
	public static final LostCastlePalette FROST = new LostCastlePalette(TrapTheme.FROST, Blocks.DEEPSLATE_BRICKS, Blocks.COBBLED_DEEPSLATE, Blocks.BLUE_ICE);
	/** The castle of the void: end stone, where nothing grows. */
	public static final LostCastlePalette VOID = new LostCastlePalette(TrapTheme.VOID, Blocks.END_STONE_BRICKS, Blocks.END_STONE, Blocks.PURPUR_BLOCK);
	/** The castle of the shadow: blackstone the dark has taken back. */
	public static final LostCastlePalette SHADOW = new LostCastlePalette(TrapTheme.SHADOW, Blocks.BLACKSTONE, Blocks.DEEPSLATE, Blocks.SCULK);
	/** The castle of the bog: mud brick, wet through. */
	public static final LostCastlePalette BOG = new LostCastlePalette(TrapTheme.BOG, Blocks.MUD_BRICKS, Blocks.PACKED_MUD, Blocks.MOSS_BLOCK);

	/** The surface this variant is built from. */
	public LostCastlePalette palette() {
		return switch (this) {
			case CASTLE -> INFERNO;
			case KEEP -> FROST;
			case MAZE -> VOID;
			case FLOODED -> SHADOW;
			case CRYPT -> BOG;
		};
	}

	/**
	 * What is fixed before building: the portal's lower block, the yard (x and z, low and high), the lowest
	 * layer from the floor, and the variant's own numbers.
	 */
	public record Layout(int[] portal, int[] yard, int lowest, int[] numbers) {
	}

	/** The variant a castle of this seed takes. */
	public static LostCastleVariant of(final long seed) {
		long mixed = seed * 0x9E3779B97F4A7C15L;
		mixed ^= mixed >>> 29;
		return values()[(int) Math.floorMod(mixed, (long) values().length)];
	}

	public Layout layout(final long seed) {
		RandomSource random = RandomSource.create(seed ^ LAYOUT_SALT);
		return switch (this) {
			case CASTLE -> new Layout(new int[] {LostRoomPiece.PORTAL_X, 1, LostRoomPiece.PORTAL_Z}, LostRoomPiece.YARD, 0, new int[0]);
			case KEEP -> {
				int width = 15 + 2 * random.nextInt(3);
				int depth = 11 + random.nextInt(3);
				int floors = 2 + random.nextInt(2);
				int x0 = (LostRoomPiece.LENGTH - width) / 2;
				int z0 = LostRoomPiece.WIDTH - 1 - depth;
				int top = 1 + STOREY * (floors - 1);
				yield new Layout(new int[] {x0 + width / 2, top, z0 + depth - 2}, new int[] {6, 2, 24, z0 - 2}, 0,
					new int[] {x0, z0, width, depth, floors});
			}
			case MAZE -> new Layout(new int[] {MAZE_X + 2 * MAZE_PITCH + 2, 1, 4 * MAZE_PITCH - 1}, new int[] {4, 1, 27, 24}, 0,
				new int[] {random.nextInt(Integer.MAX_VALUE)});
			case FLOODED -> {
				int width = 7 + 2 * random.nextInt(2);
				int depth = 7 + random.nextInt(3);
				int x0 = (LostRoomPiece.LENGTH - width - 2) / 2;
				int back = LostRoomPiece.WIDTH - 4;
				int z0 = back - depth - 1;
				yield new Layout(new int[] {x0 + 1 + width / 2, 1, back - 1}, new int[] {4, 8, 26, 10}, -1,
					new int[] {x0, z0, width, depth, back});
			}
			case CRYPT -> {
				int upper = 7 + random.nextInt(3);
				int lower = 7 + random.nextInt(3);
				yield new Layout(new int[] {15, CRYPT_LOW, 5}, new int[] {3, 12, 27, 24}, -LostRoomPiece.DEPTH,
					new int[] {upper, lower});
			}
		};
	}

	static void build(final LostRoomPiece piece, final LostCastleVariant variant, final Layout layout, final WorldGenLevel level,
		final BoundingBox bb, final RandomSource ruin, final LostCastlePalette palette) {
		switch (variant) {
			case KEEP -> keep(piece, layout, level, bb, ruin, palette);
			case MAZE -> maze(piece, layout, level, bb, ruin, palette);
			case FLOODED -> flooded(piece, layout, level, bb, ruin, palette);
			case CRYPT -> crypt(piece, layout, level, bb, ruin, palette);
			default -> {
			}
		}
	}

	private static BlockState wall(final LostCastlePalette palette) {
		return palette.wall().defaultBlockState();
	}

	private static BlockState air() {
		return Blocks.AIR.defaultBlockState();
	}

	/** The outer ward every surface variant shares: curtain, gate, three towers and a fallen fourth. */
	private static void ward(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final RandomSource ruin) {
		piece.curtain(level, bb, ruin);
		piece.gate(level, bb);
		piece.tower(level, bb, ruin, 0, 0, Direction.SOUTH);
		piece.tower(level, bb, ruin, LostRoomPiece.LENGTH - LostRoomPiece.TOWER, 0, Direction.SOUTH);
		piece.tower(level, bb, ruin, LostRoomPiece.LENGTH - LostRoomPiece.TOWER, LostRoomPiece.WIDTH - LostRoomPiece.TOWER, Direction.WEST);
		piece.fallenTower(level, bb, ruin);
	}

	private static void cobwebs(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final RandomSource ruin,
		final int x0, final int x1, final int y, final int z0, final int z1, final int count) {
		for (int i = 0; i < count; i++) {
			int x = x0 + ruin.nextInt(x1 - x0 + 1);
			int z = z0 + ruin.nextInt(z1 - z0 + 1);
			if (piece.get(level, bb, x, y, z).isAir()) {
				piece.set(level, bb, Blocks.COBWEB.defaultBlockState(), x, y, z);
			}
		}
	}


	/**
	 * The keep: floors {@value #STOREY} apart, each joined to the next by a two-wide stair along a side wall,
	 * west and east by turns. Its upper floors open onto broken balconies over the courtyard.
	 */
	private static void keep(final LostRoomPiece piece, final Layout layout, final WorldGenLevel level, final BoundingBox bb, final RandomSource ruin,
		final LostCastlePalette palette) {
		ward(piece, level, bb, ruin);
		int[] n = layout.numbers();
		int x0 = n[0];
		int z0 = n[1];
		int width = n[2];
		int depth = n[3];
		int floors = n[4];
		int x1 = x0 + width - 1;
		int z1 = z0 + depth - 1;
		int roof = STOREY * floors;
		piece.fill(level, bb, x0, 1, z0, x1, roof, z1, wall(palette));
		for (int s = 0; s < floors; s++) {
			int feet = 1 + STOREY * s;
			piece.fill(level, bb, x0 + 1, feet, z0 + 1, x1 - 1, feet + STOREY - 2, z1 - 1, air());
		}
		for (int x = x0 + 1; x < x1; x++) {
			for (int z = z0 + 1; z < z1; z++) {
				if (ruin.nextInt(100) < LostRoomPiece.ROOF_GAPS) {
					piece.set(level, bb, air(), x, roof, z);
				}
			}
		}
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean ring = x == x0 || x == x1 || z == z0 || z == z1;
				if (ring && (x + z) % 2 == 0 && ruin.nextInt(4) != 0) {
					piece.set(level, bb, wall(palette), x, roof + 1, z);
				}
			}
		}
		int door = x0 + width / 2 - 1;
		piece.fill(level, bb, door, 1, z0, door + 1, 3, z0, air());
		piece.set(level, bb, TowerBlocks.TOWER_LAMP.defaultBlockState(), door, 4, z0);
		piece.set(level, bb, TowerBlocks.TOWER_LAMP.defaultBlockState(), door + 1, 4, z0);
		for (int s = 0; s + 1 < floors; s++) {
			int feet = 1 + STOREY * s;
			int sx = s % 2 == 0 ? x0 + 1 : x1 - 2;
			// Headroom for the climb: no floor above it.
			piece.fill(level, bb, sx, feet + STOREY - 1, z0 + 1, sx + 1, feet + STOREY - 1, z0 + 6, air());
			BlockState step = Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
			for (int i = 0; i < STOREY; i++) {
				piece.fill(level, bb, sx, feet + i, z0 + 2 + i, sx + 1, feet + i, z0 + 2 + i, step);
				piece.fill(level, bb, sx, feet, z0 + 2 + i, sx + 1, feet + i - 1, z0 + 2 + i, wall(palette));
			}
		}
		for (int s = 0; s < floors; s++) {
			int feet = 1 + STOREY * s;
			// Windows in the side walls.
			for (int i = 0; i < 2; i++) {
				int z = z0 + 3 + ruin.nextInt(depth - 5);
				piece.fill(level, bb, x0, feet + 1, z, x0, feet + 2, z, air());
				piece.fill(level, bb, x1, feet + 1, z, x1, feet + 2, z, air());
			}
			cobwebs(piece, level, bb, ruin, x0 + 3, x1 - 3, feet + STOREY - 2, z0 + 8, z1 - 1, 3);
			if (s > 0) {
				balcony(piece, level, bb, ruin, x0, x1, z0, feet);
			}
			if (s + 1 < floors || floors == 2) {
				piece.chest(level, bb, x1 - 3 - s, feet, z1 - 1, Direction.SOUTH);
			}
			for (int i = 0; i < 2; i++) {
				int x = x0 + 4 + ruin.nextInt(width - 8);
				int z = z0 + 8 + ruin.nextInt(Math.max(1, depth - 10));
				// The frame leaves no room to climb rubble.
				if (Math.abs(x - layout.portal()[0]) > 2) {
					piece.placeRubble(level, bb, ruin, x, feet, z);
				}
			}
		}
		int top = 1 + STOREY * (floors - 1);
		int[] portal = layout.portal();
		piece.portal(level, bb, portal[0], top, portal[2]);
		piece.workstations(level, bb, x0 + 1, top, z1 - 1, 1, 0, Direction.SOUTH);
		piece.set(level, bb, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), x1 - 2, top, z1 - 1);
		piece.vines(level, bb, ruin);
		for (int i = 0; i < 4; i++) {
			piece.rubble(level, bb, ruin, 6 + ruin.nextInt(LostRoomPiece.LENGTH - 12), 2 + ruin.nextInt(Math.max(1, z0 - 4)), 3);
		}
		piece.crown(level, bb, palette);
	}

	/** A balcony out over the courtyard from a doorway in the keep's front, its floor and its rail broken. */
	private static void balcony(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final RandomSource ruin,
		final int x0, final int x1, final int z0, final int feet) {
		int x = x0 + 4 + ruin.nextInt(x1 - x0 - 8);
		piece.fill(level, bb, x, feet, z0, x + 1, feet + 2, z0, air());
		BlockState slab = Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
		for (int bx = x - 1; bx <= x + 2; bx++) {
			for (int dz = 1; dz <= 2; dz++) {
				// The outer corners have fallen.
				boolean fallen = dz == 2 && (bx == x - 1 || bx == x + 2) && ruin.nextBoolean();
				if (!fallen) {
					piece.set(level, bb, slab, bx, feet - 1, z0 - dz);
				}
			}
			if (ruin.nextInt(3) != 0) {
				piece.set(level, bb, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState(), bx, feet, z0 - 2);
			}
		}
	}


	/**
	 * The maze ward: a {@value #MAZE_CELLS} by {@value #MAZE_CELLS} maze of four-wide ways, drawn as a tree from
	 * the gate's cell, inside a whole curtain. The middle two cells, joined, hold the portal and workstations.
	 */
	private static void maze(final LostRoomPiece piece, final Layout layout, final WorldGenLevel level, final BoundingBox bb, final RandomSource ruin,
		final LostCastlePalette palette) {
		piece.curtain(level, bb, ruin);
		BlockState stone = palette.wall().defaultBlockState();
		int x1 = MAZE_X + MAZE_CELLS * MAZE_PITCH;
		int z1 = MAZE_CELLS * MAZE_PITCH;
		// No towers: the corners stand solid.
		for (int x = 0; x < LostRoomPiece.LENGTH; x++) {
			for (int z = 0; z < LostRoomPiece.WIDTH; z++) {
				boolean corner = (x < LostRoomPiece.TOWER || x >= LostRoomPiece.LENGTH - LostRoomPiece.TOWER)
					&& (z < LostRoomPiece.TOWER || z >= LostRoomPiece.WIDTH - LostRoomPiece.TOWER);
				boolean maze = x > MAZE_X && x < x1 && z >= 1 && z < z1;
				if (corner && !maze) {
					piece.fill(level, bb, x, 1, z, x, LostRoomPiece.WALL_HEIGHT, z, wall(palette));
				}
			}
		}
		piece.gate(level, bb);
		// No second way round the maze.
		piece.fill(level, bb, 1, 1, 1, MAZE_X, LostRoomPiece.WALL_HEIGHT, LostRoomPiece.WIDTH - 2, wall(palette));
		piece.fill(level, bb, x1, 1, 1, LostRoomPiece.LENGTH - 2, LostRoomPiece.WALL_HEIGHT, LostRoomPiece.WIDTH - 2, wall(palette));
		piece.fill(level, bb, MAZE_X, 1, z1, x1, LostRoomPiece.WALL_HEIGHT, LostRoomPiece.WIDTH - 2, wall(palette));
		for (int i = 0; i <= MAZE_CELLS; i++) {
			int line = MAZE_X + i * MAZE_PITCH;
			piece.fill(level, bb, line, 1, 1, line, LostRoomPiece.WALL_HEIGHT, z1, stone);
			if (i > 0) {
				piece.fill(level, bb, MAZE_X, 1, i * MAZE_PITCH, x1, LostRoomPiece.WALL_HEIGHT, i * MAZE_PITCH, stone);
			}
		}
		boolean[][] open = new boolean[MAZE_CELLS * MAZE_CELLS][4];
		RandomSource draw = RandomSource.create(layout.numbers()[0]);
		int gate = cell(2, 0);
		boolean[] seen = new boolean[MAZE_CELLS * MAZE_CELLS];
		List<Integer> stack = new ArrayList<>();
		stack.add(gate);
		seen[gate] = true;
		while (!stack.isEmpty()) {
			int here = stack.get(stack.size() - 1);
			List<Integer> ways = new ArrayList<>();
			for (int d = 0; d < 4; d++) {
				int next = step(here, d);
				if (next >= 0 && !seen[next] && !blocked(here, d)) {
					ways.add(d);
				}
			}
			if (ways.isEmpty()) {
				stack.remove(stack.size() - 1);
				continue;
			}
			int d = ways.get(draw.nextInt(ways.size()));
			int next = step(here, d);
			open[here][d] = true;
			open[next][(d + 2) % 4] = true;
			seen[next] = true;
			stack.add(next);
		}
		// The middle room: its two cells joined.
		open[cell(2, 2)][1] = true;
		open[cell(2, 3)][3] = true;
		for (int c = 0; c < open.length; c++) {
			for (int d = 0; d < 4; d++) {
				if (open[c][d]) {
					carve(piece, level, bb, c, d);
				}
			}
		}
		// A few walls have fallen low enough to climb.
		for (int i = 0; i < 4; i++) {
			int line = MAZE_X + (1 + ruin.nextInt(MAZE_CELLS - 1)) * MAZE_PITCH;
			int z = 1 + ruin.nextInt(z1 - 1);
			piece.fill(level, bb, line, 2, z, line, LostRoomPiece.WALL_HEIGHT, z, air());
		}
		int[] portal = layout.portal();
		piece.portal(level, bb, portal[0], 1, portal[2]);
		int east = cellX(2) + 3;
		piece.workstations(level, bb, east, 1, cellZ(3), 0, 1, Direction.WEST);
		List<Integer> ends = new ArrayList<>();
		for (int c = 0; c < open.length; c++) {
			int ways = 0;
			for (int d = 0; d < 4; d++) {
				ways += open[c][d] ? 1 : 0;
			}
			if (ways == 1 && c != gate && c != cell(2, 2) && c != cell(2, 3)) {
				ends.add(c);
			}
		}
		for (int c = 0; c < open.length && ends.size() < 3; c++) {
			if (!ends.contains(c) && c != gate && c != cell(2, 2) && c != cell(2, 3)) {
				ends.add(c);
			}
		}
		for (int i = 0; i < ends.size(); i++) {
			int c = ends.get(i);
			int x = cellX(c % MAZE_CELLS);
			int z = cellZ(c / MAZE_CELLS);
			// A corner keeps the way three wide.
			int cx = x + (ruin.nextBoolean() ? 0 : 3);
			int cz = z + (ruin.nextBoolean() ? 0 : 3);
			if (i < 3) {
				piece.chest(level, bb, cx, 1, cz, Direction.SOUTH);
			}
			piece.set(level, bb, Blocks.COBWEB.defaultBlockState(), x + 1 + ruin.nextInt(2), 3, z + 1 + ruin.nextInt(2));
		}
		piece.vines(level, bb, ruin);
		for (int i = 0; i < 6; i++) {
			int c = ruin.nextInt(MAZE_CELLS * MAZE_CELLS);
			if (c != cell(2, 2) && c != cell(2, 3)) {
				piece.placeRubble(level, bb, ruin, cellX(c % MAZE_CELLS) + 1 + ruin.nextInt(2), 1, cellZ(c / MAZE_CELLS) + 1 + ruin.nextInt(2));
			}
		}
	}

	private static int cell(final int cx, final int cz) {
		return cz * MAZE_CELLS + cx;
	}

	/** The first inner x of a maze column, and the first inner z of a maze row. */
	static int cellX(final int cx) {
		return MAZE_X + 1 + cx * MAZE_PITCH;
	}

	static int cellZ(final int cz) {
		return 1 + cz * MAZE_PITCH;
	}

	/** The neighbour a step of direction d reaches (0 +x, 1 +z, 2 -x, 3 -z), or -1. */
	private static int step(final int c, final int d) {
		int cx = c % MAZE_CELLS + (d == 0 ? 1 : d == 2 ? -1 : 0);
		int cz = c / MAZE_CELLS + (d == 1 ? 1 : d == 3 ? -1 : 0);
		return cx < 0 || cz < 0 || cx >= MAZE_CELLS || cz >= MAZE_CELLS ? -1 : cell(cx, cz);
	}

	/** The middle room's back wall holds the portal, its east wall the workstations: neither opens. */
	private static boolean blocked(final int c, final int d) {
		int far = cell(2, 3);
		return c == far && (d == 0 || d == 1) || step(c, d) == far && ((d + 2) % 4 == 0 || (d + 2) % 4 == 1);
	}

	private static void carve(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final int c, final int d) {
		int x = cellX(c % MAZE_CELLS);
		int z = cellZ(c / MAZE_CELLS);
		switch (d) {
			case 0 -> piece.fill(level, bb, x + 4, 1, z, x + 4, LostRoomPiece.WALL_HEIGHT, z + 3, air());
			case 1 -> piece.fill(level, bb, x, 1, z + 4, x + 3, LostRoomPiece.WALL_HEIGHT, z + 4, air());
			case 2 -> piece.fill(level, bb, x - 1, 1, z, x - 1, LostRoomPiece.WALL_HEIGHT, z + 3, air());
			default -> piece.fill(level, bb, x, 1, z - 1, x + 3, LostRoomPiece.WALL_HEIGHT, z - 1, air());
		}
	}


	/**
	 * The flooded ruin: a moat across the front ward with a bridge from the gate, then an inner ward, and at its
	 * back a room on an island ringed by a dry pit two wide, reached by one bridge to its door. Dry pits lie at the
	 * sides, each with a bridge of its own.
	 */
	private static void flooded(final LostRoomPiece piece, final Layout layout, final WorldGenLevel level, final BoundingBox bb, final RandomSource ruin,
		final LostCastlePalette palette) {
		ward(piece, level, bb, ruin);
		BlockState water = Blocks.WATER.defaultBlockState();
		// The pits are dry: a bed of rubble under open air, so a bridge still crosses something.
		BlockState pitBed = palette.rubble().defaultBlockState();
		BlockState bridge = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		int moatLow = LostRoomPiece.TOWER;
		int moatHigh = moatLow + 1 + ruin.nextInt(2);
		piece.fill(level, bb, 1, -1, moatLow, LostRoomPiece.LENGTH - 2, 0, moatHigh, water);
		int gate = LostRoomPiece.GATE_X;
		piece.fill(level, bb, gate - 1, 0, moatLow, gate + 2, 0, moatHigh, bridge);
		piece.fill(level, bb, gate - 1, -1, moatLow, gate + 2, -1, moatHigh, wall(palette));
		int[] n = layout.numbers();
		int x0 = n[0];
		int z0 = n[1];
		int width = n[2];
		int depth = n[3];
		int back = n[4];
		int x1 = x0 + width + 1;
		piece.fill(level, bb, x0 - 2, -1, z0 - 2, x1 + 2, -1, back + 2, pitBed);
		piece.fill(level, bb, x0 - 2, 0, z0 - 2, x1 + 2, 0, back + 2, air());
		piece.fill(level, bb, x0, 0, z0, x1, 0, back, wall(palette));
		piece.fill(level, bb, x0, 1, z0, x1, 1 + LostRoomPiece.HALL_HEIGHT, back, wall(palette));
		piece.fill(level, bb, x0 + 1, 1, z0 + 1, x1 - 1, LostRoomPiece.HALL_HEIGHT, back - 1, air());
		for (int x = x0 + 1; x < x1; x++) {
			for (int z = z0 + 1; z < back; z++) {
				if (ruin.nextInt(100) < LostRoomPiece.ROOF_GAPS) {
					piece.set(level, bb, air(), x, LostRoomPiece.HALL_HEIGHT + 1, z);
				}
			}
		}
		int door = x0 + 1 + width / 2 - 1;
		piece.fill(level, bb, door, 1, z0, door + 1, 3, z0, air());
		piece.fill(level, bb, door, 0, z0 - 2, door + 1, 0, z0 - 1, bridge);
		piece.set(level, bb, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState(), door - 1, 1, z0 - 2);
		piece.set(level, bb, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState(), door + 2, 1, z0 - 2);
		int[] portal = layout.portal();
		piece.portal(level, bb, portal[0], 1, portal[2]);
		piece.workstations(level, bb, x0 + 1, 1, z0 + 2, 0, 1, Direction.EAST);
		piece.set(level, bb, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), x1 - 1, 1, z0 + 1);
		cobwebs(piece, level, bb, ruin, x0 + 1, x1 - 1, LostRoomPiece.HALL_HEIGHT, z0 + 1, back - 1, 3);
		int pitLow = z0;
		int pitHigh = Math.min(z0 + 5, LostRoomPiece.WIDTH - LostRoomPiece.TOWER - 2);
		int westHigh = x0 - 5;
		if (westHigh >= 3) {
			piece.fill(level, bb, 2, -1, pitLow, westHigh, -1, pitHigh, pitBed);
			piece.fill(level, bb, 2, 0, pitLow, westHigh, 0, pitHigh, air());
			int span = pitLow + 1 + ruin.nextInt(Math.max(1, pitHigh - pitLow - 1));
			piece.fill(level, bb, 2, 0, span, westHigh, 0, span, bridge);
		}
		int eastLow = x1 + 5;
		if (eastLow <= LostRoomPiece.LENGTH - 4) {
			piece.fill(level, bb, eastLow, -1, pitLow, LostRoomPiece.LENGTH - 3, -1, pitHigh, pitBed);
			piece.fill(level, bb, eastLow, 0, pitLow, LostRoomPiece.LENGTH - 3, 0, pitHigh, air());
			int span = pitLow + 1 + ruin.nextInt(Math.max(1, pitHigh - pitLow - 1));
			piece.fill(level, bb, eastLow, 0, span, LostRoomPiece.LENGTH - 3, 0, span, bridge);
		}
		int side = ruin.nextBoolean() ? 2 : LostRoomPiece.LENGTH - 3;
		piece.chest(level, bb, side, 1, moatHigh + 2, Direction.SOUTH);
		piece.vines(level, bb, ruin);
		for (int i = 0; i < 4; i++) {
			piece.rubble(level, bb, ruin, 6 + ruin.nextInt(LostRoomPiece.LENGTH - 12), moatHigh + 2 + ruin.nextInt(2), 2);
		}
	}


	/**
	 * The sunken crypt: a low, half-buried ward round a mausoleum, whose stair goes down six steps to a vaulted
	 * hall, and from its far end six more, back under it, to the lowest hall, which holds the portal. The whole
	 * crypt is cut out of a block of tower wall, so no cave or water reaches into it.
	 */
	private static void crypt(final LostRoomPiece piece, final Layout layout, final WorldGenLevel level, final BoundingBox bb, final RandomSource ruin,
		final LostCastlePalette palette) {
		// Half buried: earth against a low wall.
		for (int x = 0; x < LostRoomPiece.LENGTH; x++) {
			for (int z = 0; z < LostRoomPiece.WIDTH; z++) {
				boolean edge = x == 0 || z == 0 || x == LostRoomPiece.LENGTH - 1 || z == LostRoomPiece.WIDTH - 1;
				if (edge) {
					int top = 1 + ruin.nextInt(3);
					piece.fill(level, bb, x, 1, z, x, top, z, wall(palette));
				} else if ((x == 1 || z == 1 || x == LostRoomPiece.LENGTH - 2 || z == LostRoomPiece.WIDTH - 2) && ruin.nextInt(3) == 0) {
					piece.set(level, bb, ruin.nextBoolean() ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.MOSS_BLOCK.defaultBlockState(), x, 1, z);
				}
			}
		}
		piece.gate(level, bb);
		piece.fill(level, bb, LostRoomPiece.GATE_X, 1, 1, LostRoomPiece.GATE_X + 1, 1, 1, air());
		int upper = layout.numbers()[0];
		int lower = layout.numbers()[1];
		// The block the crypt is cut from.
		piece.fill(level, bb, 9, -LostRoomPiece.DEPTH, 3, 21, -1, LostRoomPiece.WIDTH - 2, wall(palette));
		// The mausoleum, its door facing the gate.
		piece.fill(level, bb, 11, 1, 4, 19, 5, 10, wall(palette));
		piece.fill(level, bb, 12, 1, 5, 18, 4, 9, air());
		piece.fill(level, bb, 15, 1, 4, 16, 3, 4, air());
		for (int x = 12; x <= 18; x++) {
			for (int z = 5; z <= 9; z++) {
				if (ruin.nextInt(100) < LostRoomPiece.ROOF_GAPS) {
					piece.set(level, bb, air(), x, 5, z);
				}
			}
		}
		piece.chest(level, bb, 12, 1, 5, Direction.EAST);
		piece.set(level, bb, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), 18, 1, 5);
		// The first stair: down six, toward the back.
		BlockState down = Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
		for (int i = 1; i <= 6; i++) {
			int z = 5 + i;
			int feet = 1 - i;
			piece.fill(level, bb, 15, feet - 1, z, 16, feet - 1, z, down);
			piece.fill(level, bb, 15, feet, z, 16, Math.min(0, feet + 3), z, air());
		}
		int hx0 = 15 - upper / 2;
		int hx1 = hx0 + upper - 1;
		vault(piece, level, bb, ruin, hx0, hx1, CRYPT_HALL, 12, LostRoomPiece.WIDTH - 3);
		BlockState back = Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
		int start = LostRoomPiece.WIDTH - 3;
		for (int i = 1; i <= 6; i++) {
			int z = start - i;
			int feet = CRYPT_HALL - i;
			piece.fill(level, bb, 15, feet - 1, z, 16, feet - 1, z, back);
			piece.fill(level, bb, 15, feet, z, 16, Math.min(CRYPT_HALL - 1, feet + 3), z, air());
		}
		int lx0 = 15 - lower / 2;
		int lx1 = lx0 + lower - 1;
		vault(piece, level, bb, ruin, lx0, lx1, CRYPT_LOW, 5, start - 7);
		int[] portal = layout.portal();
		piece.portal(level, bb, portal[0], portal[1], portal[2]);
		piece.workstations(level, bb, lx0, CRYPT_LOW, 7, 0, 1, Direction.EAST);
		piece.set(level, bb, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), lx1, CRYPT_LOW, 6);
		// Sarcophagi along the upper hall's walls.
		piece.chest(level, bb, hx0, CRYPT_HALL, 14 + ruin.nextInt(3), Direction.EAST);
		piece.chest(level, bb, hx1, CRYPT_HALL, 18 + ruin.nextInt(3), Direction.WEST);
		piece.set(level, bb, Blocks.SKELETON_SKULL.defaultBlockState(), hx0, CRYPT_HALL, 21);
		piece.set(level, bb, Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.LIT, true), hx1, CRYPT_HALL, 13);
		for (int i = 0; i < 6; i++) {
			int x = 2 + ruin.nextInt(LostRoomPiece.LENGTH - 4);
			int z = 12 + ruin.nextInt(LostRoomPiece.WIDTH - 14);
			piece.placeRubble(level, bb, ruin, x, 1, z);
		}
		piece.vines(level, bb, ruin);
	}

	/** A vaulted hall five high from {@code feet}, x and z inclusive: pillars down its sides, ribs in its upper corners. */
	private static void vault(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final RandomSource ruin,
		final int x0, final int x1, final int feet, final int z0, final int z1) {
		piece.fill(level, bb, x0, feet, z0, x1, feet + 4, z1, air());
		for (int z = z0; z <= z1; z++) {
			piece.set(level, bb, piece.palette().rubble().defaultBlockState(), x0, feet + 4, z);
			piece.set(level, bb, piece.palette().rubble().defaultBlockState(), x1, feet + 4, z);
			if ((z - z0) % 3 == 2 && x1 - x0 >= 6) {
				piece.fill(level, bb, x0 + 1, feet, z, x0 + 1, feet + 4, z, piece.palette().rubble().defaultBlockState());
				piece.fill(level, bb, x1 - 1, feet, z, x1 - 1, feet + 4, z, piece.palette().rubble().defaultBlockState());
			}
		}
		cobwebs(piece, level, bb, ruin, x0, x1, feet + 3, z0, z1, 3);
		piece.set(level, bb, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), x0, feet, z0 + (z1 - z0) / 2);
	}
}
