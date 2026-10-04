package com.stella.ruins.site.crystalgrotto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Everything random about one grotto, and every block it is made of, as a pure function of a seed. The layout and
 * the tests both read it, so a test checks the plan without placing a block.
 * <p>
 * Cells are in the grotto frame: x and z run 0 to 24 and y runs 0 to 15. The entrance tunnel reaches out to
 * negative z, down to {@link #TUNNEL_START}. The layout shifts z by {@link #EXTENSION} into the site's own frame.
 * <p>
 * The plan reads no world block, so every chunk of the site draws the same grotto.
 */
public final class GrottoPlan {
	public static final int SIZE_X = 25;
	public static final int SIZE_Y = 16;
	public static final int GROTTO_Z = 25;
	/** How far the tunnel reaches out of the grotto toward cave air. */
	public static final int EXTENSION = 16;
	public static final int SIZE_Z = GROTTO_Z + EXTENSION;
	public static final int TUNNEL_START = -EXTENSION;

	public static final int CENTER_X = 12;
	public static final int CENTER_Z = 12;
	/** The walking level: the first block of air over the floor. */
	public static final int FLOOR = 3;
	public static final int INTERIOR_RADIUS = 8;
	public static final int INTERIOR_HEIGHT = 9;
	public static final int CORE_Y = 4;
	public static final int VAULT_X = 12;
	public static final int VAULT_Z = 20;

	public static final int MIN_PILLARS = 4;
	public static final int MAX_PILLARS = 6;
	public static final int MIN_HEIGHT = 3;
	public static final int MAX_HEIGHT = 5;
	/** Degrees from +x. North (+z) is 90 and south is 270, and both stay free for the vault and the tunnel. */
	public static final int[] SLOT_DEGREES = {0, 45, 135, 180, 225, 315};
	public static final double NOMINAL_RADIUS = 5.0;

	private static final int NONE = 4;
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState AMETHYST = Blocks.AMETHYST_BLOCK.defaultBlockState();
	private static final BlockState BUDDING = Blocks.BUDDING_AMETHYST.defaultBlockState();
	private static final BlockState CALCITE = Blocks.CALCITE.defaultBlockState();
	private static final BlockState BASALT = Blocks.SMOOTH_BASALT.defaultBlockState();
	public static final BlockState SPOKE_UNLIT = Blocks.SMOOTH_BASALT.defaultBlockState();
	public static final BlockState SPOKE_LIT = Blocks.SEA_LANTERN.defaultBlockState();

	/** A cell on the floor. */
	public record Cell(int x, int z) {
	}

	/**
	 * One crystal pillar. Its plinth is 3 x 3 at the walking level and its shaft stands on the middle cell. The
	 * spoke cells run from the core pedestal toward the plinth.
	 */
	public record Pillar(int index, int slot, int x, int z, int height, Tint tint, List<Cell> spoke) {
		public Pillar {
			spoke = List.copyOf(spoke);
		}

		/** The highest shaft block. */
		public int shaftTop() {
			return FLOOR + this.height;
		}

		/** The cell of the crown cluster. */
		public int crownY() {
			return FLOOR + 1 + this.height;
		}

		boolean plinthHas(final int cx, final int cz) {
			return Math.abs(cx - this.x) <= 1 && Math.abs(cz - this.z) <= 1;
		}
	}

	/** A short column on the floor, near the wall. */
	public record Spike(int x, int z, int height) {
	}

	private final long seed;
	private final double[] phase;
	private final int clusterPercent;
	private final int buddingPercent;
	private final List<Pillar> pillars;
	private final List<Spike> spikes;
	private final List<Cell> sentinels;
	private final List<Cell> slimes;
	private final Map<Long, Tint> spokeTint = new HashMap<>();
	private final Set<Long> spikeCells = new HashSet<>();
	private final Set<Long> markerCells = new HashSet<>();

	private GrottoPlan(
		final long seed, final double[] phase, final int clusterPercent, final int buddingPercent, final List<TentativePillar> chosen,
		final List<double[]> spikeDraws
	) {
		this.seed = seed;
		this.phase = phase;
		this.clusterPercent = clusterPercent;
		this.buddingPercent = buddingPercent;
		List<Pillar> placed = new ArrayList<>();
		List<int[]> plinths = new ArrayList<>();
		for (int i = 0; i < chosen.size(); i++) {
			TentativePillar pick = chosen.get(i);
			int[] cell = pick.jittered();
			if (!fits(cell, plinths, chosen, i)) {
				cell = pick.nominal();
			}
			plinths.add(cell);
			int cap = ceilingAir(cell[0], cell[1]) - 4;
			placed.add(new Pillar(i, pick.slot, cell[0], cell[1], Math.max(MIN_HEIGHT, Math.min(Math.min(pick.height, MAX_HEIGHT), cap)), pick.tint, List.of()));
		}
		List<Pillar> withSpokes = new ArrayList<>();
		for (Pillar pillar : placed) {
			withSpokes.add(new Pillar(pillar.index(), pillar.slot(), pillar.x(), pillar.z(), pillar.height(), pillar.tint(), spokeCells(pillar, placed)));
		}
		this.pillars = Collections.unmodifiableList(withSpokes);
		for (Pillar pillar : this.pillars) {
			for (Cell cell : pillar.spoke()) {
				this.spokeTint.put(key(cell.x(), cell.z()), pillar.tint());
			}
		}
		List<Spike> accepted = new ArrayList<>();
		for (double[] draw : spikeDraws) {
			Spike spike = spikeFrom(draw);
			if (spike != null) {
				accepted.add(spike);
				this.spikeCells.add(key(spike.x(), spike.z()));
			}
		}
		this.spikes = Collections.unmodifiableList(accepted);
		List<Cell> sentinel = new ArrayList<>();
		for (int degrees : new int[] {20, 160, 200, 340}) {
			sentinel.add(outerFreeCell(degrees));
		}
		this.sentinels = Collections.unmodifiableList(sentinel);
		this.slimes = List.of(nearestFree(10, 18), nearestFree(14, 18), nearestFree(12, 9));
		this.sentinels.forEach(cell -> this.markerCells.add(key(cell.x(), cell.z())));
		this.slimes.forEach(cell -> this.markerCells.add(key(cell.x(), cell.z())));
	}

	private record TentativePillar(int slot, int[] jittered, int[] nominal, int height, Tint tint) {
	}

	/** Draws a plan. Every draw happens in a fixed order, whatever the plan later rejects. */
	public static GrottoPlan derive(final RandomSource random) {
		long seed = random.nextLong();
		double[] phase = {random.nextDouble() * Math.PI * 2.0, random.nextDouble() * Math.PI * 2.0, random.nextDouble() * Math.PI * 2.0};
		int cluster = 10 + random.nextInt(7);
		int budding = 6 + random.nextInt(5);
		int count = MIN_PILLARS + random.nextInt(MAX_PILLARS - MIN_PILLARS + 1);
		List<Integer> slots = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5));
		shuffle(slots, random);
		List<Tint> tints = new ArrayList<>(List.of(Tint.values()));
		shuffle(tints, random);
		double[][] pillarDraws = new double[SLOT_DEGREES.length][3];
		for (double[] draw : pillarDraws) {
			draw[0] = (random.nextDouble() * 2.0 - 1.0) * 8.0;
			draw[1] = 4.5 + random.nextDouble();
			draw[2] = MIN_HEIGHT + random.nextInt(MAX_HEIGHT - MIN_HEIGHT + 1);
		}
		int spikeCount = 2 + random.nextInt(3);
		List<double[]> spikeDraws = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			spikeDraws.add(new double[] {random.nextDouble() * 360.0, 2 + random.nextInt(3)});
		}
		spikeDraws = spikeDraws.subList(0, spikeCount);
		List<Integer> picked = new ArrayList<>(slots.subList(0, count));
		Collections.sort(picked);
		List<TentativePillar> chosen = new ArrayList<>();
		for (int slot : picked) {
			double[] draw = pillarDraws[slot];
			chosen.add(new TentativePillar(
				slot, cellOf(SLOT_DEGREES[slot] + draw[0], draw[1]), cellOf(SLOT_DEGREES[slot], NOMINAL_RADIUS), (int) draw[2], tints.get(chosen.size())
			));
		}
		return new GrottoPlan(seed, phase, cluster, budding, chosen, spikeDraws);
	}

	private static <T> void shuffle(final List<T> list, final RandomSource random) {
		for (int i = list.size() - 1; i > 0; i--) {
			Collections.swap(list, i, random.nextInt(i + 1));
		}
	}

	private static int[] cellOf(final double degrees, final double radius) {
		double angle = Math.toRadians(degrees);
		return new int[] {CENTER_X + (int) Math.floor(radius * Math.cos(angle) + 0.5), CENTER_Z + (int) Math.floor(radius * Math.sin(angle) + 0.5)};
	}

	/** True when a plinth at this cell leaves room for a spoke, and clears the other plinths, the niche and the tunnel. */
	private static boolean fits(final int[] cell, final List<int[]> finished, final List<TentativePillar> chosen, final int index) {
		// A gap of one cell between the pedestal and the plinth keeps room for the floor spoke.
		if (Math.max(Math.abs(cell[0] - CENTER_X), Math.abs(cell[1] - CENTER_Z)) < 4) {
			return false;
		}
		for (int[] other : finished) {
			if (Math.abs(cell[0] - other[0]) <= 2 && Math.abs(cell[1] - other[1]) <= 2) {
				return false;
			}
		}
		for (int i = index + 1; i < chosen.size(); i++) {
			int[] nominal = chosen.get(i).nominal();
			if (Math.abs(cell[0] - nominal[0]) <= 2 && Math.abs(cell[1] - nominal[1]) <= 2) {
				return false;
			}
		}
		boolean inNiche = cell[0] + 1 >= 10 && cell[0] - 1 <= 14 && cell[1] + 1 >= 17 && cell[1] - 1 <= 21;
		boolean inTunnel = cell[0] + 1 >= 11 && cell[0] - 1 <= 13 && cell[1] - 1 <= 6;
		return !inNiche && !inTunnel;
	}

	private List<Cell> spokeCells(final Pillar pillar, final List<Pillar> all) {
		int dx = pillar.x() - CENTER_X;
		int dz = pillar.z() - CENTER_Z;
		int steps = Math.max(Math.abs(dx), Math.abs(dz));
		List<Cell> cells = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		for (int step = 1; step < steps; step++) {
			int x = CENTER_X + (int) Math.floor((double) dx * step / steps + 0.5);
			int z = CENTER_Z + (int) Math.floor((double) dz * step / steps + 0.5);
			boolean pedestal = Math.abs(x - CENTER_X) <= 1 && Math.abs(z - CENTER_Z) <= 1;
			boolean plinth = all.stream().anyMatch(other -> other.plinthHas(x, z));
			if (!pedestal && !plinth && seen.add(key(x, z))) {
				cells.add(new Cell(x, z));
			}
		}
		return cells;
	}

	private @Nullable Spike spikeFrom(final double[] draw) {
		double angle = Math.toRadians(draw[0]);
		double radius = radius(angle) - 1.5;
		int x = CENTER_X + (int) Math.floor(radius * Math.cos(angle) + 0.5);
		int z = CENTER_Z + (int) Math.floor(radius * Math.sin(angle) + 0.5);
		if (blocked(x, z, 1) || this.spokeTint.containsKey(key(x, z)) || this.spikeCells.contains(key(x, z))) {
			return null;
		}
		int height = Math.min((int) draw[1], ceilingAir(x, z) - FLOOR);
		return height < 1 ? null : new Spike(x, z, height);
	}

	private Cell outerFreeCell(final int degrees) {
		for (double radius = 6.5; radius >= 3.0; radius -= 0.5) {
			int[] cell = cellOf(degrees, radius);
			if (!blocked(cell[0], cell[1], 0) && isAir(cell[0], FLOOR, cell[1]) && isAir(cell[0], FLOOR + 1, cell[1])) {
				return new Cell(cell[0], cell[1]);
			}
		}
		return new Cell(CENTER_X + 4, CENTER_Z + 4);
	}

	private Cell nearestFree(final int x, final int z) {
		for (int ring = 0; ring <= 4; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) == ring && !blocked(x + dx, z + dz, 0)
						&& isAir(x + dx, FLOOR, z + dz) && isAir(x + dx, FLOOR + 1, z + dz)) {
						return new Cell(x + dx, z + dz);
					}
				}
			}
		}
		return new Cell(x, z);
	}

	/**
	 * True when a cell holds a pedestal, a dais, a plinth or a spike, grown by {@code margin} cells. A margin above
	 * zero also keeps the vault niche and the tunnel mouth clear.
	 */
	private boolean blocked(final int x, final int z, final int margin) {
		if (Math.abs(x - CENTER_X) <= 1 + margin && Math.abs(z - CENTER_Z) <= 1 + margin) {
			return true;
		}
		if (x >= 11 - margin && x <= 13 + margin && z >= 19 - margin && z <= 20 + margin) {
			return true;
		}
		if (x >= 11 - margin && x <= 13 + margin && z <= 6 + margin) {
			return true;
		}
		if (margin > 0 && x >= 10 - margin && x <= 14 + margin && z >= 17 - margin && z <= 21 + margin) {
			return true;
		}
		if (this.pillars != null) {
			for (Pillar pillar : this.pillars) {
				if (Math.abs(x - pillar.x()) <= 1 + margin && Math.abs(z - pillar.z()) <= 1 + margin) {
					return true;
				}
			}
		}
		for (long cell : this.spikeCells) {
			if (Math.abs(x - (int) (cell >> 32)) <= margin && Math.abs(z - (int) cell) <= margin) {
				return true;
			}
		}
		return false;
	}

	private static long key(final int x, final int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	// ---- queries ----

	public long seed() {
		return this.seed;
	}

	public List<Pillar> pillars() {
		return this.pillars;
	}

	public List<Spike> spikes() {
		return this.spikes;
	}

	/** The four cells where Shard Sentinels may appear. */
	public List<Cell> sentinelCells() {
		return this.sentinels;
	}

	/** The three cells where the Prism Slimes of an activation appear. */
	public List<Cell> slimeCells() {
		return this.slimes;
	}

	public int clusterPercent() {
		return this.clusterPercent;
	}

	public int buddingPercent() {
		return this.buddingPercent;
	}

	/** A short text that differs when the pillars, their colours, or their shape differ. */
	public String signature() {
		StringBuilder text = new StringBuilder();
		for (Pillar pillar : this.pillars) {
			text.append(pillar.x()).append(',').append(pillar.z()).append(',').append(pillar.height()).append(',').append(pillar.tint()).append(';');
		}
		return text.toString();
	}

	/** The radius of the chamber at an angle, in blocks: 8 with up to one block of noise either way. */
	public double radius(final double theta) {
		return INTERIOR_RADIUS + 0.55 * Math.sin(2.0 * theta + this.phase[0]) + 0.3 * Math.sin(3.0 * theta + this.phase[1]) + 0.15 * Math.sin(5.0 * theta + this.phase[2]);
	}

	/**
	 * The shell layer of a cell: 0 is air, 1 the amethyst lining, 2 calcite, 3 smooth basalt, and 4 is rock the
	 * grotto leaves as it found it. The chamber, the vault niche and the tunnel each give a layer; the lowest wins.
	 */
	public int layerAt(final int x, final int y, final int z) {
		return Math.min(dome(x, y, z), Math.min(niche(x, y, z), tunnel(x, y, z)));
	}

	private int dome(final int x, final int y, final int z) {
		if (z < 0) {
			return NONE;
		}
		double dx = x - CENTER_X;
		double dz = z - CENTER_Z;
		double distance = Math.hypot(dx, dz);
		double edge = radius(Math.atan2(dz, dx));
		if (y < FLOOR) {
			return switch (y) {
				case 2 -> distance <= edge + 1.0 ? 1 : NONE;
				case 1 -> distance <= edge + 2.0 ? 2 : NONE;
				case 0 -> distance <= edge + 3.0 ? 3 : NONE;
				default -> NONE;
			};
		}
		double height = y + 0.5 - FLOOR;
		for (int k = 0; k <= 3; k++) {
			double a = distance / (edge + k);
			double b = height / (INTERIOR_HEIGHT + k);
			if (a * a + b * b <= 1.0) {
				return k;
			}
		}
		return NONE;
	}

	private static int niche(final int x, final int y, final int z) {
		int d = Math.max(Math.max(Math.max(10 - x, x - 14), Math.max(FLOOR - y, y - 7)), Math.max(17 - z, z - 21));
		d = Math.max(d, 0);
		return d <= 3 ? d : NONE;
	}

	private static int tunnel(final int x, final int y, final int z) {
		int d = Math.max(Math.max(Math.max(11 - x, x - 13), Math.max(FLOOR - y, y - 5)), Math.max(TUNNEL_START - z, z - 6));
		d = Math.max(d, 0);
		return d == 0 ? 0 : d <= 2 ? d + 1 : NONE;
	}

	public boolean isAir(final int x, final int y, final int z) {
		return layerAt(x, y, z) == 0;
	}

	/** The highest block of air in the chamber at a column, or 0 when the column has none. */
	public int ceilingAir(final int x, final int z) {
		for (int y = SIZE_Y - 1; y >= FLOOR; y--) {
			if (dome(x, y, z) == 0) {
				return y;
			}
		}
		return 0;
	}

	/** The block of a cell, or null when the grotto leaves the cell as it found it. */
	public @Nullable BlockState stateAt(final int x, final int y, final int z) {
		BlockState fixed = fixedAt(x, y, z);
		if (fixed != null) {
			return fixed;
		}
		return switch (layerAt(x, y, z)) {
			case 0 -> decorAt(x, y, z);
			case 1 -> hash(x, y, z) % 100 < this.buddingPercent ? BUDDING : AMETHYST;
			case 2 -> CALCITE;
			case 3 -> BASALT;
			default -> null;
		};
	}

	private @Nullable BlockState fixedAt(final int x, final int y, final int z) {
		if (z >= 0 && Math.abs(x - CENTER_X) <= 1 && Math.abs(z - CENTER_Z) <= 1 && y == FLOOR) {
			return CALCITE;
		}
		if (x >= 11 && x <= 13 && z >= 19 && z <= 20 && y == FLOOR) {
			return CALCITE;
		}
		for (Pillar pillar : this.pillars) {
			if (!pillar.plinthHas(x, z)) {
				continue;
			}
			if (y == FLOOR) {
				return CALCITE;
			}
			if (x == pillar.x() && z == pillar.z()) {
				if (y > FLOOR && y <= pillar.shaftTop()) {
					return ResonantCrystalBlock.BLOCK.stateOf(pillar.tint());
				}
				if (y == pillar.crownY()) {
					return Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP);
				}
			}
		}
		Tint spoke = this.spokeTint.get(key(x, z));
		if (spoke != null && z >= 0) {
			if (y == 2) {
				return spoke.glass().defaultBlockState();
			}
			if (y == 1) {
				return SPOKE_UNLIT;
			}
		}
		for (Spike spike : this.spikes) {
			if (spike.x() == x && spike.z() == z) {
				if (y == FLOOR) {
					return CALCITE;
				}
				if (y > FLOOR && y < FLOOR + spike.height()) {
					return AMETHYST;
				}
				if (y == FLOOR + spike.height()) {
					return Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP);
				}
			}
		}
		return null;
	}

	private static final Direction[] ATTACH = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

	/** Air, or a crystal growing out of the lining beside it. */
	private BlockState decorAt(final int x, final int y, final int z) {
		if (z < 0 || tunnel(x, y, z) == 0) {
			return AIR;
		}
		boolean walkway = y <= FLOOR + 1;
		boolean nearCore = y == FLOOR && Math.hypot(x - CENTER_X, z - CENTER_Z) <= 6.2;
		boolean inNiche = niche(x, y, z) == 0 && x >= 10 && x <= 14 && z >= 17;
		if (nearCore || walkway && (inNiche || this.markerCells.contains(key(x, z)))) {
			return AIR;
		}
		for (Direction side : ATTACH) {
			int nx = x + side.getStepX();
			int ny = y + side.getStepY();
			int nz = z + side.getStepZ();
			if (ny < 0 || nx < 0 || nz < 0 || nx >= SIZE_X || nz >= GROTTO_Z) {
				continue;
			}
			if (fixedAt(nx, ny, nz) == null && layerAt(nx, ny, nz) == 1 && hash(x, y, z) % 100 < this.clusterPercent) {
				BlockState crystal = switch ((int) (hash(z, x, y) % 4)) {
					case 0 -> Blocks.AMETHYST_CLUSTER.defaultBlockState();
					case 1 -> Blocks.LARGE_AMETHYST_BUD.defaultBlockState();
					case 2 -> Blocks.MEDIUM_AMETHYST_BUD.defaultBlockState();
					default -> Blocks.SMALL_AMETHYST_BUD.defaultBlockState();
				};
				return crystal.setValue(AmethystClusterBlock.FACING, side.getOpposite());
			}
		}
		return AIR;
	}

	private long hash(final int x, final int y, final int z) {
		long h = this.seed ^ (x * 0x9E3779B97F4A7C15L);
		h = Long.rotateLeft(h, 31) * 0xBF58476D1CE4E5B9L;
		h ^= y * 0x94D049BB133111EBL;
		h = Long.rotateLeft(h, 29) * 0x9E3779B97F4A7C15L;
		h ^= z * 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		return (h >>> 1) % 1_000_000_007L;
	}
}
