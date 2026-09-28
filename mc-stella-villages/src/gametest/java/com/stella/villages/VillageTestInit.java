package com.stella.villages;

import com.stella.villages.patrol.VillageWitchKit;
import net.fabricmc.api.ModInitializer;

/** Keeps the legacy ten-member patrol cases separate from the witch cases. */
public final class VillageTestInit implements ModInitializer {
	@Override
	public void onInitialize() {
		VillageWitchKit.force(false);
	}
}
