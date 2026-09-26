package com.stella.villages;

import com.stella.villages.mission.MissionService;
import com.stella.villages.mission.NemesisState;
import com.stella.villages.patrol.PatrolService;
import com.stella.villages.patrol.PatrolState;
import com.stella.villages.raid.VillageRaids;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class McStellaVillages implements ModInitializer {
	/** V4: the entity tag that tells mod-stella-mobs to leave a mob's kit, health and rank to this mod. */
	public static final String NO_SPAWN_ROLL_TAG = "mod-stella-mobs.no_spawn_roll";
	public static final String MOD_ID = "mc-stella-villages";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		PatrolState.register();
		NemesisState.register();
		PatrolService.register();
		MissionService.register();
		VillageRaids.register();
		LOGGER.info("{} initialized", MOD_ID);
	}
}
