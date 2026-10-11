package com.stella.tower;

/**
 * Holds off, for one floor of a case, everything that raises a mob on a floor with a player on it by chance and that the case
 * does not mean to have: the cap's mobs, the ghost packs, the ghost squads, the phantoms with their archers, and the shamans.
 * A case that wants one of these asks for it after.
 */
final class QuietFloor {
	private QuietFloor() {
	}

	static void hold(final int floor) {
		TowerMobs.pinFloorCap(floor, 0);
		TowerGhosts.pinRiseChance(floor, 0.0);
		GhostSquads.pinRiseChance(floor, 0.0);
		TowerPhantoms.pinRiseChance(floor, 0.0);
		TowerShamans.pinHolds(floor, false);
	}

	static void release(final int floor) {
		TowerMobs.pinFloorCap(floor, null);
		TowerGhosts.pinRiseChance(floor, null);
		GhostSquads.pinRiseChance(floor, null);
		TowerPhantoms.pinRiseChance(floor, null);
		TowerShamans.pinHolds(floor, null);
	}
}
