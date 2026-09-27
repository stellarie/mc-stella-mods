package com.stella.mobs.behavior;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import org.jspecify.annotations.Nullable;

/**
 * Levitation from a weapon, an enchantment or a potion lasts at most two seconds, so no hit can float a
 * player to a lethal height. A shulker's bullet keeps vanilla's full ten seconds.
 */
public final class Levitation {
	public static final int MAX_TICKS = 40;

	private Levitation() {
	}

	/** The effect as it should land: capped, unless a shulker sent it. */
	public static MobEffectInstance cap(final MobEffectInstance instance, final @Nullable Entity source) {
		if (!instance.is(MobEffects.LEVITATION) || instance.getDuration() <= MAX_TICKS || instance.isInfiniteDuration()
			|| source instanceof Shulker || source instanceof ShulkerBullet) {
			return instance;
		}
		return new MobEffectInstance(instance.getEffect(), MAX_TICKS, instance.getAmplifier(), instance.isAmbient(), instance.isVisible(), instance.showIcon());
	}
}
