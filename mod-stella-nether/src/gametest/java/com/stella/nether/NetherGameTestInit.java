package com.stella.nether;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;

/**
 * Switches the behaviour lines on for the tests, and records every entity the raid level loads, so a test waits
 * for a mob's load instead of guessing a tick.
 */
public final class NetherGameTestInit implements ModInitializer {
	static final Set<UUID> LOADED_IN_RAID = ConcurrentHashMap.newKeySet();

	/** The behaviour lines' sink under test: the mod's logger at INFO. */
	static final BehaviorLog.Sink TEST_SINK = StellaNether.LOGGER::info;
	/** The sink the jar starts with, as a real server runs it: read before the tests replace it. */
	static volatile BehaviorLog.Sink shippedSink;

	@Override
	public void onInitialize() {
		// The shipped jar logs no behaviour; under test every line goes to the mod's logger at INFO.
		shippedSink = BehaviorLog.set(TEST_SINK);
		HitLog.install();
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (level.dimension().equals(RaidTravel.RAID)) {
				LOADED_IN_RAID.add(entity.getUUID());
			}
		});
	}
}
