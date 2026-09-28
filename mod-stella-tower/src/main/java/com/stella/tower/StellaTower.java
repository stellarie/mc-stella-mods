package com.stella.tower;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Endless Tower: a crying obsidian portal leads to an endless run of 64 by 64 floors, each generated
 * once and kept, each one a portal away from the next.
 */
public final class StellaTower implements ModInitializer {
	public static final String MOD_ID = "mod-stella-tower";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		TowerBlocks.register();
		TowerItems.register();
		TowerTravel.register();
		TowerFloors.register();
		TowerMobs.register();
		TowerElites.register();
		TowerBuffs.register();
		TowerProgress.register();
		TowerWanderers.register();
		TowerTraps.register();
		TowerRewards.register();
		TowerGhosts.register();
		TowerRunes.register();
		RuneEffects.register();
		RuneGuards.register();
		GhostRunes.register();
		TowerWisps.register();
		LOGGER.info("Endless Tower ready: floors of {} by {} blocks, {} high", TowerLayout.SIZE, TowerLayout.SIZE, TowerLayout.HEIGHT);
	}
}
