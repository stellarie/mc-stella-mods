package com.stella.rpg;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StellaRpg implements ModInitializer {
	public static final String MOD_ID = "mod-stella-rpg";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		StatSheet.EMPTY.allocated();
		RpgLevelUp.LAST_LEVEL.identifier();
		RpgDeath.register();
		RpgAttributes.register();
		RpgCommand.register();
		ServerLifecycleEvents.SERVER_STARTING.register(server -> RpgConfigStore.reload(RpgConfigStore.defaultPath()));
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(
			(server, resources, success) -> RpgConfigStore.reload(RpgConfigStore.defaultPath()));
		LOGGER.info("{} initialized", MOD_ID);
	}

	public static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
