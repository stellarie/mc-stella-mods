package com.stella.learn;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Learning mobs. Gameplay is server side; the mobs mod is a hard dependency. */
public final class StellaLearn implements ModInitializer {
	public static final String MOD_ID = "mod-stella-learn";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LearnConfigStore.load();
		Profiles.register();
		HabitRecorder.register();
	}

	public static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
