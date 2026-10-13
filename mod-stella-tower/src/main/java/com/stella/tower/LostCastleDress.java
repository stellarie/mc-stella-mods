package com.stella.tower;

import com.stella.tower.LostCastleGrid.Side;
import com.stella.tower.LostCastleLayout.Work;
import com.stella.tower.LostCastlePlan.Chest;
import com.stella.tower.LostCastlePlan.Prop;
import com.stella.tower.LostCastlePlan.PropKind;
import com.stella.tower.LostCastlePlan.Spawner;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * What a drawn floor is dressed with: one hidden spawner in each zone, and the candles and props of the tower's own
 * sets ({@link TowerProps}, {@link BiomeProps}) on the floor, its walls and its ceiling. Props keep off the routes
 * between the floor's key spots, the reserved columns, the spawners' fronts and the chest alcoves.
 */
final class LostCastleDress {
	private static final int S = LostCastleGrid.SIZE;
	private static final int LO = LostCastleGrid.IN_LOW;
	private static final int HI = LostCastleGrid.IN_HIGH;
	private static final int CLEAR = LostCastleGrid.CLEAR;
	private static final int RISE = LostCastleGrid.MAX_RISE;
	/** A spawner stands in a block at least this high, so its column is solid from the floor to over its head. */
	private static final int HIDING = 4;
	/** The least gap, in columns, between two spawners. */
	private static final int SPAWNER_APART = 3;
	private static final int MAX_BARRELS = 6;
	private static final int[][] WAYS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
	/** The floor props and their weights; the first five stand at the foot of a wall. */
	private static final PropKind[] FLOOR = {PropKind.SKULL, PropKind.BARREL, PropKind.ANVIL, PropKind.GRINDSTONE, PropKind.BONE, PropKind.FLOOR_COBWEB,
		PropKind.RUBBLE, PropKind.MOSS};
	private static final int[] WEIGHTS = {5, 1, 1, 1, 2, 2, 3, 2};

	private LostCastleDress() {
	}

	// ---------------------------------------------------------------- spawners

	/** One spawner in each of the nine zones, but the portal's zone on the bottom floor. */
	static List<Spawner> spawners(final Work w, final List<Chest> chests, final boolean bottom) {
		List<Spawner> spawners = new ArrayList<>();
		for (int zx = 0; zx < LostCastleGrid.ZONES; zx++) {
			for (int zz = 0; zz < LostCastleGrid.ZONES; zz++) {
				if (bottom && zx == LostCastlePlan.PORTAL_ZONE && zz == LostCastlePlan.PORTAL_ZONE) {
					continue;
				}
				Spawner spawner = wallSite(w, chests, spawners, zx, zz);
				if (spawner == null) {
					spawner = pillarSite(w, chests, spawners, zx, zz, false);
				}
				if (spawner == null) {
					spawner = pillarSite(w, chests, spawners, zx, zz, true);
				}
				if (spawner != null) {
					spawners.add(spawner);
					int dx = spawner.side().inX();
					int dz = spawner.side().inZ();
					for (int d = 1; d <= 2; d++) {
						w.reserve(spawner.x() + dx * d, spawner.z() + dz * d, spawner.x() + dx * d, spawner.z() + dz * d);
					}
					w.key(spawner.x() + dx, spawner.z() + dz);
				}
			}
		}
		return spawners;
	}

	private static boolean nearLocked(final Work w, final int x, final int z, final int radius) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (w.locked(x + dx, z + dz)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Whether a column lies too near a placed spawner: two spawners never touch, and each hides in its own stone. */
	private static boolean nearSpawner(final List<Spawner> placed, final int x, final int z) {
		for (Spawner spawner : placed) {
			if (Math.abs(spawner.x() - x) <= SPAWNER_APART && Math.abs(spawner.z() - z) <= SPAWNER_APART) {
				return true;
			}
		}
		return false;
	}

	private static boolean nearAlcove(final List<Chest> chests, final int x, final int z, final int radius) {
		for (Chest chest : chests) {
			for (int c = 0; c < LostCastlePlan.ALCOVE_WIDTH; c++) {
				LostCastlePlan.Cell cell = chest.cell(c);
				if (Math.abs(cell.x() - x) <= radius && Math.abs(cell.z() - z) <= radius) {
					return true;
				}
			}
		}
		return false;
	}

	/** A solid column in the zone with one open floor column beside it and solid stone on its three other sides. */
	private static Spawner wallSite(final Work w, final List<Chest> chests, final List<Spawner> placed, final int zx, final int zz) {
		List<Spawner> sites = new ArrayList<>();
		int x0 = LostCastleGrid.zoneLow(zx) - (zx == 0 ? 1 : 0);
		int x1 = LostCastleGrid.zoneHigh(zx) + (zx == LostCastleGrid.ZONES - 1 ? 1 : 0);
		int z0 = LostCastleGrid.zoneLow(zz) - (zz == 0 ? 1 : 0);
		int z1 = LostCastleGrid.zoneHigh(zz) + (zz == LostCastleGrid.ZONES - 1 ? 1 : 0);
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (w.fill(x, z) < HIDING || nearLocked(w, x, z, 3) || nearAlcove(chests, x, z, 3) || nearSpawner(placed, x, z)) {
					continue;
				}
				int solid = 0;
				int[] way = null;
				for (int[] d : WAYS) {
					int nx = x + d[0];
					int nz = z + d[1];
					if (w.fill(nx, nz) >= HIDING) {
						solid++;
					} else if (w.fill(nx, nz) == 0 && !w.blocked(nx, nz) && !w.blocked(nx + d[0], nz + d[1]) && w.fill(nx + d[0], nz + d[1]) == 0) {
						way = d;
					}
				}
				if (solid == 3 && way != null) {
					sites.add(new Spawner(x, z, LostCastleGrid.facing(way[0], way[1])));
				}
			}
		}
		return sites.isEmpty() ? null : sites.get(w.random.nextInt(sites.size()));
	}

	/**
	 * A solid pillar three by three, built in the open: the spawner sits in the middle of one face, so the pillar
	 * hides it as a wall would. The loose form keeps off nothing but the border and the vault, for a zone that
	 * holds no room for the strict one.
	 */
	private static Spawner pillarSite(final Work w, final List<Chest> chests, final List<Spawner> placed, final int zx, final int zz, final boolean loose) {
		List<int[]> spots = new ArrayList<>();
		for (int x = LostCastleGrid.zoneLow(zx); x <= LostCastleGrid.zoneHigh(zx); x++) {
			for (int z = LostCastleGrid.zoneLow(zz); z <= LostCastleGrid.zoneHigh(zz); z++) {
				spots.add(new int[] {x, z});
			}
		}
		LostCastlePlan.shuffle(spots, w.random);
		int radius = loose ? 3 : 2;
		for (int[] spot : spots) {
			int px = spot[0];
			int pz = spot[1];
			if (px - radius < LO || pz - radius < LO || px + radius > HI || pz + radius > HI || nearLocked(w, px, pz, 4) || nearAlcove(chests, px, pz, 4)) {
				continue;
			}
			boolean clear = true;
			for (int dx = -radius; dx <= radius && clear; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					clear &= loose ? !w.blocked(px + dx, pz + dz) : w.walk(px + dx, pz + dz) && !w.blocked(px + dx, pz + dz) && !w.claimed(px + dx, pz + dz);
				}
			}
			if (!clear) {
				continue;
			}
			int[] way = WAYS[w.random.nextInt(WAYS.length)];
			int[] far = {px + way[0] * 3, pz + way[1] * 3};
			boolean inZone = LostCastleGrid.zoneOf(px + way[0]) == zx && LostCastleGrid.zoneOf(pz + way[1]) == zz;
			if (!inZone || nearSpawner(placed, px + way[0], pz + way[1]) || !Work.inside(far[0], far[1]) || w.blocked(far[0], far[1])) {
				continue;
			}
			if (loose) {
				for (int dx = -radius; dx <= radius; dx++) {
					for (int dz = -radius; dz <= radius; dz++) {
						w.carve(px + dx, pz + dz);
					}
				}
			}
			// The two columns in front of the face are level floor, though the ground round them may be a hill.
			w.carve(px + way[0] * 2, pz + way[1] * 2);
			w.carve(far[0], far[1]);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					w.wall(px + dx, pz + dz, CLEAR);
					w.claim(px + dx, pz + dz);
				}
			}
			return new Spawner(px + way[0], pz + way[1], LostCastleGrid.facing(way[0], way[1]));
		}
		return null;
	}

	// ---------------------------------------------------------------- props

	private static void mark(final boolean[] mask, final int x0, final int z0, final int x1, final int z1) {
		for (int x = Math.max(LO, x0); x <= Math.min(HI, x1); x++) {
			for (int z = Math.max(LO, z0); z <= Math.min(HI, z1); z++) {
				mask[Work.at(x, z)] = true;
			}
		}
	}

	private static boolean footOfWall(final Work w, final int x, final int z) {
		for (int[] d : WAYS) {
			if (w.fill(x + d[0], z + d[1]) > RISE) {
				return true;
			}
		}
		return false;
	}

	/** Props and candles for a floor. */
	static List<Prop> props(final Work w, final boolean[] route, final List<Chest> chests, final List<Spawner> spawners) {
		Random random = w.random;
		boolean[] near = new boolean[S * S];
		for (Spawner spawner : spawners) {
			mark(near, spawner.x() - 2, spawner.z() - 2, spawner.x() + 2, spawner.z() + 2);
		}
		for (Chest chest : chests) {
			for (int c = 0; c < LostCastlePlan.ALCOVE_WIDTH; c++) {
				LostCastlePlan.Cell cell = chest.cell(c);
				mark(near, cell.x() - 3, cell.z() - 3, cell.x() + 3, cell.z() + 3);
			}
		}
		for (int x = LO; x <= HI; x++) {
			for (int z = LO; z <= HI; z++) {
				int i = Work.at(x, z);
				if (w.locked(x, z)) {
					mark(near, x - 2, z - 2, x + 2, z + 2);
				}
				if (w.hole[i]) {
					mark(near, x - 1, z - 1, x + 1, z + 1);
				}
			}
		}
		boolean[] used = new boolean[S * S];
		List<Prop> props = new ArrayList<>();
		List<int[]> cells = new ArrayList<>();
		for (int x = LO; x <= HI; x++) {
			for (int z = LO; z <= HI; z++) {
				int i = Work.at(x, z);
				if (w.fill(x, z) == 0 && !route[i] && !w.open[i] && !near[i] && !w.hole[i] && !w.locked(x, z)) {
					cells.add(new int[] {x, z});
				}
			}
		}
		LostCastlePlan.shuffle(cells, random);
		int total = 0;
		for (int weight : WEIGHTS) {
			total += weight;
		}
		int want = 60 + random.nextInt(41);
		int barrels = 0;
		for (int[] cell : cells) {
			if (props.size() >= want) {
				break;
			}
			int roll = random.nextInt(total);
			int pick = 0;
			while (roll >= WEIGHTS[pick]) {
				roll -= WEIGHTS[pick];
				pick++;
			}
			PropKind kind = FLOOR[pick];
			boolean wallBound = pick < 5;
			if (wallBound && !footOfWall(w, cell[0], cell[1]) || kind == PropKind.BARREL && barrels >= MAX_BARRELS) {
				continue;
			}
			barrels += kind == PropKind.BARREL ? 1 : 0;
			used[Work.at(cell[0], cell[1])] = true;
			props.add(new Prop(kind, cell[0], 0, cell[1], kind == PropKind.SKULL ? random.nextInt(16) : 0));
		}
		candles(w, route, near, used, props);
		walls(w, near, props);
		ceiling(w, near, props);
		return props;
	}

	/** One cluster of one to four lit candles in each six by six block of the floor that has a free spot. */
	private static void candles(final Work w, final boolean[] route, final boolean[] near, final boolean[] used, final List<Prop> props) {
		final int block = 6;
		for (int bx = LO; bx <= HI; bx += block) {
			for (int bz = LO; bz <= HI; bz += block) {
				List<int[]> spots = new ArrayList<>();
				for (int x = bx; x < Math.min(HI + 1, bx + block); x++) {
					for (int z = bz; z < Math.min(HI + 1, bz + block); z++) {
						int i = Work.at(x, z);
						if (w.fill(x, z) <= RISE && !route[i] && !w.open[i] && !near[i] && !used[i] && !w.hole[i] && !w.locked(x, z)) {
							spots.add(new int[] {x, z});
						}
					}
				}
				if (!spots.isEmpty()) {
					int[] spot = spots.get(w.random.nextInt(spots.size()));
					used[Work.at(spot[0], spot[1])] = true;
					props.add(new Prop(PropKind.CANDLE, spot[0], w.fill(spot[0], spot[1]), spot[1], 1 + w.random.nextInt(4)));
				}
			}
		}
	}

	/** Wall skulls, high cobwebs and chains, in the open beside a wall. */
	private static void walls(final Work w, final boolean[] near, final List<Prop> props) {
		Random random = w.random;
		int want = 36 + random.nextInt(25);
		int placed = 0;
		for (int tries = 0; tries < want * 60 && placed < want; tries++) {
			int x = LO + random.nextInt(HI - LO + 1);
			int z = LO + random.nextInt(HI - LO + 1);
			int i = Work.at(x, z);
			if (w.fill(x, z) > 1 || near[i] || w.open[i] || w.hole[i] || w.locked(x, z)) {
				continue;
			}
			int[] d = WAYS[random.nextInt(WAYS.length)];
			int nx = x + d[0];
			int nz = z + d[1];
			int roll = random.nextInt(100);
			PropKind kind = roll < 40 ? PropKind.WALL_SKULL : roll < 75 ? PropKind.WALL_COBWEB : PropKind.WALL_CHAIN;
			int y = switch (kind) {
				case WALL_SKULL -> 2 + random.nextInt(5);
				case WALL_COBWEB -> 8 + random.nextInt(5);
				default -> 3 + random.nextInt(7);
			};
			if (w.fill(nx, nz) <= y || w.locked(nx, nz) || w.blocked(nx, nz) && Work.inside(nx, nz)) {
				continue;
			}
			Side side = LostCastleGrid.facing(-d[0], -d[1]);
			Prop prop = new Prop(kind, x, y, z, side.ordinal());
			if (!props.contains(prop)) {
				props.add(prop);
				placed++;
			}
		}
	}

	/** Hanging chains and corner cobwebs under the ceiling, where the ceiling is whole. */
	private static void ceiling(final Work w, final boolean[] near, final List<Prop> props) {
		Random random = w.random;
		int want = 16 + random.nextInt(15);
		int placed = 0;
		for (int tries = 0; tries < want * 60 && placed < want; tries++) {
			int x = LO + random.nextInt(HI - LO + 1);
			int z = LO + random.nextInt(HI - LO + 1);
			int i = Work.at(x, z);
			if (w.fill(x, z) > RISE || w.hang[i] != 0 || near[i] || w.open[i] || w.hole[i] || w.locked(x, z)) {
				continue;
			}
			boolean corner = false;
			for (int[] d : WAYS) {
				corner |= w.fill(x + d[0], z + d[1]) >= CLEAR;
			}
			boolean cobweb = corner && random.nextInt(100) < 45;
			props.add(new Prop(cobweb ? PropKind.CEILING_COBWEB : PropKind.CEILING_CHAIN, x, CLEAR - 1, z, cobweb ? 0 : 1 + random.nextInt(3)));
			placed++;
		}
	}
}
