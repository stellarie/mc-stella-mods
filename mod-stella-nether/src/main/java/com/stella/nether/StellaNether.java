package com.stella.nether;

import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The instanced nether raid. Gameplay is server side; the rest of the modpack is a hard dependency. */
public final class StellaNether implements ModInitializer {
	public static final String MOD_ID = "mod-stella-nether";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final List<String> MODPACK = List.of(
		"mod-stella-mobs", "mod-stella-equips-enchants", "mod-stella-spells", "mc-stella-villages", "mod-stella-tower",
		"mod-stella-food"
	);

	@Override
	public void onInitialize() {
		NetherBlocks.register();
		NetherItems.register();
		RaidTravel.register();
		RaidInstance.register();
		RaidLoot.register();
		BlazeSovereign.register();
		FlameMantle.register();
		LOGGER.info("[NETHER] initialized");
	}

	public static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
