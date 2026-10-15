package com.stella.mobs.api;

import com.stella.mobs.scaling.FriendlyLevels;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Mob;

/**
 * Publishes the friendly level under {@code mod-stella-mobs:friendly-level} as a {@code Consumer<Mob>}. A mod that owns
 * a friendly NPC calls it, and again as players grow. The NPC takes the higher of the nearest player's level and its
 * area level, with the health and damage scaling of a hostile mob of that level.
 */
public final class FriendlyLevelProvider {
	public static final String OBJECT_SHARE_KEY = "mod-stella-mobs:friendly-level";

	private FriendlyLevelProvider() {
	}

	public static void register() {
		Consumer<Mob> provider = FriendlyLevels::refresh;
		FabricLoader.getInstance().getObjectShare().put(OBJECT_SHARE_KEY, provider);
	}
}
