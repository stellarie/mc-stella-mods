package com.stella.tower;

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
 * A floor has at least {@link #MIN_SPOTS} spots, on any of its stories. Each is a double chest's two blocks side by
 * side, standing against a wall on its story's floor with its front clear: where walls close in (a dead end, a room's
 * corner), far from the arrival by the walk on foot, clear of the traps, the safe zones, the hall (which holds the
 * boss's pyramid), the stairs and the way up to the portal. Where the floor has room for it they are also spread apart. On every fifth floor there is one
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
	/** The walks from the arrival a search asks of a spot, strictest first. */
	static final int[][] STAGES = {{30}, {20}, {12}, {8}, {4}, {1}};
	/** How far apart any two chests stand across the room, in blocks, whatever their stories. */
	static final int MIN_APART = 12;
	/** The most chests a quarter of the room holds. */
	static final int PER_QUADRANT = 2;
	private static final int SIZE = TowerLayout.SIZE;
	private static final long SALT = 0x43484553L;
	private static final int[][] WAYS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
	private static final Direction[] FACES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	private TowerChestSpots() {
	}

	/** The walk on foot from the arrival to every spot of the volume, in steps; -1 where it does not reach (see {@link TowerWalk#index}). */
	static int[] walk(final TowerPlan plan, final TowerWalk.Space space) {
		return TowerWalk.distances(space, plan.arrivalX(), 0, plan.arrivalZ());
	}

	/** The plan's chest spots over its volume, drawn before the chests, or null when it leaves room for fewer than {@link #MIN_SPOTS}. */
	static List<ChestSpot> place(final TowerPlan plan, final TowerVolume volume) {
		int[] dist = walk(plan, volume);
		List<TowerTraps.Trap> traps = TowerTraps.layout(plan.seed(), plan.floor(), plan);
		Random random = new Random(plan.seed() ^ SALT ^ (plan.floor() * 0x9E3779B97F4A7C15L) ^ plan.attempt());
		List<Candidate> candidates = new ArrayList<>();
		for (TowerStory story : plan.stories()) {
			for (int x = 1; x < SIZE - 1; x++) {
				for (int z = 1; z < SIZE - 1; z++) {
					// A biome floor's ground rises and falls: a column's own feet level, and none where nothing stands.
					int y = plan.isBiome() ? plan.standY(x, z) : story.y();
					if (y < TowerWalk.LOW) {
						continue;
					}
					for (Direction face : FACES) {
						ChestSpot spot = new ChestSpot(x, z, face, false, y);
						if (standsAgainstAWall(volume, spot) && reached(dist, spot) >= 0 && !keepsOut(plan, volume, spot, traps)) {
							candidates.add(new Candidate(spot, reached(dist, spot), enclosure(volume, spot)));
						}
					}
				}
			}
		}
		Collections.shuffle(candidates, random);
		// The most shut in first: a dead end or a room's corner before the middle of a wide path.
		candidates.sort((a, b) -> Integer.compare(b.enclosure(), a.enclosure()));
		// A fifth floor's guaranteed spot is placed first, so the others keep their spacing from it too.
		ChestSpot guaranteed = plan.floor() % TowerElites.ELITE_EVERY == 0 ? plan.isBiome() ? nearTheStair(plan, candidates) : guaranteed(plan) : null;
		if (plan.floor() % TowerElites.ELITE_EVERY == 0 && guaranteed == null) {
			return null;
		}
		List<ChestSpot> spots = new ArrayList<>();
		for (int[] stage : STAGES) {
			spots.clear();
			if (guaranteed != null) {
				spots.add(guaranteed);
			}
			int wanted = WANTED + spots.size();
			for (Candidate candidate : candidates) {
				if (spots.size() >= wanted) {
					break;
				}
				if (candidate.walk() >= stage[0] && apart(spots, candidate.spot(), MIN_APART) && quadrantHasRoom(spots, candidate.spot())) {
					spots.add(candidate.spot());
				}
			}
			if (spots.size() - (guaranteed != null ? 1 : 0) >= MIN_SPOTS) {
				break;
			}
		}
		if (spots.size() - (guaranteed != null ? 1 : 0) < MIN_SPOTS) {
			return null;
		}
		if (guaranteed != null) {
			spots.remove(0);
			spots.add(guaranteed);
		}
		return List.copyOf(spots);
	}

	private record Candidate(ChestSpot spot, int walk, int enclosure) {
	}

	private static boolean open(final TowerPlan plan, final int x, final int z) {
		return x >= 0 && z >= 0 && x < SIZE && z < SIZE && !plan.walls()[x][z] && !plan.isFramePost(x, z);
	}

	private static int reached(final int[] dist, final ChestSpot spot) {
		int best = -1;
		for (TowerPlan.Spot cell : spot.cells()) {
			int at = dist[TowerWalk.index(cell.x(), spot.y(), cell.z())];
			if (at >= 0 && (best < 0 || at < best)) {
				best = at;
			}
		}
		return best;
	}

	/**
	 * Both blocks on the story's own floor, both blocks behind them a wall from the feet to the head, and a body
	 * standing on both blocks in front of them.
	 */
	static boolean standsAgainstAWall(final TowerVolume volume, final ChestSpot spot) {
		Direction face = spot.facing();
		int y = spot.y();
		for (TowerPlan.Spot cell : spot.cells()) {
			int bx = cell.x() - face.getStepX();
			int bz = cell.z() - face.getStepZ();
			if (!volume.standsFlat(cell.x(), y, cell.z()) || volume.at(bx, y, bz) != TowerWalk.SOLID || volume.at(bx, y + 1, bz) != TowerWalk.SOLID
				|| !volume.stands(cell.x() + face.getStepX(), y, cell.z() + face.getStepZ())) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Whether the spot is somewhere chests do not go: the zones, the hall, a stair's way or its ends, near the portal on
	 * its story, or near a trap on the spot's story.
	 */
	private static boolean keepsOut(final TowerPlan plan, final TowerVolume volume, final ChestSpot spot, final List<TowerTraps.Trap> traps) {
		int y = spot.y();
		Direction face = spot.facing();
		for (TowerPlan.Spot cell : spot.cells()) {
			if (plan.inSafeZone(cell.x(), y, cell.z()) || y == 0 && plan.hall() > 0 && inHall(plan, cell.x(), cell.z())) {
				return true;
			}
			if (plan.isBiome() && (plan.biome().inDais(plan, cell.x(), cell.z()) || plan.biome().water()[cell.x()][cell.z()]
				|| plan.biome().water()[cell.x() + face.getStepX()][cell.z() + face.getStepZ()])) {
				return true;
			}
			if (volume.isWay(cell.x(), y, cell.z()) || volume.isWay(cell.x() + face.getStepX(), y, cell.z() + face.getStepZ())) {
				return true;
			}
			if ((plan.isBiome() || y == plan.portalY()) && Math.max(Math.abs(cell.x() - plan.portalX()), Math.abs(cell.z() - plan.portalZ())) <= PORTAL_CLEAR) {
				return true;
			}
			for (TowerTraps.Trap trap : traps) {
				if (trap.y() == y && Math.max(Math.abs(cell.x() - trap.x()), Math.abs(cell.z() - trap.z())) <= TRAP_CLEAR) {
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

	/** How shut in a spot is: the blocked cells at its feet in the ten by ten blocks round it. */
	private static int enclosure(final TowerVolume volume, final ChestSpot spot) {
		int walls = 0;
		for (int dx = -4; dx <= 5; dx++) {
			for (int dz = -4; dz <= 5; dz++) {
				if (volume.at(spot.x() + dx, spot.y(), spot.z() + dz) != TowerWalk.OPEN) {
					walls++;
				}
			}
		}
		return walls;
	}

	/** Whether a spot keeps a gap across the room from every spot, on any story. */
	private static boolean apart(final List<ChestSpot> spots, final ChestSpot spot, final int gap) {
		for (ChestSpot other : spots) {
			if (Math.max(Math.abs(other.x() - spot.x()), Math.abs(other.z() - spot.z())) < gap) {
				return false;
			}
		}
		return true;
	}

	/** The quarter of the room a spot lies in: 0 to 3. */
	static int quadrant(final ChestSpot spot) {
		return (spot.x() < SIZE / 2 ? 0 : 1) + (spot.z() < SIZE / 2 ? 0 : 2);
	}

	private static boolean quadrantHasRoom(final List<ChestSpot> spots, final ChestSpot spot) {
		int in = 0;
		for (ChestSpot other : spots) {
			in += quadrant(other) == quadrant(spot) ? 1 : 0;
		}
		return in < PER_QUADRANT;
	}

	// ------------------------------------------------------------------ the guaranteed spot

	/** How far from the stair's foot a biome floor's guaranteed chest stands, nearest and farthest, in blocks. */
	static final int NEAR_STAIR_MIN = 6;
	static final int NEAR_STAIR_MAX = 12;

	/**
	 * An elite floor's guaranteed spot on a biome floor: the most shut in of the candidates a few steps from the foot of
	 * the portal's stair, where the guard stands; null when none lies there.
	 */
	private static ChestSpot nearTheStair(final TowerPlan plan, final List<Candidate> candidates) {
		int footX = plan.portalX() + plan.portalFacing() * (2 + plan.biome().steps());
		for (Candidate candidate : candidates) {
			ChestSpot spot = candidate.spot();
			int away = Math.max(Math.abs(spot.x() - footX), Math.abs(spot.z() - plan.portalZ()));
			if (away >= NEAR_STAIR_MIN && away <= NEAR_STAIR_MAX) {
				return new ChestSpot(spot.x(), spot.z(), spot.facing(), true, spot.y());
			}
		}
		return null;
	}

	/**
	 * The one spot a fifth floor is sure to have: the entrance zone's on an elite floor, the walled boss room's on a
	 * boss floor; null on any other floor, and null if there is nowhere to put it.
	 */
	private static ChestSpot guaranteed(final TowerPlan plan) {
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
