package com.stella.tower;

import net.fabricmc.api.ModInitializer;

/**
 * The test world keeps every floor its runs have climbed, so its tower stands open millions of floors up.
 * No wanderer of such a floor may walk into another test's arena; a wanderer test widens the scan to its
 * own player alone.
 */
public final class TowerGameTestInit implements ModInitializer {
	@Override
	public void onInitialize() {
		TowerWanderers.setScanFilter(player -> false);
	}
}
