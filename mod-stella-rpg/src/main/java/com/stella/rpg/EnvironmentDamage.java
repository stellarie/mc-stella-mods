package com.stella.rpg;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Environmental damage to a player grows with the player's max health, so a fall stays a share of it. */
public final class EnvironmentDamage {
	/** Vanilla player max health. One vanilla point of damage is 1/20 of it. */
	public static final double VANILLA_PLAYER_HEALTH = 20.0;

	private EnvironmentDamage() {
	}

	public static double factor(final LivingEntity victim) {
		return Math.max(1.0, victim.getMaxHealth() / RpgScale.k() / VANILLA_PLAYER_HEALTH);
	}

	public static float scale(final LivingEntity victim, final DamageSource source, final float amount) {
		if (!(victim instanceof ServerPlayer) || !source.is(RpgDamageTypes.ENVIRONMENTAL)) {
			return amount;
		}
		return (float) (amount * factor(victim));
	}
}
