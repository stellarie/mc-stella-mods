package com.stella.ruins.site.spidernest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Everything random about one spider nest, and every block it is made of, as a pure function of a seed. The layout,
 * the objective and the tests read it. It reads no world block, so every chunk draws the same nest.
 * <p>
 * The cave is a height field. Each of the 30 x 30 columns has a floor, a roof and a tag, so steps and domes need no
 * search in three dimensions. Cells are in the nest frame: x and z run 0 to 29 and y runs 0 to 13. The mouth tunnel
 * reaches out to negative z, down to {@link #TUNNEL_START}. The layout shifts z by {@link #EXTENSION}.
 * <p>
 * The plan keeps a lane of web-free floor from the mouth to every sac, every spawn marker, the core and the vault,
 * so the webs never trap a player for good. It finds the best route over the sacs with an exact search.
 */
public final class NestPlan {
	public static final int SIZE_X = 30;
	public static final int SIZE_Y = 14;
	public static final int NEST_Z = 30;
	/** How far the mouth tunnel reaches out of the nest toward cave air. */
	public static final int EXTENSION = 16;
	public static final int SIZE_Z = NEST_Z + EXTENSION;
	public static final int TUNNEL_START = -EXTENSION;
	public static final int MIN_SACS = 6;
	public static final int MAX_SACS = 10;
	public static final int MIN_WEB_PERCENT = 15;
	public static final int MAX_WEB_PERCENT = 35;
	public static final int WEAVERS = 3;
	public static final int CRAWLERS = 6;
	/** The tunnel floor: the first block of air over the floor of the mouth. */
	public static final int TUNNEL_FLOOR = 2;
	public static final int TUNNEL_HEIGHT = 3;
	/** The highest air in the nest. Rock holds the two layers above. */
	public static final int MAX_AIR_Y = 11;

	/** Cell contents of the grid. ROCK is whatever the world holds there; the layout writes nothing for it. */
	public static final byte AIR = 0;
	public static final byte ROCK = 1;
	public static final byte WEB = 2;
	public static final byte SAC = 3;
	public static final byte BONE = 4;
	public static final byte COCOON = 5;
	public static final byte CORE = 6;
	public static final byte VAULT = 7;
	public static final byte SKULL = 8;

	/** Column tags: which shape first carved the column. Blobs hold 1 to 5. */
	public static final int TAG_SIDE_TUNNEL = 50;
	public static final int TAG_MOUTH = 51;
	public static final int TAG_NOOK = 52;
	public static final int TAG_NECK = 53;
	public static final int TAG_PIT = 60;

	static final double SPEED = 5.0;
	static final double STEP_UP = 0.12;
	static final double WEB_FACTOR = 4.0;
	/** Seconds to aim at a sac and break it. */
	public static final double BREAK_SECONDS = 2.0;
	static final double EYE = 1.62;
	/** The farthest a sac may lie from the eye of a player who stands on a floor cell. */
	public static final double REACH = 4.0;
	static final double MIN_SAC_GAP = 5.0;
	/** The longest walk, in seconds, that the greedy route may take before the plan picks its sacs again. */
	public static final double GREEDY_BUDGET = 34.0;
	static final int PICK_ATTEMPTS = 10;
	private static final int N = SIZE_X * NEST_Z;
	private static final int[] DX8 = {1, -1, 0, 0, 1, 1, -1, -1};
	private static final int[] DZ8 = {0, 0, 1, -1, 1, -1, 1, -1};
	private static final int[] DX4 = {1, -1, 0, 0};
	private static final int[] DZ4 = {0, 0, 1, -1};

	private static final int CACHE_SIZE = 16;
	private static final Map<Long, NestPlan> CACHE = Collections.synchronizedMap(new LinkedHashMap<>(32, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(final Map.Entry<Long, NestPlan> eldest) {
			return size() > CACHE_SIZE;
		}
	});

	/** A chamber: a domed blob of the cave. */
	public record Blob(int x, int z, double rx, double rz, int floor, double height) {
	}

	/** One egg sac. {@code face} is the unit step from the sac toward the rock it hangs on. */
	public static final class Sac {
		public int id;
		public final int x;
		public final int y;
		public final int z;
		public final boolean ceiling;
		public final int faceX;
		public final int faceY;
		public final int faceZ;
		/** The chamber index, or {@link #TUNNEL_CHAMBER}. */
		public final int chamber;
		public int reachX;
		public int reachZ;
		public int reachFloor;
		public double reachDistance;
		int[] sight = new int[0];
		boolean reachKnown;
		boolean reachFound;

		Sac(final int x, final int y, final int z, final boolean ceiling, final int faceX, final int faceY, final int faceZ, final int chamber) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.ceiling = ceiling;
			this.faceX = faceX;
			this.faceY = faceY;
			this.faceZ = faceZ;
			this.chamber = chamber;
		}

		public boolean tunnel() {
			return this.chamber == TUNNEL_CHAMBER;
		}
	}

	public static final int TUNNEL_CHAMBER = -1;

	/** A floor cell with the y of its floor. */
	public record Spot(int x, int y, int z) {
	}

	/** A glow lichen patch cell: the face is the rock it clings to. */
	public record Lichen(int x, int y, int z, int faceX, int faceZ) {
	}

	/** The exact best route over the sacs on the web-free grid, and the walk of a greedy player. */
	public record Route(int[] order, double approach, double walk, double timed, double[] legs) {
	}

	public final long seed;
	public final int blobCount;
	public final int webPercent;
	public final int sacTarget;
	public final int mouthOffset;
	public final int boneTarget;
	public final int cocoonTarget;
	public final int tunnelLength;
	public final int heartFloor;

	private final byte[] grid = new byte[SIZE_X * SIZE_Y * NEST_Z];
	private final byte[] floor = new byte[N];
	private final byte[] top = new byte[N];
	private final byte[] tag = new byte[N];
	private final boolean[] plug = new boolean[N];
	private final boolean[] pit = new boolean[N];
	private final byte[] lane = new byte[N];
	private final boolean[] reachColumn = new boolean[N];
	private final boolean[] noWeb = new boolean[SIZE_X * SIZE_Y * NEST_Z];
	private final boolean[] stand = new boolean[N];
	private final boolean[] webColumn = new boolean[N];
	private final List<Blob> blobs = new ArrayList<>();
	private List<Sac> sacs = new ArrayList<>();
	private final List<Spot> weavers = new ArrayList<>();
	private final List<Spot> crawlers = new ArrayList<>();
	private final List<Lichen> lichens = new ArrayList<>();
	private final List<Integer> bonePiles = new ArrayList<>();
	private final List<Integer> cocoons = new ArrayList<>();
	private Spot matriarch;
	private Spot core;
	private Spot vault;
	private Spot entrance;
	private int mouthX;
	private int pitX;
	private int pitZ;
	private int pitFloor;
	private int pitBlob;
	private int tunnelEndX;
	private int tunnelEndZ;
	private int tunnelBlob;
	private int coreFront = -1;
	private int vaultFront = -1;
	private int shellCells;
	private int webCells;
	private int attempts;
	private int lichenPatches;
	private Route route;
	private Route greedy;
	private Random rng;

	private NestPlan(final long seed, final Integer webOverride) {
		this.seed = seed;
		this.rng = new Random(seed * 2654435761L ^ 0x5EED1234L);
		int rolledBlobs = rint(3, 5);
		int rolledWeb = rint(MIN_WEB_PERCENT, MAX_WEB_PERCENT);
		int rolledSacs = rint(MIN_SACS, MAX_SACS);
		this.blobCount = rolledBlobs;
		this.webPercent = webOverride != null ? webOverride : rolledWeb;
		this.sacTarget = rolledSacs;
		this.mouthOffset = rint(-3, 3);
		this.boneTarget = rint(4, 9);
		this.cocoonTarget = rint(2, 5);
		this.tunnelLength = rint(6, 9);
		this.heartFloor = rint(2, 3);
		Arrays.fill(this.grid, ROCK);
		Arrays.fill(this.floor, (byte) -1);
		build();
		this.rng = null;
	}

	/** The plan of one seed. Layouts draw it once for each chunk, so the last plans stay cached. */
	public static NestPlan of(final long seed) {
		NestPlan plan = CACHE.get(seed);
		if (plan == null) {
			plan = new NestPlan(seed, null);
			CACHE.put(seed, plan);
		}
		return plan;
	}

	/** A plan with its web share fixed. Tests use it; it is not cached. */
	public static NestPlan withWebPercent(final long seed, final int percent) {
		return new NestPlan(seed, percent);
	}

	// ---- reading the plan ----

	static int index(final int x, final int y, final int z) {
		return (y * NEST_Z + z) * SIZE_X + x;
	}

	static boolean inside(final int x, final int y, final int z) {
		return x >= 0 && x < SIZE_X && y >= 0 && y < SIZE_Y && z >= 0 && z < NEST_Z;
	}

	/** The cell at a spot of the nest frame, or ROCK outside it. */
	public byte cell(final int x, final int y, final int z) {
		return inside(x, y, z) ? this.grid[index(x, y, z)] : ROCK;
	}

	/** The floor of a column: the y of its first air cell, or -1 for rock. */
	public int floorAt(final int x, final int z) {
		return this.floor[z * SIZE_X + x];
	}

	public int topAt(final int x, final int z) {
		return this.floor[z * SIZE_X + x] < 0 ? -1 : this.top[z * SIZE_X + x];
	}

	public int tagAt(final int x, final int z) {
		return this.tag[z * SIZE_X + x];
	}

	public boolean inPit(final int x, final int z) {
		return this.pit[z * SIZE_X + x];
	}

	public boolean inPlug(final int x, final int z) {
		return this.plug[z * SIZE_X + x];
	}

	public boolean isLane(final int x, final int z) {
		return this.lane[z * SIZE_X + x] == 1;
	}

	public boolean standable(final int x, final int z) {
		return this.stand[z * SIZE_X + x];
	}

	public boolean webbed(final int x, final int z) {
		return this.webColumn[z * SIZE_X + x];
	}

	public List<Blob> blobs() {
		return List.copyOf(this.blobs);
	}

	public List<Sac> sacs() {
		return List.copyOf(this.sacs);
	}

	public List<Spot> weavers() {
		return List.copyOf(this.weavers);
	}

	public List<Spot> crawlers() {
		return List.copyOf(this.crawlers);
	}

	public List<Lichen> lichens() {
		return List.copyOf(this.lichens);
	}

	public Spot matriarch() {
		return this.matriarch;
	}

	public Spot core() {
		return this.core;
	}

	public Spot vault() {
		return this.vault;
	}

	public Spot entrance() {
		return this.entrance;
	}

	/** The x of the mouth tunnel's middle line, at the nest wall and out to the tunnel's end. */
	public int mouthX() {
		return this.mouthX;
	}

	public int pitX() {
		return this.pitX;
	}

	public int pitZ() {
		return this.pitZ;
	}

	public int pitFloor() {
		return this.pitFloor;
	}

	public int pitBlob() {
		return this.pitBlob;
	}

	/** The cell where the side tunnel ends. */
	public Spot tunnelEnd() {
		int c = this.tunnelEndZ * SIZE_X + this.tunnelEndX;
		return new Spot(this.tunnelEndX, Math.max(0, this.floor[c]), this.tunnelEndZ);
	}

	public int tunnelBlob() {
		return this.tunnelBlob;
	}

	/** How many lichen patches the nest holds. */
	public int lichenPatches() {
		return this.lichenPatches;
	}

	public int bonePileCount() {
		return this.bonePiles.size();
	}

	public int cocoonCount() {
		return this.cocoons.size();
	}

	/** Web cells over shell cells, where a shell cell is an air cell that touches rock. Tunnel plug cells do not count. */
	public double webRatio() {
		return this.shellCells == 0 ? 0.0 : (double) this.webCells / this.shellCells;
	}

	public int shellCells() {
		return this.shellCells;
	}

	public int webCells() {
		return this.webCells;
	}

	public int pickAttempts() {
		return this.attempts;
	}

	public Route route() {
		return this.route;
	}

	public Route greedy() {
		return this.greedy;
	}

	/** True when a web-free walk joins the entrance to a floor column. */
	public boolean reachableWithoutWeb(final int x, final int z) {
		boolean[] seen = webFreeReach();
		return seen[z * SIZE_X + x];
	}

	/** The cell the player stands in to reach the core, and the one in front of the vault. */
	public Spot coreFront() {
		return this.coreFront < 0 ? null : spotOf(this.coreFront);
	}

	public Spot vaultFront() {
		return this.vaultFront < 0 ? null : spotOf(this.vaultFront);
	}

	private Spot spotOf(final int column) {
		return new Spot(column % SIZE_X, this.floor[column], column / SIZE_X);
	}

	/** A short stable description of the plan, for variety counts. */
	public String signature() {
		StringBuilder builder = new StringBuilder();
		builder.append(this.blobCount).append(':').append(this.webPercent).append(':').append(this.sacs.size());
		for (Sac sac : this.sacs) {
			builder.append(';').append(sac.x).append(',').append(sac.y).append(',').append(sac.z);
		}
		builder.append('#').append(Arrays.hashCode(this.grid));
		return builder.toString();
	}

	public int gridHash() {
		return Arrays.hashCode(this.grid);
	}

	// ---- building ----

	private int rint(final int low, final int high) {
		return low + this.rng.nextInt(high - low + 1);
	}

	private static int hashCell(final int a, final int b, final int c) {
		int h = a * 374761393 + b * 668265263 + c * 1274126177;
		h = (h ^ (h >>> 13)) * 1274126177;
		return h ^ (h >>> 16);
	}

	private static double hash01(final int a, final int b, final int c) {
		return (hashCell(a, b, c) & 0xFFFFFFFFL) / 4294967296.0;
	}

	private static double valueNoise(final double x, final double z, final int salt) {
		int xi = (int) Math.floor(x);
		int zi = (int) Math.floor(z);
		double fx = x - xi;
		double fz = z - zi;
		fx = fx * fx * (3 - 2 * fx);
		fz = fz * fz * (3 - 2 * fz);
		double a = hash01(xi, zi, salt);
		double b = hash01(xi + 1, zi, salt);
		double c = hash01(xi, zi + 1, salt);
		double d = hash01(xi + 1, zi + 1, salt);
		return a + (b - a) * fx + (c - a) * fz + (a - b - c + d) * fx * fz;
	}

	private static boolean blocker(final byte id) {
		return id == ROCK || id == SAC || id == BONE || id == SKULL || id == COCOON || id == CORE || id == VAULT;
	}

	private boolean isRock(final int x, final int y, final int z) {
		return !inside(x, y, z) || this.grid[index(x, y, z)] == ROCK;
	}

	private void carve(final int cx, final int cz, final int f, final int h, final int columnTag) {
		if (cx < 0 || cx >= SIZE_X || cz < 0 || cz >= NEST_Z) {
			return;
		}
		int c = cz * SIZE_X + cx;
		int t = Math.min(MAX_AIR_Y, f + h - 1);
		if (this.floor[c] < 0) {
			this.floor[c] = (byte) f;
			this.top[c] = (byte) t;
		} else {
			this.floor[c] = (byte) Math.min(this.floor[c], f);
			this.top[c] = (byte) Math.max(this.top[c], t);
		}
		if (columnTag != 0 && this.tag[c] == 0) {
			this.tag[c] = (byte) columnTag;
		}
	}

	/** Carves a round brush along a line. Coordinates are in cells, with cell centres at .5. */
	private void brush(final double x0, final double z0, final double x1, final double z1, final double radius, final int f, final int h, final int columnTag, final double[] firstDeep) {
		int steps = (int) Math.ceil(Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0)) * 2) + 1;
		for (int j = 0; j <= steps; j++) {
			double px = x0 + (x1 - x0) * j / steps;
			double pz = z0 + (z1 - z0) * j / steps;
			if (firstDeep != null && firstDeep[0] < 0 && pz >= 3) {
				firstDeep[0] = Math.floor(px);
				firstDeep[1] = Math.floor(pz);
			}
			for (int iz = (int) Math.floor(pz) - 2; iz <= (int) Math.floor(pz) + 2; iz++) {
				for (int ix = (int) Math.floor(px) - 2; ix <= (int) Math.floor(px) + 2; ix++) {
					if (Math.hypot(ix + .5 - px, iz + .5 - pz) <= radius) {
						carve(ix, iz, f, h, columnTag);
					}
				}
			}
		}
	}

	private void build() {
		carveChambers();
		joinChambers();
		Blob heart = this.blobs.get(0);
		int hx = heart.x();
		int hz = heart.z();
		double[] entry = carveMouth();
		for (int i = 0; i < N; i++) {
			if (this.floor[i] < 0) {
				continue;
			}
			int depth = i / SIZE_X;
			if (depth < 2 || (depth < 3 && this.tag[i] == TAG_MOUTH)) {
				this.plug[i] = true;
			}
		}
		carveSideTunnel(hx, hz);
		int[] vaultSpot = carveNook(heart, hx, hz);
		carvePit(hx, hz, vaultSpot);
		writeGrid(hx, hz, vaultSpot);
		computeStand();
		this.entrance = new Spot((int) entry[0], 0, (int) entry[1]);
		if (!this.stand[this.entrance.z() * SIZE_X + this.entrance.x()]) {
			double best = 1e9;
			for (int i = 0; i < N; i++) {
				if (!this.stand[i] || this.tag[i] != TAG_MOUTH) {
					continue;
				}
				double d = Math.hypot(i % SIZE_X - entry[0], i / SIZE_X - entry[1]);
				if (d < best) {
					best = d;
					this.entrance = new Spot(i % SIZE_X, 0, i / SIZE_X);
				}
			}
		}
		this.entrance = new Spot(this.entrance.x(), this.floor[this.entrance.z() * SIZE_X + this.entrance.x()], this.entrance.z());
		pickSacs();
		planLanes();
		placeMarkers(hx, hz);
		placeBones(hx, hz);
		hangCocoons();
		placeLichen();
		spinWebs();
		computeStand();
		this.route = planRoute();
		this.greedy = greedyRoute();
	}

	private void carveChambers() {
		int hx = 15 + rint(-2, 2);
		int hz = 15 + rint(-2, 2);
		this.blobs.add(new Blob(hx, hz, 5 + this.rng.nextDouble() * 1.5, 5 + this.rng.nextDouble() * 1.5, this.heartFloor, 7 + this.rng.nextDouble() * 1.5));
		double base = this.rng.nextDouble() * Math.PI * 2;
		for (int k = 1; k < this.blobCount; k++) {
			double angle = base + (k - 1) * 2 * Math.PI / (this.blobCount - 1) + (this.rng.nextDouble() - .5) * .6;
			double dist = 9 + this.rng.nextDouble() * 3.5;
			double rx = 3.3 + this.rng.nextDouble() * 2;
			double rz = 3.3 + this.rng.nextDouble() * 2;
			int bx = (int) Math.round(hx + Math.cos(angle) * dist);
			int bz = (int) Math.round(hz + Math.sin(angle) * dist);
			bx = Math.max((int) Math.ceil(2 + rx), Math.min((int) Math.floor(27 - rx), bx));
			bz = Math.max((int) Math.ceil(2 + rz), Math.min((int) Math.floor(27 - rz), bz));
			this.blobs.add(new Blob(bx, bz, rx, rz, rint(2, 3), 5 + this.rng.nextDouble() * 2.5));
		}
		int salt = (int) this.seed;
		for (int bi = 0; bi < this.blobs.size(); bi++) {
			Blob b = this.blobs.get(bi);
			for (int z = 2; z <= 27; z++) {
				for (int x = 2; x <= 27; x++) {
					double ddx = (x - b.x()) / b.rx();
					double ddz = (z - b.z()) / b.rz();
					double d = (ddx * ddx + ddz * ddz) * (0.8 + 0.5 * valueNoise(x * .45 + bi * 13.1, z * .45, salt));
					if (d >= 1) {
						continue;
					}
					int h = 2 + (int) Math.round((b.height() - 2) * Math.sqrt(1 - d)) + (valueNoise(x * .8 + bi * 5.7, z * .8, salt + 9) > .72 ? 1 : 0);
					carve(x, z, b.floor(), Math.max(2, h), 1 + bi);
				}
			}
		}
	}

	/** Necks join any chambers that did not overlap the heart. */
	private void joinChambers() {
		Blob heart = this.blobs.get(0);
		for (int guard = 0; guard < 8; guard++) {
			int[] comp = new int[N];
			Arrays.fill(comp, -1);
			int count = 0;
			for (int c = 0; c < N; c++) {
				if (this.floor[c] < 0 || comp[c] >= 0) {
					continue;
				}
				ArrayList<Integer> stack = new ArrayList<>();
				stack.add(c);
				comp[c] = count;
				while (!stack.isEmpty()) {
					int u = stack.remove(stack.size() - 1);
					for (int d = 0; d < 4; d++) {
						int nx = u % SIZE_X + DX4[d];
						int nz = u / SIZE_X + DZ4[d];
						if (nx < 0 || nx >= SIZE_X || nz < 0 || nz >= NEST_Z) {
							continue;
						}
						int n = nz * SIZE_X + nx;
						if (this.floor[n] >= 0 && comp[n] < 0) {
							comp[n] = count;
							stack.add(n);
						}
					}
				}
				count++;
			}
			if (count <= 1) {
				return;
			}
			int heartComp = comp[heart.z() * SIZE_X + heart.x()];
			List<Integer> edgeA = new ArrayList<>();
			List<Integer> edgeB = new ArrayList<>();
			for (int e = 0; e < N; e++) {
				if (this.floor[e] < 0) {
					continue;
				}
				int ex = e % SIZE_X;
				int ez = e / SIZE_X;
				boolean open = false;
				for (int d = 0; d < 4; d++) {
					int nx = ex + DX4[d];
					int nz = ez + DZ4[d];
					if (nx < 0 || nx >= SIZE_X || nz < 0 || nz >= NEST_Z || this.floor[nz * SIZE_X + nx] < 0) {
						open = true;
					}
				}
				if (open) {
					(comp[e] == heartComp ? edgeA : edgeB).add(e);
				}
			}
			double best = 1e9;
			int ba = -1;
			int bb = -1;
			for (int a : edgeA) {
				for (int b : edgeB) {
					double dd = Math.hypot(a % SIZE_X - b % SIZE_X, a / SIZE_X - b / SIZE_X);
					if (dd < best) {
						best = dd;
						ba = a;
						bb = b;
					}
				}
			}
			brush(ba % SIZE_X + .5, ba / SIZE_X + .5, bb % SIZE_X + .5, bb / SIZE_X + .5, 1.5, 2, 3, TAG_NECK, null);
		}
	}

	/** The 3 wide mouth, carved from the south wall to the nearest chamber. Returns the first cell behind the plug. */
	private double[] carveMouth() {
		this.mouthX = 15 + this.mouthOffset;
		int target = -1;
		double best = 1e9;
		for (int i = 0; i < N; i++) {
			if (this.floor[i] < 0 || i / SIZE_X < 5) {
				continue;
			}
			double d = Math.hypot(i % SIZE_X - this.mouthX, i / SIZE_X);
			if (d < best) {
				best = d;
				target = i;
			}
		}
		double[] entry = {-1, -1};
		brush(this.mouthX + .5, .5, target % SIZE_X + .5, target / SIZE_X + .5, 1.5, TUNNEL_FLOOR, TUNNEL_HEIGHT, TAG_MOUTH, entry);
		return entry;
	}

	private void carveSideTunnel(final int hx, final int hz) {
		this.tunnelBlob = 1 + this.rng.nextInt(this.blobCount - 1);
		Blob b = this.blobs.get(this.tunnelBlob);
		int vx = b.x() - hx;
		int vz = b.z() - hz;
		int sx = vx == 0 ? 1 : Integer.signum(vx);
		int sz = vz == 0 ? 1 : Integer.signum(vz);
		int[][] dirs = Math.abs(vx) >= Math.abs(vz)
			? new int[][] {{sx, 0}, {0, sz}, {0, -sz}, {-sx, 0}}
			: new int[][] {{0, sz}, {sx, 0}, {-sx, 0}, {0, -sz}};
		double[] p0 = new double[2];
		double[] p1 = new double[2];
		for (int j = 0; j < 4; j++) {
			int[] d = dirs[j];
			double r = d[0] != 0 ? b.rx() : b.rz();
			double ax = d[0] != 0 ? b.x() + .5 : b.x() + 1.0;
			double az = d[0] != 0 ? b.z() + 1.0 : b.z() + .5;
			p0[0] = ax + d[0] * r * .6;
			p0[1] = az + d[1] * r * .6;
			double ex = Math.max(3, Math.min(27, p0[0] + d[0] * this.tunnelLength));
			double ez = Math.max(3, Math.min(27, p0[1] + d[1] * this.tunnelLength));
			p1[0] = ex;
			p1[1] = ez;
			if (Math.hypot(ex - p0[0], ez - p0[1]) >= 4.5 || j == 3) {
				break;
			}
		}
		this.tunnelEndX = (int) Math.floor(p1[0]);
		this.tunnelEndZ = (int) Math.floor(p1[1]);
		brush(p0[0], p0[1], p1[0], p1[1], 1.0, 2, 3, TAG_SIDE_TUNNEL, null);
	}

	/** The vault nook: a 3 wide niche in the heart's north wall, opposite the mouth. */
	private int[] carveNook(final Blob heart, final int hx, final int hz) {
		int hr = (int) Math.round(heart.rz());
		int vt = Math.min(hr + 2, 27 - hz);
		for (int t = Math.max(2, vt - 3); t <= vt; t++) {
			for (int p = -1; p <= 1; p++) {
				int x = hx + p;
				int z = hz + t;
				if (x >= 2 && x <= 27 && z >= 2 && z <= 27) {
					carve(x, z, heart.floor(), 3, TAG_NOOK);
				}
			}
		}
		return new int[] {hx, hz + vt};
	}

	/** The brood pit: a flat 5 x 5 floor with five layers of air, in the biggest side chamber. */
	private void carvePit(final int hx, final int hz, final int[] vaultSpot) {
		List<Integer> order = new ArrayList<>();
		for (int k = 1; k < this.blobCount; k++) {
			order.add(k);
		}
		order.sort((a, b) -> Double.compare(Math.min(this.blobs.get(b).rx(), this.blobs.get(b).rz()), Math.min(this.blobs.get(a).rx(), this.blobs.get(a).rz())));
		int chosen = -1;
		for (int k : order) {
			Blob b = this.blobs.get(k);
			int px = Math.max(3, Math.min(26, b.x()));
			int pz = Math.max(3, Math.min(26, b.z()));
			if (Math.hypot(px - vaultSpot[0], pz - vaultSpot[1]) < 5 || Math.hypot(px - hx, pz - hz) < 5) {
				continue;
			}
			chosen = k;
			break;
		}
		if (chosen < 0) {
			chosen = order.get(0);
		}
		Blob b = this.blobs.get(chosen);
		this.pitBlob = chosen;
		this.pitX = Math.max(3, Math.min(26, b.x()));
		this.pitZ = Math.max(3, Math.min(26, b.z()));
		int center = this.pitZ * SIZE_X + this.pitX;
		this.pitFloor = this.floor[center] >= 0 ? this.floor[center] : b.floor();
		for (int iz = -2; iz <= 2; iz++) {
			for (int ix = -2; ix <= 2; ix++) {
				int c = (this.pitZ + iz) * SIZE_X + this.pitX + ix;
				boolean had = this.floor[c] >= 0;
				this.floor[c] = (byte) this.pitFloor;
				this.top[c] = (byte) Math.min(MAX_AIR_Y, Math.max(had ? this.top[c] : 0, this.pitFloor + 4));
				this.pit[c] = true;
				if (this.tag[c] == 0) {
					this.tag[c] = TAG_PIT;
				}
			}
		}
	}

	private void writeGrid(final int hx, final int hz, final int[] vaultSpot) {
		for (int z = 0; z < NEST_Z; z++) {
			for (int x = 0; x < SIZE_X; x++) {
				int i = z * SIZE_X + x;
				if (this.floor[i] < 0) {
					continue;
				}
				for (int y = this.floor[i]; y <= this.top[i]; y++) {
					this.grid[index(x, y, z)] = AIR;
				}
			}
		}
		this.core = new Spot(hx, this.floor[hz * SIZE_X + hx], hz);
		this.vault = new Spot(vaultSpot[0], this.floor[vaultSpot[1] * SIZE_X + vaultSpot[0]], vaultSpot[1]);
		this.grid[index(this.core.x(), this.core.y(), this.core.z())] = CORE;
		this.grid[index(this.vault.x(), this.vault.y(), this.vault.z())] = VAULT;
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				int c = (hz + dz) * SIZE_X + hx + dx;
				if (this.floor[c] >= 0) {
					this.grid[index(hx + dx, this.floor[c] - 1, hz + dz)] = BONE;
				}
			}
		}
		for (int i = 0; i < N; i++) {
			if (this.pit[i]) {
				this.grid[index(i % SIZE_X, this.floor[i] - 1, i / SIZE_X)] = BONE;
			}
		}
	}

	private void computeStand() {
		for (int z = 0; z < NEST_Z; z++) {
			for (int x = 0; x < SIZE_X; x++) {
				int i = z * SIZE_X + x;
				this.stand[i] = false;
				this.webColumn[i] = false;
				int f = this.floor[i];
				if (f < 0 || this.top[i] - f < 1) {
					continue;
				}
				byte a = this.grid[index(x, f, z)];
				byte b = this.grid[index(x, f + 1, z)];
				if (blocker(a) || blocker(b)) {
					continue;
				}
				this.stand[i] = true;
				this.webColumn[i] = a == WEB || b == WEB;
			}
		}
	}

	// ---- egg sacs ----

	private int chamberOf(final int x, final int z) {
		int i = z * SIZE_X + x;
		int t = this.tag[i];
		if (t == TAG_SIDE_TUNNEL) {
			return TUNNEL_CHAMBER;
		}
		if (t >= 1 && t <= 5) {
			return t - 1;
		}
		if (t == TAG_PIT) {
			return this.pitBlob;
		}
		int best = 0;
		double bestValue = 1e9;
		for (int j = 0; j < this.blobs.size(); j++) {
			Blob b = this.blobs.get(j);
			double d = Math.pow((x - b.x()) / b.rx(), 2) + Math.pow((z - b.z()) / b.rz(), 2);
			if (d < bestValue) {
				bestValue = d;
				best = j;
			}
		}
		return best;
	}

	private void findReach(final Sac sac) {
		if (sac.reachKnown) {
			return;
		}
		sac.reachKnown = true;
		double cx = sac.x + .5;
		double cy = sac.y + .5;
		double cz = sac.z + .5;
		double bestDistance = 1e9;
		for (int z = Math.max(0, sac.z - 5); z <= Math.min(NEST_Z - 1, sac.z + 5); z++) {
			for (int x = Math.max(0, sac.x - 5); x <= Math.min(SIZE_X - 1, sac.x + 5); x++) {
				int i = z * SIZE_X + x;
				if (!this.stand[i]) {
					continue;
				}
				double ex = x + .5;
				double ey = this.floor[i] + EYE;
				double ez = z + .5;
				double d = Math.sqrt((cx - ex) * (cx - ex) + (cy - ey) * (cy - ey) + (cz - ez) * (cz - ez));
				if (d > REACH || d >= bestDistance || !lineClear(ex, ey, ez, cx, cy, cz, sac)) {
					continue;
				}
				bestDistance = d;
				sac.reachFound = true;
				sac.reachX = x;
				sac.reachZ = z;
				sac.reachFloor = this.floor[i];
				sac.reachDistance = d;
			}
		}
	}

	private boolean lineClear(final double ex, final double ey, final double ez, final double cx, final double cy, final double cz, final Sac sac) {
		int steps = (int) Math.ceil(Math.sqrt((cx - ex) * (cx - ex) + (cy - ey) * (cy - ey) + (cz - ez) * (cz - ez)) / 0.2);
		for (int j = 0; j <= steps; j++) {
			double px = ex + (cx - ex) * j / steps;
			double py = ey + (cy - ey) * j / steps;
			double pz = ez + (cz - ez) * j / steps;
			int xi = (int) Math.floor(px);
			int yi = (int) Math.floor(py);
			int zi = (int) Math.floor(pz);
			if (xi == sac.x && yi == sac.y && zi == sac.z) {
				continue;
			}
			if (isRock(xi, yi, zi)) {
				return false;
			}
		}
		return true;
	}

	private int[] sightCells(final Sac sac) {
		java.util.TreeSet<Integer> out = new java.util.TreeSet<>();
		double ex = sac.reachX + .5;
		double ez = sac.reachZ + .5;
		double ey = sac.reachFloor + EYE;
		double dx = sac.x + .5 - ex;
		double dy = sac.y + .5 - ey;
		double dz = sac.z + .5 - ez;
		int steps = (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) / 0.2);
		for (int j = 0; j <= steps; j++) {
			int xi = (int) Math.floor(ex + dx * j / steps);
			int yi = (int) Math.floor(ey + dy * j / steps);
			int zi = (int) Math.floor(ez + dz * j / steps);
			if (inside(xi, yi, zi) && !(xi == sac.x && yi == sac.y && zi == sac.z)) {
				out.add(index(xi, yi, zi));
			}
		}
		return out.stream().mapToInt(Integer::intValue).toArray();
	}

	private List<Sac> candidates() {
		List<Sac> out = new ArrayList<>();
		for (int z = 0; z < NEST_Z; z++) {
			for (int x = 0; x < SIZE_X; x++) {
				int i = z * SIZE_X + x;
				int f = this.floor[i];
				if (f < 0 || this.tag[i] == TAG_MOUTH || this.tag[i] == TAG_NOOK) {
					continue;
				}
				int t = this.top[i];
				int chamber = chamberOf(x, z);
				if (this.grid[index(x, t, z)] == AIR && t >= f + 2 && isRock(x, t + 1, z)) {
					out.add(new Sac(x, t, z, true, 0, 1, 0, chamber));
				}
				for (int y = f + 2; y <= Math.min(t, f + 3); y++) {
					if (this.grid[index(x, y, z)] != AIR) {
						continue;
					}
					for (int d = 0; d < 4; d++) {
						if (isRock(x + DX4[d], y, z + DZ4[d])) {
							out.add(new Sac(x, y, z, false, DX4[d], 0, DZ4[d], chamber));
							break;
						}
					}
				}
			}
		}
		return out;
	}

	private static double distance(final Sac a, final Sac b) {
		return Math.sqrt((a.x - b.x) * (double) (a.x - b.x) + (a.y - b.y) * (double) (a.y - b.y) + (a.z - b.z) * (double) (a.z - b.z));
	}

	private double nearestPicked(final Sac sac, final List<Sac> picked) {
		double m = 1e9;
		for (Sac other : picked) {
			m = Math.min(m, distance(sac, other));
		}
		return m;
	}

	private record Scored(Sac sac, double value) {
	}

	private Sac take(final List<Sac> pool, final List<Sac> picked, final double spacing) {
		List<Scored> scored = new ArrayList<>();
		for (Sac sac : pool) {
			if (picked.contains(sac)) {
				continue;
			}
			double m = nearestPicked(sac, picked);
			if (m < spacing) {
				continue;
			}
			scored.add(new Scored(sac, Math.min(m, 12) + this.rng.nextDouble() * 3));
		}
		scored.sort((a, b) -> Double.compare(b.value(), a.value()));
		for (Scored s : scored) {
			findReach(s.sac());
			if (s.sac().reachFound) {
				return s.sac();
			}
		}
		return null;
	}

	private Sac pick(final List<Sac> pool, final List<Sac> picked, final Boolean preferCeiling) {
		Sac chosen = null;
		if (preferCeiling != null) {
			List<Sac> preferred = pool.stream().filter(s -> s.ceiling == preferCeiling).toList();
			chosen = take(preferred, picked, MIN_SAC_GAP);
			if (chosen == null) {
				chosen = take(preferred, picked, 3.5);
			}
		}
		if (chosen == null) {
			chosen = take(pool, picked, MIN_SAC_GAP);
		}
		if (chosen == null) {
			chosen = take(pool, picked, 3.5);
		}
		if (chosen == null) {
			chosen = take(pool, picked, 1.5);
		}
		if (chosen != null) {
			picked.add(chosen);
		}
		return chosen;
	}

	private List<Sac> selectAll(final List<Sac> cands, final List<Sac> tunnelPool) {
		List<Sac> picked = new ArrayList<>();
		if (!tunnelPool.isEmpty()) {
			double endX = this.tunnelEndX + .5;
			double endZ = this.tunnelEndZ + .5;
			List<Scored> scored = new ArrayList<>();
			for (Sac sac : tunnelPool) {
				scored.add(new Scored(sac, -Math.hypot(sac.x + .5 - endX, sac.z + .5 - endZ) + this.rng.nextDouble() * .5 - (sac.ceiling ? 1.5 : 0)));
			}
			scored.sort((a, b) -> Double.compare(b.value(), a.value()));
			for (Scored s : scored) {
				findReach(s.sac());
				if (s.sac().reachFound) {
					picked.add(s.sac());
					break;
				}
			}
		}
		boolean[] preferCeiling = {true, false, false, true, false};
		for (int k = 0; k < this.blobCount; k++) {
			int chamber = k;
			pick(cands.stream().filter(s -> s.chamber == chamber).toList(), picked, preferCeiling[k]);
		}
		for (int guard = 0; picked.size() < this.sacTarget && guard < 60; guard++) {
			long ceilings = picked.stream().filter(s -> s.ceiling).count();
			long walls = picked.stream().filter(s -> !s.ceiling).count();
			Boolean need = ceilings < 2 ? Boolean.TRUE : walls < 3 ? Boolean.FALSE : null;
			List<Sac> pool = cands.stream().filter(s -> !picked.contains(s) && (need == null || s.ceiling == need)).toList();
			if (pick(pool, picked, null) == null && need != null) {
				pick(cands.stream().filter(s -> !picked.contains(s)).toList(), picked, null);
			}
		}
		return picked;
	}

	private void pickSacs() {
		List<Sac> cands = candidates();
		List<Sac> tunnelPool = cands.stream().filter(Sac::tunnel).toList();
		List<Sac> best = null;
		double bestWalk = 0;
		int tries = 0;
		for (int attempt = 0; attempt < PICK_ATTEMPTS; attempt++) {
			List<Sac> set = selectAll(cands, tunnelPool);
			tries++;
			for (int j = 0; j < set.size(); j++) {
				set.get(j).id = j + 1;
			}
			this.sacs = set;
			double walk = greedyRoute().timed();
			if (best == null || walk < bestWalk) {
				best = set;
				bestWalk = walk;
			}
			if (walk <= GREEDY_BUDGET) {
				break;
			}
		}
		this.attempts = tries;
		this.sacs = best;
		for (int j = 0; j < this.sacs.size(); j++) {
			Sac sac = this.sacs.get(j);
			sac.id = j + 1;
			this.grid[index(sac.x, sac.y, sac.z)] = SAC;
			this.reachColumn[sac.reachZ * SIZE_X + sac.reachX] = true;
		}
		for (Sac sac : this.sacs) {
			sac.sight = sightCells(sac);
			for (int c : sac.sight) {
				this.noWeb[c] = true;
			}
		}
	}

	// ---- routes and lanes ----

	private record Paths(double[] dist, int[] prev) {
	}

	private Paths dijkstra(final int source, final int[] targets) {
		double[] dist = new double[N];
		int[] prev = new int[N];
		Arrays.fill(dist, 1e9);
		Arrays.fill(prev, -1);
		if (source < 0 || !this.stand[source]) {
			return new Paths(dist, prev);
		}
		boolean[] wanted = new boolean[N];
		int want = 0;
		if (targets != null) {
			for (int t : targets) {
				if (!wanted[t]) {
					wanted[t] = true;
					want++;
				}
			}
		}
		java.util.PriorityQueue<double[]> heap = new java.util.PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
		dist[source] = 0;
		heap.add(new double[] {0, source});
		while (!heap.isEmpty()) {
			double[] top = heap.poll();
			int u = (int) top[1];
			double du = top[0];
			if (du > dist[u]) {
				continue;
			}
			if (targets != null && wanted[u]) {
				wanted[u] = false;
				if (--want <= 0) {
					break;
				}
			}
			int ux = u % SIZE_X;
			int uz = u / SIZE_X;
			int fu = this.floor[u];
			for (int d = 0; d < 8; d++) {
				int nx = ux + DX8[d];
				int nz = uz + DZ8[d];
				if (nx < 0 || nx >= SIZE_X || nz < 0 || nz >= NEST_Z) {
					continue;
				}
				int n = nz * SIZE_X + nx;
				if (!this.stand[n]) {
					continue;
				}
				int df = this.floor[n] - fu;
				if (df > 1 || df < -1) {
					continue;
				}
				if (d >= 4) {
					int a = uz * SIZE_X + nx;
					int b = nz * SIZE_X + ux;
					if (!this.stand[a] || !this.stand[b] || Math.abs(this.floor[a] - fu) > 1 || Math.abs(this.floor[b] - fu) > 1) {
						continue;
					}
				}
				double nd = du + (d >= 4 ? 1.4142 : 1) / SPEED * (this.webColumn[n] ? WEB_FACTOR : 1) + (df > 0 ? STEP_UP : 0);
				if (nd < dist[n]) {
					dist[n] = nd;
					prev[n] = u;
					heap.add(new double[] {nd, n});
				}
			}
		}
		return new Paths(dist, prev);
	}

	private static List<Integer> pathTo(final int[] prev, final int source, final int target) {
		List<Integer> path = new ArrayList<>();
		int c = target;
		for (int guard = 0; c >= 0 && guard < 2000; guard++) {
			path.add(c);
			if (c == source) {
				break;
			}
			c = prev[c];
		}
		if (path.isEmpty() || path.get(path.size() - 1) != source) {
			return List.of();
		}
		Collections.reverse(path);
		return path;
	}

	private int[] reachColumns() {
		return this.sacs.stream().mapToInt(s -> s.reachZ * SIZE_X + s.reachX).toArray();
	}

	private int entranceColumn() {
		return this.entrance.z() * SIZE_X + this.entrance.x();
	}

	private final List<List<Integer>> routePaths = new ArrayList<>();

	/** The exact shortest tour over the sacs, with a free start and a free end. */
	private Route planRoute() {
		int n = this.sacs.size();
		int source = entranceColumn();
		int[] reach = reachColumns();
		Paths fromEntrance = dijkstra(source, reach);
		Paths[] res = new Paths[n];
		double[][] dm = new double[n][n];
		for (int i = 0; i < n; i++) {
			res[i] = dijkstra(reach[i], reach);
		}
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				dm[i][j] = i == j ? 0 : res[i].dist()[reach[j]];
			}
		}
		int full = (1 << n) - 1;
		double[] dp = new double[(1 << n) * n];
		short[] par = new short[(1 << n) * n];
		Arrays.fill(dp, Double.POSITIVE_INFINITY);
		Arrays.fill(par, (short) -1);
		for (int i = 0; i < n; i++) {
			dp[(1 << i) * n + i] = 0;
		}
		for (int mask = 1; mask <= full; mask++) {
			for (int last = 0; last < n; last++) {
				if ((mask & (1 << last)) == 0) {
					continue;
				}
				double v = dp[mask * n + last];
				if (v == Double.POSITIVE_INFINITY) {
					continue;
				}
				for (int next = 0; next < n; next++) {
					if ((mask & (1 << next)) != 0) {
						continue;
					}
					int nm = mask | (1 << next);
					double c = v + dm[last][next];
					if (c < dp[nm * n + next]) {
						dp[nm * n + next] = c;
						par[nm * n + next] = (short) last;
					}
				}
			}
		}
		int bestEnd = 0;
		double best = Double.POSITIVE_INFINITY;
		for (int i = 0; i < n; i++) {
			if (dp[full * n + i] < best) {
				best = dp[full * n + i];
				bestEnd = i;
			}
		}
		int[] order = new int[n];
		int mask = full;
		int cur = bestEnd;
		for (int k = n - 1; k >= 0; k--) {
			order[k] = cur;
			int p = par[mask * n + cur];
			mask &= ~(1 << cur);
			cur = p;
		}
		this.routePaths.clear();
		this.routePaths.add(pathTo(fromEntrance.prev(), source, reach[order[0]]));
		double[] legs = new double[Math.max(0, n - 1)];
		for (int i = 0; i < n - 1; i++) {
			this.routePaths.add(pathTo(res[order[i]].prev(), reach[order[i]], reach[order[i + 1]]));
			legs[i] = dm[order[i]][order[i + 1]];
		}
		return new Route(order, fromEntrance.dist()[reach[order[0]]], best, best + BREAK_SECONDS * (n - 1), legs);
	}

	/** The walk of a player who always goes to the nearest sac next. */
	private Route greedyRoute() {
		int n = this.sacs.size();
		int[] reach = reachColumns();
		int pos = entranceColumn();
		Paths paths = dijkstra(pos, reach);
		List<Integer> left = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			left.add(i);
		}
		int[] order = new int[n];
		double walk = 0;
		double approach = 0;
		int taken = 0;
		while (!left.isEmpty()) {
			int bestIndex = -1;
			double bestValue = Double.POSITIVE_INFINITY;
			for (int j : left) {
				if (paths.dist()[reach[j]] < bestValue) {
					bestValue = paths.dist()[reach[j]];
					bestIndex = j;
				}
			}
			if (taken == 0) {
				approach = bestValue;
			} else {
				walk += bestValue;
			}
			order[taken++] = bestIndex;
			left.remove(Integer.valueOf(bestIndex));
			pos = reach[bestIndex];
			paths = dijkstra(pos, reach);
		}
		return new Route(order, approach, walk, walk + BREAK_SECONDS * (n - 1), new double[0]);
	}

	private int nearestStand(final int x, final int z) {
		Paths fromEntrance = dijkstra(entranceColumn(), null);
		int best = -1;
		double bestValue = 1e18;
		for (int dz = -2; dz <= 2; dz++) {
			for (int dx = -2; dx <= 2; dx++) {
				int nx = x + dx;
				int nz = z + dz;
				if (nx < 0 || nx >= SIZE_X || nz < 0 || nz >= NEST_Z || !this.stand[nz * SIZE_X + nx]) {
					continue;
				}
				double v = fromEntrance.dist()[nz * SIZE_X + nx] + Math.hypot(dx, dz) * 0.01;
				if (v < bestValue) {
					bestValue = v;
					best = nz * SIZE_X + nx;
				}
			}
		}
		return best;
	}

	private void addLane(final List<Integer> path) {
		for (int j = 0; j < path.size(); j++) {
			int c = path.get(j);
			this.lane[c] = 1;
			if (j > 0) {
				int p = path.get(j - 1);
				int px = p % SIZE_X;
				int pz = p / SIZE_X;
				int cx = c % SIZE_X;
				int cz = c / SIZE_X;
				if (px != cx && pz != cz) {
					this.lane[pz * SIZE_X + cx] = (byte) Math.max(this.lane[pz * SIZE_X + cx], 1);
					this.lane[cz * SIZE_X + px] = (byte) Math.max(this.lane[cz * SIZE_X + px], 1);
				}
			}
		}
	}

	/** Reserves the cells of the best route, and the paths to the core and the vault, as web-free lanes. */
	private void planLanes() {
		computeStand();
		Route best = planRoute();
		for (List<Integer> path : this.routePaths) {
			addLane(path);
		}
		this.coreFront = nearestStand(this.core.x(), this.core.z());
		this.vaultFront = nearestStand(this.vault.x(), this.vault.z());
		Paths fromEntrance = dijkstra(entranceColumn(), null);
		addLane(pathTo(fromEntrance.prev(), entranceColumn(), this.coreFront));
		addLane(pathTo(fromEntrance.prev(), entranceColumn(), this.vaultFront));
		this.laneSource = fromEntrance;
		this.route = best;
	}

	private Paths laneSource;

	// ---- markers, bones, cocoons, lichen, webs ----

	private boolean footFree(final int c, final int width) {
		int cx = c % SIZE_X;
		int cz = c / SIZE_X;
		for (int iz = 0; iz < width; iz++) {
			for (int ix = 0; ix < width; ix++) {
				if (cx + ix >= SIZE_X || cz + iz >= NEST_Z) {
					return false;
				}
				int n = (cz + iz) * SIZE_X + cx + ix;
				if (!this.stand[n] || this.floor[n] != this.floor[c]) {
					return false;
				}
			}
		}
		return true;
	}

	private void reserve(final int c, final int width) {
		int cx = c % SIZE_X;
		int cz = c / SIZE_X;
		for (int iz = -1; iz <= width; iz++) {
			for (int ix = -1; ix <= width; ix++) {
				int xx = cx + ix;
				int zz = cz + iz;
				if (xx < 0 || xx >= SIZE_X || zz < 0 || zz >= NEST_Z) {
					continue;
				}
				int n = zz * SIZE_X + xx;
				if (this.floor[n] < 0) {
					continue;
				}
				for (int y = 0; y <= 2; y++) {
					this.noWeb[index(xx, this.floor[n] + y, zz)] = true;
				}
				this.lane[n] = (byte) Math.max(this.lane[n], 2);
			}
		}
	}

	private List<Integer> farthestPoints(final List<Integer> cells, final int count, final List<int[]> seeds, final double minDistance) {
		List<Integer> out = new ArrayList<>();
		List<int[]> points = new ArrayList<>(seeds);
		for (int j = 0; j < count; j++) {
			int best = -1;
			double bestValue = -1;
			for (int c : cells) {
				if (out.contains(c)) {
					continue;
				}
				double m = 1e9;
				for (int[] p : points) {
					m = Math.min(m, Math.hypot(c % SIZE_X - p[0], c / SIZE_X - p[1]));
				}
				if (points.isEmpty()) {
					m = 6;
				}
				if (m < minDistance) {
					continue;
				}
				double v = Math.min(m, 14) + this.rng.nextDouble() * 3;
				if (v > bestValue) {
					bestValue = v;
					best = c;
				}
			}
			if (best < 0) {
				break;
			}
			out.add(best);
			points.add(new int[] {best % SIZE_X, best / SIZE_X});
		}
		return out;
	}

	private void placeMarkers(final int hx, final int hz) {
		List<Integer> weaverCells = new ArrayList<>();
		for (int i = 0; i < N; i++) {
			if (!this.stand[i] || this.pit[i]) {
				continue;
			}
			double r = Math.hypot(i % SIZE_X - hx, i / SIZE_X - hz);
			if (r >= 3 && r <= 8 && footFree(i, 2)) {
				weaverCells.add(i);
			}
		}
		List<Integer> weaverPicks = farthestPoints(weaverCells, WEAVERS, List.of(), 4);
		for (int c : weaverPicks) {
			this.weavers.add(spotOf(c));
			reserve(c, 2);
		}
		List<Integer> crawlerCells = new ArrayList<>();
		for (int i = 0; i < N; i++) {
			if (!this.stand[i] || this.pit[i] || this.tag[i] == TAG_MOUTH || Math.hypot(i % SIZE_X - hx, i / SIZE_X - hz) < 4) {
				continue;
			}
			crawlerCells.add(i);
		}
		List<int[]> seeds = new ArrayList<>();
		for (int c : weaverPicks) {
			seeds.add(new int[] {c % SIZE_X, c / SIZE_X});
		}
		seeds.add(new int[] {this.pitX, this.pitZ});
		List<Integer> crawlerPicks = farthestPoints(crawlerCells, CRAWLERS, seeds, 4);
		for (int c : crawlerPicks) {
			this.crawlers.add(spotOf(c));
			reserve(c, 1);
		}
		this.matriarch = new Spot(this.pitX, this.pitFloor, this.pitZ);
		for (int iz = -1; iz <= 1; iz++) {
			for (int ix = -1; ix <= 1; ix++) {
				for (int y = this.pitFloor; y <= this.pitFloor + 3; y++) {
					this.noWeb[index(this.pitX + ix, y, this.pitZ + iz)] = true;
				}
			}
		}
		computeStand();
		List<Integer> markerCells = new ArrayList<>(weaverPicks);
		markerCells.addAll(crawlerPicks);
		markerCells.add(this.pitZ * SIZE_X + this.pitX);
		for (int c : markerCells) {
			List<Integer> path = pathTo(this.laneSource.prev(), entranceColumn(), c);
			if (!path.isEmpty()) {
				addLane(path);
			}
		}
		for (int i = 0; i < N; i++) {
			if (this.lane[i] == 1 || this.lane[i] == 2) {
				this.noWeb[index(i % SIZE_X, this.floor[i], i / SIZE_X)] = true;
				this.noWeb[index(i % SIZE_X, this.floor[i] + 1, i / SIZE_X)] = true;
			}
		}
	}

	private void placeBones(final int hx, final int hz) {
		List<Integer> cells = new ArrayList<>();
		for (int i = 0; i < N; i++) {
			if (!this.stand[i] || this.lane[i] != 0 || this.reachColumn[i] || this.pit[i] || this.tag[i] == TAG_MOUTH || this.tag[i] == TAG_NOOK) {
				continue;
			}
			int bx = i % SIZE_X;
			int bz = i / SIZE_X;
			if (Math.hypot(bx - hx, bz - hz) < 3) {
				continue;
			}
			boolean wall = false;
			for (int d = 0; d < 4; d++) {
				wall |= isRock(bx + DX4[d], this.floor[i], bz + DZ4[d]);
			}
			if (wall) {
				cells.add(i);
			}
		}
		for (int k = 0; k < this.boneTarget && !cells.isEmpty(); k++) {
			int pick = cells.remove(this.rng.nextInt(cells.size()));
			boolean close = false;
			for (int p : this.bonePiles) {
				close |= Math.hypot(p % SIZE_X - pick % SIZE_X, p / SIZE_X - pick / SIZE_X) < 3;
			}
			if (close) {
				k--;
				continue;
			}
			this.bonePiles.add(pick);
			int bx = pick % SIZE_X;
			int bz = pick / SIZE_X;
			this.grid[index(bx, this.floor[pick], bz)] = BONE;
			if (this.rng.nextDouble() < .4 && this.grid[index(bx, this.floor[pick] + 1, bz)] == AIR && this.top[pick] >= this.floor[pick] + 3) {
				this.grid[index(bx, this.floor[pick] + 1, bz)] = SKULL;
			}
		}
	}

	private boolean hang(final int c, final int wanted) {
		int cx = c % SIZE_X;
		int cz = c / SIZE_X;
		int t = this.top[c];
		int f = this.floor[c];
		int length = Math.min(wanted, t - f - 1);
		if (length < 2) {
			return false;
		}
		for (int y = t; y > t - length; y--) {
			if (this.grid[index(cx, y, cz)] != AIR || this.noWeb[index(cx, y, cz)]) {
				return false;
			}
		}
		for (int y = t; y > t - length; y--) {
			this.grid[index(cx, y, cz)] = COCOON;
		}
		return true;
	}

	private void hangCocoons() {
		List<Integer> cells = new ArrayList<>();
		for (int i = 0; i < N; i++) {
			if (this.floor[i] < 0 || this.pit[i] || this.tag[i] == TAG_MOUTH || this.tag[i] == TAG_NOOK) {
				continue;
			}
			if (this.top[i] - this.floor[i] >= 3) {
				cells.add(i);
			}
		}
		for (int tries = 0; this.cocoons.size() < this.cocoonTarget && tries < 80 && !cells.isEmpty(); tries++) {
			int c = cells.get(this.rng.nextInt(cells.size()));
			if (hang(c, 2 + this.rng.nextInt(2))) {
				this.cocoons.add(c);
			}
		}
		int[][] corners = {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}};
		for (int[] o : corners) {
			int c = (this.pitZ + o[1]) * SIZE_X + this.pitX + o[0];
			if (hang(c, 2)) {
				this.cocoons.add(c);
			}
		}
	}

	/** 4 to 8 patches of glow lichen on the walls, so the sacs and the rock read in the dark. */
	private void placeLichen() {
		int target = rint(4, 8);
		List<Lichen> cells = new ArrayList<>();
		for (int z = 0; z < NEST_Z; z++) {
			for (int x = 0; x < SIZE_X; x++) {
				int i = z * SIZE_X + x;
				if (this.floor[i] < 0 || this.tag[i] == TAG_MOUTH || this.tag[i] == TAG_NOOK) {
					continue;
				}
				for (int y = this.floor[i]; y <= Math.min(this.top[i], this.floor[i] + 3); y++) {
					if (this.grid[index(x, y, z)] != AIR || this.noWeb[index(x, y, z)]) {
						continue;
					}
					for (int d = 0; d < 4; d++) {
						if (isRock(x + DX4[d], y, z + DZ4[d])) {
							cells.add(new Lichen(x, y, z, DX4[d], DZ4[d]));
							break;
						}
					}
				}
			}
		}
		List<Lichen> chosen = new ArrayList<>();
		for (int patch = 0; patch < target && !cells.isEmpty(); patch++) {
			Lichen start = cells.get(this.rng.nextInt(cells.size()));
			boolean near = false;
			for (Lichen other : chosen) {
				near |= Math.hypot(other.x() - start.x(), other.z() - start.z()) < 4;
			}
			if (near) {
				patch--;
				cells.remove(start);
				continue;
			}
			chosen.add(start);
			this.lichenPatches++;
			this.lichens.add(start);
			this.noWeb[index(start.x(), start.y(), start.z())] = true;
			int extra = this.rng.nextInt(3);
			for (Lichen other : new ArrayList<>(cells)) {
				if (extra > 0 && other.faceX() == start.faceX() && other.faceZ() == start.faceZ() && other != start
					&& Math.abs(other.x() - start.x()) + Math.abs(other.y() - start.y()) + Math.abs(other.z() - start.z()) == 1) {
					this.lichens.add(other);
					this.noWeb[index(other.x(), other.y(), other.z())] = true;
					extra--;
				}
			}
		}
	}

	private record Shell(int x, int y, int z, int neighbours) {
	}

	private void spinWebs() {
		List<Shell> shell = new ArrayList<>();
		for (int z = 0; z < NEST_Z; z++) {
			for (int x = 0; x < SIZE_X; x++) {
				int i = z * SIZE_X + x;
				if (this.floor[i] < 0 || this.plug[i]) {
					continue;
				}
				for (int y = this.floor[i]; y <= this.top[i]; y++) {
					if (this.grid[index(x, y, z)] != AIR) {
						continue;
					}
					int solid = 0;
					solid += isRock(x + 1, y, z) ? 1 : 0;
					solid += isRock(x - 1, y, z) ? 1 : 0;
					solid += isRock(x, y, z + 1) ? 1 : 0;
					solid += isRock(x, y, z - 1) ? 1 : 0;
					solid += isRock(x, y + 1, z) ? 1 : 0;
					solid += isRock(x, y - 1, z) ? 1 : 0;
					if (solid > 0) {
						shell.add(new Shell(x, y, z, solid));
					}
				}
			}
		}
		int target = (int) Math.round(this.webPercent / 100.0 * shell.size());
		int salt = (int) this.seed;
		List<double[]> ranked = new ArrayList<>();
		List<Shell> pool = new ArrayList<>();
		for (Shell c : shell) {
			if (this.noWeb[index(c.x(), c.y(), c.z())]) {
				continue;
			}
			double weight = 1 + 0.8 * Math.max(0, c.neighbours() - 1) + (isRock(c.x(), c.y() + 1, c.z()) ? 0.6 : 0);
			boolean near = false;
			for (Sac sac : this.sacs) {
				near |= Math.sqrt(Math.pow(sac.x - c.x(), 2) + Math.pow(sac.y - c.y(), 2) + Math.pow(sac.z - c.z(), 2)) <= 2.6;
			}
			if (near) {
				weight += 2.2;
			}
			double key = Math.pow(hash01(c.x() * 31 + salt, c.y() * 17, c.z() * 13 + salt * 7), 1 / weight);
			ranked.add(new double[] {key, pool.size()});
			pool.add(c);
		}
		ranked.sort((a, b) -> Double.compare(b[0], a[0]));
		int webs = 0;
		for (int k = 0; k < Math.min(target, ranked.size()); k++) {
			Shell c = pool.get((int) ranked.get(k)[1]);
			this.grid[index(c.x(), c.y(), c.z())] = WEB;
			webs++;
		}
		this.shellCells = shell.size();
		this.webCells = webs;
		for (int i = 0; i < N; i++) {
			if (!this.plug[i]) {
				continue;
			}
			for (int y = this.floor[i]; y <= this.top[i]; y++) {
				if (this.grid[index(i % SIZE_X, y, i / SIZE_X)] == AIR) {
					this.grid[index(i % SIZE_X, y, i / SIZE_X)] = WEB;
				}
			}
		}
	}

	// ---- checks the tests use ----

	private boolean[] webFreeReach() {
		boolean[] seen = new boolean[N];
		int start = entranceColumn();
		if (!this.stand[start] || this.webColumn[start]) {
			return seen;
		}
		ArrayList<Integer> stack = new ArrayList<>();
		stack.add(start);
		seen[start] = true;
		while (!stack.isEmpty()) {
			int u = stack.remove(stack.size() - 1);
			int ux = u % SIZE_X;
			int uz = u / SIZE_X;
			for (int d = 0; d < 8; d++) {
				int nx = ux + DX8[d];
				int nz = uz + DZ8[d];
				if (nx < 0 || nx >= SIZE_X || nz < 0 || nz >= NEST_Z) {
					continue;
				}
				int n = nz * SIZE_X + nx;
				if (seen[n] || !this.stand[n] || this.webColumn[n] || Math.abs(this.floor[n] - this.floor[u]) > 1) {
					continue;
				}
				if (d >= 4) {
					int a = uz * SIZE_X + nx;
					int b = nz * SIZE_X + ux;
					if (!this.stand[a] || !this.stand[b] || this.webColumn[a] || this.webColumn[b]) {
						continue;
					}
				}
				seen[n] = true;
				stack.add(n);
			}
		}
		return seen;
	}

	/** Cells along the line from a sac's reach cell to the sac. They hold no web. */
	public List<int[]> sightOf(final Sac sac) {
		List<int[]> cells = new ArrayList<>();
		for (int c : sac.sight) {
			cells.add(new int[] {c % SIZE_X, (c / (SIZE_X * NEST_Z)), (c / SIZE_X) % NEST_Z});
		}
		return cells;
	}
}
