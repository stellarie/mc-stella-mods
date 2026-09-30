package com.stella.tower;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import com.stella.tower.TowerPlan.ChestSpot;
import net.minecraft.core.Direction;

/**
 * Where a floor's double chests go, decided in the plan and placed by a later slice. Pure: the same plan gives the
 * same spots.
 * <p>
 * A floor has at least {@link #MIN_SPOTS} spots. Each is a double chest's two blocks side by side, standing against a
 * wall on open ground with its front clear: in open ground where walls close in (a dead end, a room's corner), far
 * from the arrival by the walk, clear of the traps, the safe zones, the hall (which holds the boss's pyramid) and the
 * way up to the portal. Where the floor has room for it they are also spread apart. On every fifth floor there is one
 * more, {@link ChestSpot#guaranteed()}: in the entrance zone on an elite floor, and inside the walled boss room on
 * a boss floor.
 */
final class TowerChestSpots {
	/** The fewest regular spots a floor has. */
	static final int MIN_SPOTS = 5;
	/** How many regular spots a floor is given when it has room for them. */
	static final int WANTED = 6;
	/** How far a spot keeps from every trap, in blocks. */
	static final int TRAP_CLEAR = 3;
	/** How far a spot keeps from the portal, in blocks: the way up to it, and the pyramid's steps. */
	static final int PORTAL_CLEAR = 9;
	/** How far from the arrival a guaranteed spot in the entrance zone keeps from the arrival's own block, in blocks. */
	static final int ARRIVAL_CLEAR = 3;
	/** The stages a search goes through, strictest first: the walk from the arrival, and the spacing between spots. */
	static final int[][] STAGES = {{30, 12}, {20, 9}, {12, 7}, {8, 5}, {4, 4}, {1, 3}};
	private static final int SIZE = TowerLayout.SIZE;
	private static final long SALT = 0x43484553L;
	private static final int[][] WAYS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
	private static final Direction[] FACES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	private TowerChestSpots() {
	}

	/** The walk from the arrival to every open spot of the plan, in blocks; -1 where it does not reach. */
	static int[][] walk(final TowerPlan plan) {
		int[][] dist = new int[SIZE][SIZE];
		for (int[] row : dist) {
			java.util.Arrays.fill(row, -1);
		}
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		dist[plan.arrivalX()][plan.arrivalZ()] = 0;
		queue.add(new int[] {plan.arrivalX(), plan.arrivalZ()});
		while (!queue.isEmpty()) {
			int[] at = queue.poll();
			for (int[] way : WAYS) {
				int x = at[0] + way[0];
				int z = at[1] + way[1];
				if (x >= 0 && z >= 0 && x < SIZE && z < SIZE && dist[x][z] < 0 && !plan.walls()[x][z] && !plan.isFramePost(x, z)) {
					dist[x][z] = dist[at[0]][at[1]] + 1;
					queue.add(new int[] {x, z});
				}
			}
		}
		return dist;
	}

	/** The plan's chest spots, or null when the plan leaves room for fewer than {@link #MIN_SPOTS}. */
	static List<ChestSpot> place(final TowerPlan plan) {
		int[][] dist = walk(plan);
		List<TowerTraps.Trap> traps = TowerTraps.layout(plan.seed(), plan.floor(), plan);
		Random random = new Random(plan.seed() ^ SALT ^ (plan.floor() * 0x9E3779B97F4A7C15L) ^ plan.attempt());
		List<Candidate> candidates = new ArrayList<>();
		for (int x = 1; x < SIZE - 1; x++) {
			for (int z = 1; z < SIZE - 1; z++) {
				for (Direction face : FACES) {
					ChestSpot spot = new ChestSpot(x, z, face, false);
					if (standsAgainstAWall(plan, spot) && reached(dist, spot) >= 0 && !keepsOut(plan, spot, traps)) {
						candidates.add(new Candidate(spot, reached(dist, spot), enclosure(plan, spot)));
					}
				}
			}
		}
		Collections.shuffle(candidates, random);
		// The most shut in first: a dead end or a room's corner before the middle of a wide path.
		candidates.sort((a, b) -> Integer.compare(b.enclosure(), a.enclosure()));
		List<ChestSpot> spots = new ArrayList<>();
		for (int[] stage : STAGES) {
			spots.clear();
			for (Candidate candidate : candidates) {
				if (spots.size() >= WANTED) {
					break;
				}
				if (candidate.walk() >= stage[0] && apart(spots, candidate.spot(), stage[1])) {
					spots.add(candidate.spot());
				}
			}
			if (spots.size() >= MIN_SPOTS) {
				break;
			}
		}
		if (spots.size() < MIN_SPOTS) {
			return null;
		}
		ChestSpot guaranteed = guaranteed(plan, dist);
		if (plan.floor() % TowerElites.ELITE_EVERY == 0) {
			if (guaranteed == null) {
				return null;
			}
			spots.add(guaranteed);
		}
		return List.copyOf(spots);
	}

	private record Candidate(ChestSpot spot, int walk, int enclosure) {
	}

	private static boolean open(final TowerPlan plan, final int x, final int z) {
		return x >= 0 && z >= 0 && x < SIZE && z < SIZE && !plan.walls()[x][z] && !plan.isFramePost(x, z);
	}

	private static int reached(final int[][] dist, final ChestSpot spot) {
		return Math.min(dist[spot.x()][spot.z()], dist[spot.second().x()][spot.second().z()]);
	}

	/** Both blocks open, both blocks behind them wall, both blocks in front of them open. */
	static boolean standsAgainstAWall(final TowerPlan plan, final ChestSpot spot) {
		Direction face = spot.facing();
		for (TowerPlan.Spot cell : spot.cells()) {
			if (!open(plan, cell.x(), cell.z()) || open(plan, cell.x() - face.getStepX(), cell.z() - face.getStepZ())
				|| !open(plan, cell.x() + face.getStepX(), cell.z() + face.getStepZ())) {
				return false;
			}
		}
		return true;
	}

	/** Whether the spot is somewhere chests do not go: the zones, the hall, the portal's way, near a trap. */
	private static boolean keepsOut(final TowerPlan plan, final ChestSpot spot, final List<TowerTraps.Trap> traps) {
		for (TowerPlan.Spot cell : spot.cells()) {
			if (plan.inSafeZone(cell.x(), cell.z()) || plan.hall() > 0 && inHall(plan, cell.x(), cell.z())) {
				return true;
			}
			if (Math.max(Math.abs(cell.x() - plan.portalX()), Math.abs(cell.z() - plan.portalZ())) <= PORTAL_CLEAR) {
				return true;
			}
			for (TowerTraps.Trap trap : traps) {
				if (Math.max(Math.abs(cell.x() - trap.x()), Math.abs(cell.z() - trap.z())) <= TRAP_CLEAR) {
					return true;
				}
			}
		}
		return false;
	}

	/** The hall and the wall round it, which the boss floor's pyramid stands in. */
	private static boolean inHall(final TowerPlan plan, final int x, final int z) {
		int low = plan.hallLow() - 2;
		int high = plan.hallLow() + plan.hall() + 1;
		return x >= low && x <= high && z >= low && z <= high;
	}

	/** How shut in a spot is: the walls in the nine by nine blocks round it. */
	private static int enclosure(final TowerPlan plan, final ChestSpot spot) {
		int walls = 0;
		for (int dx = -4; dx <= 5; dx++) {
			for (int dz = -4; dz <= 5; dz++) {
				int x = spot.x() + dx;
				int z = spot.z() + dz;
				if (x < 0 || z < 0 || x >= SIZE || z >= SIZE || plan.walls()[x][z]) {
					walls++;
				}
			}
		}
		return walls;
	}

	private static boolean apart(final List<ChestSpot> spots, final ChestSpot spot, final int gap) {
		for (ChestSpot other : spots) {
			if (Math.max(Math.abs(other.x() - spot.x()), Math.abs(other.z() - spot.z())) < gap) {
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------ the guaranteed spot

	/**
	 * The one spot a fifth floor is sure to have: the entrance zone's on an elite floor, the walled boss room's on a
	 * boss floor; null on any other floor, and null if there is nowhere to put it.
	 */
	private static ChestSpot guaranteed(final TowerPlan plan, final int[][] dist) {
		if (plan.floor() % TowerElites.ELITE_EVERY != 0 || plan.hall() == 0) {
			return null;
		}
		boolean boss = plan.floor() % TowerElites.BOSS_EVERY == 0;
		TowerMaze.Zone zone = plan.safeZones().get(boss ? 1 : 0);
		ChestSpot best = null;
		int bestScore = Integer.MIN_VALUE;
		for (int x = zone.x(); x < zone.x() + TowerMaze.SAFE; x++) {
			for (int z = zone.z(); z < zone.z() + TowerMaze.SAFE; z++) {
				for (Direction face : FACES) {
					ChestSpot spot = new ChestSpot(x, z, face, true);
					if (!fitsZone(plan, spot, zone, boss)) {
						continue;
					}
					// Against a wall where there is one; in the entrance zone, off the arrival's own block.
					boolean backed = true;
					int away = Integer.MAX_VALUE;
					for (TowerPlan.Spot cell : spot.cells()) {
						backed &= !open(plan, cell.x() - face.getStepX(), cell.z() - face.getStepZ());
						away = Math.min(away, Math.max(Math.abs(cell.x() - plan.arrivalX()), Math.abs(cell.z() - plan.arrivalZ())));
					}
					if (!boss && away < ARRIVAL_CLEAR) {
						continue;
					}
					int score = (backed ? 1000 : 0) + (boss ? 0 : away);
					if (score > bestScore) {
						bestScore = score;
						best = spot;
					}
				}
			}
		}
		return best;
	}

	/**
	 * Whether a guaranteed spot fits its zone: both blocks and the one in front inside it, open; on a boss floor inside
	 * the ring, off the two rows the openings run along, and off the workstations' row.
	 */
	private static boolean fitsZone(final TowerPlan plan, final ChestSpot spot, final TowerMaze.Zone zone, final boolean boss) {
		Direction face = spot.facing();
		for (TowerPlan.Spot cell : spot.cells()) {
			int fx = cell.x() + face.getStepX();
			int fz = cell.z() + face.getStepZ();
			if (!open(plan, cell.x(), cell.z()) || !open(plan, fx, fz) || !zone.contains(cell.x(), cell.z()) || !zone.contains(fx, fz)) {
				return false;
			}
			if (boss) {
				if (plan.isRing(cell.x(), cell.z()) || plan.isRing(fx, fz) || onOpeningLine(plan, cell.x(), cell.z()) || onOpeningLine(plan, fx, fz)
					|| onStationRow(plan, cell.x(), cell.z()) || onStationRow(plan, fx, fz)) {
					return false;
				}
			}
		}
		return true;
	}

	/** Whether a room offset lies on a line through the room from one of its openings to the other. */
	private static boolean onOpeningLine(final TowerPlan plan, final int x, final int z) {
		for (TowerPlan.Gate gate : plan.gates()) {
			for (TowerPlan.Spot cell : gate.cells()) {
				if (cell.x() == x || cell.z() == z) {
					return true;
				}
			}
		}
		return false;
	}

	/** Whether a room offset is on, or in front of, the workstations along the room's wall. */
	private static boolean onStationRow(final TowerPlan plan, final int x, final int z) {
		for (TowerPlan.Station station : plan.stations()) {
			if (Math.abs(station.x() - x) <= 1 && Math.abs(station.z() - z) <= 1) {
				return true;
			}
		}
		return false;
	}
}
