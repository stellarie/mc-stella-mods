package com.stella.nether;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;

/** Records every entity the raid level loads, so a test waits for a mob's load instead of guessing a tick. */
public final class NetherGameTestInit implements ModInitializer {
	static final Set<UUID> LOADED_IN_RAID = ConcurrentHashMap.newKeySet();

	@Override
	public void onInitialize() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (level.dimension().equals(RaidTravel.RAID)) {
				LOADED_IN_RAID.add(entity.getUUID());
			}
		});
	}
}
