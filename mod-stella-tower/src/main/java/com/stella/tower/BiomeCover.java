package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The cover of an open biome floor: broken towers up to twenty blocks high, ruined houses, and broken runs of wall, in
 * the biome's own stone, standing on the gentle ground between the hills. A floor has {@value #LEAST} to {@value #MOST}
 * of them, spread at least {@value #APART} blocks apart and away from the arrival, the road and the stair. They use the
 * room's height, and they give the floor's mobs somewhere to stand that a player who lands cannot see.
 */
final class BiomeCover {
	static final int LEAST = 6;
	static final int MOST = 12;
	static final int TOWER_MAX = 20;
	private static final int N = TowerLayout.SIZE;
	private static final int APART = 11;
	private static final int FROM_ARRIVAL = 10;
	private static final int TOWER_MIN = 9;
	/** The kinds of cover, as {@link BiomeDraft} records them. */
	private static final int TOWER = BiomeDraft.COVER_TOWER;
	private static final int HOUSE = BiomeDraft.COVER_HOUSE;
	private static final int WALL = BiomeDraft.COVER_WALL;

	/** What a biome's ruins are built of: stone for towers and walls, timber for houses, and the dressing. */
	private record Palette(byte[] stone, byte[] timber, byte floor, byte beam, byte growth) {
	}

	private BiomeCover() {
	}

	private static Palette palette(final FloorBiome biome) {
		return switch (biome) {
			case RAINY_FOREST -> new Palette(new byte[] {BiomeBlocks.MOSSY_BRICKS, BiomeBlocks.COBBLE, BiomeBlocks.STONE_BRICKS, BiomeBlocks.MOSSY_BRICKS},
				new byte[] {BiomeBlocks.PLANKS, BiomeBlocks.MOSSY_BRICKS, BiomeBlocks.PLANKS}, BiomeBlocks.COBBLE, BiomeBlocks.OAK_LOG, BiomeBlocks.OAK_LEAVES);
			case DESTROYED_VILLAGE -> new Palette(new byte[] {BiomeBlocks.COBBLESTONE, BiomeBlocks.COBBLE, BiomeBlocks.STONE_BRICKS},
				new byte[] {BiomeBlocks.PLANKS, BiomeBlocks.COBBLESTONE, BiomeBlocks.PLANKS}, BiomeBlocks.COBBLESTONE, BiomeBlocks.SPRUCE_LOG, BiomeBlocks.NONE);
			case DESTROYED_CASTLE -> new Palette(new byte[] {BiomeBlocks.STONE_BRICKS, BiomeBlocks.MOSSY_BRICKS, BiomeBlocks.DEEPSLATE_BRICKS, BiomeBlocks.CRACKED_DEEPSLATE},
				new byte[] {BiomeBlocks.STONE_BRICKS, BiomeBlocks.DEEPSLATE_BRICKS, BiomeBlocks.MOSSY_BRICKS}, BiomeBlocks.STONE, BiomeBlocks.DEEPSLATE_BRICKS, BiomeBlocks.NONE);
			case ADVENTURERS_GRAVEYARD -> new Palette(new byte[] {BiomeBlocks.DEEPSLATE_BRICKS, BiomeBlocks.CRACKED_DEEPSLATE, BiomeBlocks.MOSSY_BRICKS},
				new byte[] {BiomeBlocks.DEEPSLATE_BRICKS, BiomeBlocks.CRACKED_DEEPSLATE, BiomeBlocks.MOSSY_BRICKS}, BiomeBlocks.STONE_BRICKS, BiomeBlocks.DEEPSLATE_BRICKS, BiomeBlocks.NONE);
			case SCORCHED_BATTLEFIELD -> new Palette(new byte[] {BiomeBlocks.BLACKSTONE, BiomeBlocks.CRACKED_DEEPSLATE, BiomeBlocks.COBBLESTONE},
				new byte[] {BiomeBlocks.SPRUCE_LOG, BiomeBlocks.BLACKSTONE, BiomeBlocks.SPRUCE_LOG}, BiomeBlocks.BLACKSTONE, BiomeBlocks.SPRUCE_LOG, BiomeBlocks.NONE);
			case JUNGLE -> new Palette(new byte[] {BiomeBlocks.MOSSY_BRICKS, BiomeBlocks.COBBLE, BiomeBlocks.STONE_BRICKS},
				new byte[] {BiomeBlocks.MOSSY_BRICKS, BiomeBlocks.COBBLE, BiomeBlocks.JUNGLE_LOG}, BiomeBlocks.MOSS, BiomeBlocks.JUNGLE_LOG, BiomeBlocks.JUNGLE_LEAVES);
			case MISTBOUND_MARSH -> new Palette(new byte[] {BiomeBlocks.COBBLE, BiomeBlocks.MOSSY_BRICKS, BiomeBlocks.COBBLESTONE},
				new byte[] {BiomeBlocks.PLANKS, BiomeBlocks.OAK_LOG, BiomeBlocks.PLANKS}, BiomeBlocks.PLANKS, BiomeBlocks.OAK_LOG, BiomeBlocks.MOSS);
			case ETERNAL_SNOWSTORM -> new Palette(new byte[] {BiomeBlocks.PACKED_ICE, BiomeBlocks.STONE_BRICKS, BiomeBlocks.SNOW_BLOCK},
				new byte[] {BiomeBlocks.WHITE_WOOL, BiomeBlocks.PACKED_ICE, BiomeBlocks.STONE_BRICKS}, BiomeBlocks.PACKED_ICE, BiomeBlocks.BLUE_ICE, BiomeBlocks.SNOW_BLOCK);
			case DESERT_STORM -> new Palette(new byte[] {BiomeBlocks.SANDSTONE, BiomeBlocks.CUT_SANDSTONE},
				new byte[] {BiomeBlocks.SANDSTONE, BiomeBlocks.CUT_SANDSTONE, BiomeBlocks.SANDSTONE}, BiomeBlocks.CUT_SANDSTONE, BiomeBlocks.CUT_SANDSTONE, BiomeBlocks.NONE);
			default -> new Palette(new byte[] {BiomeBlocks.STONE_BRICKS, BiomeBlocks.MOSSY_BRICKS},
				new byte[] {BiomeBlocks.STONE_BRICKS}, BiomeBlocks.STONE, BiomeBlocks.STONE_BRICKS, BiomeBlocks.NONE);
		};
	}

	/**
	 * Sets the floor's cover down: finds a level site for each piece, flattens it, and builds the piece. The pieces are
	 * recorded in the work's cover list. Fewer than {@value #LEAST} sited is a draw the caller throws away.
	 */
	static void place(final BiomeGen.Work w) {
		Palette p = palette(w.biome);
		int target = LEAST + w.random.nextInt(MOST - LEAST + 1);
		int[] kinds = new int[target];
		for (int i = 0; i < target; i++) {
			kinds[i] = i < 6 ? i % 3 : w.random.nextInt(3);
		}
		java.util.Arrays.sort(kinds);
		List<int[]> sites = new ArrayList<>();
		for (int kind : kinds) {
			site(w, sites, kind);
		}
		// A crowded floor falls back on walls, the least to fit.
		for (int tries = 0; sites.size() < LEAST && tries < 30; tries++) {
			site(w, sites, WALL);
		}
		w.relax(true);
		for (int[] site : sites) {
			int height = switch (site[0]) {
				case TOWER -> tower(w, p, site);
				case HOUSE -> house(w, p, site);
				default -> wall(w, p, site);
			};
			w.cover.add(new int[] {site[0], site[1], site[2], site[3], site[4], height});
		}
	}

	/** Draws a piece's footprint, finds it a site on gentle ground clear of the rest, and levels and claims it. */
	private static void site(final BiomeGen.Work w, final List<int[]> sites, final int kind) {
		Random r = w.random;
		int fw;
		int fl;
		int aux = 0;
		switch (kind) {
			case TOWER -> {
				fw = 5 + r.nextInt(3);
				fl = fw;
				// The first tower is the tall one.
				aux = sites.stream().noneMatch(s -> s[0] == TOWER) ? TOWER_MAX - r.nextInt(3) : TOWER_MIN + r.nextInt(TOWER_MAX - TOWER_MIN - 2);
			}
			case HOUSE -> {
				fw = 5 + r.nextInt(3);
				fl = 6 + r.nextInt(4);
			}
			default -> {
				int length = 8 + r.nextInt(9);
				boolean alongX = r.nextBoolean();
				fw = alongX ? length : 3;
				fl = alongX ? 3 : length;
			}
		}
		int[] spot = find(w, sites, fw, fl);
		if (spot == null) {
			return;
		}
		w.flatten(spot[0], spot[1], fw, fl, (byte) 4);
		w.claim(spot[0], spot[1], fw, fl);
		sites.add(new int[] {kind, spot[0], spot[1], fw, fl, aux});
	}

	/** A corner for a footprint where the ground is level to a block, clear of the rest and of the arrival; null if none. */
	private static int[] find(final BiomeGen.Work w, final List<int[]> sites, final int fw, final int fl) {
		int[] gentle = null;
		for (int i = 0; i < 200; i++) {
			int x0 = 3 + w.random.nextInt(N - fw - 6);
			int z0 = 3 + w.random.nextInt(N - fl - 6);
			if (!w.rectFree(x0, z0, fw, fl, 2)) {
				continue;
			}
			double cx = x0 + fw / 2.0;
			double cz = z0 + fl / 2.0;
			if (Math.hypot(cx - w.ax, cz - w.az) < FROM_ARRIVAL) {
				continue;
			}
			boolean apart = true;
			for (int[] s : sites) {
				apart &= Math.hypot(cx - (s[1] + s[3] / 2.0), cz - (s[2] + s[4] / 2.0)) >= APART;
			}
			if (!apart) {
				continue;
			}
			int low = Integer.MAX_VALUE;
			int high = Integer.MIN_VALUE;
			for (int x = x0; x < x0 + fw; x++) {
				for (int z = z0; z < z0 + fl; z++) {
					low = Math.min(low, w.h[x][z]);
					high = Math.max(high, w.h[x][z]);
				}
			}
			if (high - low <= 1) {
				return new int[] {x0, z0};
			}
			if (high - low == 2 && gentle == null) {
				gentle = new int[] {x0, z0};
			}
		}
		return gentle;
	}

	private static byte pick(final Random r, final byte[] kinds) {
		return kinds[r.nextInt(kinds.length)];
	}

	/** A column of the piece's wall: from {@code from} blocks over the ground to {@code height}, a window left at {@code hole} if it is one. */
	private static void column(final BiomeGen.Work w, final byte[] kinds, final int x, final int z, final int from, final int height, final int hole) {
		int base = w.h[x][z];
		for (int y = from; y < height; y++) {
			if (y != hole) {
				w.put(x, base + y, z, y == 0 ? kinds[0] : pick(w.random, kinds));
			}
		}
		w.used[x][z] = true;
		w.tall[x][z] = height > 1;
	}

	/** Rubble on the ground round a footprint, a block high, where nothing else stands. */
	private static void rubble(final BiomeGen.Work w, final int x0, final int z0, final int fw, final int fl, final int count) {
		int placed = 0;
		for (int i = 0; i < count * 6 && placed < count; i++) {
			int x = x0 - 1 + w.random.nextInt(fw + 2);
			int z = z0 - 1 + w.random.nextInt(fl + 2);
			boolean outside = x < x0 || z < z0 || x >= x0 + fw || z >= z0 + fl;
			if (outside && w.in(x, z, 1) && w.free(x, z) && w.get(x, w.h[x][z], z) == 0) {
				w.put(x, w.h[x][z], z, BiomeBlocks.COBBLE);
				w.used[x][z] = true;
				placed++;
			}
		}
	}

	// ------------------------------------------------------------------ a broken tower

	/**
	 * A hollow tower of stone, a ring one block thick. Its tallest corner stands {@code site[5]} blocks high and the
	 * opposite one lower; the wall falls away from them in a ragged edge. It has a door, some windows, rubble inside and out,
	 * and in a tall one the stump of a floor.
	 */
	private static int tower(final BiomeGen.Work w, final Palette p, final int[] site) {
		Random r = w.random;
		int x0 = site[1];
		int z0 = site[2];
		int s = site[3];
		int top = site[5];
		int base = w.h[x0][z0];
		int sx = r.nextBoolean() ? x0 : x0 + s - 1;
		int sz = r.nextBoolean() ? z0 : z0 + s - 1;
		int ox = sx == x0 ? x0 + s - 1 : x0;
		int oz = sz == z0 ? z0 + s - 1 : z0;
		int lowTop = Math.max(5, top - 6 - r.nextInt(4));
		int door = r.nextInt(4);
		int tallest = 0;
		for (int x = x0; x < x0 + s; x++) {
			for (int z = z0; z < z0 + s; z++) {
				boolean ring = x == x0 || x == x0 + s - 1 || z == z0 || z == z0 + s - 1;
				if (!ring) {
					w.surface[x][z] = p.floor;
					w.used[x][z] = true;
					if (r.nextInt(100) < 22) {
						w.put(x, base, z, BiomeBlocks.COBBLE);
					}
					continue;
				}
				int d1 = Math.abs(x - sx) + Math.abs(z - sz);
				int d2 = Math.abs(x - ox) + Math.abs(z - oz);
				int height = (int) Math.round(Math.max(top - d1 * 1.7, lowTop - d2 * 1.7)) + r.nextInt(3) - 1;
				height = Math.max(2, Math.min(top, height));
				boolean corner = (x == x0 || x == x0 + s - 1) && (z == z0 || z == z0 + s - 1);
				int mid = s / 2;
				boolean doorway = !corner && switch (door) {
					case 0 -> z == z0 && x == x0 + mid;
					case 1 -> z == z0 + s - 1 && x == x0 + mid;
					case 2 -> x == x0 && z == z0 + mid;
					default -> x == x0 + s - 1 && z == z0 + mid;
				};
				int hole = !corner && height >= 7 && r.nextInt(100) < 25 ? 4 : -1;
				column(w, p.stone(), x, z, doorway ? (height > 3 ? 2 : height) : 0, height, hole);
				if (doorway) {
					w.tall[x][z] = false;
				}
				if (p.growth() != BiomeBlocks.NONE && r.nextInt(100) < 30) {
					w.put(x, base + height, z, p.growth());
				}
				tallest = Math.max(tallest, height);
			}
		}
		if (top >= 12) {
			for (int x = x0 + 1; x <= x0 + s / 2; x++) {
				for (int z = z0 + 1; z < z0 + s - 1; z++) {
					w.put(x, base + 6, z, pick(r, p.stone()));
				}
			}
		}
		rubble(w, x0, z0, s, s, 8 + r.nextInt(5));
		return tallest;
	}

	// ------------------------------------------------------------------ a ruined house

	/**
	 * A house of timber on a course of stone, its walls three or four blocks high and torn in places, a doorway, windows,
	 * a chimney, and the beams of a roof over the part that still has walls to rest on.
	 */
	private static int house(final BiomeGen.Work w, final Palette p, final int[] site) {
		Random r = w.random;
		int x0 = site[1];
		int z0 = site[2];
		int fw = site[3];
		int fl = site[4];
		int base = w.h[x0][z0];
		int wallH = 3 + r.nextInt(2);
		boolean alongZ = fl >= fw;
		int door = r.nextInt(4);
		int[][] heights = new int[fw][fl];
		List<int[]> inside = new ArrayList<>();
		int tallest = 0;
		for (int x = x0; x < x0 + fw; x++) {
			for (int z = z0; z < z0 + fl; z++) {
				boolean ring = x == x0 || x == x0 + fw - 1 || z == z0 || z == z0 + fl - 1;
				if (!ring) {
					w.surface[x][z] = p.floor;
					w.used[x][z] = true;
					inside.add(new int[] {x, z});
					continue;
				}
				boolean corner = (x == x0 || x == x0 + fw - 1) && (z == z0 || z == z0 + fl - 1);
				int height = wallH;
				if (r.nextInt(100) < 35) {
					height = Math.max(corner ? 2 : 0, wallH - 1 - r.nextInt(wallH));
				}
				boolean doorway = !corner && switch (door) {
					case 0 -> z == z0 && x == x0 + fw / 2;
					case 1 -> z == z0 + fl - 1 && x == x0 + fw / 2;
					case 2 -> x == x0 && z == z0 + fl / 2;
					default -> x == x0 + fw - 1 && z == z0 + fl / 2;
				};
				int along = alongZ ? z - z0 : x - x0;
				boolean longSide = alongZ ? x == x0 || x == x0 + fw - 1 : z == z0 || z == z0 + fl - 1;
				int hole = !corner && longSide && along % 3 == 1 && height >= 3 ? 1 : -1;
				if (doorway) {
					height = Math.min(height, 1);
				}
				if (height > 0) {
					column(w, p.timber(), x, z, 0, height, hole);
				} else {
					w.used[x][z] = true;
				}
				heights[x - x0][z - z0] = height;
				tallest = Math.max(tallest, height);
			}
		}
		int span = alongZ ? fl : fw;
		for (int t = 1; t < span - 1; t += 2) {
			if (r.nextInt(100) >= 65) {
				continue;
			}
			int a = alongZ ? 0 : t;
			int b = alongZ ? t : 0;
			int cx0 = alongZ ? x0 : x0 + t;
			int cz0 = alongZ ? z0 + t : z0;
			int ex = alongZ ? fw - 1 : t;
			int ez = alongZ ? t : fl - 1;
			if (heights[a][b] < wallH || heights[ex][ez] < wallH) {
				continue;
			}
			int cells = alongZ ? fw : fl;
			for (int c = 1; c < cells - 1; c++) {
				w.put(cx0 + (alongZ ? c : 0), base + wallH, cz0 + (alongZ ? 0 : c), p.beam());
			}
			tallest = Math.max(tallest, wallH + 1);
		}
		if (r.nextBoolean()) {
			int cx = x0 + (r.nextBoolean() ? 0 : fw - 1);
			int cz = z0 + (r.nextBoolean() ? 0 : fl - 1);
			column(w, p.stone(), cx, cz, 0, wallH + 3, -1);
			tallest = Math.max(tallest, wallH + 3);
		}
		w.barrels(inside, 2);
		rubble(w, x0, z0, fw, fl, 5 + r.nextInt(4));
		return tallest;
	}

	// ------------------------------------------------------------------ a broken wall

	/**
	 * A run of wall one block thick, down the middle of its three-wide strip: high at the ends, torn through in one or two
	 * places, with rubble in the breach.
	 */
	private static int wall(final BiomeGen.Work w, final Palette p, final int[] site) {
		Random r = w.random;
		int x0 = site[1];
		int z0 = site[2];
		int fw = site[3];
		int fl = site[4];
		boolean alongX = fw > fl;
		int length = alongX ? fw : fl;
		int high = 3 + r.nextInt(3);
		int breaches = length >= 12 ? 2 : 1;
		int[] breach = new int[breaches];
		for (int i = 0; i < breaches; i++) {
			breach[i] = 2 + r.nextInt(length - 4);
		}
		int tallest = 0;
		for (int i = 0; i < length; i++) {
			int x = alongX ? x0 + i : x0 + 1;
			int z = alongX ? z0 + 1 : z0 + i;
			int near = length;
			for (int b : breach) {
				near = Math.min(near, Math.abs(i - b));
			}
			int height = high + (i % 4 == 0 ? 1 : 0) + (i == 0 || i == length - 1 ? 1 : 0);
			if (near < 3) {
				height = near == 0 ? 0 : near == 1 ? 1 : 2;
			}
			if (height > 0) {
				column(w, p.stone(), x, z, 0, height, -1);
				if (p.growth() != BiomeBlocks.NONE && r.nextInt(100) < 20) {
					w.put(x, w.h[x][z] + height, z, p.growth());
				}
				tallest = Math.max(tallest, height);
			} else {
				w.used[x][z] = true;
				w.put(x, w.h[x][z], z, BiomeBlocks.COBBLE);
			}
		}
		rubble(w, x0, z0, fw, fl, 6);
		return tallest;
	}
}
