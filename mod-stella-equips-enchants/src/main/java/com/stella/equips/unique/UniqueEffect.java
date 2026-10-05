package com.stella.equips.unique;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

/**
 * The behavior of one unique. Every hook has a default that changes nothing, so an effect overrides only the
 * hooks it needs. Hooks fire for a {@link net.minecraft.server.level.ServerPlayer} holder only.
 *
 * <p>An "outgoing" hook returns the damage of a hit before armor and blocking. An "after" hook runs once the
 * target has taken the hit, with the damage it took after mitigation.
 */
public interface UniqueEffect {
	/** The effect of a unique with no behavior yet. */
	UniqueEffect NONE = new UniqueEffect() {
	};

	default float outgoingMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float amount) {
		return amount;
	}

	default void afterMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float dealt, final boolean crit, final boolean killed) {
	}

	default void onShot(final UniqueContext c, final AbstractArrow arrow, final float power) {
	}

	default float outgoingArrow(final UniqueContext c, final LivingEntity target, final AbstractArrow arrow, final float amount) {
		return amount;
	}

	default void afterArrow(final UniqueContext c, final LivingEntity target, final AbstractArrow arrow, final float dealt, final boolean killed) {
	}

	default float outgoingSpell(final UniqueContext c, final LivingEntity target, final DamageSource s, final float amount) {
		return amount;
	}

	default void afterSpell(final UniqueContext c, final LivingEntity target, final DamageSource s, final float dealt, final boolean killed) {
	}

	default float incoming(final UniqueContext c, final DamageSource s, final float amount) {
		return amount;
	}

	default void afterIncoming(final UniqueContext c, final DamageSource s, final float taken) {
	}

	default void onKill(final UniqueContext c, final LivingEntity victim, final DamageSource s) {
	}

	default void everySecond(final UniqueContext c) {
	}

	default int harmfulDuration(final UniqueContext c, final MobEffectInstance effect, final int ticks) {
		return ticks;
	}
}
