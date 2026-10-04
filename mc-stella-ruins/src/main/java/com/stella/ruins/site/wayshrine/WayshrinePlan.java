package com.stella.ruins.site.wayshrine;

import com.stella.ruins.world.LayoutContext;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The whole shrine in memory, drawn in the local frame with the path on the south edge (local z 0). Local y 0 is
 * the floor course and local y 1 is where a player stands. The plan is complete before any block is written,
 * because decay needs a whole column and the settle pass needs a whole structure. The layout then emits the cells
 * and the context clips them to the chunk box.
 * <p>
 * Every random choice comes from a stream that restarts from the piece seed, in a fixed order, so every chunk
 * draws the same shrine.
 */
public final class WayshrinePlan {
	public static final int SIZE = 21;
	public static final int TOP = 15;
	/** The foundation the site may draw below its floor. It equals the slope the placement allows. */
	public static final int DEPTH = WayshrineSite.SLOPE;
	public static final int CENTER = 10;
	public static final double PLAZA_RADIUS = 8.7;
	public static final double CLEAR_RADIUS = 10.5;
	public static final int LAMP_X = 4;
	public static final int LAMP_Z = 4;
	public static final int LAMP_MIN = 8;
	public static final int LAMP_MAX = 11;
	public static final int DECAY_MIN = 20;
	public static final int DECAY_MAX = 40;
	public static final int VARIANTS = 3;
	public static final int RING = 0;
	public static final int CHAPEL = 1;
	public static final int SUNKEN = 2;
	public static final String SPAWN_PREFIX = "spawn.";
	public static final String LAMP = "lamp";
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final long SALT_DECAY = 1L;
	private static final long SALT_FLOOR = 2L;
	private static final long SALT_LINTEL = 3L;
	private static final long SALT_WEATHER = 4L;
	private static final byte GROUP_BODY = 1;
	private static final byte GROUP_LINTEL = 2;
	private static final int[][] RING_PILLARS = {{8, 3}, {12, 3}, {17, 8}, {17, 12}, {12, 17}, {8, 17}, {3, 12}, {3, 8}};
	private static final int[][] STUMPS = {{2, 10, 2}, {18, 10, 3}, {3, 15, 1}, {17, 15, 2}, {3, 5, 3}, {17, 5, 2}, {7, 17, 1}, {13, 17, 3}};

	/** The rolls that shape one shrine. */
	public record Params(int variant, int palette, int decayPercent, int candles, int lampHeight) {
		public static Params roll(final RandomSource random) {
			int variant = random.nextInt(VARIANTS);
			int palette = random.nextInt(WayshrinePalette.ALL.size());
			int decay = DECAY_MIN + random.nextInt(DECAY_MAX - DECAY_MIN + 1);
			int candles = 2 + random.nextInt(3);
			int lamp = LAMP_MIN + random.nextInt(LAMP_MAX - LAMP_MIN + 1);
			return new Params(variant, palette, decay, candles, lamp);
		}
	}

	/** A named spot in the local frame. */
	public record Marker(String name, int x, int y, int z) {
		public BlockPos local() {
			return new BlockPos(this.x, this.y, this.z);
		}
	}

	private final Params params;
	private final WayshrinePalette palette;
	private final BlockState[] cells = new BlockState[SIZE * SIZE * (TOP + DEPTH + 1)];
	private final byte[] group = new byte[this.cells.length];
	private final boolean[] hit = new boolean[SIZE * SIZE];
	private final List<Marker> markers = new ArrayList<>();
	private final RandomSource decayRandom;
	private final RandomSource floorRandom;
	private final RandomSource lintelRandom;
	private final RandomSource weatherRandom;
	private BlockPos core;
	private BlockPos vault;
	private BlockPos lamp;
	private int decayCandidates;
	private int mossy;
	private int cracked;
	private int air;
	private int pruned;

	/**
	 * Draws the plan. {@code main} is the piece's own stream and {@code salted} gives the extra streams; both restart
	 * from the piece seed on every call.
	 */
	public WayshrinePlan(final RandomSource main, final java.util.function.LongFunction<RandomSource> salted) {
		this.params = Params.roll(main);
		this.palette = WayshrinePalette.ALL.get(this.params.palette());
		this.decayRandom = salted.apply(SALT_DECAY);
		this.floorRandom = salted.apply(SALT_FLOOR);
		this.lintelRandom = salted.apply(SALT_LINTEL);
		this.weatherRandom = salted.apply(SALT_WEATHER);
		clear();
		switch (this.params.variant()) {
			case RING -> ring(main);
			case CHAPEL -> chapel(main);
			default -> sunken(main);
		}
		obelisk();
		weatherFloor();
	}

	public Params params() {
		return this.params;
	}

	WayshrinePalette palette() {
		return this.palette;
	}

	public List<Marker> markers() {
		return List.copyOf(this.markers);
	}

	public BlockPos core() {
		return this.core;
	}

	public BlockPos vault() {
		return this.vault;
	}

	public BlockPos lamp() {
		return this.lamp;
	}

	/** How many blocks of the decay-flagged kind the plan started with. */
	public int decayCandidates() {
		return this.decayCandidates;
	}

	public int mossy() {
		return this.mossy;
	}

	public int cracked() {
		return this.cracked;
	}

	public int air() {
		return this.air;
	}

	public int pruned() {
		return this.pruned;
	}

	/** True when a column lost blocks to an air hit or to the settle pass. */
	public boolean removedAt(final int x, final int z) {
		return this.hit[z * SIZE + x];
	}

	/** The planned block, or null where the plan leaves the world alone. */
	public BlockState at(final int x, final int y, final int z) {
		return inside(x, y, z) ? this.cells[index(x, y, z)] : null;
	}

	/** The blocks a context emits, in local spots. A null cell is skipped. */
	public void emit(final LayoutContext ctx) {
		for (int y = -DEPTH; y <= TOP; y++) {
			for (int z = 0; z < SIZE; z++) {
				for (int x = 0; x < SIZE; x++) {
					BlockState state = this.cells[index(x, y, z)];
					if (state != null) {
						ctx.set(x, y, z, state);
					}
				}
			}
		}
		ctx.placeCore(this.core.getX(), this.core.getY(), this.core.getZ());
		ctx.placeVault(this.vault.getX(), this.vault.getY(), this.vault.getZ());
		for (Marker marker : this.markers) {
			ctx.marker(marker.name(), marker.x(), marker.y(), marker.z());
		}
	}

	private static boolean inside(final int x, final int y, final int z) {
		return x >= 0 && x < SIZE && z >= 0 && z < SIZE && y >= -DEPTH && y <= TOP;
	}

	private static int index(final int x, final int y, final int z) {
		return ((y + DEPTH) * SIZE + z) * SIZE + x;
	}

	private void set(final int x, final int y, final int z, final BlockState state, final int flag) {
		if (!inside(x, y, z)) {
			throw new IllegalStateException("the plan wrote outside its box at " + x + "," + y + "," + z);
		}
		int i = index(x, y, z);
		this.cells[i] = state;
		this.group[i] = (byte) flag;
	}

	private void place(final int x, final int y, final int z, final BlockState state) {
		set(x, y, z, state, 0);
	}

	private void decayable(final int x, final int y, final int z, final BlockState state) {
		set(x, y, z, state, GROUP_BODY);
	}

	private void marker(final String name, final int x, final int y, final int z) {
		this.markers.add(new Marker(name, x, y, z));
	}

	private static double distance(final int x, final int z) {
		return Math.hypot(x - CENTER, z - CENTER);
	}

	/** Sweeps the air above the floor, so trees and snow do not stand in the shrine. */
	private void clear() {
		for (int z = 0; z < SIZE; z++) {
			for (int x = 0; x < SIZE; x++) {
				if (distance(x, z) <= CLEAR_RADIUS) {
					for (int y = 0; y <= TOP; y++) {
						place(x, y, z, AIR);
					}
				}
			}
		}
	}

	private void foundationAt(final int x, final int z) {
		for (int y = -DEPTH; y < 0; y++) {
			place(x, y, z, this.palette.foundation());
		}
	}

	private void floorAt(final int x, final int z, final BlockState course) {
		foundationAt(x, z);
		place(x, 0, z, course);
	}

	private void plaza() {
		for (int z = 0; z < SIZE; z++) {
			for (int x = 0; x < SIZE; x++) {
				if (distance(x, z) <= PLAZA_RADIUS) {
					floorAt(x, z, this.palette.floor());
				}
			}
		}
	}

	private void pathStones() {
		for (int z = 0; z <= 1; z++) {
			for (int x = 9; x <= 11; x++) {
				floorAt(x, z, Blocks.DIRT_PATH.defaultBlockState());
			}
		}
	}

	/** A 3x3 dais, the core on it, and two to four lit candles on its corners. */
	private void altar(final RandomSource random, final int baseY) {
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				place(CENTER + dx, baseY, CENTER + dz, this.palette.accent());
			}
		}
		this.core = new BlockPos(CENTER, baseY + 1, CENTER);
		List<int[]> corners = new ArrayList<>(List.of(new int[] {9, 9}, new int[] {11, 9}, new int[] {9, 11}, new int[] {11, 11}));
		for (int i = corners.size() - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			int[] swap = corners.get(i);
			corners.set(i, corners.get(j));
			corners.set(j, swap);
		}
		BlockState candle = Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true);
		for (int i = 0; i < this.params.candles(); i++) {
			place(corners.get(i)[0], baseY + 1, corners.get(i)[1], candle);
		}
	}

	private void vaultAt(final int x, final int y, final int z) {
		this.vault = new BlockPos(x, y, z);
	}

	private BlockState stairs(final Direction ascends) {
		return this.palette.stairs().setValue(StairBlock.FACING, ascends);
	}

	/** A ring of eight pillars, a gate pair at each end, and a reliquary on the north side that holds the vault. */
	private void ring(final RandomSource random) {
		plaza();
		pathStones();
		for (int z = 0; z < SIZE; z++) {
			for (int x = 0; x < SIZE; x++) {
				double d = distance(x, z);
				if (d >= 4.5 && d <= 5.5 && this.cells[index(x, 0, z)] == this.palette.floor()) {
					place(x, 0, z, this.palette.accent());
				}
			}
		}
		for (int z = 18; z <= 19; z++) {
			for (int x = 9; x <= 11; x++) {
				floorAt(x, z, this.palette.floor());
			}
		}
		altar(random, 1);
		for (int[] pillar : RING_PILLARS) {
			decayable(pillar[0], 1, pillar[1], this.palette.accent());
			for (int y = 2; y <= 6; y++) {
				decayable(pillar[0], y, pillar[1], this.palette.shaft());
			}
			decayable(pillar[0], 7, pillar[1], this.palette.accent());
		}
		for (int[] wall : new int[][] {{9, 18}, {9, 19}, {11, 18}, {11, 19}, {10, 19}}) {
			for (int y = 1; y <= 3; y++) {
				decayable(wall[0], y, wall[1], this.palette.wall());
			}
		}
		for (int z = 18; z <= 19; z++) {
			for (int x = 9; x <= 11; x++) {
				decayable(x, 4, z, this.palette.slab());
			}
		}
		vaultAt(10, 1, 18);
		decay(GROUP_BODY);
		for (int g = 0; g < 8; g++) {
			int[] a = RING_PILLARS[g];
			int[] b = RING_PILLARS[(g + 1) % 8];
			boolean attempt = g == 0 || this.lintelRandom.nextInt(100) < 45;
			if (attempt && this.cells[index(a[0], 7, a[1])] != null && !this.cells[index(a[0], 7, a[1])].isAir()
				&& this.cells[index(b[0], 7, b[1])] != null && !this.cells[index(b[0], 7, b[1])].isAir()) {
				int steps = Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]));
				for (int i = 1; i < steps; i++) {
					int x = (int) Math.round(a[0] + (b[0] - a[0]) * (double) i / steps);
					int z = (int) Math.round(a[1] + (b[1] - a[1]) * (double) i / steps);
					set(x, 7, z, this.palette.wall(), GROUP_LINTEL);
				}
			}
		}
		decay(GROUP_LINTEL);
		settle();
		spawn(new int[][] {{6, 6}, {14, 6}, {6, 14}, {14, 14}}, 1, new int[][] {{4, 10}, {16, 10}, {10, 5}}, 5, 10, 15, 1);
	}

	/** A roadside chapel shaped like a cross: nave, transept, an apse with the vault, and a gabled door. */
	private void chapel(final RandomSource random) {
		for (int z = 0; z < SIZE; z++) {
			for (int x = 0; x < SIZE; x++) {
				if (inChapel(x, z)) {
					floorAt(x, z, this.palette.floor());
				}
			}
		}
		pathStones();
		for (int x = 9; x <= 11; x++) {
			floorAt(x, 2, Blocks.DIRT_PATH.defaultBlockState());
		}
		for (int z = 3; z <= 12; z++) {
			if (this.cells[index(10, 0, z)] == this.palette.floor()) {
				place(10, 0, z, this.palette.accent());
			}
		}
		for (int z = 3; z <= 5; z++) {
			for (int x = 3; x <= 5; x++) {
				floorAt(x, z, this.palette.floor());
			}
		}
		for (int z = 0; z < SIZE; z++) {
			for (int x = 0; x < SIZE; x++) {
				if (!chapelWall(x, z)) {
					continue;
				}
				int top = z == 18 && x >= 7 && x <= 13 ? 7 : 6;
				for (int y = 1; y <= top; y++) {
					boolean door = z == 2 && x >= 9 && x <= 11 && y <= 5;
					boolean naveWindow = (x == 7 || x == 13) && (z == 5 || z == 15) && (y == 3 || y == 4);
					boolean transeptWindow = (z == 7 || z == 13) && (x == 4 || x == 16) && (y == 3 || y == 4);
					boolean endWindow = (x == 1 || x == 19) && z == 10 && y >= 3 && y <= 5;
					if (!door && !naveWindow && !transeptWindow && !endWindow) {
						decayable(x, y, z, this.palette.wall());
					}
				}
			}
		}
		for (int x = 8; x <= 12; x++) {
			decayable(x, 7, 2, this.palette.wall());
		}
		for (int x = 9; x <= 11; x++) {
			decayable(x, 8, 2, this.palette.wall());
		}
		decayable(10, 9, 2, this.palette.accent());
		for (int z = 14; z <= 17; z++) {
			for (int x = 7; x <= 13; x++) {
				decayable(x, 7, z, this.palette.slab());
			}
		}
		for (int x = 8; x <= 12; x++) {
			decayable(x, 6, 5, this.palette.wall());
		}
		for (int px : new int[] {8, 9, 11, 12}) {
			for (int pz : new int[] {4, 6}) {
				decayable(px, 1, pz, stairs(Direction.SOUTH));
			}
		}
		altar(random, 1);
		vaultAt(10, 1, 17);
		decay(GROUP_BODY);
		settle();
		spawn(new int[][] {{3, 10}, {17, 10}, {8, 5}, {12, 5}}, 1, new int[][] {{5, 10}, {15, 10}, {10, 6}}, 5, 10, 15, 1);
	}

	private static boolean inChapel(final int x, final int z) {
		return inNave(x, z) || inTranssept(x, z);
	}

	private static boolean inNave(final int x, final int z) {
		return x >= 7 && x <= 13 && z >= 2 && z <= 18;
	}

	private static boolean inTranssept(final int x, final int z) {
		return x >= 1 && x <= 19 && z >= 7 && z <= 13;
	}

	private static boolean chapelWall(final int x, final int z) {
		if (!inChapel(x, z)) {
			return false;
		}
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				if (!inChapel(x + dx, z + dz)) {
					return true;
				}
			}
		}
		return false;
	}

	/** An altar in a pit three blocks down, three steps to reach it, and the vault set into the far wall. */
	private void sunken(final RandomSource random) {
		for (int z = 0; z < SIZE; z++) {
			for (int x = 0; x < SIZE; x++) {
				if (distance(x, z) > PLAZA_RADIUS) {
					continue;
				}
				int ring = Math.max(Math.abs(x - CENTER), Math.abs(z - CENTER));
				if (ring <= 4) {
					for (int y = -DEPTH; y <= -4; y++) {
						place(x, y, z, this.palette.foundation());
					}
					place(x, -3, z, this.palette.floor());
					for (int y = -2; y <= 0; y++) {
						place(x, y, z, AIR);
					}
				} else if (ring == 5) {
					foundationAt(x, z);
					for (int y = -3; y <= 0; y++) {
						place(x, y, z, this.palette.wall());
					}
					decayable(x, 1, z, this.palette.wall());
				} else {
					floorAt(x, z, this.palette.floor());
				}
			}
		}
		pathStones();
		for (int z = 0; z < SIZE; z++) {
			for (int x = 0; x < SIZE; x++) {
				if (distance(x, z) <= PLAZA_RADIUS && Math.max(Math.abs(x - CENTER), Math.abs(z - CENTER)) == 3) {
					place(x, -3, z, this.palette.accent());
				}
			}
		}
		for (int x = 9; x <= 11; x++) {
			place(x, -1, 5, stairs(Direction.SOUTH));
			place(x, 0, 5, AIR);
			place(x, 1, 5, AIR);
			place(x, -2, 6, stairs(Direction.SOUTH));
		}
		for (int[] corner : new int[][] {{5, 5}, {15, 5}, {5, 15}, {15, 15}}) {
			for (int y = 2; y <= 6; y++) {
				decayable(corner[0], y, corner[1], this.palette.shaft());
			}
			decayable(corner[0], 7, corner[1], this.palette.accent());
		}
		for (int[] stump : STUMPS) {
			for (int y = 1; y <= stump[2]; y++) {
				decayable(stump[0], y, stump[1], this.palette.wall());
			}
		}
		altar(random, -2);
		vaultAt(10, -2, 15);
		decay(GROUP_BODY);
		settle();
		spawn(new int[][] {{4, 10}, {16, 10}, {7, 3}, {13, 3}}, 1, new int[][] {{6, 6}, {14, 6}, {10, 14}}, 3, 10, 13, -2);
	}

	/** Names the spawn spots. Each kind has its own markers. */
	private void spawn(final int[][] pilgrims, final int pilgrimY, final int[][] wisps, final int wispY, final int acolyteX, final int acolyteZ, final int acolyteY) {
		for (int[] spot : pilgrims) {
			marker(SPAWN_PREFIX + "hollow_pilgrim", spot[0], pilgrimY, spot[1]);
		}
		for (int[] spot : wisps) {
			marker(SPAWN_PREFIX + "lantern_wisp", spot[0], wispY, spot[1]);
		}
		marker(SPAWN_PREFIX + "fallen_acolyte", acolyteX, acolyteY, acolyteZ);
	}

	/** The landmark: a plinth, a shaft, a capital and a shrine lamp, between 8 and 11 blocks above the floor course. */
	private void obelisk() {
		int top = this.params.lampHeight();
		place(LAMP_X, 1, LAMP_Z, this.palette.accent());
		for (int y = 2; y <= top - 2; y++) {
			int roll = this.weatherRandom.nextInt(100);
			BlockState block = roll < 20 ? this.palette.wallMossy() : roll < 40 ? this.palette.wallCracked() : this.palette.shaft();
			place(LAMP_X, y, LAMP_Z, block);
		}
		place(LAMP_X, top - 1, LAMP_Z, this.palette.accent());
		place(LAMP_X, top, LAMP_Z, WayshrineLamp.LAMP.defaultBlockState());
		this.lamp = new BlockPos(LAMP_X, top, LAMP_Z);
		marker(LAMP, LAMP_X, top, LAMP_Z);
	}

	/** A hit becomes a mossy block, a cracked block, or air, one third each. */
	private void decay(final int which) {
		for (int y = -DEPTH; y <= TOP; y++) {
			for (int z = 0; z < SIZE; z++) {
				for (int x = 0; x < SIZE; x++) {
					int i = index(x, y, z);
					if (this.group[i] != which || this.cells[i] == null || this.cells[i].isAir()) {
						continue;
					}
					this.decayCandidates++;
					if (this.decayRandom.nextInt(100) >= this.params.decayPercent()) {
						continue;
					}
					switch (this.decayRandom.nextInt(3)) {
						case 0 -> {
							this.cells[i] = this.palette.wallMossy();
							this.mossy++;
						}
						case 1 -> {
							this.cells[i] = this.palette.wallCracked();
							this.cracked++;
						}
						default -> {
							this.cells[i] = AIR;
							this.air++;
							this.hit[z * SIZE + x] = true;
						}
					}
				}
			}
		}
	}

	/** Removes every decay-flagged block that no longer touches the floor through solid blocks. */
	private void settle() {
		boolean[] seen = new boolean[this.cells.length];
		java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
		for (int y = -DEPTH; y <= 0; y++) {
			for (int z = 0; z < SIZE; z++) {
				for (int x = 0; x < SIZE; x++) {
					int i = index(x, y, z);
					if (solid(i)) {
						seen[i] = true;
						queue.add(new int[] {x, y, z});
					}
				}
			}
		}
		int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		while (!queue.isEmpty()) {
			int[] at = queue.poll();
			for (int[] step : steps) {
				int nx = at[0] + step[0];
				int ny = at[1] + step[1];
				int nz = at[2] + step[2];
				if (!inside(nx, ny, nz)) {
					continue;
				}
				int j = index(nx, ny, nz);
				if (!seen[j] && solid(j)) {
					seen[j] = true;
					queue.add(new int[] {nx, ny, nz});
				}
			}
		}
		for (int y = 1; y <= TOP; y++) {
			for (int z = 0; z < SIZE; z++) {
				for (int x = 0; x < SIZE; x++) {
					int i = index(x, y, z);
					if (this.group[i] != 0 && solid(i) && !seen[i]) {
						this.cells[i] = AIR;
						this.pruned++;
						this.hit[z * SIZE + x] = true;
					}
				}
			}
		}
	}

	private boolean solid(final int i) {
		return this.cells[i] != null && !this.cells[i].isAir();
	}

	/** Cracks and moss on the floor at half the decay rate each. No air: the floor stays whole. */
	private void weatherFloor() {
		int half = this.params.decayPercent();
		for (int y : new int[] {-3, 0}) {
			for (int z = 0; z < SIZE; z++) {
				for (int x = 0; x < SIZE; x++) {
					int i = index(x, y, z);
					if (this.cells[i] != this.palette.floor()) {
						continue;
					}
					int roll = this.floorRandom.nextInt(200);
					if (roll < half) {
						this.cells[i] = this.palette.floorCracked();
					} else if (roll < half * 2) {
						this.cells[i] = this.palette.floorMossy();
					}
				}
			}
		}
	}

	/** The marker names for tests and for the objective. */
	public static String spawnMarker(final String kindId) {
		return SPAWN_PREFIX + kindId;
	}
}
