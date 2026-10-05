package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.Direction;

/**
 * The pillared great hall ({@link TowerArchetype#GREAT_HALL}): a hall open from the ground to the roof, ringed by a
 * gallery four to six wide along the room's walls, and the portal in the gallery's apse.
 * <p>
 * Drawn the canonical way round, then mirrored at random along x, along z, or both. The nave runs along x between
 * two rows of free-standing pillars that reach the roof; more pillars hold the gallery's inner edge. Rows of pews
 * give cover in the nave, with a lamp set in each row's outer end and a walk four wide down the middle. Two to four
 * chapels, walled rooms with one door two wide, stand under the gallery along the long walls. One stair three wide
 * climbs to the gallery at each end of the hall, so the gallery is a ring with two ways down. The arrival is at the
 * nave's near end; the portal on the gallery at the far end, against the end wall.
 */
final class TowerGreatHall {
	private static final int SIZE = TowerLayout.SIZE;
	static final int STAIR_WIDTH = 3;
	/** How wide the gallery runs along the walls, at least and at most. */
	static final int GALLERY_MIN = 4;
	static final int GALLERY_MAX = 6;
	static final int CHAPEL_WIDTH = 7;
	static final int PEW_PITCH = 4;
	/** The walk down the nave's middle that no pew crosses. */
	static final int AISLE = 4;
	/** How far pews keep from a stair's cells. */
	private static final int STAIR_CLEAR = 2;

	private TowerGreatHall() {
	}

	static TowerPlan.Draft draw(final Random random) {
		int gallery = GALLERY_MIN + random.nextInt(GALLERY_MAX - GALLERY_MIN + 1);
		int nave = 16 + 2 * random.nextInt(3);
		int naveLowZ = SIZE / 2 - nave / 2;
		int naveHighZ = naveLowZ + nave - 1;
		int pitch = 6 + random.nextInt(3);
		int story = TowerPlan.STORY;
		int inLow = gallery + 1;
		int inHigh = SIZE - 2 - gallery;

		boolean[][] groundWall = new boolean[SIZE][SIZE];
		boolean[][] galleryWall = new boolean[SIZE][SIZE];
		boolean[][] pillar = new boolean[SIZE][SIZE];
		boolean[][] galleryFloor = TowerStory.full();
		byte[][] cover = new byte[SIZE][SIZE];
		for (int i = 0; i < SIZE; i++) {
			border(groundWall, i);
			border(galleryWall, i);
		}
		for (int x = inLow; x <= inHigh; x++) {
			for (int z = inLow; z <= inHigh; z++) {
				galleryFloor[x][z] = false;
			}
		}
		// The gallery's edge pillars, two by two on its inner edge, clear of the apse in the middle of the far end.
		for (int x = inLow + 2; x + 1 <= inHigh - 2; x += pitch) {
			square(pillar, x, gallery - 1);
			square(pillar, x, inHigh + 1);
		}
		// The stairs' places first, one at each end, the near one north of the nave, the far one south; the end
		// pillars keep clear of them.
		int nearLow = gallery + 2;
		int nearHigh = naveLowZ - 5 - STAIR_WIDTH;
		int farLow = naveHighZ + 5;
		int farHigh = inHigh - 2 - STAIR_WIDTH;
		if (nearHigh < nearLow || farHigh < farLow) {
			return null;
		}
		int nearZ = nearLow + random.nextInt(nearHigh - nearLow + 1);
		int farZ = farLow + random.nextInt(farHigh - farLow + 1);
		for (int z = inLow + 2; z + 1 <= inHigh - 2; z += pitch) {
			if (z + 1 < nearZ - 1 || z > nearZ + STAIR_WIDTH) {
				square(pillar, gallery - 1, z);
			}
			if (Math.abs(z - SIZE / 2) > 4 && (z + 1 < farZ - 1 || z > farZ + STAIR_WIDTH)) {
				square(pillar, inHigh + 1, z);
			}
		}
		// The nave's own pillars, free-standing, to the roof.
		for (int x = inLow + 6; x + 1 <= inHigh - 6; x += pitch) {
			square(pillar, x, naveLowZ - 2);
			square(pillar, x, naveHighZ + 1);
		}
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				groundWall[x][z] |= pillar[x][z];
				galleryWall[x][z] |= pillar[x][z];
			}
		}
		List<TowerFlight> flights = List.of(
			new TowerFlight(inLow + story - 1, nearZ + STAIR_WIDTH - 1, Direction.WEST, STAIR_WIDTH, 0, story, TowerFlight.STAIRS),
			new TowerFlight(inHigh - story + 1, farZ, Direction.EAST, STAIR_WIDTH, 0, story, TowerFlight.STAIRS));
		// The chapels, under the gallery along the long walls, away from the stairs' ends.
		int chapels = 2 + random.nextInt(3);
		List<Integer> slots = new ArrayList<>();
		for (int x = inLow + 10; x + CHAPEL_WIDTH <= inHigh - 9; x += CHAPEL_WIDTH + 2) {
			slots.add(x);
		}
		java.util.Collections.shuffle(slots, random);
		int built = 0;
		for (int i = 0; i < slots.size() * 2 && built < chapels; i++) {
			int x = slots.get(i / 2);
			boolean north = i % 2 == 0;
			int front = north ? gallery : SIZE - 1 - gallery;
			int door = x + CHAPEL_WIDTH / 2 - 1;
			boolean blocked = false;
			for (int dz = -1; dz <= 1; dz++) {
				blocked |= pillar[door][front + dz] || pillar[door + 1][front + dz];
			}
			if (!blocked) {
				chapel(groundWall, x, north, gallery);
				built++;
			}
		}
		int arrivalX = inLow + 7;
		int arrivalZ = SIZE / 2;
		TowerMaze.Zone zone = new TowerMaze.Zone(arrivalX - 4, arrivalZ - 4, 0, TowerLayout.HEIGHT);
		pews(cover, flights, zone.x() + TowerMaze.SAFE + 2, inHigh - 3, naveLowZ, naveHighZ);
		int portalX = SIZE - 2;
		int portalZ = SIZE / 2 - 1;
		TowerPlan.Room runner = new TowerPlan.Room(zone.x() + TowerMaze.SAFE, SIZE / 2 - 1, inHigh - zone.x() - TowerMaze.SAFE + 1, 2);
		List<TowerStory> stories = List.of(
			new TowerStory(0, story - 1, story - 1, TowerStory.full(), groundWall, pillar, cover, null, null),
			new TowerStory(story, TowerLayout.HEIGHT, TowerLayout.HEIGHT - story, galleryFloor, galleryWall, pillar, null, null, null));
		TowerPlan.Draft draft = new TowerPlan.Draft(stories, flights, arrivalX, arrivalZ, portalX, portalZ, -1, 1, List.of(zone), runner);
		return draft.mirrored(random.nextBoolean(), random.nextBoolean());
	}

	/** A chapel against a long wall: two side walls and a front wall the gallery's depth out, its door two wide in the middle. */
	private static void chapel(final boolean[][] wall, final int x, final boolean north, final int depth) {
		for (int d = 1; d <= depth; d++) {
			int z = north ? d : SIZE - 1 - d;
			wall[x][z] = true;
			wall[x + CHAPEL_WIDTH - 1][z] = true;
		}
		int front = north ? depth : SIZE - 1 - depth;
		int door = x + CHAPEL_WIDTH / 2 - 1;
		for (int dx = 1; dx < CHAPEL_WIDTH - 1; dx++) {
			if (x + dx != door && x + dx != door + 1) {
				wall[x + dx][front] = true;
			}
		}
	}

	/** Rows of pews across the nave, a walk four wide left down its middle and a lamp in each row's outer end. */
	private static void pews(final byte[][] cover, final List<TowerFlight> flights, final int fromX, final int toX, final int naveLowZ, final int naveHighZ) {
		int middle = SIZE / 2;
		for (int x = fromX; x <= toX; x += PEW_PITCH) {
			for (int z = naveLowZ + 1; z <= naveHighZ - 1; z++) {
				if (Math.abs(z - middle + 0.5) < AISLE / 2.0 || nearFlight(flights, x, z)) {
					continue;
				}
				boolean end = z == naveLowZ + 1 || z == naveHighZ - 1;
				cover[x][z] = end ? TowerStory.COVER_LAMP : TowerStory.COVER;
			}
		}
	}

	private static boolean nearFlight(final List<TowerFlight> flights, final int x, final int z) {
		for (TowerFlight flight : flights) {
			for (int i = -1; i <= flight.length(); i++) {
				for (int w = 0; w < flight.width(); w++) {
					if (Math.abs(flight.cellX(i, w) - x) <= STAIR_CLEAR && Math.abs(flight.cellZ(i, w) - z) <= STAIR_CLEAR) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static void border(final boolean[][] wall, final int i) {
		wall[i][0] = true;
		wall[i][SIZE - 1] = true;
		wall[0][i] = true;
		wall[SIZE - 1][i] = true;
	}

	private static void square(final boolean[][] grid, final int x, final int z) {
		for (int dx = 0; dx < 2; dx++) {
			for (int dz = 0; dz < 2; dz++) {
				grid[x + dx][z + dz] = true;
			}
		}
	}
}
