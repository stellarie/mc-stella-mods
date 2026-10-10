package com.stella.mobs.api;

import com.stella.mobs.scaling.SpawnScaling;
import java.util.function.BiConsumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Mob;

/**
 * Publishes the level fix under {@code mod-stella-mobs:apply-level} as a {@code BiConsumer<Mob, Integer>}. A mod that
 * owns a mob (it carries the no-roll tag) gives it a level, and the mob gets the health and damage scaling of a natural
 * spawn at that level, with no elite, boss or variation roll. A bad level, a second call, or a mob the natural roll
 * still owns changes nothing.
 */
public final class ApplyLevelProvider {
	public static final String OBJECT_SHARE_KEY = "mod-stella-mobs:apply-level";

	private ApplyLevelProvider() {
	}

	public static void register() {
		BiConsumer<Mob, Integer> provider = ApplyLevelProvider::apply;
		FabricLoader.getInstance().getObjectShare().put(OBJECT_SHARE_KEY, provider);
	}

	public static void apply(final Mob mob, final Integer level) {
		if (mob != null && level != null) {
			SpawnScaling.applyFixedLevel(mob, level);
		}
	}
}
