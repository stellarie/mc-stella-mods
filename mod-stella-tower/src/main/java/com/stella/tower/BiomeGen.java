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
	/** Steps of the portal's stair on an ordinary floor. */
	private static final int STEPS = 5;
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
		FloorBiome biome = FloorBiomes.of(seed, floor);
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
		w.ends();
		w.terrain(features);
		w.prepare();
		if (features) {
			switch (biome) {
				case RAINY_FOREST -> w.rainyForest();
			}
		}
		w.nooks();
		w.clearSpine();
		TowerPlan first = w.assemble(attempt, w.draft());
		TowerVolume volume = TowerVolume.of(first);
		if (!volume.reaches(first)) {
			return null;
		}
		w.lights(first.withStands(volume), volume);
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
			return !this.tall[x][z] && !this.water[x][z];
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
					if (this.keep[x][z] != 0 || this.used[x][z] || this.water[x][z] || this.tall[x][z]) {
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
			this.foot = this.px + this.facing * (2 + STEPS);
			int a = this.px - this.facing * 3;
			// The guard's yard: level ground past the stair's foot, where an elite or a boss stands.
			int b = this.px + this.facing * (2 + STEPS + TowerElites.GUARD_DISTANCE_BIOME + 3);
			this.boxX0 = Math.min(a, b) - 1;
			this.boxX1 = Math.max(a, b) + 1;
			this.boxZ0 = this.pz - 4 - STEPS;
			this.boxZ1 = this.pz + 5 + STEPS;
		}

		void terrain(final boolean features) {
			for (int x = 0; x < N; x++) {
				for (int z = 0; z < N; z++) {
					this.h[x][z] = features ? (int) (fbm(this.seed, x, z, 16) * 3.4) : 0;
					this.surface[x][z] = vnoise(this.seed, x, z, 9, 3) > 0.25 ? BiomeBlocks.GRASS : BiomeBlocks.MUD;
					if (features && vnoise(this.seed, x, z, 7, 5) > 0.82) {
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
			int s = STEPS;
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
				ruin(shrine[0], shrine[1], 5, 5);
			}
			for (int[] camp : camps) {
				int y = this.h[camp[0]][camp[1]];
				put(camp[0], y, camp[1], BiomeBlocks.BARREL);
				put(camp[0] + 1, y, camp[1] + 1, BiomeBlocks.FENCE);
				put(camp[0] + 1, y + 1, camp[1] + 1, BiomeBlocks.LANTERN);
				this.tall[camp[0] + 1][camp[1] + 1] = true;
				for (int x = camp[0]; x < camp[0] + 3; x++) {
					for (int z = camp[1]; z < camp[1] + 3; z++) {
						this.surface[x][z] = BiomeBlocks.DIRT;
					}
				}
			}
			fallenLogs();
			ferns();
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
			int reach = big ? 3 : 2;
			int size = big ? 2 : 1;
			int ground = 0;
			for (int dx = -reach - 1; dx <= reach + size; dx++) {
				for (int dz = -reach - 1; dz <= reach + size; dz++) {
					if (in(x + dx, z + dz, 0)) {
						ground = Math.max(ground, this.h[x + dx][z + dz]);
					}
				}
			}
			int bottom = ground + 3;
			if (bottom + 3 > TowerLayout.ROOM_HEIGHT - 4) {
				return false;
			}
			int kind = big ? 1 : this.random.nextInt(4) == 0 ? 2 : 0;
			byte log = kind == 1 ? BiomeBlocks.DARK_OAK_LOG : kind == 2 ? BiomeBlocks.BIRCH_LOG : BiomeBlocks.OAK_LOG;
			byte leaves = kind == 1 ? BiomeBlocks.DARK_OAK_LEAVES : kind == 2 ? BiomeBlocks.BIRCH_LEAVES : BiomeBlocks.OAK_LEAVES;
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
		void ruin(final int x0, final int z0, final int w, final int l) {
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
					|| this.keep[bx][bz] != 0 || this.used[bx][bz] || this.water[bx][bz] || this.tall[bx][bz] || this.used[fx][fz]
					|| this.keep[fx][fz] != 0 || this.water[fx][fz] || this.tall[fx][fz] || get(cx, base, cz) != 0 || get(fx, base, fz) != 0
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
				for (int reach = 2; reach <= 5 && best == null; reach++) {
					for (int dx = -reach; dx <= reach; dx++) {
						for (int dz = -reach; dz <= reach; dz++) {
							int x = spot[0] + dx;
							int z = spot[2] + dz;
							if (!in(x, z, 2) || this.keep[x][z] != 0 && this.keep[x][z] != 4 || this.daisBlock[x][z] || this.water[x][z] || this.tall[x][z]
								|| plan.standY(x, z) != this.h[x][z] || get(x, this.h[x][z], z) != 0) {
								continue;
							}
							int[] lamp = {x, this.h[x][z] + 1, z};
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
				if (best == null) {
					continue;
				}
				put(best[0], best[1] - 1, best[2], BiomeBlocks.FENCE);
				put(best[0], best[1], best[2], BiomeBlocks.LANTERN);
				this.tall[best[0]][best[2]] = true;
				for (int j = 0; j < stands.size(); j++) {
					lit[j] |= squared(best, stands.get(j)) <= LIGHT_REACH_SQUARED;
				}
			}
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
			for (int x = 0; x < N; x++) {
				for (int z = 0; z < N; z++) {
					feet[x][z] = (short) this.h[x][z];
				}
				surface[x] = this.surface[x].clone();
				water[x] = this.water[x].clone();
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
			return new BiomeDraft(this.biome, feet, surface, water, placed, null, STEPS);
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
