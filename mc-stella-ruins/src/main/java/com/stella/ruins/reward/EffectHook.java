package com.stella.ruins.reward;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

/**
 * The five moments at which a unique effect may act. The user holds or wears the item that carries the effect.
 * Every method has an empty default, so an effect overrides only what it needs.
 */
public interface EffectHook {
	/** The user dealt damage. {@code dealt} is the damage that the target took. */
	default void onAttack(final LivingEntity user, final LivingEntity target, final DamageSource source, final float dealt) {
	}

	/** The user took damage. {@code taken} is the damage after armor, and a blocked hit does not count. */
	default void onHurt(final LivingEntity user, final DamageSource source, final float taken) {
	}

	default void onKill(final LivingEntity user, final LivingEntity victim) {
	}

	/** Runs every 20 ticks, for players only. */
	default void onTick(final LivingEntity user) {
	}

	default void onArrowFired(final LivingEntity user, final AbstractArrow arrow) {
	}
}
