package com.stella.tower;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;

/** Who the tower treats as hostile: an enemy by kind, unless the mobs mod marks it harmless. */
public final class Hostiles {
	/** The tag {@code mod-stella-mobs} puts on a natural blue slime. */
	public static final String HARMLESS_TAG = "mod-stella-mobs.harmless";

	private Hostiles() {
	}

	public static boolean isHostile(final Entity entity) {
		return entity instanceof Enemy && !entity.entityTags().contains(HARMLESS_TAG);
	}
}
