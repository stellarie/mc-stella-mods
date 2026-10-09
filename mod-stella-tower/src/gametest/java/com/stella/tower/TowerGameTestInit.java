package com.stella.tower;

import net.fabricmc.api.ModInitializer;

/**
 * The tests of the older floors (mazes, halls, caverns, stories) keep their floors on the older generator, which the
 * tower keeps for floors built before the biome floors; only the floors from {@link TowerTestSupport#BIOME_FLOORS_FROM}
 * up are biome floors. In play every floor is a biome floor.
 * <p>
 * The test world keeps every floor its runs have climbed, so its tower stands open millions of floors up.
 * No wanderer of such a floor may walk into another test's arena; a wanderer test widens the scan to its
 * own player alone.
 */
public final class TowerGameTestInit implements ModInitializer {
	@Override
	public void onInitialize() {
		TowerWanderers.setScanFilter(player -> false);
		TowerPlan.drawLegacyFloors(floor -> floor < TowerTestSupport.BIOME_FLOORS_FROM);
	}
}
