package com.stella.villages;

import com.stella.villages.patrol.PatrolService;
import com.stella.villages.patrol.PatrolState;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class McStellaVillages implements ModInitializer {
	public static final String MOD_ID = "mc-stella-villages";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		PatrolState.register();
		PatrolService.register();
		LOGGER.info("{} initialized", MOD_ID);
	}
}
