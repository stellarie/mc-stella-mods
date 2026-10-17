package com.stella.end;

import java.util.function.BiConsumer;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/**
 * The optional link to the mobs mod's level scaling, reached only through ObjectShare. With the mobs mod absent
 * every call does nothing, and a castle mob keeps its vanilla strength.
 */
public final class LevelBridge {
	public static final String APPLY_KEY = "mod-stella-mobs:apply-level";
	public static final String READ_KEY = "mod-stella-mobs:threat-level";
	public static final String NO_ROLL_TAG = "mod-stella-mobs.no_spawn_roll";

	private LevelBridge() {
	}

	/** Marks the mob so the mobs mod's spawn-time rolls leave it alone. Call before the mob joins the level. */
	public static void claim(final Mob mob) {
		mob.addTag(NO_ROLL_TAG);
	}

	/** Gives a claimed mob a fixed level. Returns whether the mobs mod answered. */
	@SuppressWarnings("unchecked")
	public static boolean apply(final Mob mob, final int level) {
		if (FabricLoader.getInstance().getObjectShare().get(APPLY_KEY) instanceof BiConsumer<?, ?> share) {
			((BiConsumer<Mob, Integer>) share).accept(mob, level);
			return true;
		}
		return false;
	}

	/** The level the mobs mod reports for an entity, or -1 when it is absent or the entity has none. */
	@SuppressWarnings("unchecked")
	public static int read(final Entity entity) {
		if (FabricLoader.getInstance().getObjectShare().get(READ_KEY) instanceof Function<?, ?> share) {
			Object level = ((Function<Entity, Object>) share).apply(entity);
			return level instanceof Integer value ? value : -1;
		}
		return -1;
	}
}
