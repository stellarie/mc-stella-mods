package com.stella.ruins.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** A trick a themed mob has. Both hooks run on the server thread and default to nothing. */
public interface MobAbility {
	/** Runs every five ticks while the mob is loaded. */
	default void tick(final Mob mob, final ServerLevel level) {
	}

	/** Runs after the mob hurt a living target. */
	default void onHurtTarget(final Mob mob, final LivingEntity target) {
	}
}
