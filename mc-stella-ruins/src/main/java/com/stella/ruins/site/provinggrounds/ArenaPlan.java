package com.stella.ruins.site.provinggrounds;

import com.stella.ruins.world.LayoutContext;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * The whole arena in memory, drawn in the local frame with the main gate on the south edge (local z 0). Local y 0 is
 * the sand course and local y 1 is where a player stands. Local z grows toward the champion's loge.
 * <p>
 * The plan is complete before any block is written, because decay needs a whole column and the settle pass needs a
 * whole structure. The layout then emits the cells and the context clips them to the chunk box. Every random choice
 * comes from a stream that restarts from the piece seed, in a fixed order, so every chunk draws the same arena.
 * <p>
 * Rings, counted from the center by distance: the floor out to {@link Params#radius()}, then the wall, the lower
 * stand and the upper stand, one ring each.
 */
public final class ArenaPlan {
	public static final int SIZE = 48;
	/** The cells in use run 0 to 46, so that cell 23 is a true center. Column 47 and row 47 stay empty. */
	public static final int CELLS = 47;
	public static final int CENTER = 23;
	public static final int TOP = 16;
	public static final int DEPTH = ProvingGroundsSite.SLOPE;
	public static final int RADIUS_MIN = 16;
	public static final int RADIUS_MAX = 20;
	public static final int DECAY_MIN = 20;
	public static final int DECAY_MAX = 50;
	public static final int PILLARS_MIN = 4;
	public static final int PILLARS_MAX = 8;
	public static final int GATES_MIN = 2;
	public static final int GATES_MAX = 4;
	public static final int FEATURES_MIN = 1;
	public static final int FEATURES_MAX = 3;
	public static final int GRATES = 2;
	/** The least distance between the low corners of two cover pillars. */
	public static final double PILLAR_SPACING = 5.0;
	public static final int BRAZIERS = 4;
	public static final int SHAPE_ROUND = 0;
	public static final int SHAPE_OCTAGON = 1;
	public static final int FEATURE_RUBBLE = 0;
	public static final int FEATURE_COLUMN = 1;
	public static final int FEATURE_RING = 2;
	public static final int FEATURE_KINDS = 3;
	public static final int STAGE_LEFT = 19;
	public static final int STAGE_RIGHT = 27;
	public static final int PIT_X0 = 20;
	public static final int PIT_X1 = 26;
	public static final int PIT_Z0 = 22;
	public static final int PIT_Z1 = 24;
	public static final int LOGE_X0 = 20;
	public static final int LOGE_X1 = 26;
	public static final int LECTERN_Y = 2;
	public static final String LECTERN = "lectern.";
	public static final String GONG = "gong";
	public static final String PORTCULLIS = "portcullis.main";
	public static final String BRAZIER = "brazier";
	public static final String GRATE = "grate.";
	public static final String SPAWN_PREFIX = "spawn.";
	/** The compass slots, as unit steps: S, SE, E, NE, N, NW, W, SW. The south slot is the main gate. */
	static final double[][] SLOTS = unitSlots();
	static final String[] SLOT_NAMES = {"S", "SE", "E", "NE", "N", "NW", "W", "SW"};
	/** Slots an extra gate may take: never the main gate, never the loge. */
	private static final int[] EXTRA_SLOTS = {1, 2, 3, 5, 6, 7};
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState BRICKS = Blocks.STONE_BRICKS.defaultBlockState();
	private static final BlockState CRACKED = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
	private static final BlockState MOSSY = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
	private static final BlockState CHISELED = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
	private static final BlockState SEAT = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
	private static final BlockState SAND = Blocks.SAND.defaultBlockState();
	private static final BlockState COARSE = Blocks.COARSE_DIRT.defaultBlockState();
	private static final BlockState FOUNDATION = Blocks.COBBLESTONE.defaultBlockState();
	private static final BlockState LOG = Blocks.DARK_OAK_LOG.defaultBlockState();
	private static final long SALT_DECAY = 1L;
	private static final long SALT_SCUFF = 2L;
	private static final long SALT_FEATURE = 3L;
	private static final byte DECAY = 1;

	/** The rolls that shape one arena. */
	public record Params(int shape, int radius, int decayPercent, int pillars, int gates, int mainDye, int trimDye, int features) {
		public static Params roll(final RandomSource random) {
			int shape = random.nextInt(2);
			int radius = RADIUS_MIN + random.nextInt(RADIUS_MAX - RADIUS_MIN + 1);
			int decay = DECAY_MIN + random.nextInt(DECAY_MAX - DECAY_MIN + 1);
			int pillars = PILLARS_MIN + random.nextInt(PILLARS_MAX - PILLARS_MIN + 1);
			int gates = GATES_MIN + random.nextInt(GATES_MAX - GATES_MIN + 1);
			int main = random.nextInt(DyeColor.values().length);
			int trim = random.nextInt(DyeColor.values().length - 1);
			if (trim >= main) {
				trim++;
			}
			int features = FEATURES_MIN + random.nextInt(FEATURES_MAX - FEATURES_MIN + 1);
			return new Params(shape, radius, decay, pillars, gates, main, trim, features);
		}
	}

	/** A named spot in the local frame. */
	public record Marker(String name, int x, int y, int z) {
		public BlockPos local() {
			return new BlockPos(this.x, this.y, this.z);
		}
	}

	/** A round footprint a placement keeps clear. */
	private record Blocker(double x, double z, double radius) {
	}

	/** One floor feature: its kind and where it stands. */
	public record Feature(int kind, int x, int z) {
	}

	/** One cover pillar: the low corner of its 2 by 2 footprint, and its height. */
	public record Pillar(int x, int z, int height) {
	}

	private final Params params;
	private final BlockState[] cells = new BlockState[SIZE * SIZE * (TOP + DEPTH + 1)];
	private final byte[] flags = new byte[this.cells.length];
	private final boolean[] hit = new boolean[SIZE * SIZE];
	private final List<Marker> markers = new ArrayList<>();
	private final List<Integer> gateSlots = new ArrayList<>();
	private final List<Pillar> pillars = new ArrayList<>();
	private final List<Feature> features = new ArrayList<>();
	private final List<int[]> cages = new ArrayList<>();
	private final List<int[][]> grates = new ArrayList<>();
	private final List<int[]> towers = new ArrayList<>();
	private final List<Blocker> blockers = new ArrayList<>();
	private final RandomSource main;
	private final RandomSource decayRandom;
	private final RandomSource scuffRandom;
	private final RandomSource featureRandom;
	private final BlockPos core;
	private final BlockPos vault;
	private int decayCandidates;
	private int mossy;
	private int cracked;
	private int air;
	private int pruned;

	/**
	 * Draws the plan. {@code main} is the piece's own stream and {@code salted} gives the extra streams; both restart
	 * from the piece seed on every call.
	 */
	public ArenaPlan(final RandomSource main, final LongFunction<RandomSource> salted) {
		this.main = main;
		this.params = Params.roll(main);
		this.decayRandom = salted.apply(SALT_DECAY);
		this.scuffRandom = salted.apply(SALT_SCUFF);
		this.featureRandom = salted.apply(SALT_FEATURE);
		int r = this.params.radius();
		this.core = new BlockPos(CENTER, LECTERN_Y, CENTER - (r - 3));
		this.vault = new BlockPos(CENTER, 5, CENTER + r + 2);
		clear();
		rings();
		pit();
		stage();
		pickGates();
		this.gateSlots.forEach(slot -> carveGate(slot, slot == 0));
		this.gateSlots.forEach(slot -> raiseTowers(slot, slot == 0));
		gong();
		loge();
		cages();
		spawnMarkers();
		braziers();
		pillars();
		grates();
		features();
		decay();
		prune();
		scuff();
		connectBars();
		retiariusMarkers();
	}

	private static double[][] unitSlots() {
		double[][] slots = new double[8][];
		int[][] raw = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};
		for (int i = 0; i < 8; i++) {
			double length = Math.hypot(raw[i][0], raw[i][1]);
			slots[i] = new double[] {raw[i][0] / length, raw[i][1] / length};
		}
		return slots;
	}

	public Params params() {
		return this.params;
	}

	public List<Marker> markers() {
		return List.copyOf(this.markers);
	}

	public List<Marker> markers(final String name) {
		return this.markers.stream().filter(marker -> marker.name().equals(name)).toList();
	}

	public BlockPos core() {
		return this.core;
	}

	public BlockPos vault() {
		return this.vault;
	}

	/** The gate slots drawn: slot 0 is the main gate on the south side. */
	public List<Integer> gateSlots() {
		return List.copyOf(this.gateSlots);
	}

	public List<Pillar> pillarList() {
		return List.copyOf(this.pillars);
	}

	public List<Feature> featureList() {
		return List.copyOf(this.features);
	}

	/** The hound cages' center cells, in local x and z. */
	public List<int[]> cageCenters() {
		return List.copyOf(this.cages);
	}

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

	/** True when the column lost blocks to an air hit or to the settle pass. */
	public boolean hit(final int x, final int z) {
		return this.hit[z * SIZE + x];
	}

	/** The state at a local cell, or null where the plan draws nothing. */
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
		this.flags[i] = (byte) flag;
	}

	private void place(final int x, final int y, final int z, final BlockState state) {
		set(x, y, z, state, 0);
	}

	/** A block that decay may take, from local y 2 up. Lower blocks never decay, so the stand ring stays readable. */
	private void decayable(final int x, final int y, final int z, final BlockState state) {
		set(x, y, z, state, y >= 2 ? DECAY : 0);
	}

	private void marker(final String name, final int x, final int y, final int z) {
		this.markers.add(new Marker(name, x, y, z));
	}

	/** The ring a cell lies in: its distance from the center, rounded. */
	public int ring(final int x, final int z) {
		return ring(this.params, x, z);
	}

	static int ring(final Params params, final int x, final int z) {
		return (int) Math.round(metric(params.shape(), x - CENTER, z - CENTER));
	}

	static double metric(final int shape, final double dx, final double dz) {
		double ax = Math.abs(dx);
		double az = Math.abs(dz);
		return shape == SHAPE_ROUND ? Math.hypot(dx, dz) : Math.max(Math.max(ax, az), (ax + az) / Math.sqrt(2.0));
	}

	private int radius() {
		return this.params.radius();
	}

	/** Sweeps the air above the floor, so trees and snow do not stand in the arena. */
	private void clear() {
		for (int z = 0; z < CELLS; z++) {
			for (int x = 0; x < CELLS; x++) {
				if (ring(x, z) <= radius() + 3) {
					for (int y = 1; y <= TOP; y++) {
						place(x, y, z, AIR);
					}
				}
			}
		}
	}

	private void rings() {
		int r = radius();
		for (int z = 0; z < CELLS; z++) {
			for (int x = 0; x < CELLS; x++) {
				int f = ring(x, z);
				if (f > r + 3) {
					continue;
				}
				for (int y = -DEPTH; y <= -1; y++) {
					place(x, y, z, FOUNDATION);
				}
				if (f <= r) {
					place(x, 0, z, SAND);
					continue;
				}
				place(x, 0, z, FOUNDATION);
				if (f == r + 1) {
					// The wall keeps its low two courses, so no mob walks through it.
					place(x, 1, z, BRICKS);
					place(x, 2, z, BRICKS);
					decayable(x, 3, z, BRICKS);
				} else if (f == r + 2) {
					for (int y = 1; y <= 3; y++) {
						decayable(x, y, z, BRICKS);
					}
					decayable(x, 4, z, SEAT);
				} else {
					for (int y = 1; y <= 5; y++) {
						decayable(x, y, z, BRICKS);
					}
					decayable(x, 6, z, SEAT);
					if ((x + z) % 2 == 0) {
						decayable(x, 7, z, BRICKS);
					}
				}
			}
		}
	}

	private void pit() {
		for (int z = PIT_Z0; z <= PIT_Z1; z++) {
			for (int x = PIT_X0; x <= PIT_X1; x++) {
				place(x, -3, z, Blocks.BLACKSTONE.defaultBlockState());
				place(x, -2, z, AIR);
				place(x, -1, z, AIR);
				place(x, 0, z, Blocks.IRON_TRAPDOOR.defaultBlockState());
			}
		}
		marker(SPAWN_PREFIX + GroundsMobs.PIT_BEAST, CENTER, 1, CENTER);
	}

	private void stage() {
		int r = radius();
		int za = CENTER - (r - 1);
		int zb = CENTER - (r - 2);
		int zc = CENTER - (r - 3);
		BlockState up = stairs(Blocks.STONE_BRICK_STAIRS.defaultBlockState(), Direction.SOUTH);
		for (int x = STAGE_LEFT; x <= STAGE_RIGHT; x++) {
			place(x, 1, za, up);
			place(x, 1, zb, BRICKS);
			place(x, 1, zc, BRICKS);
		}
		int[] lecternX = {STAGE_LEFT, CENTER, STAGE_RIGHT};
		for (int i = 0; i < 3; i++) {
			place(lecternX[i], LECTERN_Y, zb, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.NORTH));
			marker(LECTERN + (i + 1), lecternX[i], LECTERN_Y, zb);
		}
		place(this.core.getX(), this.core.getY(), this.core.getZ(), AIR);
		blockers.add(new Blocker(CENTER, CENTER - r + 2, 6.0));
	}

	private static BlockState stairs(final BlockState state, final Direction facing) {
		return state.setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM);
	}

	private void pickGates() {
		this.gateSlots.add(0);
		List<Integer> pool = new ArrayList<>();
		for (int slot : EXTRA_SLOTS) {
			pool.add(slot);
		}
		for (int i = pool.size() - 1; i > 0; i--) {
			int j = this.main.nextInt(i + 1);
			int swap = pool.get(i);
			pool.set(i, pool.get(j));
			pool.set(j, swap);
		}
		for (int i = 0; i < this.params.gates() - 1; i++) {
			this.gateSlots.add(pool.get(i));
		}
	}

	private static double gateWidth(final boolean mainGate) {
		return mainGate ? 3.5 : 2.5;
	}

	private void carveGate(final int slot, final boolean mainGate) {
		int r = radius();
		double[] u = SLOTS[slot];
		double w = gateWidth(mainGate);
		for (int z = 0; z < CELLS; z++) {
			for (int x = 0; x < CELLS; x++) {
				int f = ring(x, z);
				if (f < r + 1 || f > r + 3) {
					continue;
				}
				double dx = x - CENTER;
				double dz = z - CENTER;
				double along = dx * u[0] + dz * u[1];
				double lateral = Math.abs(-dx * u[1] + dz * u[0]);
				if (along < 0 || lateral > w) {
					continue;
				}
				for (int y = 1; y <= 4; y++) {
					place(x, y, z, AIR);
				}
				if (!mainGate && f == r + 1) {
					for (int y = 1; y <= 3; y++) {
						place(x, y, z, Blocks.IRON_BARS.defaultBlockState());
					}
				}
			}
		}
	}

	private void raiseTowers(final int slot, final boolean mainGate) {
		int r = radius();
		double[] u = SLOTS[slot];
		double w = gateWidth(mainGate);
		List<double[]> negative = new ArrayList<>();
		List<double[]> positive = new ArrayList<>();
		for (int z = 0; z < CELLS; z++) {
			for (int x = 0; x < CELLS; x++) {
				int f = ring(x, z);
				if (f < r + 2 || f > r + 3) {
					continue;
				}
				double dx = x - CENTER;
				double dz = z - CENTER;
				double along = dx * u[0] + dz * u[1];
				double signed = -dx * u[1] + dz * u[0];
				double lateral = Math.abs(signed);
				if (along < 0 || lateral <= w || lateral > w + 2.1) {
					continue;
				}
				for (int y = 1; y <= 9; y++) {
					decayable(x, y, z, BRICKS);
				}
				(signed < 0 ? negative : positive).add(new double[] {x, z, along, lateral});
			}
		}
		DyeColor dye = DyeColor.byId(mainGate ? this.params.mainDye() : this.params.trimDye());
		for (List<double[]> side : List.of(negative, positive)) {
			if (side.isEmpty()) {
				continue;
			}
			double[] best = side.get(0);
			double bestScore = -1.0E9;
			for (double[] cell : side) {
				double score = cell[2] - Math.abs(cell[3] - (w + 1.0));
				if (score > bestScore) {
					bestScore = score;
					best = cell;
				}
			}
			int x = (int) best[0];
			int z = (int) best[1];
			for (int y = 1; y <= 9; y++) {
				place(x, y, z, BRICKS);
			}
			for (int y = 10; y <= 13; y++) {
				place(x, y, z, LOG);
			}
			place(x, 14, z, Blocks.BANNER.pick(dye).defaultBlockState());
			this.towers.add(new int[] {x, z});
		}
	}

	private void gong() {
		int zg = CENTER - (radius() + 2);
		for (int y = 1; y <= 3; y++) {
			place(CENTER - 1, y, zg, LOG);
			place(CENTER + 1, y, zg, LOG);
		}
		place(CENTER - 1, 4, zg, LOG.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
		place(CENTER, 4, zg, LOG.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
		place(CENTER + 1, 4, zg, LOG.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
		place(CENTER, 3, zg, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING).setValue(BellBlock.FACING, Direction.NORTH));
		marker(GONG, CENTER, 3, zg);
		int zp = CENTER - (radius() + 1);
		for (int x = 20; x <= 26; x++) {
			marker(PORTCULLIS, x, 1, zp);
		}
	}

	private void loge() {
		int zn = CENTER + radius();
		BlockState up = stairs(Blocks.STONE_BRICK_STAIRS.defaultBlockState(), Direction.SOUTH);
		for (int x = CENTER - 1; x <= CENTER + 1; x++) {
			place(x, 1, zn - 2, up);
			place(x, 1, zn - 1, BRICKS);
			place(x, 2, zn - 1, up);
			for (int y = 1; y <= 2; y++) {
				place(x, y, zn, BRICKS);
			}
			place(x, 3, zn, up);
			for (int y = 1; y <= 3; y++) {
				place(x, y, zn + 1, BRICKS);
			}
			place(x, 4, zn + 1, up);
		}
		for (int x = LOGE_X0; x <= LOGE_X1; x++) {
			for (int y = 1; y <= 3; y++) {
				place(x, y, zn + 2, BRICKS);
			}
			place(x, 4, zn + 2, CHISELED);
		}
		place(this.vault.getX(), this.vault.getY(), this.vault.getZ(), AIR);
		blockers.add(new Blocker(CENTER, zn - 2, 6.0));
	}

	private void cages() {
		int r = radius();
		BlockState bars = Blocks.IRON_BARS.defaultBlockState();
		for (int slot : this.gateSlots) {
			if (slot == 0) {
				continue;
			}
			double[] u = SLOTS[slot];
			double[] perp = {-u[1], u[0]};
			double w = gateWidth(false);
			for (int side = -1; side <= 1; side += 2) {
				int cx = (int) Math.round(CENTER + u[0] * (r - 2) + side * perp[0] * (w + 2.0));
				int cz = (int) Math.round(CENTER + u[1] * (r - 2) + side * perp[1] * (w + 2.0));
				this.cages.add(new int[] {cx, cz});
				for (int dz = -1; dz <= 1; dz++) {
					for (int dx = -1; dx <= 1; dx++) {
						int bx = cx + dx;
						int bz = cz + dz;
						if ((dx != 0 || dz != 0) && ring(bx, bz) <= r) {
							place(bx, 1, bz, bars);
							place(bx, 2, bz, bars);
						}
					}
				}
				marker(SPAWN_PREFIX + GroundsMobs.HOUND, cx, 1, cz);
				blockers.add(new Blocker(cx, cz, 3.0));
			}
			double gx = CENTER + u[0] * (r - 3);
			double gz = CENTER + u[1] * (r - 3);
			blockers.add(new Blocker(gx, gz, 5.0));
		}
		blockers.add(new Blocker(CENTER, CENTER - r + 1, 6.0));
	}

	/**
	 * The cell on a ring nearest to a ray from the center. A diagonal ray steps over some rings, so the search runs
	 * over every cell of the ring and keeps the one closest to the ray.
	 */
	private int[] cellOnRing(final double[] direction, final int target) {
		int[] best = {CENTER, CENTER};
		double bestScore = Double.MAX_VALUE;
		for (int z = 0; z < CELLS; z++) {
			for (int x = 0; x < CELLS; x++) {
				if (ring(x, z) != target) {
					continue;
				}
				double dx = x - CENTER;
				double dz = z - CENTER;
				double along = dx * direction[0] + dz * direction[1];
				double across = Math.abs(-dx * direction[1] + dz * direction[0]);
				if (along <= 0) {
					continue;
				}
				double score = across * 4.0 + Math.abs(along - target);
				if (score < bestScore) {
					bestScore = score;
					best = new int[] {x, z};
				}
			}
		}
		return best;
	}

	private void spawnMarkers() {
		for (int slot = 0; slot < 8; slot++) {
			int[] cell = cellOnRing(SLOTS[slot], radius() - 5);
			marker(SPAWN_PREFIX + GroundsMobs.GLADIATOR, cell[0], 1, cell[1]);
			blockers.add(new Blocker(cell[0], cell[1], 1.5));
		}
	}

	/** Eight stand spots between the compass slots. Their height is set after decay. */
	private void retiariusMarkers() {
		for (int k = 0; k < 8; k++) {
			double angle = Math.toRadians(22.5 + 45.0 * k);
			int[] cell = cellOnRing(new double[] {Math.cos(angle), Math.sin(angle)}, radius() + 2);
			marker(SPAWN_PREFIX + GroundsMobs.RETIARIUS, cell[0], topOf(cell[0], cell[1]) + 1, cell[1]);
		}
	}

	private int topOf(final int x, final int z) {
		for (int y = TOP; y >= -DEPTH; y--) {
			BlockState state = this.cells[index(x, y, z)];
			if (state != null && !state.isAir()) {
				return y;
			}
		}
		return -DEPTH;
	}

	private void braziers() {
		double[] angles = {22.5, 157.5, 202.5, 337.5};
		BlockState unlit = Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false);
		for (double degrees : angles) {
			double angle = Math.toRadians(degrees);
			int[] cell = cellOnRing(new double[] {Math.cos(angle), Math.sin(angle)}, radius() + 1);
			for (int y = 1; y <= 3; y++) {
				place(cell[0], y, cell[1], BRICKS);
			}
			place(cell[0], 4, cell[1], unlit);
			marker(BRAZIER, cell[0], 4, cell[1]);
		}
	}

	private boolean zoneFree(final double cx, final double cz, final double radius) {
		int r = radius();
		if (metric(this.params.shape(), cx - CENTER, cz - CENTER) > r - 4 - (radius - 1.0)) {
			return false;
		}
		if (Math.abs(cx - CENTER) < radius + 1.5) {
			return false;
		}
		if (cx + radius + 0.5 >= PIT_X0 && cx - radius - 0.5 <= PIT_X1 + 1 && cz + radius + 0.5 >= PIT_Z0 && cz - radius - 0.5 <= PIT_Z1 + 1) {
			return false;
		}
		for (Blocker blocker : this.blockers) {
			if (Math.hypot(cx - blocker.x(), cz - blocker.z()) < radius + blocker.radius()) {
				return false;
			}
		}
		return true;
	}

	private void pillars() {
		int tries = 0;
		while (this.pillars.size() < this.params.pillars() && tries < this.params.pillars() * 300) {
			tries++;
			int r = radius();
			int px = this.main.nextInt(2 * r) - r + CENTER;
			int pz = this.main.nextInt(2 * r) - r + CENTER;
			int height = 3 + this.main.nextInt(3);
			if (!zoneFree(px + 0.5, pz + 0.5, 1.5)) {
				continue;
			}
			boolean spaced = true;
			for (Pillar other : this.pillars) {
				spaced &= Math.hypot(px - other.x(), pz - other.z()) >= PILLAR_SPACING;
			}
			if (!spaced) {
				continue;
			}
			this.pillars.add(new Pillar(px, pz, height));
			this.blockers.add(new Blocker(px + 0.5, pz + 0.5, 1.5));
		}
		for (Pillar pillar : this.pillars) {
			for (int dz = 0; dz < 2; dz++) {
				for (int dx = 0; dx < 2; dx++) {
					for (int y = 1; y <= pillar.height(); y++) {
						decayable(pillar.x() + dx, y, pillar.z() + dz, BRICKS);
					}
				}
			}
		}
	}

	private void grates() {
		for (int g = 0; g < GRATES; g++) {
			int[][] cellsOfGrate = null;
			for (int tries = 0; tries < 400 && cellsOfGrate == null; tries++) {
				int r = radius();
				int gx = this.featureRandom.nextInt(2 * r) - r + CENTER;
				int gz = this.featureRandom.nextInt(2 * r) - r + CENTER;
				if (zoneFree(gx + 1.0, gz + 1.0, 1.5)) {
					cellsOfGrate = new int[][] {{gx, gz}, {gx + 1, gz}, {gx, gz + 1}, {gx + 1, gz + 1}};
					this.blockers.add(new Blocker(gx + 1.0, gz + 1.0, 3.0));
				}
			}
			if (cellsOfGrate == null) {
				cellsOfGrate = new int[][] {{CENTER - 10, CENTER}, {CENTER - 9, CENTER}, {CENTER - 10, CENTER + 1}, {CENTER - 9, CENTER + 1}};
			}
			this.grates.add(cellsOfGrate);
			for (int[] cell : cellsOfGrate) {
				place(cell[0], -2, cell[1], Blocks.NETHERRACK.defaultBlockState());
				place(cell[0], -1, cell[1], AIR);
				place(cell[0], 0, cell[1], Blocks.IRON_TRAPDOOR.defaultBlockState());
				marker(GRATE + (g + 1), cell[0], 0, cell[1]);
			}
		}
	}

	private void features() {
		for (int f = 0; f < this.params.features(); f++) {
			int kind = this.featureRandom.nextInt(FEATURE_KINDS);
			double size = featureSize(kind);
			int[] spot = findFeatureSpot(size);
			if (spot == null) {
				// A crowded arena trades the roll for a small mound, so every arena keeps its floor features.
				kind = FEATURE_RUBBLE;
				size = featureSize(kind);
				spot = findFeatureSpot(size);
			}
			if (spot == null) {
				spot = plainSpot();
			}
			if (spot == null) {
				continue;
			}
			this.blockers.add(new Blocker(spot[0], spot[1], size));
			this.features.add(new Feature(kind, spot[0], spot[1]));
			switch (kind) {
				case FEATURE_RUBBLE -> rubble(spot[0], spot[1]);
				case FEATURE_COLUMN -> fallenColumn(spot[0], spot[1]);
				default -> sunkenRing(spot[0], spot[1]);
			}
		}
	}

	private static double featureSize(final int kind) {
		return kind == FEATURE_RING ? 3.5 : kind == FEATURE_COLUMN ? 2.5 : 2.0;
	}

	/** The last resort: any 3 by 3 of bare sand off the center lane and the pit, however crowded the arena is. */
	private int[] plainSpot() {
		int r = radius();
		for (int tries = 0; tries < 2000; tries++) {
			int cx = this.featureRandom.nextInt(2 * r) - r + CENTER;
			int cz = this.featureRandom.nextInt(2 * r) - r + CENTER;
			boolean bare = ring(cx, cz) <= r - 5 && Math.abs(cx - CENTER) >= 4 && !(cx >= PIT_X0 - 3 && cx <= PIT_X1 + 3 && cz >= PIT_Z0 - 3 && cz <= PIT_Z1 + 3);
			for (int dz = -1; dz <= 1 && bare; dz++) {
				for (int dx = -1; dx <= 1 && bare; dx++) {
					BlockState floor = this.cells[index(cx + dx, 0, cz + dz)];
					BlockState above = this.cells[index(cx + dx, 1, cz + dz)];
					bare = floor == SAND && above != null && above.isAir();
				}
			}
			for (Marker marker : this.markers) {
				bare &= Math.abs(marker.x() - cx) > 2 || Math.abs(marker.z() - cz) > 2;
			}
			if (bare) {
				return new int[] {cx, cz};
			}
		}
		return null;
	}

	private int[] findFeatureSpot(final double size) {
		int r = radius();
		for (int tries = 0; tries < 400; tries++) {
			int cx = this.featureRandom.nextInt(2 * r) - r + CENTER;
			int cz = this.featureRandom.nextInt(2 * r) - r + CENTER;
			if (zoneFree(cx, cz, size)) {
				return new int[] {cx, cz};
			}
		}
		return null;
	}

	private void rubble(final int cx, final int cz) {
		BlockState[] kinds = {Blocks.COBBLESTONE.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.GRAVEL.defaultBlockState()};
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				boolean corner = dx != 0 && dz != 0;
				if (corner && this.featureRandom.nextInt(3) == 0) {
					continue;
				}
				place(cx + dx, 1, cz + dz, kinds[this.featureRandom.nextInt(kinds.length)]);
			}
		}
		place(cx, 2, cz, Blocks.COBBLESTONE.defaultBlockState());
	}

	private void fallenColumn(final int cx, final int cz) {
		boolean alongX = this.featureRandom.nextBoolean();
		BlockState slab = Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.BOTTOM);
		for (int i = -1; i <= 2; i++) {
			place(alongX ? cx + i : cx, 1, alongX ? cz : cz + i, i == 2 ? CHISELED : slab);
		}
	}

	private void sunkenRing(final int cx, final int cz) {
		for (int dz = -3; dz <= 3; dz++) {
			for (int dx = -3; dx <= 3; dx++) {
				double d = Math.hypot(dx, dz);
				if (d >= 2.0 && d <= 3.2) {
					place(cx + dx, 0, cz + dz, AIR);
					place(cx + dx, -1, cz + dz, SAND);
				}
			}
		}
	}

	private void decay() {
		int pct = this.params.decayPercent();
		for (int y = 2; y <= TOP; y++) {
			for (int z = 0; z < CELLS; z++) {
				for (int x = 0; x < CELLS; x++) {
					int i = index(x, y, z);
					if (this.flags[i] != DECAY || this.cells[i] == null || this.cells[i].isAir()) {
						continue;
					}
					this.decayCandidates++;
					if (this.decayRandom.nextInt(100) < pct) {
						int kind = this.decayRandom.nextInt(3);
						if (kind == 0) {
							this.cells[i] = MOSSY;
							this.mossy++;
						} else if (kind == 1) {
							this.cells[i] = CRACKED;
							this.cracked++;
						} else {
							this.cells[i] = AIR;
							this.air++;
							this.hit[z * SIZE + x] = true;
						}
					}
				}
			}
		}
	}

	/** Removes every decay-flagged block that no longer touches the base through solid blocks. */
	private void prune() {
		boolean[] seen = new boolean[this.cells.length];
		java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
		for (int y = -DEPTH; y <= 0; y++) {
			for (int z = 0; z < SIZE; z++) {
				for (int x = 0; x < SIZE; x++) {
					int i = index(x, y, z);
					if (this.cells[i] != null && !this.cells[i].isAir()) {
						seen[i] = true;
						queue.add(new int[] {x, y, z});
					}
				}
			}
		}
		int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		while (!queue.isEmpty()) {
			int[] c = queue.poll();
			for (int[] s : steps) {
				int nx = c[0] + s[0];
				int ny = c[1] + s[1];
				int nz = c[2] + s[2];
				if (!inside(nx, ny, nz)) {
					continue;
				}
				int j = index(nx, ny, nz);
				if (seen[j] || this.cells[j] == null || this.cells[j].isAir()) {
					continue;
				}
				seen[j] = true;
				queue.add(new int[] {nx, ny, nz});
			}
		}
		for (int y = 1; y <= TOP; y++) {
			for (int z = 0; z < SIZE; z++) {
				for (int x = 0; x < SIZE; x++) {
					int k = index(x, y, z);
					if (this.cells[k] != null && !this.cells[k].isAir() && this.flags[k] == DECAY && !seen[k]) {
						this.cells[k] = AIR;
						this.pruned++;
						this.hit[z * SIZE + x] = true;
					}
				}
			}
		}
	}

	private void scuff() {
		for (int z = 0; z < CELLS; z++) {
			for (int x = 0; x < CELLS; x++) {
				BlockState floor = this.cells[index(x, 0, z)];
				BlockState above = this.cells[index(x, 1, z)];
				if (floor == SAND && (above == null || above.isAir()) && this.scuffRandom.nextInt(100) < 12) {
					this.cells[index(x, 0, z)] = COARSE;
				}
			}
		}
	}

	/** Joins each iron bar to the bars and solid blocks beside it, as a placed bar would join. */
	private void connectBars() {
		for (int y = 1; y <= TOP; y++) {
			for (int z = 1; z < CELLS - 1; z++) {
				for (int x = 1; x < CELLS - 1; x++) {
					int i = index(x, y, z);
					BlockState state = this.cells[i];
					if (state == null || !state.is(Blocks.IRON_BARS)) {
						continue;
					}
					state = state
						.setValue(IronBarsBlock.NORTH, joins(x, y, z - 1))
						.setValue(IronBarsBlock.SOUTH, joins(x, y, z + 1))
						.setValue(IronBarsBlock.EAST, joins(x + 1, y, z))
						.setValue(IronBarsBlock.WEST, joins(x - 1, y, z));
					this.cells[i] = state;
				}
			}
		}
	}

	private boolean joins(final int x, final int y, final int z) {
		BlockState state = this.cells[index(x, y, z)];
		return state != null && !state.isAir() && (state.is(Blocks.IRON_BARS) || state.canOcclude());
	}
}
