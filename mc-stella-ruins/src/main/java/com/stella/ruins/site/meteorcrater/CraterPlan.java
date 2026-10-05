package com.stella.ruins.site.meteorcrater;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The whole plan of one crater, as a function of the seed. It reads no world block, so the layout and the tests
 * both read it, and every chunk draws the same crater.
 * <p>
 * The frame is local: x east, z north, y up from the floor of the site box. The framework puts that floor one
 * block above the highest ground of the footprint, so the top solid block of the reference ground is local
 * {@link #GROUND}. The crater is cut down from that ground, and the foundation holds the deepest bowl.
 * <p>
 * Shape: a bowl of radius 12 to 18 and depth 8 to 16 around the core, a noisy rim, a lip of spoil that is
 * highest on the north side, and a gouge with no lip on the south side, where the route comes in. The meteor is
 * a blob of 30 to 60 blocks on the floor, with 8 to 16 hot Starmetal ore blocks. The vault sits in a niche in
 * the north wall.
 */
public final class CraterPlan {
	public static final int SIZE_X = 42;
	public static final int SIZE_Z = 42;
	/** The height of the box above the floor. The foundation below it holds the bowl. */
	public static final int SIZE_Y = 8;
	public static final int FOUNDATION = 17;
	/** The top solid block of the reference ground, in local y. */
	public static final int GROUND = -1;
	public static final int MIN_Y = -FOUNDATION;
	/** The cell of the core, and the centre of the bowl. */
	public static final int CENTER = 21;
	public static final double CENTER_POS = 21.5;
	/** The radius of the flat floor. */
	public static final int FLAT = 3;
	public static final int MIN_RADIUS = 12;
	public static final int MAX_RADIUS = 18;
	public static final int MIN_DEPTH = 8;
	public static final int MAX_DEPTH = 16;
	public static final double RIM_NOISE = 1.5;
	/** No rim goes past this radius, so the ring always fits in the box. */
	public static final double RIM_CAP = 18.4;
	public static final int MIN_BLOB = 30;
	public static final int MAX_BLOB = 60;
	public static final int MIN_ORE = 8;
	public static final int MAX_ORE = 16;
	public static final int MIN_EXPOSED = 6;
	public static final int MIN_FIRES = 3;
	public static final int MAX_FIRES = 6;
	public static final int GOLEMS = 3;
	public static final int SLIMES = 4;
	/** The blob stays within this many blocks of the core along x and z, and this many above the floor. */
	public static final int BLOB_REACH = 6;
	public static final int BLOB_HEIGHT = 6;
	/** The cell south of the core that stays open, for the player to stand in. */
	public static final int NOTCH_Z = CENTER - 1;
	private static final double TAU = Math.PI * 2.0;
	private static final double[] GOLEM_ANGLES = {30.0, 150.0, 215.0};
	private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
	private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

	/** The block on top of a column. */
	public enum Surface {
		GRASS,
		COARSE,
		BLACKSTONE,
		BASALT,
		MAGMA
	}

	public enum CrustKind {
		BASALT,
		BLACKSTONE,
		MAGMA
	}

	public record Spot(int x, int y, int z) {
	}

	/** One block of the meteor. */
	public record Blob(Spot spot, CrustKind crust, boolean ore, boolean exposed) {
	}

	/** A fire patch (one to three cells of magma with fire on top) or a smoke campfire on hay (one cell). */
	public record Fire(boolean campfire, List<Spot> cells) {
	}

	private final int radius;
	private final int depthRolled;
	private final int depth;
	private final int floorY;
	private final int blobSize;
	private final int oreCount;
	private final int fireCount;
	private final double[] phase;
	private final long hashSeed;
	private final double[][] rim = new double[SIZE_X][SIZE_Z];
	private final int[][] top = new int[SIZE_X][SIZE_Z];
	private final boolean[][] affected = new boolean[SIZE_X][SIZE_Z];
	private final Surface[][] surface = new Surface[SIZE_X][SIZE_Z];
	private final Map<Integer, Blob> blob = new LinkedHashMap<>();
	private final List<Blob> blobList = new ArrayList<>();
	private final List<Spot> ore = new ArrayList<>();
	private final List<Spot> golems = new ArrayList<>();
	private final List<Spot> slimes = new ArrayList<>();
	private final List<Fire> fires = new ArrayList<>();
	private final Map<Integer, BlockState> overrides = new HashMap<>();
	private int nicheZ = -1;

	private CraterPlan(final RandomSource random) {
		this.radius = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);
		this.depthRolled = MIN_DEPTH + random.nextInt(MAX_DEPTH - MIN_DEPTH + 1);
		this.depth = Math.min(this.depthRolled, this.radius - FLAT);
		this.floorY = GROUND - this.depth;
		this.blobSize = MIN_BLOB + random.nextInt(MAX_BLOB - MIN_BLOB + 1);
		this.oreCount = MIN_ORE + random.nextInt(MAX_ORE - MIN_ORE + 1);
		this.fireCount = MIN_FIRES + random.nextInt(MAX_FIRES - MIN_FIRES + 1);
		this.phase = new double[] {random.nextDouble() * TAU, random.nextDouble() * TAU, random.nextDouble() * TAU};
		this.hashSeed = random.nextLong();
		RandomSource blobRandom = RandomSource.create(random.nextLong());
		RandomSource markRandom = RandomSource.create(random.nextLong());
		shapeGround();
		gradeRoute();
		paintSurface();
		carveNiche();
		growBlob(blobRandom);
		chooseOre(blobRandom);
		placeMarkers(markRandom);
		placeFires(markRandom);
	}

	public static CraterPlan derive(final RandomSource random) {
		return new CraterPlan(random);
	}

	// ---- shape ----

	/** The rim radius at an angle: the rolled radius with noise, never past {@link #RIM_CAP}. */
	public double rimAt(final double angle) {
		double noise = 0.5 * StrictMath.sin(2.0 * angle + this.phase[0]) + 0.3 * StrictMath.sin(3.0 * angle + this.phase[1])
			+ 0.2 * StrictMath.sin(5.0 * angle + this.phase[2]);
		return Math.min(this.radius + RIM_NOISE * noise, RIM_CAP);
	}

	private void shapeGround() {
		for (int x = 0; x < SIZE_X; x++) {
			for (int z = 0; z < SIZE_Z; z++) {
				double dx = x + 0.5 - CENTER_POS;
				double dz = z + 0.5 - CENTER_POS;
				double distance = StrictMath.sqrt(dx * dx + dz * dz);
				double angle = StrictMath.atan2(dz, dx);
				double rim = rimAt(angle);
				this.rim[x][z] = rim;
				double dip = 0.0;
				if (distance < rim) {
					double u = clamp((distance - FLAT) / (rim - FLAT), 0.0, 1.0);
					dip = this.depth * (1.0 - StrictMath.pow(u, 1.15));
				}
				boolean gouge = dz < 0.0 && Math.abs(dx) < 3.5;
				double north = StrictMath.pow(Math.max(0.0, StrictMath.cos(angle - Math.PI / 2.0)), 1.5);
				double lipHeight = gouge ? 0.0 : 1.0 + 2.0 * north;
				double crest = rim + 0.5;
				double d = distance - crest;
				double reach = d < 0.0 ? 2.5 : clamp(19.8 - crest, 0.8, 4.5);
				double lip = lipHeight * Math.max(0.0, 1.0 - Math.abs(d) / reach);
				this.top[x][z] = GROUND - (int) Math.round(dip) + (int) Math.round(lip);
				this.affected[x][z] = distance <= rim + 1.5 || Math.round(lip) > 0;
			}
		}
	}

	/** Cuts or fills the three-wide strip of the route so that no step is more than one block. */
	private void gradeRoute() {
		int[] graded = new int[CENTER];
		graded[NOTCH_Z] = this.top[CENTER][NOTCH_Z];
		for (int z = NOTCH_Z - 1; z >= 0; z--) {
			graded[z] = (int) clamp(this.top[CENTER][z], graded[z + 1] - 1, graded[z + 1] + 1);
		}
		for (int z = 0; z <= NOTCH_Z; z++) {
			for (int x = CENTER - 1; x <= CENTER + 1; x++) {
				if (this.affected[x][z] || this.top[x][z] != graded[z]) {
					this.top[x][z] = graded[z];
					this.affected[x][z] = true;
				}
			}
		}
	}

	public static boolean onRoute(final int x, final int z) {
		return x >= CENTER - 1 && x <= CENTER + 1 && z <= NOTCH_Z;
	}

	private void paintSurface() {
		for (int x = 0; x < SIZE_X; x++) {
			for (int z = 0; z < SIZE_Z; z++) {
				double dx = x + 0.5 - CENTER_POS;
				double dz = z + 0.5 - CENTER_POS;
				double distance = StrictMath.sqrt(dx * dx + dz * dz);
				double rim = this.rim[x][z];
				Surface type;
				if (distance < rim - 3.0) {
					double p = unit(x, z, 1);
					type = p < 0.45 ? Surface.BLACKSTONE : p < 0.70 ? Surface.BASALT : p < 0.92 ? Surface.COARSE : Surface.MAGMA;
				} else if (distance <= rim + 1.5) {
					boolean ring = distance <= rim + 0.5 || unit(x, z, 3) < 1.0 - (distance - rim - 0.5);
					double p = unit(x, z, 2);
					type = !ring ? Surface.GRASS : p < 0.55 ? Surface.COARSE : p < 0.90 ? Surface.BLACKSTONE : Surface.MAGMA;
				} else {
					type = Surface.GRASS;
				}
				if (onRoute(x, z) && type == Surface.MAGMA) {
					type = Surface.COARSE;
				}
				this.surface[x][z] = type;
			}
		}
	}

	/** A hash in [0, 1) of the seed and a column, so the surface does not depend on the order of the columns. */
	private double unit(final int x, final int z, final int salt) {
		long h = this.hashSeed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		h ^= h >>> 33;
		return (h >>> 11) * 0x1.0p-53;
	}

	private static double clamp(final double value, final double low, final double high) {
		return value < low ? low : Math.min(value, high);
	}

	// ---- niche and vault ----

	private void carveNiche() {
		int z = CENTER + 1;
		for (; z < SIZE_Z - 2; z++) {
			boolean walled = true;
			for (int x = CENTER - 1; x <= CENTER + 1; x++) {
				walled &= this.top[x][z] >= this.floorY + 3 && this.top[x][z + 1] >= this.floorY + 3;
			}
			if (walled) {
				break;
			}
		}
		this.nicheZ = z;
		for (int x = CENTER - 1; x <= CENTER + 1; x++) {
			for (int dz = 0; dz <= 1; dz++) {
				for (int y = this.floorY + 1; y <= this.floorY + 2; y++) {
					this.overrides.put(pack(x, y, this.nicheZ + dz), Blocks.AIR.defaultBlockState());
				}
			}
		}
	}

	private boolean inNiche(final int x, final int z) {
		return z >= this.nicheZ - 1 && Math.abs(x - CENTER) <= 3;
	}

	// ---- meteor ----

	private void growBlob(final RandomSource random) {
		int coreY = this.floorY + 1;
		int coreKey = pack(CENTER, coreY, CENTER);
		Map<Integer, Spot> grown = new LinkedHashMap<>();
		List<Spot> order = new ArrayList<>();
		for (int n = 0; n < this.blobSize; n++) {
			Map<Integer, Spot> candidates = new LinkedHashMap<>();
			Spot[] sources = new Spot[order.size() + 1];
			sources[0] = new Spot(CENTER, coreY, CENTER);
			for (int i = 0; i < order.size(); i++) {
				sources[i + 1] = order.get(i);
			}
			for (Spot source : sources) {
				for (int[] face : FACES) {
					int x = source.x() + face[0];
					int y = source.y() + face[1];
					int z = source.z() + face[2];
					int key = pack(x, y, z);
					if (!grown.containsKey(key) && !candidates.containsKey(key) && key != coreKey && blobCell(x, y, z)) {
						candidates.put(key, new Spot(x, y, z));
					}
				}
			}
			double total = 0.0;
			double[] weights = new double[candidates.size()];
			Spot[] spots = candidates.values().toArray(new Spot[0]);
			for (int i = 0; i < spots.length; i++) {
				Spot s = spots[i];
				int neighbours = 0;
				for (int[] face : FACES) {
					int key = pack(s.x() + face[0], s.y() + face[1], s.z() + face[2]);
					if (key == coreKey || grown.containsKey(key)) {
						neighbours++;
					}
				}
				int layer = s.y() - this.floorY;
				double dx = s.x() - CENTER;
				double dz = s.z() - CENTER;
				double weight = (1.0 + neighbours * neighbours * 0.6) * (layer <= 1 ? 1.5 : 1.0) / (1.0 + 0.5 * Math.max(0, layer - 2)) / (1.0 + 0.04 * (dx * dx + dz * dz));
				weights[i] = weight;
				total += weight;
			}
			double pick = random.nextDouble() * total;
			int chosen = 0;
			for (; chosen < spots.length; chosen++) {
				pick -= weights[chosen];
				if (pick <= 0.0) {
					break;
				}
			}
			chosen = Math.min(chosen, spots.length - 1);
			Spot spot = spots[chosen];
			grown.put(pack(spot.x(), spot.y(), spot.z()), spot);
			order.add(spot);
		}
		for (Spot spot : order) {
			double p = random.nextDouble();
			CrustKind crust = p < 0.45 ? CrustKind.BASALT : p < 0.80 ? CrustKind.BLACKSTONE : CrustKind.MAGMA;
			boolean exposed = exposed(spot, grown, coreKey);
			Blob made = new Blob(spot, crust, false, exposed);
			this.blob.put(pack(spot.x(), spot.y(), spot.z()), made);
			this.blobList.add(made);
		}
	}

	private boolean blobCell(final int x, final int y, final int z) {
		if (x < 0 || z < 0 || x >= SIZE_X || z >= SIZE_Z || Math.abs(x - CENTER) > BLOB_REACH || Math.abs(z - CENTER) > BLOB_REACH) {
			return false;
		}
		if (y < this.top[x][z] || y > this.floorY + BLOB_HEIGHT) {
			return false;
		}
		return !(x == CENTER && z < CENTER) && !inNiche(x, z);
	}

	private boolean exposed(final Spot spot, final Map<Integer, Spot> grown, final int coreKey) {
		for (int[] face : FACES) {
			int x = spot.x() + face[0];
			int y = spot.y() + face[1];
			int z = spot.z() + face[2];
			if (x < 0 || z < 0 || x >= SIZE_X || z >= SIZE_Z) {
				continue;
			}
			int key = pack(x, y, z);
			boolean solid = y <= this.top[x][z];
			boolean carved = this.overrides.containsKey(key);
			if ((!solid || carved) && !grown.containsKey(key) && key != coreKey) {
				return true;
			}
		}
		return false;
	}

	private void chooseOre(final RandomSource random) {
		List<Blob> pool = new ArrayList<>(this.blobList);
		List<Blob> chosen = new ArrayList<>();
		// The first ore blocks all come from the meteor's surface, so at least six can be mined without digging.
		for (int i = 0; i < Math.min(this.oreCount, MIN_EXPOSED); i++) {
			List<Blob> open = new ArrayList<>();
			for (Blob b : pool) {
				if (b.exposed()) {
					open.add(b);
				}
			}
			if (open.isEmpty()) {
				break;
			}
			Blob pick = open.get(random.nextInt(open.size()));
			pool.remove(pick);
			chosen.add(pick);
		}
		while (chosen.size() < this.oreCount) {
			double total = 0.0;
			for (Blob b : pool) {
				total += b.exposed() ? 6.0 : 1.0;
			}
			double pick = random.nextDouble() * total;
			int index = 0;
			for (; index < pool.size(); index++) {
				pick -= pool.get(index).exposed() ? 6.0 : 1.0;
				if (pick <= 0.0) {
					break;
				}
			}
			index = Math.min(index, pool.size() - 1);
			chosen.add(pool.remove(index));
		}
		for (Blob b : chosen) {
			Spot s = b.spot();
			Blob oreBlob = new Blob(s, b.crust(), true, b.exposed());
			this.blob.put(pack(s.x(), s.y(), s.z()), oreBlob);
			this.blobList.set(this.blobList.indexOf(b), oreBlob);
			this.ore.add(s);
		}
	}

	// ---- markers and fire ----

	private int columnTop(final int x, final int z) {
		int best = this.top[x][z];
		for (Blob b : this.blobList) {
			if (b.spot().x() == x && b.spot().z() == z) {
				best = Math.max(best, b.spot().y());
			}
		}
		return best;
	}

	private boolean inBlobColumn(final int x, final int z) {
		for (Blob b : this.blobList) {
			if (b.spot().x() == x && b.spot().z() == z) {
				return true;
			}
		}
		return false;
	}

	private void placeMarkers(final RandomSource random) {
		for (double base : GOLEM_ANGLES) {
			double angle = Math.toRadians(base + random.nextDouble() * 24.0 - 12.0);
			double rim = rimAt(angle);
			double reach = FLAT + 0.55 * (rim - FLAT);
			int x = (int) Math.floor(CENTER_POS + reach * StrictMath.cos(angle));
			int z = (int) Math.floor(CENTER_POS + reach * StrictMath.sin(angle));
			this.golems.add(new Spot(x, columnTop(x, z) + 1, z));
		}
		int tries = 0;
		while (this.slimes.size() < SLIMES && tries++ < 400) {
			double angle = random.nextDouble() * TAU;
			double reach = 4.0 + random.nextDouble() * 2.5;
			double degrees = Math.toDegrees(angle);
			double awayRoute = Math.abs(((degrees - 270.0 + 540.0) % 360.0) - 180.0);
			double awayVault = Math.abs(((degrees - 90.0 + 540.0) % 360.0) - 180.0);
			if (awayRoute < 28.0 || awayVault < 28.0) {
				continue;
			}
			int x = (int) Math.floor(CENTER_POS + reach * StrictMath.cos(angle));
			int z = (int) Math.floor(CENTER_POS + reach * StrictMath.sin(angle));
			if (inBlobColumn(x, z) || inNiche(x, z)) {
				continue;
			}
			boolean crowded = false;
			for (Spot other : this.slimes) {
				crowded |= Math.abs(other.x() - x) + Math.abs(other.z() - z) < 3;
			}
			if (!crowded) {
				this.slimes.add(new Spot(x, this.top[x][z] + 1, z));
			}
		}
	}

	private void placeFires(final RandomSource random) {
		int tries = 0;
		List<int[]> used = new ArrayList<>();
		while (this.fires.size() < this.fireCount && tries++ < 800) {
			double angle = random.nextDouble() * TAU;
			double rim = rimAt(angle);
			double reach = rim - 2.0 + random.nextDouble() * 3.4;
			int x = (int) Math.floor(CENTER_POS + reach * StrictMath.cos(angle));
			int z = (int) Math.floor(CENTER_POS + reach * StrictMath.sin(angle));
			if (x < 1 || z < 1 || x >= SIZE_X - 1 || z >= SIZE_Z - 1 || !this.affected[x][z] || onRoute(x, z) || inNiche(x, z)
				|| Math.abs(x - CENTER) <= 1 && z <= NOTCH_Z) {
				continue;
			}
			boolean crowded = false;
			for (int[] other : used) {
				crowded |= Math.abs(other[0] - x) + Math.abs(other[1] - z) < 4;
			}
			if (crowded) {
				continue;
			}
			boolean campfire = random.nextDouble() >= 0.6;
			List<Spot> cells = new ArrayList<>();
			cells.add(new Spot(x, this.top[x][z], z));
			if (!campfire) {
				double extra = random.nextDouble();
				for (int more = 0; more < (extra < 0.2 ? 2 : extra < 0.5 ? 1 : 0); more++) {
					int[] face = SIDES[random.nextInt(4)];
					int nx = x + face[0];
					int nz = z + face[1];
					if (nx >= 1 && nz >= 1 && nx < SIZE_X - 1 && nz < SIZE_Z - 1 && this.affected[nx][nz] && !onRoute(nx, nz) && !inNiche(nx, nz)) {
						cells.add(new Spot(nx, this.top[nx][nz], nz));
					}
				}
			}
			used.add(new int[] {x, z});
			this.fires.add(new Fire(campfire, cells));
			for (Spot cell : cells) {
				if (campfire) {
					this.overrides.put(pack(cell.x(), cell.y() + 1, cell.z()), Blocks.HAY_BLOCK.defaultBlockState());
					this.overrides.put(pack(cell.x(), cell.y() + 2, cell.z()), Blocks.CAMPFIRE.defaultBlockState()
						.setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, true));
				} else {
					this.overrides.put(pack(cell.x(), cell.y(), cell.z()), Blocks.MAGMA_BLOCK.defaultBlockState());
					this.overrides.put(pack(cell.x(), cell.y() + 1, cell.z()), Blocks.FIRE.defaultBlockState());
				}
			}
		}
	}

	// ---- blocks ----

	/** The block at a local spot, or null when the crater leaves that spot to the world. */
	public BlockState stateAt(final int x, final int y, final int z) {
		if (x < 0 || z < 0 || x >= SIZE_X || z >= SIZE_Z || y < MIN_Y || y >= SIZE_Y || !this.affected[x][z]) {
			return null;
		}
		int key = pack(x, y, z);
		Blob b = this.blob.get(key);
		if (b != null) {
			return b.ore() ? MeteorBlocks.STARMETAL_ORE.defaultBlockState() : crustState(b.crust());
		}
		BlockState override = this.overrides.get(key);
		if (override != null) {
			return override;
		}
		int surfaceY = this.top[x][z];
		if (y > surfaceY) {
			return Blocks.AIR.defaultBlockState();
		}
		if (y == surfaceY) {
			return surfaceState(this.surface[x][z]);
		}
		if (y >= surfaceY - 3) {
			return this.surface[x][z] == Surface.GRASS ? Blocks.DIRT.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
		}
		return Blocks.STONE.defaultBlockState();
	}

	public static BlockState surfaceState(final Surface surface) {
		return switch (surface) {
			case GRASS -> Blocks.GRASS_BLOCK.defaultBlockState();
			case COARSE -> Blocks.COARSE_DIRT.defaultBlockState();
			case BLACKSTONE -> Blocks.BLACKSTONE.defaultBlockState();
			case BASALT -> Blocks.SMOOTH_BASALT.defaultBlockState();
			case MAGMA -> Blocks.MAGMA_BLOCK.defaultBlockState();
		};
	}

	public static BlockState crustState(final CrustKind crust) {
		return switch (crust) {
			case BASALT -> Blocks.SMOOTH_BASALT.defaultBlockState();
			case BLACKSTONE -> Blocks.BLACKSTONE.defaultBlockState();
			case MAGMA -> Blocks.MAGMA_BLOCK.defaultBlockState();
		};
	}

	private static int pack(final int x, final int y, final int z) {
		return (x & 63) | ((z & 63) << 6) | ((y + 32) << 12);
	}

	// ---- reads ----

	public int radius() {
		return this.radius;
	}

	public int depthRolled() {
		return this.depthRolled;
	}

	public int depth() {
		return this.depth;
	}

	public int floorY() {
		return this.floorY;
	}

	public Spot core() {
		return new Spot(CENTER, this.floorY + 1, CENTER);
	}

	public Spot vault() {
		return new Spot(CENTER, this.floorY + 1, this.nicheZ + 1);
	}

	public int nicheZ() {
		return this.nicheZ;
	}

	public int top(final int x, final int z) {
		return this.top[x][z];
	}

	public boolean affected(final int x, final int z) {
		return this.affected[x][z];
	}

	public Surface surface(final int x, final int z) {
		return this.surface[x][z];
	}

	public double rim(final int x, final int z) {
		return this.rim[x][z];
	}

	public List<Blob> blob() {
		return List.copyOf(this.blobList);
	}

	public List<Spot> ore() {
		return List.copyOf(this.ore);
	}

	public int exposedOre() {
		int count = 0;
		for (Blob b : this.blobList) {
			if (b.ore() && b.exposed()) {
				count++;
			}
		}
		return count;
	}

	public List<Spot> golems() {
		return List.copyOf(this.golems);
	}

	public List<Spot> slimes() {
		return List.copyOf(this.slimes);
	}

	public List<Fire> fires() {
		return List.copyOf(this.fires);
	}

	/** The highest solid block of a column that the plan draws, the blob included. */
	public int highest(final int x, final int z) {
		return columnTop(x, z);
	}

	public boolean blobAt(final int x, final int y, final int z) {
		return this.blob.containsKey(pack(x, y, z));
	}
}
