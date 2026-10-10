package com.stella.tower;

/**
 * A lost room's plan, frozen. A room is the pre-biome plan of its borrowed boss floor, moved to its cell, drawn
 * by {@link TowerPlan#legacy}. It reads no biome, no new height and no new safe zone rule, so the biome floors
 * cannot change a room's design, traps or levels. {@code TowerLostRoomFrozenGameTest} holds its hash.
 */
final class LegacyRoomPlan {
	private LegacyRoomPlan() {
	}

	static TowerPlan of(final long seed, final int room) {
		return TowerPlan.legacy(seed, LostRooms.looksFloor(room)).relocated(room);
	}
}
