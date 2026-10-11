package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The set pieces of the larger floors ({@link BiomeLayout}): a walled town, a canyon and a fortress's yard. Each is laid
 * on the work after the road and before the cover, so that the cover and the biome's own features grow round it. Each
 * levels the ground it stands on and blends the slope round it away, keeps its walls off the road, which becomes its gate,
 * and records its parts as marks ({@link BiomeDraft#marks()}).
 */
final class BiomeLayouts {
	/** How many blocks the ground round a levelled rectangle is blended over, back to the land. */
	private static final int SKIRT = 8;
	private static final int SITE_TRIES = 160;

	private BiomeLayouts() {
	}

	/** Lays the work's layout down; false when its parts find no room, and the floor is drawn again. */
	static boolean place(final BiomeGen.Work w) {
		BiomeCover.Palette palette = BiomeCover.palette(w.biome);
		return switch (w.layout) {
			case WALLED_TOWN -> walledTown(w, palette);
			case CANYON -> canyon(w, palette);
			case FORTRESS_YARD -> fortressYard(w, palette);
			default -> true;
		};
	}

	private static int clamp(final int value, final int low, final int high) {
		return Math.max(low, Math.min(high, value));
	}

	private static void mark(final BiomeGen.Work w, final int kind, final int x, final int z, final int width, final int length, final int height) {
		w.marks.add(new int[] {kind, x, z, width, length, height});
	}

	/**
	 * The corner of a rectangle for a set piece where the land is gentlest: clear of the stair, the arrival's pad, the
	 * guard's yard, the water and the lava, and well away from the arrival and the portal. Null when none is clear.
	 */
	private static int[] site(final BiomeGen.Work w, final int rw, final int rl) {
		int[] best = null;
		long bestCost = Long.MAX_VALUE;
		for (int i = 0; i < SITE_TRIES; i++) {
			int x0 = 4 + w.random.nextInt(Math.max(1, w.sx - rw - 7));
			int z0 = 4 + w.random.nextInt(Math.max(1, w.sz - rl - 7));
			if (x0 + rw + 3 >= w.sx || z0 + rl + 3 >= w.sz) {
				continue;
			}
			double cx = x0 + rw / 2.0;
			double cz = z0 + rl / 2.0;
			if (Math.hypot(cx - w.ax, cz - w.az) < Math.max(rw, rl) / 2.0 + 10.0) {
				continue;
			}
			int reference = w.h[(int) cx][(int) cz];
			long cost = 0;
			boolean clear = true;
			for (int x = x0 - 2; x < x0 + rw + 2 && clear; x++) {
				for (int z = z0 - 2; z < z0 + rl + 2; z++) {
					if (w.keep[x][z] >= 2 || w.water[x][z] || w.lava[x][z]) {
						clear = false;
						break;
					}
					cost += Math.abs(w.h[x][z] - reference);
				}
			}
			if (clear && cost < bestCost) {
				bestCost = cost;
				best = new int[] {x0, z0};
			}
		}
		return best;
	}

	/**
	 * Levels a rectangle to the height of the road through it, or to its median height if none runs through, keeps the road
	 * where it is, and blends the land round it back to its own height over {@value #SKIRT} blocks. Returns the level.
	 */
	private static int level(final BiomeGen.Work w, final int x0, final int z0, final int rw, final int rl) {
		int roads = 0;
		int roadSum = 0;
		List<Integer> heights = new ArrayList<>();
		for (int x = x0; x < x0 + rw; x++) {
			for (int z = z0; z < z0 + rl; z++) {
				if (w.keep[x][z] == 1) {
					roads++;
					roadSum += w.h[x][z];
				} else {
					heights.add(w.h[x][z]);
				}
			}
		}
		java.util.Collections.sort(heights);
		int level = roads > 0 ? Math.round(roadSum / (float) roads) : heights.get(heights.size() / 2);
		level = Math.max(0, level);
		for (int x = x0; x < x0 + rw; x++) {
			for (int z = z0; z < z0 + rl; z++) {
				if (w.keep[x][z] == 0) {
					w.h[x][z] = level;
					w.water[x][z] = false;
					w.keep[x][z] = 4;
				}
			}
		}
		for (int x = Math.max(1, x0 - SKIRT); x < Math.min(w.sx - 1, x0 + rw + SKIRT); x++) {
			for (int z = Math.max(1, z0 - SKIRT); z < Math.min(w.sz - 1, z0 + rl + SKIRT); z++) {
				int away = Math.max(Math.max(x0 - x, x - (x0 + rw - 1)), Math.max(z0 - z, z - (z0 + rl - 1)));
				if (away < 1 || w.keep[x][z] != 0 || w.water[x][z] || w.lava[x][z]) {
					continue;
				}
				double t = Math.min(1.0, away / (SKIRT + 1.0));
				w.h[x][z] = Math.max(0, (int) Math.round(level + (w.h[x][z] - level) * t));
			}
		}
		return level;
	}

	/** Whether a road, the stair or the pad lies within a few blocks of a column: a wall leaves a gate there. */
	private static boolean nearRoad(final BiomeGen.Work w, final int x, final int z, final int reach) {
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				int a = x + dx;
				int b = z + dz;
				if (w.in(a, b, 0) && w.keep[a][b] != 0 && w.keep[a][b] != 4) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * A wall round the outline of a rectangle, {@code thick} deep, its top ragged. It leaves a gate wherever the road
	 * crosses it and in every gap given as {x, z, width, length, height}; a gap of height 0 is open to the ground, any
	 * other is rubble that high.
	 */
	private static void ring(final BiomeGen.Work w, final int x0, final int z0, final int rw, final int rl, final int thick, final int height,
		final byte[] stone, final double ragged, final List<int[]> gaps) {
		for (int x = x0; x < x0 + rw; x++) {
			for (int z = z0; z < z0 + rl; z++) {
				int depth = Math.min(Math.min(x - x0, x0 + rw - 1 - x), Math.min(z - z0, z0 + rl - 1 - z));
				if (depth >= thick || !w.in(x, z, 1) || w.water[x][z] || w.lava[x][z]) {
					continue;
				}
				if (nearRoad(w, x, z, 2)) {
					continue;
				}
				int top = height - (w.random.nextDouble() < ragged ? 1 + w.random.nextInt(3) : 0);
				boolean open = false;
				for (int[] gap : gaps) {
					if (x >= gap[0] && x < gap[0] + gap[2] && z >= gap[1] && z < gap[1] + gap[3]) {
						top = Math.min(top, gap[4]);
						open = gap[4] == 0;
					}
				}
				if (!open) {
					w.stack(x, z, stone[w.random.nextInt(stone.length)], Math.max(1, top));
				}
			}
		}
	}

	/**
	 * Where a corner tower stands: the corner of the wall, or the nearest spot along the walls clear of the road, the stair and
	 * the pad. A tower the road crosses stands on as an arch the road runs under, for the road's clearing takes the four blocks
	 * over its ground only; so the corner itself is the last choice. A corner is {x, z, step in x, step in z}.
	 */
	private static int[] towerSpot(final BiomeGen.Work w, final int[] corner, final int size) {
		for (int shift = 0; shift <= 12; shift += 2) {
			int[][] tries = {{0, 0}, {shift * corner[2], 0}, {0, shift * corner[3]}, {shift * corner[2], shift * corner[3]}};
			for (int[] move : tries) {
				if (clearOfRoad(w, corner[0] + move[0], corner[1] + move[1], size, size)) {
					return new int[] {corner[0] + move[0], corner[1] + move[1]};
				}
			}
		}
		return new int[] {corner[0], corner[1]};
	}

	/** Whether a rectangle and a block round it hold no road, stair or pad. */
	private static boolean clearOfRoad(final BiomeGen.Work w, final int x0, final int z0, final int width, final int length) {
		for (int x = x0 - 1; x <= x0 + width; x++) {
			for (int z = z0 - 1; z <= z0 + length; z++) {
				if (!w.in(x, z, 1) || w.keep[x][z] != 0 && w.keep[x][z] != 4) {
					return false;
				}
			}
		}
		return true;
	}

	/** A gap the width of a gate, across the ring's depth, centred on a point of one side. */
	private static int[] gate(final int x0, final int z0, final int rw, final int rl, final int side, final int along, final int width, final int thick) {
		return switch (side) {
			case 0 -> new int[] {x0 + along - width / 2, z0, width, thick, 0};
			case 1 -> new int[] {x0 + along - width / 2, z0 + rl - thick, width, thick, 0};
			case 2 -> new int[] {x0, z0 + along - width / 2, thick, width, 0};
			default -> new int[] {x0 + rw - thick, z0 + along - width / 2, thick, width, 0};
		};
	}

	// ------------------------------------------------------------------ a walled town

	private static boolean walledTown(final BiomeGen.Work w, final BiomeCover.Palette p) {
		Random r = w.random;
		int rw = clamp(34 + r.nextInt(15), 30, w.sx - 20);
		int rl = clamp(30 + r.nextInt(15), 28, w.sz - 20);
		int[] at = site(w, rw, rl);
		if (at == null) {
			return false;
		}
		int x0 = at[0];
		int z0 = at[1];
		level(w, x0, z0, rw, rl);
		int wallH = 6 + r.nextInt(3);
		int midX = x0 + rw / 2;
		int midZ = z0 + rl / 2;
		boolean[][] street = new boolean[rw][rl];
		for (int x = x0 + 3; x < x0 + rw - 3; x++) {
			for (int z = z0 + 3; z < z0 + rl - 3; z++) {
				boolean main = rw >= rl ? Math.abs(z - midZ) <= 1 : Math.abs(x - midX) <= 1;
				boolean cross = rw >= rl ? Math.abs(x - midX) <= 1 : Math.abs(z - midZ) <= 1;
				if ((main || cross) && w.keep[x][z] == 4) {
					street[x - x0][z - z0] = true;
					w.surface[x][z] = BiomeBlocks.PATH;
				}
			}
		}
		for (int x = midX - 4; x <= midX + 4; x++) {
			for (int z = midZ - 4; z <= midZ + 4; z++) {
				if (w.keep[x][z] == 4) {
					street[x - x0][z - z0] = true;
					w.surface[x][z] = p.floor();
				}
			}
		}
		w.well(new int[] {midX - 1, midZ - 1});
		int want = rw * rl / 150;
		int placed = 0;
		for (int tries = 0; placed < want && tries < want * 40; tries++) {
			int fw = 5 + r.nextInt(3);
			int fl = 5 + r.nextInt(4);
			int x = x0 + 3 + r.nextInt(Math.max(1, rw - 6 - fw));
			int z = z0 + 3 + r.nextInt(Math.max(1, rl - 6 - fl));
			if (houseFits(w, street, x0, z0, x, z, fw, fl)) {
				BiomeCover.house(w, p, new int[] {BiomeDraft.COVER_HOUSE, x, z, fw, fl, 0});
				w.claim(x, z, fw, fl);
				placed++;
			}
		}
		if (placed < Math.max(2, want / 2)) {
			return false;
		}
		List<int[]> gaps = new ArrayList<>();
		gaps.add(gate(x0, z0, rw, rl, 2, rl / 2, 5, 2));
		gaps.add(gate(x0, z0, rw, rl, 3, rl / 2, 5, 2));
		gaps.add(gate(x0, z0, rw, rl, r.nextInt(2), rw / 2, 5, 2));
		ring(w, x0, z0, rw, rl, 2, wallH, p.stone(), 0.08, gaps);
		mark(w, BiomeDraft.MARK_WALL, x0, z0, rw, rl, wallH);
		mark(w, BiomeDraft.MARK_PLAZA, midX - 4, midZ - 4, 9, 9, 0);
		int[][] corners = {{x0, z0, 1, 1}, {x0 + rw - 5, z0, -1, 1}, {x0, z0 + rl - 5, 1, -1}, {x0 + rw - 5, z0 + rl - 5, -1, -1}};
		for (int[] corner : corners) {
			int[] spot = towerSpot(w, corner, 5);
			int top = 11 + r.nextInt(5);
			BiomeCover.tower(w, p, new int[] {BiomeDraft.COVER_TOWER, spot[0], spot[1], 5, 5, top});
			w.claim(spot[0], spot[1], 5, 5);
			mark(w, BiomeDraft.MARK_TOWER, spot[0], spot[1], 5, 5, top);
		}
		return true;
	}

	/** Whether a house fits a lot: all of it and a block round it is level town ground off the streets and other houses. */
	private static boolean houseFits(final BiomeGen.Work w, final boolean[][] street, final int x0, final int z0, final int x, final int z, final int fw, final int fl) {
		for (int a = x - 1; a < x + fw + 1; a++) {
			for (int b = z - 1; b < z + fl + 1; b++) {
				if (!w.in(a, b, 1) || w.keep[a][b] != 4 || w.used[a][b] || w.water[a][b] || street[a - x0][b - z0]) {
					return false;
				}
			}
		}
		return true;
	}

	// ------------------------------------------------------------------ a canyon

	private static boolean canyon(final BiomeGen.Work w, final BiomeCover.Palette p) {
		Random r = w.random;
		int gorges = w.along(1);
		int cliffs = 0;
		List<Integer> used = new ArrayList<>();
		for (int g = 0; g < gorges; g++) {
			int n = w.spine.size();
			int index = n / 4 + r.nextInt(Math.max(1, n / 2));
			boolean near = false;
			for (int other : used) {
				near |= Math.abs(other - index) < 40;
			}
			if (near) {
				continue;
			}
			used.add(index);
			int[] at = w.spine.get(index);
			int[] before = w.spine.get(Math.max(0, index - 6));
			int[] after = w.spine.get(Math.min(n - 1, index + 6));
			boolean alongX = Math.abs(after[0] - before[0]) >= Math.abs(after[1] - before[1]);
			int length = 30 + r.nextInt(21);
			cliffs += cliffPair(w, p, at[0], at[1], alongX, length);
		}
		if (cliffs < 2) {
			return false;
		}
		int buttes = 0;
		for (int[] c : w.poisson(w.n(5), 12, (x, z) -> w.free(x, z, 3))) {
			int radius = 2 + r.nextInt(2);
			int height = 5 + r.nextInt(7);
			for (int[] d : w.disc(c[0], c[1], radius)) {
				if (w.free(d[0], d[1])) {
					boolean top = Math.hypot(d[0] - c[0], d[1] - c[1]) <= radius - 1.0;
					w.stack(d[0], d[1], p.stone()[r.nextInt(p.stone().length)], height + (top ? 2 : 0));
				}
			}
			mark(w, BiomeDraft.MARK_BUTTE, c[0] - radius, c[1] - radius, 2 * radius + 1, 2 * radius + 1, height + 2);
			buttes++;
		}
		return buttes >= 3;
	}

	/**
	 * Two cliffs facing across a corridor eleven wide, each four deep and stacked eight to twelve high, broken by a gap
	 * five wide every fifteen blocks or so; returns how many strips were stood.
	 */
	private static int cliffPair(final BiomeGen.Work w, final BiomeCover.Palette p, final int cx, final int cz, final boolean alongX, final int length) {
		Random r = w.random;
		int stood = 0;
		for (int side = -1; side <= 1; side += 2) {
			int t = -length / 2;
			while (t < length / 2) {
				int run = 11 + r.nextInt(8);
				int end = Math.min(length / 2, t + run);
				int height = 8 + r.nextInt(5);
				int low = side < 0 ? -9 : 6;
				int x0 = alongX ? cx + t : cx + low;
				int z0 = alongX ? cz + low : cz + t;
				int rw = alongX ? end - t : 4;
				int rl = alongX ? 4 : end - t;
				int cells = 0;
				int minX = Integer.MAX_VALUE;
				int minZ = Integer.MAX_VALUE;
				int maxX = Integer.MIN_VALUE;
				int maxZ = Integer.MIN_VALUE;
				for (int x = x0; x < x0 + rw; x++) {
					for (int z = z0; z < z0 + rl; z++) {
						if (w.in(x, z, 2) && w.keep[x][z] == 0 && !w.water[x][z] && !w.lava[x][z] && !w.used[x][z] && !w.tall[x][z]) {
							int top = height - (r.nextDouble() < 0.12 ? 1 + r.nextInt(2) : 0);
							w.stack(x, z, p.stone()[r.nextInt(p.stone().length)], top);
							cells++;
							minX = Math.min(minX, x);
							minZ = Math.min(minZ, z);
							maxX = Math.max(maxX, x);
							maxZ = Math.max(maxZ, z);
						}
					}
				}
				// A strip with most of its box left unbuilt (other parts stand there) is no cliff to record.
				if (cells >= 12 && cells * 100 >= (maxX - minX + 1) * (maxZ - minZ + 1) * 45) {
					mark(w, BiomeDraft.MARK_CLIFF, minX, minZ, maxX - minX + 1, maxZ - minZ + 1, height);
					stood++;
				}
				t = end + 5;
			}
		}
		return stood;
	}

	// ------------------------------------------------------------------ a fortress's yard

	private static boolean fortressYard(final BiomeGen.Work w, final BiomeCover.Palette p) {
		Random r = w.random;
		int rw = clamp(40 + r.nextInt(9), 36, w.sx - 20);
		int rl = clamp(34 + r.nextInt(9), 32, w.sz - 20);
		int[] at = site(w, rw, rl);
		if (at == null) {
			return false;
		}
		int x0 = at[0];
		int z0 = at[1];
		level(w, x0, z0, rw, rl);
		int thick = 3;
		int wallH = 9 + r.nextInt(3);
		for (int x = x0 + thick; x < x0 + rw - thick; x++) {
			for (int z = z0 + thick; z < z0 + rl - thick; z++) {
				if (w.keep[x][z] == 4) {
					w.surface[x][z] = p.floor();
				}
			}
		}
		List<int[]> gaps = new ArrayList<>();
		int gateSide = r.nextBoolean() ? 2 : 3;
		int gateAt = rl / 2;
		gaps.add(gate(x0, z0, rw, rl, gateSide, gateAt, 5, thick));
		gaps.add(gate(x0, z0, rw, rl, gateSide == 2 ? 3 : 2, rl / 2 + r.nextInt(7) - 3, 5, thick));
		for (int breach = 0; breach < 4; breach++) {
			int side = r.nextInt(4);
			int span = side < 2 ? rw : rl;
			int[] g = gate(x0, z0, rw, rl, side, 8 + r.nextInt(Math.max(1, span - 16)), 3 + r.nextInt(4), thick);
			g[4] = r.nextInt(3);
			gaps.add(g);
		}
		ring(w, x0, z0, rw, rl, thick, wallH, p.stone(), 0.12, gaps);
		mark(w, BiomeDraft.MARK_WALL, x0, z0, rw, rl, wallH);
		int[][] corners = {{x0, z0, 1, 1}, {x0 + rw - 7, z0, -1, 1}, {x0, z0 + rl - 7, 1, -1}, {x0 + rw - 7, z0 + rl - 7, -1, -1}};
		for (int[] corner : corners) {
			int[] spot = towerSpot(w, corner, 7);
			int top = 15 + r.nextInt(6);
			BiomeCover.tower(w, p, new int[] {BiomeDraft.COVER_TOWER, spot[0], spot[1], 7, 7, top});
			w.claim(spot[0], spot[1], 7, 7);
			mark(w, BiomeDraft.MARK_TOWER, spot[0], spot[1], 7, 7, top);
		}
		int keepX = gateSide == 2 ? x0 + rw - thick - 4 - 15 : x0 + thick + 4;
		int keepZ = z0 + (rl - 11) / 2;
		// The road's clearing would cut a keep down where the road crosses it, so the keep moves along the wall to clear ground.
		int[] keepAt = null;
		for (int shift = 0; shift <= 12 && keepAt == null; shift += 3) {
			for (int side = -1; side <= 1 && keepAt == null; side += 2) {
				int z = keepZ + side * shift;
				if (clearOfRoad(w, keepX, z, 15, 11)) {
					keepAt = new int[] {keepX, z};
				}
			}
		}
		if (keepAt != null) {
			keepZ = keepAt[1];
		}
		int keepH = 10 + r.nextInt(4);
		List<int[]> inside = w.ruin(keepX, keepZ, 15, 11, p.stone(), keepH - 3, keepH, 0.3, p.floor());
		w.barrels(inside, 3);
		mark(w, BiomeDraft.MARK_KEEP, keepX, keepZ, 15, 11, keepH);
		for (int row = 0; row < 2; row++) {
			int z = row == 0 ? z0 + thick + 1 : z0 + rl - thick - 1 - 5;
			int x = x0 + thick + 8 + r.nextInt(Math.max(1, rw - 2 * thick - 30));
			if (x + 14 < keepX || x > keepX + 15) {
				w.ruin(x, z, 14, 5, p.stone(), 3, 5, 0.35, p.floor());
				w.claim(x, z, 14, 5);
			}
		}
		w.claim(keepX, keepZ, 15, 11);
		return true;
	}
}
