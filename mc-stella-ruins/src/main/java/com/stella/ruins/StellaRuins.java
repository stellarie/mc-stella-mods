package com.stella.ruins;

import com.stella.ruins.reward.RuinsRewards;
import com.stella.ruins.site.Sites;
import com.stella.ruins.world.RuinsWorld;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stella Ruins: structures on the surface and in caves. Each one holds an objective, themed mobs, and
 * rewards that drop nowhere else. Clues to them turn up in ordinary chests.
 */
public final class StellaRuins implements ModInitializer {
	public static final String MOD_ID = "mod-stella-ruins";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		RuinsWorld.register();
		RuinsRewards.register();
		Sites.register();
	}
}
