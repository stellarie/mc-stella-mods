package com.stella.end;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The End castle. Gameplay is server side; every sibling mod is optional and reached only through ObjectShare keys. */
public final class StellaEnd implements ModInitializer {
	public static final String MOD_ID = "mod-stella-end";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		EndClock.register();
		EndBlocks.register();
		EndItems.register();
		EndCastles.register();
		FloorSpawner.register();
		ShulkerKnockback.register();
		UndeadBlazes.register();
		SoulCharges.register();
		UndeadSovereign.register();
		EndDragons.register();
		BehaviorLog.info("[END] initialized");
	}

	public static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
