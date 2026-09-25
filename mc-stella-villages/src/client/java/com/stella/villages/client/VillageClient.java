package com.stella.villages.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VillageClient implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("mc-stella-villages-client");

	@Override
	public void onInitializeClient() {
		LOGGER.info("mc-stella-villages client initialized");
	}
}
