package com.stella.tower;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.Direction;

/**
 * Draws a biome floor's plan: one open volume of 64 by 64 columns. Every floor follows one frame, and a biome
 * adds its own features to it.
 * <ol>
 * <li>The ends: the portal on one side, entered along x, and the arrival on the other.</li>
 * <li>The ground: the biome's heights, kept to one step between columns, with the portal's stair stamped flat.</li>
 * <li>The spine: a winding road, three blocks wide, from the arrival to the stair's foot. It clears whatever stands
 * on it, so a body can always walk it.</li>
 * <li>The features: the biome's own set pieces, each keeping off the spine, the stair and the arrival.</li>
 * <li>The finish: chest nooks, a light over every spot a body reaches, and the spine drawn clear again.</li>
 * </ol>
 * A draw is proved by a walk from the arrival to the portal over the whole volume ({@link TowerPlan#finish}); a draw
 * that fails is drawn again, up to {@value TowerPlan#ATTEMPTS} times, and then the floor is a plain yard of the same
 * biome: flat ground, the spine, the stair and the nooks, with no feature.
 * <p>
 * Water lies one block under every other column's feet level, so a stream is held in by banks at least as high as its
 * surface and never needs a wall.
 */
final class BiomeGen {
	private static final int N = TowerLayout.SIZE;
	private static final long SALT = 0x42494F4DL;
	private static final long FALLBACK_SALT = 0x504C4149L;
	/** Offsets of {@link Work#over}: the lowest block it holds, and how many it holds in a column. */
	private static final int OLOW = TowerLayout.ROOM_LOW;
	private static final int OSPAN = TowerLayout.ROOM_HEIGHT - OLOW + 1;
	/** Steps of the portal's stair on an ordinary floor, and on an elite or a boss floor. */
	private static final int STEPS = 5;
	private static final int GUARDED_STEPS = 6;
	private static final int HALL_STEPS = 8;
	/** Chest nooks drawn on every floor, how far they lie from the arrival by foot, and apart from each other. */
	private static final int NOOKS = 8;
	private static final int NOOK_WALK = 30;
	private static final int NOOK_APART = 12;
	/** How far a light reaches a spot a body walks, squared: a little under the six blocks the lamp rule asks. */
	private static final int LIGHT_REACH_SQUARED = 30;
	/** The reach the lamp rule asks of the floor's lights, in blocks. */
	static final int LIGHT_REACH = 6;

	private BiomeGen() {
	}

	/** The plan of a biome floor: drawn and proved by a walk, or the plain yard. */
	static TowerPlan plan(final long seed, final int floor) {
		FloorBiome biome = floor % TowerElites.BOSS_EVERY == 0 ? FloorBiome.GREAT_HALL : FloorBiomes.of(seed, floor);
		for (int attempt = 0; attempt < TowerPlan.ATTEMPTS; attempt++) {
			Random random = new Random(TowerPlan.mix(seed ^ SALT ^ (floor * 0x9E3779B97F4A7C15L) ^ (attempt * 0xC2B2AE3D27D4EB4FL)));
			TowerPlan drawn = draw(seed, floor, biome, attempt, random, true);
			if (drawn != null) {
				TowerPlan done = TowerPlan.finish(drawn, plan -> false);
				if (done != null) {
					return done;
				}
			}
		}
		Random random = new Random(TowerPlan.mix(seed ^ FALLBACK_SALT ^ (floor * 0x9E3779B97F4A7C15L)));
		TowerPlan fallback = draw(seed, floor, biome, TowerPlan.FALLBACK, random, false);
		if (fallback == null) {
			throw new IllegalStateException("tower floor " + floor + ": not even the plain yard could be drawn");
		}
		TowerPlan done = TowerPlan.finish(fallback, plan -> false);
		return done != null ? done : fallback.withStands(TowerVolume.of(fallback));
	}

	private static TowerPlan draw(final long seed, final int floor, final FloorBiome biome, final int attempt, final Random random, final boolean features) {
		Work w = new Work(seed, floor, biome, random);
		if (biome == FloorBiome.GREAT_HALL) {
			w.hallEnds();
		} else {
			w.ends();
		}
		w.terrain(features);
		w.prepare();
		if (features) {
			switch (biome) {
				case RAINY_FOREST -> w.rainyForest();
				case DESTROYED_VILLAGE -> w.village();
				case DESTROYED_CASTLE -> w.castle();
				case ADVENTURERS_GRAVEYARD -> w.graveyard();
				case SCORCHED_BATTLEFIELD -> w.battlefield();
				case JUNGLE -> w.jungle();
				case MISTBOUND_MARSH -> w.marsh();
				case BASALT_DELTAS -> w.basalt();
				case ETERNAL_SNOWSTORM -> w.snow();
				case DESERT_STORM -> w.desert();
				case AMETHYST_CAVE -> w.cave();
				case SUNKEN_CATACOMBS -> w.catacombs();
				case GREAT_HALL -> w.hall();
			}
		}
		w.nooks();
		w.clearSpine();
		w.props();
		TowerPlan first = w.assemble(attempt, w.draft());
		TowerVolume volume = TowerVolume.of(first);
		if (!volume.reaches(first)) {
			return null;
		}
		w.lights(first.withStands(volume), volume);
		w.trimCacti();
		return w.assemble(attempt, w.draft());
	}

	// ------------------------------------------------------------------ noise

	private static double lattice(final long seed, final int gx, final int gz, final int layer) {
		long h = TowerPlan.mix(seed ^ (gx * 0x9E3779B97F4A7C15L) ^ (gz * 0xC2B2AE3D27D4EB4FL) ^ (layer * 0x165667B19E3779F9L));
		return ((h >>> 11) % 10000L) / 10000.0;
	}

	private static double vnoise(final long seed, final int x, final int z, final double cell, final int layer) {
		int c = Math.max(1, (int) cell);
		int gx = Math.floorDiv(x, c);
		int gz = Math.floorDiv(z, c);
		double fx = Math.floorMod(x, c) / (double) c;
		double fz = Math.floorMod(z, c) / (double) c;
		fx = fx * fx * (3 - 2 * fx);
		fz = fz * fz * (3 - 2 * fz);
		double a = lattice(seed, gx, gz, layer);
		double b = lattice(seed, gx + 1, gz, layer);
		double cc = lattice(seed, gx, gz + 1, layer);
		double d = lattice(seed, gx + 1, gz + 1, layer);
		return (a * (1 - fx) + b * fx) * (1 - fz) + (cc * (1 - fx) + d * fx) * fz;
	}

	private static double fbm(final long seed, final int x, final int z, final double cell) {
		double total = 0;
		double norm = 0;
		double amp = 1;
		double c = cell;
		for (int octave = 0; octave < 3; octave++) {
			total += amp * vnoise(seed, x, z, c, octave);
			norm += amp;
			amp *= 0.5;
			c = Math.max(1, c / 2);
		}
		return total / norm;
	}

	// ------------------------------------------------------------------ the working floor

	private static final class Work {
		final long seed;
		final int floor;
		final FloorBiome biome;
		final Random random;
		final int[][] h = new int[N][N];
		final byte[][] surface = new byte[N][N];
		final boolean[][] water = new boolean[N][N];
		final boolean[][] lava = new boolean[N][N];
		final boolean[][] fragile = new boolean[N][N];
		/** The columns of rock, where the biome has rock; null otherwise. */
		boolean[][] rockMask;
		/** The first block of rock over each column, and its look; null under an open roof. */
		short[][] ceil;
		byte[][] ceilCode;
		/** 1 the spine, 2 the stair, 3 the arrival's pad, 4 a feature's flat ground. */
		final byte[][] keep = new byte[N][N];
		final boolean[][] used = new boolean[N][N];
		/** A column something tall stands in. */
		final boolean[][] tall = new boolean[N][N];
		/** The blocks above the surface, by column and height; 0 for none. */
		final byte[] over = new byte[N * OSPAN * N];
		final boolean[][] daisBlock = new boolean[N][N];
		int facing;
		int px;
		int pz;
		int ax;
		int az;
		int foot;
		final int steps;
		int boxX0;
		int boxX1;
		int boxZ0;
		int boxZ1;
		List<int[]> spine = new ArrayList<>();
		final List<int[]> nooks = new ArrayList<>();

		Work(final long seed, final int floor, final FloorBiome biome, final Random random) {
			this.seed = seed;
			this.floor = floor;
			this.biome = biome;
			this.random = random;
			this.steps = biome == FloorBiome.GREAT_HALL ? HALL_STEPS : floor % TowerElites.ELITE_EVERY == 0 ? GUARDED_STEPS : STEPS;
		}

		// ---- cells

		boolean in(final int x, final int z, final int margin) {
			return x >= margin && z >= margin && x < N - margin && z < N - margin;
		}

		boolean free(final int x, final int z) {
			return free(x, z, 0);
		}

		/** Whether the column and the margin round it are open ground that no part of the frame or a feature holds. */
		boolean free(final int x, final int z, final int margin) {
			for (int dx = -margin; dx <= margin; dx++) {
				for (int dz = -margin; dz <= margin; dz++) {
					int a = x + dx;
					int b = z + dz;
					if (!in(a, b, 1) || this.keep[a][b] != 0 || this.used[a][b]) {
						return false;
					}
				}
			}
			return !this.tall[x][z] && !this.water[x][z] && !this.lava[x][z];
		}

		private int overIndex(final int x, final int y, final int z) {
			return (x * OSPAN + (y - OLOW)) * N + z;
		}

		void put(final int x, final int y, final int z, final byte code) {
			if (in(x, z, 0) && y >= OLOW && y < TowerLayout.ROOM_HEIGHT - 1) {
				this.over[overIndex(x, y, z)] = code;
			}
		}

		byte get(final int x, final int y, final int z) {
			return in(x, z, 0) && y >= OLOW && y < TowerLayout.ROOM_HEIGHT ? this.over[overIndex(x, y, z)] : 0;
		}

		/** A column of one block, from the feet level up. */
		void stack(final int x, final int z, final byte code, final int height) {
			for (int y = this.h[x][z]; y < this.h[x][z] + height; y++) {
				put(x, y, z, code);
			}
			this.tall[x][z] = height > 1;
			this.used[x][z] = true;
		}

		void clearAbove(final int x, final int z, final int from, final int to) {
			for (int y = from; y <= to; y++) {
				if (in(x, z, 0) && y >= OLOW && y < TowerLayout.ROOM_HEIGHT) {
					this.over[overIndex(x, y, z)] = 0;
				}
			}
		}

		void flatten(final int x0, final int z0, final int w, final int l, final byte mark) {
			int low = Integer.MAX_VALUE;
			for (int x = x0; x < x0 + w; x++) {
				for (int z = z0; z < z0 + l; z++) {
					low = Math.min(low, this.h[x][z]);
				}
			}
			for (int x = x0; x < x0 + w; x++) {
				for (int z = z0; z < z0 + l; z++) {
					this.h[x][z] = Math.max(0, low);
					this.keep[x][z] = mark;
				}
			}
		}

		int[] rect(final int w, final int l, final int tries, final int margin) {
			for (int i = 0; i < tries; i++) {
				int x0 = 2 + this.random.nextInt(N - w - 4);
				int z0 = 2 + this.random.nextInt(N - l - 4);
				if (rectFree(x0, z0, w, l, margin)) {
					return new int[] {x0, z0};
				}
			}
			return null;
		}

		boolean rectFree(final int x0, final int z0, final int w, final int l, final int margin) {
			if (x0 - margin < 1 || z0 - margin < 1 || x0 + w + margin > N - 1 || z0 + l + margin > N - 1) {
				return false;
			}
			for (int x = x0 - margin; x < x0 + w + margin; x++) {
				for (int z = z0 - margin; z < z0 + l + margin; z++) {
					if (this.keep[x][z] != 0 || this.used[x][z] || this.water[x][z] || this.lava[x][z] || this.tall[x][z]) {
						return false;
					}
				}
			}
			return true;
		}

		void claim(final int x0, final int z0, final int w, final int l) {
			for (int x = x0; x < x0 + w; x++) {
				for (int z = z0; z < z0 + l; z++) {
					this.used[x][z] = true;
				}
			}
		}

		// ---- the ends and the stair

		void ends() {
			this.facing = this.random.nextBoolean() ? -1 : 1;
			if (this.facing == -1) {
				this.px = 52 + this.random.nextInt(4);
				this.ax = 3 + this.random.nextInt(6);
			} else {
				this.px = 8 + this.random.nextInt(4);
				this.ax = 55 + this.random.nextInt(6);
			}
			this.pz = 15 + this.random.nextInt(33);
			this.az = 7 + this.random.nextInt(50);
			this.foot = this.px + this.facing * (2 + this.steps);
			int a = this.px - this.facing * 3;
			// The guard's yard: level ground past the stair's foot, where an elite or a boss stands.
			int b = this.px + this.facing * (2 + this.steps + TowerElites.GUARD_DISTANCE_BIOME + 3);
			this.boxX0 = Math.min(a, b) - 1;
			this.boxX1 = Math.max(a, b) + 1;
			this.boxZ0 = this.pz - 4 - this.steps;
			this.boxZ1 = this.pz + 5 + this.steps;
		}

		void terrain(final boolean features) {
			double cell = switch (this.biome) {
				case RAINY_FOREST, MISTBOUND_MARSH -> 16;
				case JUNGLE -> 12;
				case DESTROYED_VILLAGE, ETERNAL_SNOWSTORM -> 20;
				case DESTROYED_CASTLE, SCORCHED_BATTLEFIELD -> 18;
				case ADVENTURERS_GRAVEYARD -> 17;
				case BASALT_DELTAS -> 13;
				case DESERT_STORM -> 22;
				case AMETHYST_CAVE -> 10;
				case SUNKEN_CATACOMBS, GREAT_HALL -> 16;
			};
			double scale = switch (this.biome) {
				case RAINY_FOREST -> 3.4;
				case DESTROYED_VILLAGE -> 2.4;
				case DESTROYED_CASTLE -> 2.2;
				case ADVENTURERS_GRAVEYARD -> 3.0;
				case SCORCHED_BATTLEFIELD -> 2.6;
				case JUNGLE -> 4.6;
				case MISTBOUND_MARSH -> 1.9;
				case BASALT_DELTAS -> 3.4;
				case ETERNAL_SNOWSTORM -> 4.4;
				case DESERT_STORM -> 5.4;
				case AMETHYST_CAVE -> 2.5;
				case SUNKEN_CATACOMBS, GREAT_HALL -> 0.0;
			};
			byte base = switch (this.biome) {
				case SCORCHED_BATTLEFIELD -> BiomeBlocks.BLACKSTONE;
				case MISTBOUND_MARSH -> BiomeBlocks.MUD;
				case BASALT_DELTAS -> BiomeBlocks.BASALT;
				case ETERNAL_SNOWSTORM -> BiomeBlocks.SNOW_BLOCK;
				case DESERT_STORM -> BiomeBlocks.SAND;
				case AMETHYST_CAVE -> BiomeBlocks.TUFF;
				case SUNKEN_CATACOMBS -> BiomeBlocks.STONE_BRICKS;
				case GREAT_HALL -> BiomeBlocks.DEEPSLATE_BRICKS;
				default -> BiomeBlocks.GRASS;
			};
			byte patch = switch (this.biome) {
				case DESTROYED_CASTLE -> BiomeBlocks.STONE;
				case ADVENTURERS_GRAVEYARD, DESTROYED_VILLAGE -> BiomeBlocks.COARSE_DIRT;
				case MISTBOUND_MARSH, JUNGLE -> BiomeBlocks.MOSS;
				case BASALT_DELTAS -> BiomeBlocks.BLACKSTONE;
				case ETERNAL_SNOWSTORM -> BiomeBlocks.SNOW_BLOCK;
				case DESERT_STORM -> BiomeBlocks.RED_SAND;
				case AMETHYST_CAVE -> BiomeBlocks.CALCITE;
				case SUNKEN_CATACOMBS -> BiomeBlocks.MOSSY_BRICKS;
				case GREAT_HALL -> BiomeBlocks.CRACKED_DEEPSLATE;
				default -> BiomeBlocks.MUD;
			};
			for (int x = 0; x < N; x++) {
				for (int z = 0; z < N; z++) {
					double n = switch (this.biome) {
						// Drifts stretched along z, and dunes in ridges.
						case ETERNAL_SNOWSTORM -> fbm(this.seed, x, z / 3, cell);
						case DESERT_STORM -> 1.0 - Math.abs(2.0 * fbm(this.seed, x, z * 22 / 9, cell) - 1.0);
						default -> fbm(this.seed, x, z, cell);
					};
					this.h[x][z] = features ? (int) (n * scale) : 0;
					this.surface[x][z] = vnoise(this.seed, x, z, 9, 3) > 0.3 ? base : patch;
					if (features && this.biome == FloorBiome.RAINY_FOREST && vnoise(this.seed, x, z, 7, 5) > 0.82) {
						this.surface[x][z] = BiomeBlocks.MOSS;
					}
				}
			}
		}

		/** The zone the arrival sits in on floor 1: eight blocks square, kept inside the room. */
		int zoneX() {
			return Math.max(0, Math.min(N - 8, this.ax - 4));
		}

		int zoneZ() {
			return Math.max(0, Math.min(N - 8, this.az - 4));
		}

		void prepare() {
			for (int x = Math.max(0, this.boxX0); x <= Math.min(N - 1, this.boxX1); x++) {
				for (int z = Math.max(0, this.boxZ0); z <= Math.min(N - 1, this.boxZ1); z++) {
					this.h[x][z] = 0;
				}
			}
			int padX = this.floor == 1 ? zoneX() : this.ax - 2;
			int padZ = this.floor == 1 ? zoneZ() : this.az - 2;
			int pad = this.floor == 1 ? 8 : 5;
			for (int x = padX; x < padX + pad; x++) {
				for (int z = padZ; z < padZ + pad; z++) {
					this.h[x][z] = 0;
				}
			}
			relax(false);
			for (int x = Math.max(0, this.boxX0); x <= Math.min(N - 1, this.boxX1); x++) {
				for (int z = Math.max(0, this.boxZ0); z <= Math.min(N - 1, this.boxZ1); z++) {
					this.keep[x][z] = 4;
				}
			}
			markDais();
			route();
			stampSpine();
			for (int x = padX; x < padX + pad; x++) {
				for (int z = padZ; z < padZ + pad; z++) {
					this.h[x][z] = 0;
					this.keep[x][z] = 3;
					this.water[x][z] = false;
					clearAbove(x, z, 0, TowerLayout.ROOM_HEIGHT);
				}
			}
		}

		/** The stair, the platform and the backdrop keep their ground flat at the base; only they are set aside. */
		void markDais() {
			int f = this.facing;
			int s = this.steps;
			for (int dx = -2; dx <= 2; dx++) {
				for (int z = this.pz - 3; z <= this.pz + 4; z++) {
					dais(this.px + dx, z, true);
				}
			}
			for (int k = 1; k < s; k++) {
				for (int z = this.pz - 3 - k; z <= this.pz + 4 + k; z++) {
					dais(this.px + f * (2 + k), z, true);
				}
			}
			for (int z = this.pz - 3 - s; z <= this.pz + 4 + s; z++) {
				dais(this.px + f * (2 + s), z, false);
			}
			int from = f == -1 ? this.px + 3 : 0;
			int to = f == -1 ? N - 1 : this.px - 3;
			for (int z = this.pz - 4; z <= this.pz + 5; z++) {
				for (int x = from; x <= to; x++) {
					dais(x, z, true);
				}
			}
		}

		private void dais(final int x, final int z, final boolean block) {
			if (in(x, z, 0)) {
				this.keep[x][z] = 2;
				this.h[x][z] = 0;
				this.water[x][z] = false;
				this.surface[x][z] = BiomeBlocks.PATH;
				this.tall[x][z] = block;
				this.daisBlock[x][z] = block;
			}
		}

		/** Lowers every column that stands more than a step over a neighbour; water is the lowest. */
		void relax(final boolean freeOnly) {
			for (int pass = 0; pass < 60; pass++) {
				boolean changed = false;
				for (int x = 0; x < N; x++) {
					for (int z = 0; z < N; z++) {
						if (freeOnly && (this.keep[x][z] != 0 || this.water[x][z])) {
							continue;
						}
						int low = Integer.MAX_VALUE;
						if (x + 1 < N) {
							low = Math.min(low, this.h[x + 1][z]);
						}
						if (x > 0) {
							low = Math.min(low, this.h[x - 1][z]);
						}
						if (z + 1 < N) {
							low = Math.min(low, this.h[x][z + 1]);
						}
						if (z > 0) {
							low = Math.min(low, this.h[x][z - 1]);
						}
						if (this.h[x][z] > low + 1) {
							this.h[x][z] = low + 1;
							changed = true;
						}
					}
				}
				if (!changed) {
					break;
				}
			}
		}

		// ---- the spine

		void route() {
			if (this.biome == FloorBiome.GREAT_HALL) {
				this.spine = nave();
				return;
			}
			int[] from = {this.ax, this.az};
			int[] to = {this.foot, this.pz};
			int k = 3 + this.random.nextInt(3);
			List<int[]> points = new ArrayList<>();
			points.add(from);
			int lastZ = this.az;
			for (int i = 1; i <= k; i++) {
				double t = i / (double) (k + 1);
				int x = 0;
				int z = 0;
				for (int tries = 0; tries < 20; tries++) {
					x = (int) (from[0] + (to[0] - from[0]) * t + this.random.nextInt(11) - 5);
					z = 7 + this.random.nextInt(50);
					x = Math.max(4, Math.min(59, x));
					boolean inBox = x >= this.boxX0 - 2 && x <= this.boxX1 + 2 && z >= this.boxZ0 - 2 && z <= this.boxZ1 + 2;
					if (Math.abs(z - lastZ) >= 16 && !inBox) {
						break;
					}
				}
				lastZ = z;
				points.add(new int[] {x, z});
			}
			points.add(to);
			List<int[]> out = new ArrayList<>();
			out.add(points.get(0));
			for (int i = 0; i + 1 < points.size(); i++) {
				List<int[]> leg = drunk(points.get(i), points.get(i + 1));
				out.addAll(leg.subList(1, leg.size()));
			}
			this.spine = out;
		}

		/** A walk from a to b that leans toward b but wanders: most steps along the longer way, some aside. */
		List<int[]> drunk(final int[] a, final int[] b) {
			int x = a[0];
			int z = a[1];
			List<int[]> out = new ArrayList<>();
			out.add(new int[] {x, z});
			for (int i = 0; i < 900 && !(x == b[0] && z == b[1]); i++) {
				int dx = Integer.signum(b[0] - x);
				int dz = Integer.signum(b[1] - z);
				boolean longX = Math.abs(b[0] - x) >= Math.abs(b[1] - z);
				List<int[]> toward = new ArrayList<>();
				if (dx != 0) {
					toward.add(new int[] {dx, 0, Math.abs(b[0] - x)});
				}
				if (dz != 0) {
					toward.add(new int[] {0, dz, Math.abs(b[1] - z)});
				}
				toward.sort((m, n) -> Integer.compare(n[2], m[2]));
				int[] side = longX ? new int[] {0, this.random.nextBoolean() ? 1 : -1, 0} : new int[] {this.random.nextBoolean() ? 1 : -1, 0, 0};
				double r = this.random.nextDouble();
				List<int[]> order = new ArrayList<>();
				if (r < 0.55) {
					order.addAll(toward);
					order.add(side);
				} else if (r < 0.8) {
					order.add(side);
					order.addAll(toward);
				} else {
					List<int[]> reversed = new ArrayList<>(toward);
					Collections.reverse(reversed);
					order.addAll(reversed);
					order.add(side);
				}
				boolean moved = false;
				for (int[] m : order) {
					int nx = x + m[0];
					int nz = z + m[1];
					if (nx >= 2 && nx <= 61 && nz >= 2 && nz <= 61 && !this.daisBlock[nx][nz]) {
						x = nx;
						z = nz;
						moved = true;
						break;
					}
				}
				if (!moved) {
					int[] m = toward.get(0);
					x = Math.max(2, Math.min(61, x + m[0]));
					z = Math.max(2, Math.min(61, z + m[1]));
				}
				out.add(new int[] {x, z});
			}
			while (x != b[0] || z != b[1]) {
				x += Integer.signum(b[0] - x);
				if (x != b[0] || z != b[1]) {
					z += Integer.signum(b[1] - z);
				}
				out.add(new int[] {x, z});
			}
			return out;
		}

		/** Sets the spine's heights to the ground's, a step at a time, and stamps the road three wide. */
		void stampSpine() {
			int n = this.spine.size();
			int[] hs = new int[n];
			for (int i = 0; i < n; i++) {
				hs[i] = this.h[this.spine.get(i)[0]][this.spine.get(i)[1]];
			}
			hs[0] = 0;
			hs[n - 1] = 0;
			for (int pass = 0; pass < 60; pass++) {
				boolean moved = false;
				for (int i = 1; i < n; i++) {
					int v = Math.min(Math.max(hs[i], hs[i - 1] - 1), hs[i - 1] + 1);
					moved |= v != hs[i];
					hs[i] = v;
				}
				hs[n - 1] = 0;
				for (int i = n - 2; i >= 0; i--) {
					int v = Math.min(Math.max(hs[i], hs[i + 1] - 1), hs[i + 1] + 1);
					moved |= v != hs[i];
					hs[i] = v;
				}
				hs[0] = 0;
				if (!moved) {
					break;
				}
			}
			Map<Integer, Integer> cell = new HashMap<>();
			for (int i = 0; i < n; i++) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						int a = this.spine.get(i)[0] + dx;
						int b = this.spine.get(i)[1] + dz;
						if (in(a, b, 1) && this.keep[a][b] != 2) {
							cell.merge(a * N + b, hs[i], Math::min);
						}
					}
				}
			}
			for (int pass = 0; pass < 40; pass++) {
				boolean moved = false;
				for (Map.Entry<Integer, Integer> e : cell.entrySet()) {
					int x = e.getKey() / N;
					int z = e.getKey() % N;
					int lim = 99;
					int[][] near = {{x + 1, z}, {x - 1, z}, {x, z + 1}, {x, z - 1}};
					for (int[] c : near) {
						Integer v = cell.get(c[0] * N + c[1]);
						if (v != null) {
							lim = Math.min(lim, v);
						}
					}
					if (e.getValue() > lim + 1) {
						e.setValue(lim + 1);
						moved = true;
					}
				}
				if (!moved) {
					break;
				}
			}
			for (Map.Entry<Integer, Integer> e : cell.entrySet()) {
				int x = e.getKey() / N;
				int z = e.getKey() % N;
				this.keep[x][z] = 1;
				this.h[x][z] = Math.max(0, e.getValue());
				this.surface[x][z] = BiomeBlocks.PATH;
				this.water[x][z] = false;
				this.tall[x][z] = false;
				this.used[x][z] = false;
				clearAbove(x, z, 0, TowerLayout.ROOM_HEIGHT);
			}
		}

		/** Draws the road and the pad clear again, a head and an arm's reach up: nothing a feature set down stays on them. */
		void clearSpine() {
			for (int x = 0; x < N; x++) {
				for (int z = 0; z < N; z++) {
					if (this.keep[x][z] == 1 || this.keep[x][z] == 3) {
						clearAbove(x, z, this.h[x][z], this.h[x][z] + 3);
						this.tall[x][z] = false;
						this.water[x][z] = false;
					}
				}
			}
		}

		// ---- the rainy forest

		void rainyForest() {
			for (int stream = 0; stream < 2; stream++) {
				boolean alongX = this.random.nextBoolean();
				int[] a = alongX ? new int[] {0, 12 + this.random.nextInt(40)} : new int[] {12 + this.random.nextInt(40), 0};
				int[] b = alongX ? new int[] {N - 1, 12 + this.random.nextInt(40)} : new int[] {12 + this.random.nextInt(40), N - 1};
				for (int[] at : drunk(a, b)) {
					int[][] cells = {{0, 0}, {1, 0}, {0, 1}};
					for (int[] d : cells) {
						wet(at[0] + d[0], at[1] + d[1]);
					}
				}
			}
			for (int i = 0; i < 14; i++) {
				int cx = 3 + this.random.nextInt(N - 6);
				int cz = 3 + this.random.nextInt(N - 6);
				int[][] cross = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
				for (int[] d : cross) {
					wet(cx + d[0], cz + d[1]);
				}
			}
			int[] shrine = rect(5, 5, 80, 1);
			if (shrine != null) {
				flatten(shrine[0], shrine[1], 5, 5, (byte) 4);
				claim(shrine[0], shrine[1], 5, 5);
			}
			List<int[]> camps = new ArrayList<>();
			for (int i = 0; i < 3; i++) {
				int[] camp = rect(3, 3, 60, 1);
				if (camp != null) {
					flatten(camp[0], camp[1], 3, 3, (byte) 4);
					claim(camp[0], camp[1], 3, 3);
					camps.add(camp);
				}
			}
			relax(true);
			trees();
			if (shrine != null) {
				shrine(shrine[0], shrine[1], 5, 5);
			}
			for (int[] camp : camps) {
				int y = this.h[camp[0]][camp[1]];
				put(camp[0], y, camp[1], BiomeBlocks.BARREL);
				put(camp[0] + 1, y, camp[1] + 1, BiomeBlocks.FENCE);
				put(camp[0] + 1, y + 1, camp[1] + 1, BiomeBlocks.LANTERN);
				this.tall[camp[0] + 1][camp[1] + 1] = true;
				for (int x = camp[0]; x < camp[0] + 3; x++) {
					for (int z = camp[1]; z < camp[1] + 3; z++) {
						this.surface[x][z] = BiomeBlocks.COARSE_DIRT;
					}
				}
			}
			fallenLogs();
			ferns();
			podzol();
		}

		/** Patches of podzol in the forest floor with a few mushrooms, which grow on it whatever the light. */
		void podzol() {
			for (int patch = 0; patch < 5; patch++) {
				int cx = 4 + this.random.nextInt(N - 8);
				int cz = 4 + this.random.nextInt(N - 8);
				if (!free(cx, cz, 2)) {
					continue;
				}
				int mushrooms = 0;
				for (int[] c : disc(cx, cz, 2)) {
					if (free(c[0], c[1])) {
						this.surface[c[0]][c[1]] = BiomeBlocks.PODZOL;
						if (mushrooms < 4 && this.random.nextInt(3) == 0 && get(c[0], this.h[c[0]][c[1]], c[1]) == 0) {
							put(c[0], this.h[c[0]][c[1]], c[1], this.random.nextBoolean() ? BiomeBlocks.BROWN_MUSHROOM : BiomeBlocks.RED_MUSHROOM);
							mushrooms++;
						}
					}
				}
			}
		}

		void wet(final int x, final int z) {
			if (free(x, z)) {
				this.water[x][z] = true;
				this.h[x][z] = -1;
				this.surface[x][z] = BiomeBlocks.MUD;
			}
		}

		void trees() {
			List<int[]> placed = new ArrayList<>();
			for (int tries = 0; placed.size() < 70 && tries < 70 * 50; tries++) {
				int x = 2 + this.random.nextInt(N - 4);
				int z = 2 + this.random.nextInt(N - 4);
				boolean far = true;
				for (int[] p : placed) {
					far &= (x - p[0]) * (x - p[0]) + (z - p[1]) * (z - p[1]) >= 25;
				}
				if (!far || !free(x, z, 1)) {
					continue;
				}
				boolean big = this.random.nextDouble() < 0.3 && x + 1 < N - 2 && z + 1 < N - 2 && free(x + 1, z, 1) && free(x, z + 1, 1) && free(x + 1, z + 1, 1);
				if (tree(x, z, big)) {
					placed.add(new int[] {x, z});
				}
			}
		}

		/** One tree, its canopy kept three blocks over the highest ground within reach so no body is capped. */
		boolean tree(final int x, final int z, final boolean big) {
			int kind = big ? 1 : this.random.nextInt(4) == 0 ? 2 : 0;
			byte log = kind == 1 ? BiomeBlocks.DARK_OAK_LOG : kind == 2 ? BiomeBlocks.BIRCH_LOG : BiomeBlocks.OAK_LOG;
			byte leaves = kind == 1 ? BiomeBlocks.DARK_OAK_LEAVES : kind == 2 ? BiomeBlocks.BIRCH_LEAVES : BiomeBlocks.OAK_LEAVES;
			return grow(x, z, big ? 2 : 1, big ? 3 : 2, 4, log, leaves);
		}

		/** A trunk {@code size} blocks square under a canopy of {@code reach}, {@code lift} blocks over the highest ground within it. */
		boolean grow(final int x, final int z, final int size, final int reach, final int lift, final byte log, final byte leaves) {
			int ground = 0;
			// Over the highest ground a step away from the canopy's edge, so no wall or step beside it reaches the leaves.
			for (int dx = -reach - 3; dx <= reach + size + 2; dx++) {
				for (int dz = -reach - 3; dz <= reach + size + 2; dz++) {
					if (in(x + dx, z + dz, 0)) {
						ground = Math.max(ground, this.h[x + dx][z + dz]);
					}
				}
			}
			int bottom = ground + lift;
			if (bottom + 3 > TowerLayout.ROOM_HEIGHT - 4) {
				return false;
			}
			for (int dx = 0; dx < size; dx++) {
				for (int dz = 0; dz < size; dz++) {
					for (int y = this.h[x + dx][z + dz]; y <= bottom + 2; y++) {
						put(x + dx, y, z + dz, log);
					}
					this.tall[x + dx][z + dz] = true;
				}
			}
			for (int dx = -1; dx <= size; dx++) {
				for (int dz = -1; dz <= size; dz++) {
					if (in(x + dx, z + dz, 1)) {
						this.used[x + dx][z + dz] = true;
					}
				}
			}
			for (int layer = 0; layer < 4; layer++) {
				int radius = layer < 2 ? reach : reach - 1;
				for (int dx = -radius; dx <= radius + size - 1; dx++) {
					for (int dz = -radius; dz <= radius + size - 1; dz++) {
						boolean cornerX = dx == -radius || dx == radius + size - 1;
						boolean cornerZ = dz == -radius || dz == radius + size - 1;
						if (cornerX && cornerZ && this.random.nextBoolean()) {
							continue;
						}
						if (get(x + dx, bottom + layer, z + dz) == 0) {
							put(x + dx, bottom + layer, z + dz, leaves);
						}
					}
				}
			}
			return true;
		}

		/** A broken ring of mossy wall round a floor of stone, a little higher in some places than others. */
		void shrine(final int x0, final int z0, final int w, final int l) {
			for (int x = x0; x < x0 + w; x++) {
				for (int z = z0; z < z0 + l; z++) {
					boolean edge = x == x0 || x == x0 + w - 1 || z == z0 || z == z0 + l - 1;
					if (!edge) {
						this.surface[x][z] = BiomeBlocks.COBBLE;
					} else if (this.random.nextDouble() >= 0.4) {
						stack(x, z, BiomeBlocks.MOSSY_BRICKS, 1 + this.random.nextInt(3));
					}
				}
			}
		}

		void fallenLogs() {
			for (int i = 0, placed = 0; placed < 8 && i < 400; i++) {
				boolean alongX = this.random.nextBoolean();
				int x = 3 + this.random.nextInt(N - 8);
				int z = 3 + this.random.nextInt(N - 8);
				int base = this.h[x][z];
				boolean fits = true;
				for (int k = 0; k < 3 && fits; k++) {
					int cx = alongX ? x + k : x;
					int cz = alongX ? z : z + k;
					fits = free(cx, cz, 1) && this.h[cx][cz] == base;
				}
				if (!fits) {
					continue;
				}
				for (int k = 0; k < 3; k++) {
					int cx = alongX ? x + k : x;
					int cz = alongX ? z : z + k;
					put(cx, base, cz, alongX ? BiomeBlocks.OAK_LOG_X : BiomeBlocks.OAK_LOG_Z);
					this.used[cx][cz] = true;
				}
				placed++;
			}
		}

		void ferns() {
			for (int i = 0, placed = 0; placed < 50 && i < 600; i++) {
				int x = 2 + this.random.nextInt(N - 4);
				int z = 2 + this.random.nextInt(N - 4);
				if (free(x, z) && this.surface[x][z] == BiomeBlocks.GRASS && get(x, this.h[x][z], z) == 0) {
					put(x, this.h[x][z], z, BiomeBlocks.FERN);
					placed++;
				}
			}
		}

		// ---- helpers of the biomes built of walls

		/** The columns of a disc, the middle included, kept off the room's edge. */
		List<int[]> disc(final int cx, final int cz, final int r) {
			List<int[]> cells = new ArrayList<>();
			for (int x = cx - r; x <= cx + r; x++) {
				for (int z = cz - r; z <= cz + r; z++) {
					if (in(x, z, 1) && (x - cx) * (x - cx) + (z - cz) * (z - cz) <= r * r + r / 2) {
						cells.add(new int[] {x, z});
					}
				}
			}
			return cells;
		}

		/** Points spread at least {@code minDistance} apart over the room, each one {@code ok}. */
		List<int[]> poisson(final int count, final int minDistance, final java.util.function.BiPredicate<Integer, Integer> ok) {
			List<int[]> points = new ArrayList<>();
			for (int tries = 0; points.size() < count && tries < count * 50; tries++) {
				int x = 2 + this.random.nextInt(N - 4);
				int z = 2 + this.random.nextInt(N - 4);
				boolean far = true;
				for (int[] p : points) {
					far &= (x - p[0]) * (x - p[0]) + (z - p[1]) * (z - p[1]) >= minDistance * minDistance;
				}
				if (far && ok.test(x, z)) {
					points.add(new int[] {x, z});
				}
			}
			return points;
		}

		/** Sets a level rectangle aside for a building and returns its corner, or null when none fits. */
		int[] pad(final int w, final int l, final int tries) {
			int[] spot = rect(w, l, tries, 1);
			if (spot != null) {
				flatten(spot[0], spot[1], w, l, (byte) 4);
				claim(spot[0], spot[1], w, l);
			}
			return spot;
		}

		/**
		 * A broken ring of wall round a floor, the gaps drawn at random and some of them left as rubble; returns the
		 * columns inside. The spine, drawn before, always cuts through whatever it meets.
		 */
		List<int[]> ruin(final int x0, final int z0, final int w, final int l, final byte[] stone, final int hmin, final int hmax, final double gap,
			final byte floorCode) {
			List<int[]> inside = new ArrayList<>();
			for (int x = x0; x < x0 + w; x++) {
				for (int z = z0; z < z0 + l; z++) {
					boolean edge = x == x0 || x == x0 + w - 1 || z == z0 || z == z0 + l - 1;
					if (!edge) {
						if (floorCode != 0) {
							this.surface[x][z] = floorCode;
						}
						inside.add(new int[] {x, z});
					} else if (this.random.nextDouble() < gap) {
						if (this.random.nextDouble() < 0.4) {
							put(x, this.h[x][z], z, BiomeBlocks.COBBLE);
							this.used[x][z] = true;
						}
					} else {
						stack(x, z, stone[this.random.nextInt(stone.length)], hmin + this.random.nextInt(hmax - hmin + 1));
					}
				}
			}
			return inside;
		}

		/** A dent one block deep, where no neighbour stands higher than the ground it is cut from and no water lies near. */
		boolean dent(final int x, final int z) {
			if (!free(x, z, 1) || this.h[x][z] < 0) {
				return false;
			}
			int[][] near = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
			for (int[] n : near) {
				if (this.h[x + n[0]][z + n[1]] > this.h[x][z]) {
					return false;
				}
			}
			if (this.h[x][z] == 0) {
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						if (in(x + dx, z + dz, 0) && this.water[x + dx][z + dz]) {
							return false;
						}
					}
				}
			}
			this.h[x][z]--;
			this.surface[x][z] = BiomeBlocks.COARSE_DIRT;
			this.used[x][z] = true;
			return true;
		}

		/** A flame that spreads to nothing: soul fire on soul soil. */
		boolean soulFire(final int x, final int z) {
			if (!free(x, z, 1)) {
				return false;
			}
			this.surface[x][z] = BiomeBlocks.SOUL_SOIL;
			put(x, this.h[x][z], z, BiomeBlocks.SOUL_FIRE);
			this.used[x][z] = true;
			return true;
		}

		void fires(final int count) {
			for (int tries = 0, placed = 0; placed < count && tries < count * 60; tries++) {
				placed += soulFire(2 + this.random.nextInt(N - 4), 2 + this.random.nextInt(N - 4)) ? 1 : 0;
			}
		}

		void barrels(final List<int[]> inside, final int count) {
			List<int[]> cells = new ArrayList<>(inside);
			Collections.shuffle(cells, this.random);
			for (int[] c : cells.subList(0, Math.min(cells.size(), count))) {
				if (get(c[0], this.h[c[0]][c[1]], c[1]) == 0) {
					put(c[0], this.h[c[0]][c[1]], c[1], BiomeBlocks.BARREL);
				}
			}
		}

		/** A well: a short ring of stone, and still water in the middle. */
		void well(final int[] spot) {
			ruin(spot[0], spot[1], 3, 3, new byte[] {BiomeBlocks.COBBLESTONE, BiomeBlocks.COBBLE}, 1, 2, 0.15, (byte) 0);
			int cx = spot[0] + 1;
			int cz = spot[1] + 1;
			this.water[cx][cz] = true;
			this.h[cx][cz] = -1;
			this.surface[cx][cz] = BiomeBlocks.MUD;
		}

		// ---- the destroyed village

		void village() {
			int[] mid = this.spine.get(this.spine.size() / 2);
			for (int[] c : disc(mid[0], mid[1], 6)) {
				if (free(c[0], c[1])) {
					this.surface[c[0]][c[1]] = BiomeBlocks.PATH;
				}
				this.used[c[0]][c[1]] = true;
			}
			for (int i = 0; i < 3; i++) {
				int lo = this.spine.size() / 5;
				int[] a = this.spine.get(lo + this.random.nextInt(this.spine.size() * 4 / 5 - lo + 1));
				int[] end = {Math.max(4, Math.min(59, a[0] + this.random.nextInt(41) - 20)), Math.max(4, Math.min(59, a[1] + this.random.nextInt(41) - 20))};
				for (int[] c : drunk(a, end)) {
					if (free(c[0], c[1])) {
						this.surface[c[0]][c[1]] = BiomeBlocks.PATH;
					}
				}
			}
			int[][] kinds = {{7, 9, 0}, {5, 7, 0}, {5, 5, 0}, {6, 6, 0}, {9, 11, 1}, {7, 7, 2}, {5, 5, 0}, {6, 8, 0}};
			List<int[]> buildings = new ArrayList<>();
			for (int[] kind : kinds) {
				int[] spot = pad(kind[0], kind[1], 120);
				if (spot != null) {
					buildings.add(new int[] {spot[0], spot[1], kind[0], kind[1], kind[2]});
				}
			}
			List<int[]> wells = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				int[] spot = pad(3, 3, 80);
				if (spot != null) {
					wells.add(spot);
				}
			}
			List<int[]> farms = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				int[] spot = pad(5, 7, 80);
				if (spot != null) {
					farms.add(spot);
				}
			}
			relax(true);
			byte[] stone = {BiomeBlocks.COBBLESTONE, BiomeBlocks.COBBLE, BiomeBlocks.STONE_BRICKS};
			for (int[] b : buildings) {
				List<int[]> inside = ruin(b[0], b[1], b[2], b[3], stone, 1, b[4] == 1 ? 6 : 4, 0.38, b[4] == 2 ? BiomeBlocks.STONE_BRICKS : BiomeBlocks.COBBLESTONE);
				barrels(inside, b[4] == 0 ? 2 : 4);
			}
			for (int[] spot : wells) {
				well(spot);
			}
			for (int[] farm : farms) {
				for (int x = farm[0]; x < farm[0] + 5; x++) {
					for (int z = farm[1]; z < farm[1] + 7; z++) {
						this.surface[x][z] = BiomeBlocks.MUD;
						if ((x - farm[0]) % 2 == 0 && this.random.nextDouble() < 0.8) {
							put(x, this.h[x][z], z, BiomeBlocks.HAY);
						}
					}
				}
			}
			for (int i = 0; i < 12; i++) {
				int x = 3 + this.random.nextInt(N - 6);
				int z = 3 + this.random.nextInt(N - 6);
				if (free(x, z, 1)) {
					stack(x, z, this.random.nextBoolean() ? BiomeBlocks.STRIPPED_SPRUCE : BiomeBlocks.COAL_BLOCK, 3 + this.random.nextInt(4));
				}
			}
			for (int i = 0, placed = 0; placed < 6 && i < 200; i++) {
				placed += dent(3 + this.random.nextInt(N - 6), 3 + this.random.nextInt(N - 6)) ? 1 : 0;
			}
			fires(6);
		}

		// ---- the destroyed castle

		void castle() {
			int ring = 5;
			int[][] corners = {{ring - 1, ring - 1}, {N - ring - 4, ring - 1}, {ring - 1, N - ring - 4}, {N - ring - 4, N - ring - 4}};
			int fx = this.facing;
			int kx0 = fx == -1 ? this.px - 13 : this.px - 4;
			int kx1 = fx == -1 ? this.px + 4 : this.px + 13;
			int kz0 = this.pz - 11;
			int kz1 = this.pz + 12;
			List<int[]> halls = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				int[] spot = pad(8, 6, 100);
				if (spot != null) {
					halls.add(spot);
				}
			}
			int[] well = pad(3, 3, 80);
			relax(true);
			byte[] stone = {BiomeBlocks.STONE_BRICKS, BiomeBlocks.MOSSY_BRICKS, BiomeBlocks.DEEPSLATE_BRICKS, BiomeBlocks.CRACKED_DEEPSLATE};
			for (int x = ring; x < N - ring; x++) {
				for (int z = ring; z < N - ring; z++) {
					boolean on = x == ring || x == ring + 1 || x == N - ring - 2 || x == N - ring - 1 || z == ring || z == ring + 1 || z == N - ring - 2 || z == N - ring - 1;
					if (on && free(x, z)) {
						if (this.random.nextDouble() < 0.12) {
							put(x, this.h[x][z], z, BiomeBlocks.COBBLE);
							this.used[x][z] = true;
						} else {
							stack(x, z, stone[this.random.nextInt(stone.length)], 5 + this.random.nextInt(5));
						}
					}
				}
			}
			for (int[] c : corners) {
				for (int x = c[0]; x < c[0] + 5; x++) {
					for (int z = c[1]; z < c[1] + 5; z++) {
						if (in(x, z, 1) && free(x, z) && this.random.nextDouble() < 0.9) {
							stack(x, z, stone[this.random.nextInt(stone.length)], 9 + this.random.nextInt(8));
						}
					}
				}
			}
			for (int x = kx0; x <= kx1; x++) {
				for (int z = kz0; z <= kz1; z++) {
					boolean edge = Math.min(Math.min(x - kx0, kx1 - x), Math.min(z - kz0, kz1 - z)) < 2;
					if (edge && in(x, z, 1) && free(x, z) && this.random.nextDouble() > 0.14) {
						stack(x, z, stone[this.random.nextInt(stone.length)], 7 + this.random.nextInt(7));
					}
				}
			}
			for (int x = kx0; x <= kx1; x++) {
				for (int z = kz0; z <= kz1; z++) {
					if (in(x, z, 0)) {
						this.used[x][z] = true;
					}
				}
			}
			for (int[] hall : halls) {
				ruin(hall[0], hall[1], 8, 6, stone, 1, 4, 0.45, BiomeBlocks.STONE);
			}
			if (well != null) {
				well(well);
			}
			for (int[] c : poisson(10, 5, (x, z) -> free(x, z, 1))) {
				stack(c[0], c[1], BiomeBlocks.POLISHED_ANDESITE, 3 + this.random.nextInt(6));
			}
			for (int[] c : poisson(5, 9, (x, z) -> free(x, z, 1) && x > ring + 2 && x < N - ring - 3)) {
				for (int[] d : disc(c[0], c[1], 1)) {
					dent(d[0], d[1]);
				}
			}
			fires(4);
		}

		// ---- the adventurers' graveyard

		void graveyard() {
			List<int[]> plots = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				int[] spot = pad(14, 10, 80);
				if (spot != null) {
					plots.add(spot);
				}
			}
			List<int[]> tombs = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				int[] spot = pad(7, 9, 80);
				if (spot != null) {
					tombs.add(spot);
				}
			}
			int[] chapel = pad(9, 13, 80);
			relax(true);
			for (int[] plot : plots) {
				for (int x = plot[0]; x < plot[0] + 14; x++) {
					for (int z = plot[1]; z < plot[1] + 10; z++) {
						boolean edge = x == plot[0] || x == plot[0] + 13 || z == plot[1] || z == plot[1] + 9;
						if (edge && this.random.nextDouble() > 0.12) {
							stack(x, z, BiomeBlocks.COBBLE_WALL, 1);
						} else if ((x - plot[0]) % 3 == 1 && (z - plot[1]) % 3 == 1) {
							if (this.random.nextDouble() < 0.3) {
								graveDent(x, z);
							} else {
								put(x, this.h[x][z], z, BiomeBlocks.COBBLE_WALL);
							}
						}
					}
				}
			}
			byte[] stone = {BiomeBlocks.DEEPSLATE_BRICKS, BiomeBlocks.CRACKED_DEEPSLATE, BiomeBlocks.MOSSY_BRICKS};
			for (int[] tomb : tombs) {
				List<int[]> inside = ruin(tomb[0], tomb[1], 7, 9, stone, 3, 5, 0.25, BiomeBlocks.STONE_BRICKS);
				if (!inside.isEmpty()) {
					int[] middle = inside.get(inside.size() / 2);
					graveDent(middle[0], middle[1]);
				}
			}
			if (chapel != null) {
				ruin(chapel[0], chapel[1], 9, 13, stone, 2, 7, 0.45, BiomeBlocks.STONE_BRICKS);
			}
			for (int[] t : poisson(14, 8, (x, z) -> free(x, z, 1))) {
				stack(t[0], t[1], BiomeBlocks.OAK_LOG, 4 + this.random.nextInt(5));
			}
		}

		/** An open grave, one block deep: the plot is level, so the dent is cut without the free-ground test. */
		private void graveDent(final int x, final int z) {
			if (this.h[x][z] >= 1) {
				this.h[x][z]--;
				this.surface[x][z] = BiomeBlocks.COARSE_DIRT;
			}
		}

		// ---- the scorched battlefield

		void battlefield() {
			for (int[] c : poisson(14, 9, (x, z) -> free(x, z, 3))) {
				int r = 2 + this.random.nextInt(4);
				for (int[] d : disc(c[0], c[1], r)) {
					if (free(d[0], d[1])) {
						this.h[d[0]][d[1]] = Math.max(0, this.h[d[0]][d[1]] - 1);
						this.surface[d[0]][d[1]] = BiomeBlocks.COARSE_DIRT;
					}
				}
				if (this.random.nextDouble() < 0.3 && free(c[0], c[1])) {
					this.surface[c[0]][c[1]] = BiomeBlocks.MAGMA;
				}
			}
			List<int[]> stakes = new ArrayList<>();
			for (int t = 0; t < 3; t++) {
				int[] a = {3 + this.random.nextInt(18), 5 + this.random.nextInt(54)};
				int[] b = {43 + this.random.nextInt(18), 5 + this.random.nextInt(54)};
				for (int[] c : drunk(a, b)) {
					if (free(c[0], c[1], 1)) {
						this.h[c[0]][c[1]] = Math.max(0, this.h[c[0]][c[1]] - 1);
						this.surface[c[0]][c[1]] = BiomeBlocks.COARSE_DIRT;
						if (this.random.nextDouble() < 0.18) {
							stakes.add(new int[] {c[0], c[1] + 2});
						}
					}
				}
			}
			List<int[]> wrecks = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				int[] spot = pad(3, 5, 80);
				if (spot != null) {
					wrecks.add(spot);
				}
			}
			relax(true);
			for (int[] s : stakes) {
				if (free(s[0], s[1])) {
					stack(s[0], s[1], BiomeBlocks.FENCE, 2);
				}
			}
			for (int[] w : wrecks) {
				ruin(w[0], w[1], 3, 5, new byte[] {BiomeBlocks.SPRUCE_LOG}, 3, 7, 0.45, (byte) 0);
				if (get(w[0] + 1, this.h[w[0] + 1][w[1] + 2], w[1] + 2) == 0) {
					put(w[0] + 1, this.h[w[0] + 1][w[1] + 2], w[1] + 2, BiomeBlocks.ANVIL);
				}
			}
			fires(14);
		}

		// ---- the jungle

		void jungle() {
			for (int x = 0; x < N; x++) {
				for (int z = 0; z < N; z++) {
					if (in(x, z, 1) && vnoise(this.seed, x, z, 9, 5) < 0.26 && free(x, z, 1)) {
						this.water[x][z] = true;
						this.h[x][z] = -1;
						this.surface[x][z] = BiomeBlocks.MUD;
					}
				}
			}
			List<int[]> temples = new ArrayList<>();
			for (int i = 0; i < 3; i++) {
				int[] spot = pad(7, 7, 60);
				if (spot != null) {
					temples.add(spot);
				}
			}
			relax(true);
			for (int[] t : poisson(24, 8, (x, z) -> free(x, z, 2) && free(x + 1, z + 1, 2) && free(x + 1, z, 2) && free(x, z + 1, 2))) {
				grow(t[0], t[1], 2, 4, 5 + this.random.nextInt(8), BiomeBlocks.JUNGLE_LOG, BiomeBlocks.JUNGLE_LEAVES);
			}
			for (int i = 0; i < 30; i++) {
				int bx = 3 + this.random.nextInt(N - 6);
				int bz = 3 + this.random.nextInt(N - 6);
				for (int[] c : disc(bx, bz, 1 + this.random.nextInt(2))) {
					if (free(c[0], c[1], 1)) {
						stack(c[0], c[1], BiomeBlocks.JUNGLE_LEAVES, 2);
					}
				}
			}
			byte[] stone = {BiomeBlocks.MOSSY_BRICKS, BiomeBlocks.COBBLE, BiomeBlocks.STONE_BRICKS};
			for (int[] t : temples) {
				ruin(t[0], t[1], 7, 7, stone, 2, 5, 0.35, BiomeBlocks.STONE);
			}
		}

		// ---- the mistbound marsh

		void marsh() {
			for (int x = 0; x < N; x++) {
				for (int z = 0; z < N; z++) {
					if (in(x, z, 1) && vnoise(this.seed, x, z, 10, 4) < 0.34 && free(x, z, 1)) {
						this.water[x][z] = true;
						this.h[x][z] = -1;
						this.surface[x][z] = BiomeBlocks.MUD;
					}
				}
			}
			List<int[]> huts = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				int[] spot = pad(5, 5, 80);
				if (spot != null) {
					huts.add(spot);
				}
			}
			relax(true);
			for (int[] t : poisson(26, 6, (x, z) -> free(x, z, 1))) {
				stack(t[0], t[1], BiomeBlocks.OAK_LOG, 5 + this.random.nextInt(5));
			}
			for (int[] hut : huts) {
				ruin(hut[0], hut[1], 5, 5, new byte[] {BiomeBlocks.PLANKS}, 2, 4, 0.3, BiomeBlocks.PLANKS);
			}
			for (int i = 0, placed = 0; placed < 8 && i < 400; i++) {
				int x = 3 + this.random.nextInt(N - 6);
				int z = 3 + this.random.nextInt(N - 6);
				if (free(x, z, 1)) {
					stack(x, z, BiomeBlocks.COBBLE, 2);
					placed++;
				}
			}
			// The spine is a boardwalk.
			for (int x = 0; x < N; x++) {
				for (int z = 0; z < N; z++) {
					if (this.keep[x][z] == 1) {
						this.surface[x][z] = BiomeBlocks.PLANKS;
					}
				}
			}
		}

		// ---- the basalt deltas

		void basalt() {
			for (int x = 0; x < N; x++) {
				for (int z = 0; z < N; z++) {
					// A lava lake keeps three blocks from the road, the stair, the guard's yard and the arrival.
					if (in(x, z, 1) && vnoise(this.seed, x, z, 11, 6) < 0.30 && free(x, z, 3)) {
						this.lava[x][z] = true;
						this.h[x][z] = -1;
						this.surface[x][z] = BiomeBlocks.BASALT;
					}
				}
			}
			relax(true);
			for (int x = 1; x < N - 1; x++) {
				for (int z = 1; z < N - 1; z++) {
					if (this.keep[x][z] != 0 || this.lava[x][z] || this.used[x][z]) {
						continue;
					}
					boolean beside = this.lava[x + 1][z] || this.lava[x - 1][z] || this.lava[x][z + 1] || this.lava[x][z - 1];
					if (this.random.nextDouble() < (beside ? 0.28 : 0.02)) {
						this.surface[x][z] = BiomeBlocks.MAGMA;
					}
				}
			}
			for (int[] c : poisson(30, 5, (x, z) -> free(x, z, 2))) {
				int r = this.random.nextDouble() < 0.25 ? 2 : 1;
				int height = 3 + this.random.nextInt(7);
				for (int[] d : disc(c[0], c[1], r)) {
					if (free(d[0], d[1])) {
						stack(d[0], d[1], BiomeBlocks.BASALT, height);
					}
				}
			}
			fires(8);
		}

		// ---- the eternal snowstorm

		void snow() {
			for (int[] c : poisson(16, 5, (x, z) -> free(x, z, 1))) {
				for (int[] d : disc(c[0], c[1], 1 + this.random.nextInt(2))) {
					if (free(d[0], d[1])) {
						this.surface[d[0]][d[1]] = BiomeBlocks.POWDER_SNOW;
						this.used[d[0]][d[1]] = true;
					}
				}
			}
			for (int i = 0; i < 3; i++) {
				int cx = 8 + this.random.nextInt(N - 16);
				int cz = 8 + this.random.nextInt(N - 16);
				for (int[] d : disc(cx, cz, 3 + this.random.nextInt(3))) {
					if (free(d[0], d[1], 1)) {
						this.surface[d[0]][d[1]] = BiomeBlocks.PACKED_ICE;
					}
				}
			}
			List<int[]> camps = new ArrayList<>();
			for (int i = 0; i < 3; i++) {
				int[] spot = pad(5, 5, 60);
				if (spot != null) {
					camps.add(spot);
				}
			}
			relax(true);
			for (int[] c : poisson(14, 7, (x, z) -> free(x, z, 1))) {
				stack(c[0], c[1], BiomeBlocks.BLUE_ICE, 5 + this.random.nextInt(8));
			}
			for (int[] camp : camps) {
				ruin(camp[0], camp[1], 5, 5, new byte[] {BiomeBlocks.WHITE_WOOL}, 2, 2, 0.2, (byte) 0);
				int y = this.h[camp[0] + 2][camp[1] + 2];
				put(camp[0] + 2, y, camp[1] + 2, BiomeBlocks.SOUL_CAMPFIRE);
				put(camp[0] + 1, y, camp[1] + 1, BiomeBlocks.BARREL);
			}
		}

		// ---- the desert storm

		void desert() {
			int[] oasis = null;
			for (int tries = 0; tries < 60 && oasis == null; tries++) {
				int cx = 10 + this.random.nextInt(N - 20);
				int cz = 10 + this.random.nextInt(N - 20);
				if (free(cx, cz, 6)) {
					oasis = new int[] {cx, cz};
				}
			}
			List<int[]> ruins = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				int size = 6 + this.random.nextInt(4);
				int[] spot = pad(size, size, 60);
				if (spot != null) {
					ruins.add(new int[] {spot[0], spot[1], size});
				}
			}
			if (oasis != null) {
				for (int[] d : disc(oasis[0], oasis[1], 5)) {
					if (free(d[0], d[1])) {
						this.surface[d[0]][d[1]] = BiomeBlocks.GRASS;
						this.h[d[0]][d[1]] = 0;
						this.keep[d[0]][d[1]] = 4;
					}
				}
			}
			relax(true);
			if (oasis != null) {
				for (int[] d : disc(oasis[0], oasis[1], 3)) {
					if (this.keep[d[0]][d[1]] == 4) {
						this.water[d[0]][d[1]] = true;
						this.h[d[0]][d[1]] = -1;
						this.surface[d[0]][d[1]] = BiomeBlocks.MUD;
					}
				}
				for (int palm = 0, placed = 0; palm < 40 && placed < 3; palm++) {
					int[] d = disc(oasis[0], oasis[1], 5).get(this.random.nextInt(disc(oasis[0], oasis[1], 5).size()));
					if (this.keep[d[0]][d[1]] == 4 && !this.water[d[0]][d[1]] && !this.tall[d[0]][d[1]] && !this.used[d[0]][d[1]]
						&& grow(d[0], d[1], 1, 2, 4, BiomeBlocks.JUNGLE_LOG, BiomeBlocks.JUNGLE_LEAVES)) {
						placed++;
					}
				}
			}
			byte[] stone = {BiomeBlocks.SANDSTONE, BiomeBlocks.CUT_SANDSTONE};
			for (int[] r : ruins) {
				ruin(r[0], r[1], r[2], r[2], stone, 1, 5, 0.4, BiomeBlocks.CUT_SANDSTONE);
			}
			for (int[] c : poisson(3, 10, (x, z) -> free(x, z, 2))) {
				stack(c[0], c[1], BiomeBlocks.CUT_SANDSTONE, 8 + this.random.nextInt(5));
			}
			for (int i = 0, placed = 0; placed < 3 && i < 200; i++) {
				placed += dent(3 + this.random.nextInt(N - 6), 3 + this.random.nextInt(N - 6)) ? 1 : 0;
			}
			for (int i = 0; i < 5; i++) {
				int cx = 4 + this.random.nextInt(N - 8);
				int cz = 4 + this.random.nextInt(N - 8);
				for (int[] d : disc(cx, cz, 1)) {
					if (free(d[0], d[1], 1)) {
						this.surface[d[0]][d[1]] = BiomeBlocks.SOUL_SAND;
					}
				}
			}
			for (int[] c : poisson(34, 4, (x, z) -> free(x, z, 1) && (this.surface[x][z] == BiomeBlocks.SAND || this.surface[x][z] == BiomeBlocks.RED_SAND))) {
				// Three high or more: a cactus that tall neither grows nor ages.
				stack(c[0], c[1], BiomeBlocks.CACTUS, 3 + this.random.nextInt(2));
				markFragile(c[0], c[1]);
			}
		}

		/** A cactus breaks beside any solid block: its column and the four beside it are kept from anything solid. */
		void markFragile(final int x, final int z) {
			int[][] around = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
			for (int[] a : around) {
				if (in(x + a[0], z + a[1], 0)) {
					this.fragile[x + a[0]][z + a[1]] = true;
				}
			}
		}

		/** Takes down every cactus that something set beside it, and marks the columns of the rest. */
		void trimCacti() {
			int[][] ways = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
			for (int x = 0; x < N; x++) {
				for (int z = 0; z < N; z++) {
					this.fragile[x][z] = false;
				}
			}
			for (int x = 0; x < N; x++) {
				for (int z = 0; z < N; z++) {
					if (get(x, this.h[x][z], z) != BiomeBlocks.CACTUS) {
						continue;
					}
					boolean crowded = false;
					for (int[] way : ways) {
						for (int y = this.h[x][z]; y < this.h[x][z] + 4; y++) {
							byte beside = get(x + way[0], y, z + way[1]);
							crowded |= beside != 0 && beside != BiomeBlocks.CACTUS;
						}
					}
					if (crowded) {
						clearAbove(x, z, this.h[x][z], this.h[x][z] + 5);
						this.tall[x][z] = false;
					}
				}
			}
			for (int x = 0; x < N; x++) {
				for (int z = 0; z < N; z++) {
					if (get(x, this.h[x][z], z) == BiomeBlocks.CACTUS) {
						markFragile(x, z);
					}
				}
			}
		}

		// ---- the biomes under rock

		/** Opens the columns of a three wide corridor from a to b, along x and then along z. */
		void carve(final boolean[][] open, final int ax, final int az, final int bx, final int bz) {
			int x = ax;
			int z = az;
			while (x != bx) {
				open3(open, x, z);
				x += Integer.signum(bx - x);
			}
			while (z != bz) {
				open3(open, x, z);
				z += Integer.signum(bz - z);
			}
			open3(open, bx, bz);
		}

		private void open3(final boolean[][] open, final int x, final int z) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (in(x + dx, z + dz, 1)) {
						open[x + dx][z + dz] = true;
					}
				}
			}
		}

		/** Marks every column that is not open, and not part of the frame, as rock to the roof; returns them. */
		boolean[][] rock(final boolean[][] open) {
			boolean[][] solid = new boolean[N][N];
			this.rockMask = solid;
			for (int x = 0; x < N; x++) {
				for (int z = 0; z < N; z++) {
					if (!open[x][z] && this.keep[x][z] == 0 && !this.water[x][z]) {
						solid[x][z] = true;
						this.tall[x][z] = true;
						this.used[x][z] = true;
					}
				}
			}
			return solid;
		}

		/**
		 * Closes the room over its air: a rock column is rock from its feet to the roof, and an open column from the height
		 * {@code air} gives it, in the rock of the biome.
		 */
		void enclose(final boolean[][] solid, final java.util.function.BiFunction<Integer, Integer, Integer> air, final byte[] rocks) {
			this.ceil = new short[N][N];
			this.ceilCode = new byte[N][N];
			for (int x = 0; x < N; x++) {
				for (int z = 0; z < N; z++) {
					this.ceil[x][z] = (short) (solid[x][z] ? Math.max(0, this.h[x][z]) : Math.min(TowerLayout.ROOM_HEIGHT - 2, air.apply(x, z)));
					byte code = rocks[0];
					if (rocks.length > 1 && vnoise(this.seed, x, z, 5, 8) > 0.78) {
						code = rocks[1];
					}
					if (rocks.length > 2 && this.random.nextDouble() < 0.07) {
						code = rocks[2];
					}
					this.ceilCode[x][z] = code;
				}
			}
		}

		// ---- the amethyst cave

		void cave() {
			boolean[][] rock = new boolean[N][N];
			for (int x = 0; x < N; x++) {
				for (int z = 0; z < N; z++) {
					rock[x][z] = !in(x, z, 1) || this.random.nextDouble() < 0.46;
				}
			}
			for (int pass = 0; pass < 4; pass++) {
				boolean[][] next = new boolean[N][N];
				for (int x = 0; x < N; x++) {
					for (int z = 0; z < N; z++) {
						int count = 0;
						for (int dx = -1; dx <= 1; dx++) {
							for (int dz = -1; dz <= 1; dz++) {
								count += !in(x + dx, z + dz, 0) || rock[x + dx][z + dz] ? 1 : 0;
							}
						}
						next[x][z] = count >= 5;
					}
				}
				rock = next;
			}
			boolean[][] open = new boolean[N][N];
			for (int x = 0; x < N; x++) {
				for (int z = 0; z < N; z++) {
					open[x][z] = (!rock[x][z] && in(x, z, 1)) || this.keep[x][z] != 0;
				}
			}
			joinCaves(open);
			boolean[][] solid = rock(open);
			for (int i = 0; i < 6; i++) {
				int cx = 4 + this.random.nextInt(N - 8);
				int cz = 4 + this.random.nextInt(N - 8);
				int[][] cross = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
				for (int[] d : cross) {
					if (this.random.nextInt(4) > 0) {
						wet(cx + d[0], cz + d[1]);
					}
				}
			}
			relax(true);
			for (int[] c : poisson(4, 14, (x, z) -> free(x, z, 5))) {
				for (int[] d : disc(c[0], c[1], 4)) {
					double dist = Math.hypot(d[0] - c[0], d[1] - c[1]);
					int height = Math.max(1, (int) Math.round(Math.sqrt(Math.max(0.0, 16.0 - dist * dist))));
					byte code = dist < 1.0 ? BiomeBlocks.BUDDING_AMETHYST : dist < 2.0 ? BiomeBlocks.AMETHYST_BLOCK : dist < 3.0 ? BiomeBlocks.CALCITE : BiomeBlocks.SMOOTH_BASALT;
					if (free(d[0], d[1])) {
						stack(d[0], d[1], code, height);
					}
				}
			}
			for (int[] c : poisson(10, 4, (x, z) -> free(x, z, 1))) {
				stack(c[0], c[1], BiomeBlocks.DRIPSTONE_BLOCK, 2 + this.random.nextInt(2));
			}
			enclose(solid, (x, z) -> this.h[x][z] + 20 + (int) (vnoise(this.seed, x, z, 6, 7) * 8), new byte[] {BiomeBlocks.TUFF, BiomeBlocks.CALCITE, BiomeBlocks.AMETHYST_BLOCK});
		}

		/** Joins every cave of a size to the one the road runs through by a tunnel, and fills the small pockets. */
		private void joinCaves(final boolean[][] open) {
			int[][] label = new int[N][N];
			for (int[] row : label) {
				java.util.Arrays.fill(row, -1);
			}
			List<List<int[]>> caves = new ArrayList<>();
			int[][] ways = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
			for (int x = 0; x < N; x++) {
				for (int z = 0; z < N; z++) {
					if (!open[x][z] || label[x][z] >= 0) {
						continue;
					}
					List<int[]> cells = new ArrayList<>();
					ArrayDeque<int[]> queue = new ArrayDeque<>();
					label[x][z] = caves.size();
					queue.add(new int[] {x, z});
					while (!queue.isEmpty()) {
						int[] at = queue.poll();
						cells.add(at);
						for (int[] way : ways) {
							int nx = at[0] + way[0];
							int nz = at[1] + way[1];
							if (in(nx, nz, 0) && open[nx][nz] && label[nx][nz] < 0) {
								label[nx][nz] = caves.size();
								queue.add(new int[] {nx, nz});
							}
						}
					}
					caves.add(cells);
				}
			}
			int main = label[this.ax][this.az];
			List<int[]> home = caves.get(main);
			for (int i = 0; i < caves.size(); i++) {
				if (i == main) {
					continue;
				}
				List<int[]> cave = caves.get(i);
				if (cave.size() < 30) {
					for (int[] c : cave) {
						if (this.keep[c[0]][c[1]] == 0) {
							open[c[0]][c[1]] = false;
						}
					}
					continue;
				}
				int[] from = cave.get(cave.size() / 2);
				int[] to = home.get(0);
				int best = Integer.MAX_VALUE;
				for (int[] c : home) {
					int d = Math.abs(c[0] - from[0]) + Math.abs(c[1] - from[1]);
					if (d < best) {
						best = d;
						to = c;
					}
				}
				carve(open, from[0], from[1], to[0], to[1]);
			}
		}

		// ---- the sunken catacombs

		void catacombs() {
			boolean[][] open = new boolean[N][N];
			List<int[]> rooms = new ArrayList<>();
			split(2, 2, N - 4, N - 4, 3, rooms);
			for (int x = 0; x < N; x++) {
				for (int z = 0; z < N; z++) {
					open[x][z] = this.keep[x][z] != 0;
				}
			}
			for (int[] room : rooms) {
				for (int x = room[0] + 1; x < room[0] + room[2] - 1; x++) {
					for (int z = room[1] + 1; z < room[1] + room[3] - 1; z++) {
						open[x][z] = true;
					}
				}
			}
			for (int i = 0; i < rooms.size(); i++) {
				int[] mid = {rooms.get(i)[0] + rooms.get(i)[2] / 2, rooms.get(i)[1] + rooms.get(i)[3] / 2};
				int[] to = null;
				int best = Integer.MAX_VALUE;
				if (i > 0) {
					for (int j = 0; j < i; j++) {
						int[] other = {rooms.get(j)[0] + rooms.get(j)[2] / 2, rooms.get(j)[1] + rooms.get(j)[3] / 2};
						int d = Math.abs(other[0] - mid[0]) + Math.abs(other[1] - mid[1]);
						if (d < best) {
							best = d;
							to = other;
						}
					}
				} else {
					for (int[] c : this.spine) {
						int d = Math.abs(c[0] - mid[0]) + Math.abs(c[1] - mid[1]);
						if (d < best) {
							best = d;
							to = c;
						}
					}
				}
				carve(open, mid[0], mid[1], to[0], to[1]);
			}
			// Every room is joined to the one before it; the first to the road, so all are.
			boolean[][] solid = rock(open);
			for (int i = 0; i < rooms.size(); i++) {
				int[] room = rooms.get(i);
				if (i % 3 == 1) {
					for (int x = room[0] + 1; x < room[0] + room[2] - 1; x++) {
						for (int z = room[1] + 1; z < room[1] + room[3] - 1; z++) {
							if (this.keep[x][z] == 0 && !this.tall[x][z]) {
								this.water[x][z] = true;
								this.h[x][z] = -1;
								this.surface[x][z] = BiomeBlocks.MUD;
							}
						}
					}
				} else if (room[2] >= 7 && room[3] >= 7) {
					int cx = room[0] + room[2] / 2;
					int cz = room[1] + room[3] / 2;
					for (int k = -1; k <= 1; k += 2) {
						if (free(cx + k * 2, cz, 1) && free(cx + k * 2 + 1, cz, 1)) {
							stack(cx + k * 2, cz, BiomeBlocks.STONE, 1);
							stack(cx + k * 2 + 1, cz, BiomeBlocks.STONE, 1);
						}
					}
					for (int x = room[0] + 1; x < room[0] + room[2] - 1; x += 2) {
						if (free(x, room[1] + 1)) {
							stack(x, room[1] + 1, BiomeBlocks.BONE_BLOCK, 2);
						}
					}
				}
			}
			for (int i = 0, placed = 0; placed < 6 && i < 300; i++) {
				placed += dent(3 + this.random.nextInt(N - 6), 3 + this.random.nextInt(N - 6)) ? 1 : 0;
			}
			enclose(solid, (x, z) -> 15, new byte[] {BiomeBlocks.STONE_BRICKS, BiomeBlocks.MOSSY_BRICKS, BiomeBlocks.DEEPSLATE_BRICKS});
		}

		/** Splits a region of the room into rooms: along its longer side, near the middle, down to {@code depth}. */
		private void split(final int x0, final int z0, final int w, final int l, final int depth, final List<int[]> rooms) {
			boolean alongX = w >= l;
			int size = alongX ? w : l;
			if (depth == 0 || size < 16) {
				rooms.add(new int[] {x0, z0, w, l});
				return;
			}
			int cut = size * 2 / 5 + this.random.nextInt(size / 5 + 1);
			cut = Math.max(7, Math.min(size - 7, cut));
			if (alongX) {
				split(x0, z0, cut, l, depth - 1, rooms);
				split(x0 + cut, z0, w - cut, l, depth - 1, rooms);
			} else {
				split(x0, z0, w, cut, depth - 1, rooms);
				split(x0, z0 + cut, w, l - cut, depth - 1, rooms);
			}
		}

		// ---- the great hall

		/** The ends of a hall: the arrival at one end of the nave, the portal's stair at the other, on the nave's middle line. */
		void hallEnds() {
			this.facing = this.random.nextBoolean() ? -1 : 1;
			this.ax = this.facing == -1 ? 6 : 57;
			this.px = this.facing == -1 ? 53 : 10;
			this.pz = 31;
			this.az = 31;
			this.foot = this.px + this.facing * (2 + this.steps);
			int a = this.px - this.facing * 3;
			int b = this.px + this.facing * (2 + this.steps + TowerElites.GUARD_DISTANCE_BIOME + 3);
			this.boxX0 = Math.min(a, b) - 1;
			this.boxX1 = Math.max(a, b) + 1;
			this.boxZ0 = this.pz - 4 - this.steps;
			this.boxZ1 = this.pz + 5 + this.steps;
		}

		/** The spine of a hall runs straight down the nave. */
		private List<int[]> nave() {
			List<int[]> line = new ArrayList<>();
			for (int x = this.ax; x != this.foot; x += Integer.signum(this.foot - this.ax)) {
				line.add(new int[] {x, this.pz});
			}
			line.add(new int[] {this.foot, this.pz});
			return line;
		}

		/**
		 * The hall: walls three thick, a vaulted rock roof, two rows of pillars down the nave, pews between them and the
		 * carpet, and a gallery over each aisle, on pillars with a railing. The boss walks the nave; there is no safe room.
		 */
		void hall() {
			boolean[][] open = new boolean[N][N];
			for (int x = 3; x < N - 3; x++) {
				for (int z = 3; z < N - 3; z++) {
					open[x][z] = true;
				}
			}
			boolean[][] solid = rock(open);
			int low = this.facing == -1 ? 12 : this.foot + 6;
			int high = this.facing == -1 ? this.foot - 6 : 52;
			for (int x = low; x <= high; x += 6) {
				for (int z : new int[] {22, 40}) {
					if (free(x, z, 1)) {
						stack(x, z, BiomeBlocks.DEEPSLATE_BRICKS, 22);
					}
				}
			}
			int from = this.facing == -1 ? this.ax + 8 : this.foot + 8;
			int to = this.facing == -1 ? this.foot - 8 : this.ax - 8;
			for (int x = from; x <= to; x++) {
				if ((x - from) % 4 == 3) {
					continue;
				}
				for (int z : new int[] {25, 26, 27, 35, 36, 37}) {
					if (free(x, z)) {
						put(x, this.h[x][z], z, BiomeBlocks.PLANKS);
						this.used[x][z] = true;
					}
				}
			}
			for (int x = 3; x < N - 3; x++) {
				for (int z = 3; z < N - 3; z++) {
					boolean aisle = z <= 18 || z >= 45;
					if (aisle) {
						put(x, 6, z, BiomeBlocks.STONE_BRICKS);
					}
					if ((z == 18 || z == 45) && x >= 4 && x < N - 4) {
						put(x, 7, z, BiomeBlocks.COBBLE_WALL);
					}
				}
			}
			for (int x = 6; x < N - 3; x += 6) {
				for (int z : new int[] {18, 45}) {
					if (free(x, z)) {
						stack(x, z, BiomeBlocks.DEEPSLATE_BRICKS, 6);
					}
				}
			}
			enclose(solid, (x, z) -> 30 - Math.abs(z - 31) / 5, new byte[] {BiomeBlocks.DEEPSLATE_BRICKS, BiomeBlocks.CRACKED_DEEPSLATE});
		}

		// ---- the things the dead left

		/** Scatters the floor's props and the walls', clear of the road, the stair, the pad, the nooks and the water. */
		void props() {
			byte[] pool = BiomeProps.floor(this.biome);
			int floors = 60 + this.random.nextInt(51);
			int barrels = 0;
			for (byte code : this.over) {
				barrels += code == BiomeBlocks.BARREL ? 1 : 0;
			}
			int placed = 0;
			for (int tries = 0; placed < floors && tries < floors * 25; tries++) {
				int x = 2 + this.random.nextInt(N - 4);
				int z = 2 + this.random.nextInt(N - 4);
				if (!free(x, z, 1) || get(x, this.h[x][z], z) != 0) {
					continue;
				}
				byte code = pool[this.random.nextInt(pool.length)];
				if (code == BiomeBlocks.BARREL && barrels++ >= BiomeProps.BARRELS) {
					continue;
				}
				put(x, this.h[x][z], z, code);
				this.used[x][z] = true;
				placed++;
			}
			int walls = 40 + this.random.nextInt(31);
			int hung = 0;
			int[][] ways = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
			Direction[] faces = {Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};
			for (int tries = 0; hung < walls && tries < walls * 150; tries++) {
				int x = 2 + this.random.nextInt(N - 4);
				int z = 2 + this.random.nextInt(N - 4);
				if (this.keep[x][z] != 0 || this.water[x][z] || this.lava[x][z]) {
					continue;
				}
				// Over a body's head, so it never cuts a walk: from the third block above the feet.
				int y = this.h[x][z] + 2 + this.random.nextInt(5);
				int way = this.random.nextInt(4);
				int nx = x + ways[way][0];
				int nz = z + ways[way][1];
				if (y > TowerLayout.ROOM_HEIGHT - 4 || !in(nx, nz, 0) || get(x, y, z) != 0) {
					continue;
				}
				byte beside = get(nx, y, nz);
				boolean wall = BiomeBlocks.walk(beside) == TowerWalk.SOLID && BiomeBlocks.opaque(beside)
					|| this.rockMask != null && this.rockMask[nx][nz] && y >= this.h[nx][nz];
				if (!wall || this.ceil != null && y >= this.ceil[x][z]) {
					continue;
				}
				// A skull is a block a body could stand on, so it hangs four blocks over the feet or more; a cobweb is air to a walk.
				boolean skull = this.random.nextBoolean() && y >= this.h[x][z] + 4;
				put(x, y, z, skull ? BiomeBlocks.wallSkull(faces[way]) : BiomeBlocks.COBWEB);
				hung++;
			}
		}

		// ---- chest nooks

		/** The columns a body reaches from the arrival, in steps, by the ground alone: a coarse stand-in for the walk proof. */
		int[][] steps() {
			int[][] dist = new int[N][N];
			for (int[] row : dist) {
				java.util.Arrays.fill(row, -1);
			}
			ArrayDeque<int[]> queue = new ArrayDeque<>();
			dist[this.ax][this.az] = 0;
			queue.add(new int[] {this.ax, this.az});
			int[][] ways = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
			while (!queue.isEmpty()) {
				int[] at = queue.poll();
				for (int[] way : ways) {
					int nx = at[0] + way[0];
					int nz = at[1] + way[1];
					if (in(nx, nz, 0) && dist[nx][nz] < 0 && !this.tall[nx][nz] && Math.abs(this.h[nx][nz] - this.h[at[0]][at[1]]) <= 1) {
						dist[nx][nz] = dist[at[0]][at[1]] + 1;
						queue.add(new int[] {nx, nz});
					}
				}
			}
			return dist;
		}

		/**
		 * Walled nooks for the double chests: two level columns side by side, a wall of mossy brick two high behind
		 * both, and level ground in front. An elite floor also gets one near the stair's foot, for its guaranteed chest.
		 */
		void nooks() {
			int[][] dist = steps();
			Direction[] faces = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
			List<int[]> candidates = new ArrayList<>();
			for (int x = 2; x < N - 3; x++) {
				for (int z = 2; z < N - 3; z++) {
					for (int f = 0; f < 4; f++) {
						candidates.add(new int[] {x, z, f});
					}
				}
			}
			Collections.shuffle(candidates, this.random);
			if (this.floor % TowerElites.ELITE_EVERY == 0) {
				for (int[] c : candidates) {
					int away = Math.max(Math.abs(c[0] - this.foot), Math.abs(c[1] - this.pz));
					if (away >= 6 && away <= 12 && nookFits(c[0], c[1], faces[c[2]])) {
						buildNook(c[0], c[1], faces[c[2]]);
						break;
					}
				}
			}
			int made = 0;
			for (int[] c : candidates) {
				if (made >= NOOKS) {
					break;
				}
				Direction face = faces[c[2]];
				if (!nookFits(c[0], c[1], face)) {
					continue;
				}
				int sx = c[0] + (face.getAxis() == Direction.Axis.Z ? 1 : 0);
				int sz = c[1] + (face.getAxis() == Direction.Axis.Z ? 0 : 1);
				if (dist[c[0]][c[1]] < NOOK_WALK || dist[sx][sz] < NOOK_WALK) {
					continue;
				}
				boolean spaced = true;
				for (int[] other : this.nooks) {
					spaced &= Math.max(Math.abs(other[0] - c[0]), Math.abs(other[1] - c[1])) >= NOOK_APART;
				}
				if (spaced) {
					buildNook(c[0], c[1], face);
					made++;
				}
			}
		}

		private boolean nookFits(final int x, final int z, final Direction face) {
			int base = this.h[x][z];
			for (int k = 0; k < 2; k++) {
				int cx = x + (face.getAxis() == Direction.Axis.Z ? k : 0);
				int cz = z + (face.getAxis() == Direction.Axis.Z ? 0 : k);
				int bx = cx - face.getStepX();
				int bz = cz - face.getStepZ();
				int fx = cx + face.getStepX();
				int fz = cz + face.getStepZ();
				if (!free(cx, cz, 1) || !in(bx, bz, 1) || !in(fx, fz, 1) || this.h[cx][cz] != base || this.h[fx][fz] != base
					|| this.keep[bx][bz] != 0 || this.used[bx][bz] || this.water[bx][bz] || this.lava[bx][bz] || this.tall[bx][bz] || this.used[fx][fz]
					|| this.keep[fx][fz] != 0 || this.water[fx][fz] || this.lava[fx][fz] || this.tall[fx][fz] || get(cx, base, cz) != 0 || get(fx, base, fz) != 0
					|| this.h[bx][bz] > base + 1) {
					return false;
				}
			}
			return true;
		}

		private void buildNook(final int x, final int z, final Direction face) {
			int base = this.h[x][z];
			for (int k = 0; k < 2; k++) {
				int cx = x + (face.getAxis() == Direction.Axis.Z ? k : 0);
				int cz = z + (face.getAxis() == Direction.Axis.Z ? 0 : k);
				int bx = cx - face.getStepX();
				int bz = cz - face.getStepZ();
				for (int y = this.h[bx][bz]; y <= base + 1; y++) {
					put(bx, y, bz, BiomeBlocks.MOSSY_BRICKS);
				}
				this.tall[bx][bz] = true;
				this.used[bx][bz] = true;
				this.used[cx][cz] = true;
				this.used[cx + face.getStepX()][cz + face.getStepZ()] = true;
			}
			this.nooks.add(new int[] {x, z});
		}

		// ---- light

		/**
		 * Sets a lantern on a fence post wherever a spot a body reaches has none within reach, greedily, the post
		 * standing at the spot nearby that lights the most. The posts change no ground, and the walk is proved again after.
		 */
		void lights(final TowerPlan plan, final TowerVolume volume) {
			int[] dist = TowerWalk.distances(volume, plan.arrivalX(), 0, plan.arrivalZ());
			List<int[]> stands = new ArrayList<>();
			for (int x = 0; x < N; x++) {
				for (int z = 0; z < N; z++) {
					for (int y = TowerWalk.LOW; y <= TowerWalk.HIGH; y++) {
						if (dist[TowerWalk.index(x, y, z)] >= 0) {
							stands.add(new int[] {x, y, z});
						}
					}
				}
			}
			boolean[] lit = new boolean[stands.size()];
			for (int i = 0; i < stands.size(); i++) {
				if (lit[i]) {
					continue;
				}
				int[] spot = stands.get(i);
				int[] best = null;
				int gain = -1;
				for (int pass = 0; pass < 2 && best == null; pass++) {
				for (int reach = 2; reach <= 5 && best == null; reach++) {
					for (int dx = -reach; dx <= reach; dx++) {
						for (int dz = -reach; dz <= reach; dz++) {
							int x = spot[0] + dx;
							int z = spot[2] + dz;
							if (!in(x, z, 2) || this.keep[x][z] != 0 && this.keep[x][z] != 4 && (pass == 0 || this.keep[x][z] == 2) || this.daisBlock[x][z] || this.tall[x][z]
								|| !postable(plan, x, z)) {
								continue;
							}
							// A lamp in the water is a sea lantern at the feet level; on land a lantern on a post.
							int[] lamp = {x, this.h[x][z] + (this.water[x][z] ? 0 : 1), z};
							if (squared(lamp, spot) > LIGHT_REACH_SQUARED) {
								continue;
							}
							int covers = 0;
							for (int j = 0; j < stands.size(); j++) {
								covers += !lit[j] && squared(lamp, stands.get(j)) <= LIGHT_REACH_SQUARED ? 1 : 0;
							}
							if (covers > gain) {
								gain = covers;
								best = lamp;
							}
						}
					}
				}
				}
				if (best == null) {
					continue;
				}
				if (this.water[best[0]][best[2]]) {
					put(best[0], best[1], best[2], BiomeBlocks.SEA_LANTERN);
				} else {
					// Nether brick does not burn beside lava; a soul lantern does not melt the snow.
					put(best[0], best[1] - 1, best[2], this.biome == FloorBiome.BASALT_DELTAS ? BiomeBlocks.NETHER_BRICK_FENCE : BiomeBlocks.FENCE);
					put(best[0], best[1], best[2], this.biome == FloorBiome.ETERNAL_SNOWSTORM ? BiomeBlocks.SOUL_LANTERN : BiomeBlocks.LANTERN);
				}
				this.tall[best[0]][best[2]] = true;
				for (int j = 0; j < stands.size(); j++) {
					lit[j] |= squared(best, stands.get(j)) <= LIGHT_REACH_SQUARED;
				}
			}
		}

		/** Whether a post may stand in a column: free ground, or a floor prop it may replace. */
		private boolean postable(final TowerPlan plan, final int x, final int z) {
			byte there = get(x, this.h[x][z], z);
			return there == 0 ? plan.standY(x, z) == this.h[x][z] : BiomeBlocks.isProp(there) && plan.standY(x, z) == this.h[x][z] + 1;
		}

		private static int squared(final int[] a, final int[] b) {
			int dx = a[0] - b[0];
			int dy = a[1] - b[1];
			int dz = a[2] - b[2];
			return dx * dx + dy * dy + dz * dz;
		}

		// ---- the plan

		BiomeDraft draft() {
			short[][] feet = new short[N][N];
			byte[][] surface = new byte[N][N];
			boolean[][] water = new boolean[N][N];
			boolean[][] lava = new boolean[N][N];
			boolean[][] fragile = new boolean[N][N];
			short[][] ceiling = new short[N][N];
			byte[][] ceilCodes = new byte[N][N];
			for (int x = 0; x < N; x++) {
				for (int z = 0; z < N; z++) {
					feet[x][z] = (short) this.h[x][z];
				}
				surface[x] = this.surface[x].clone();
				water[x] = this.water[x].clone();
				lava[x] = this.lava[x].clone();
				fragile[x] = this.fragile[x].clone();
				if (this.ceil != null) {
					ceiling[x] = this.ceil[x].clone();
					ceilCodes[x] = this.ceilCode[x].clone();
				}
			}
			// Grass dies under a block that shuts out the light, and dirt would spread back: bare dirt under any cover.
			for (int x = 0; x < N; x++) {
				for (int z = 0; z < N; z++) {
					byte above = get(x, this.h[x][z], z);
					// A road turns to dirt under anything solid, grass dies under a block that shuts out the light.
					boolean dies = surface[x][z] == BiomeBlocks.GRASS && BiomeBlocks.opaque(above)
						|| surface[x][z] == BiomeBlocks.PATH && above != BiomeBlocks.NONE && BiomeBlocks.walk(above) != TowerWalk.OPEN;
					if (dies) {
						surface[x][z] = BiomeBlocks.COARSE_DIRT;
					}
				}
			}
			int count = 0;
			for (byte code : this.over) {
				count += code != 0 ? 1 : 0;
			}
			int[] placed = new int[count * 4];
			int i = 0;
			for (int x = 0; x < N; x++) {
				for (int y = OLOW; y < TowerLayout.ROOM_HEIGHT; y++) {
					for (int z = 0; z < N; z++) {
						byte code = this.over[overIndex(x, y, z)];
						if (code != 0) {
							placed[i++] = x;
							placed[i++] = y;
							placed[i++] = z;
							placed[i++] = code;
						}
					}
				}
			}
			return new BiomeDraft(this.biome, feet, surface, water, lava, fragile, this.ceil == null ? null : ceiling, this.ceil == null ? null : ceilCodes, placed, null, this.steps);
		}

		TowerPlan assemble(final int attempt, final BiomeDraft draft) {
			float yaw = (float) Math.toDegrees(Math.atan2(-(this.px - this.ax), this.pz - this.az));
			TowerStory story = TowerStory.of(0, TowerLayout.ROOM_HEIGHT, TowerStory.full(), new boolean[N][N]);
			List<TowerMaze.Zone> zones = this.floor == 1
				? List.of(new TowerMaze.Zone(zoneX(), zoneZ(), 0, TowerLayout.ROOM_HEIGHT)) : List.of();
			return new TowerPlan(this.seed, this.floor, TowerArchetype.BIOME, TowerPlan.Variation.MAZE, 0, attempt, this.ax, this.az, yaw,
				this.px, this.pz, this.facing, 0, new boolean[N][N], List.of(story), List.of(), zones, 0, List.of(), List.of(), null, this.floor, draft);
		}
	}
}
