package com.stella.equips.unique;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/** Effects that uniques put on foes. */
public final class UniqueEffects {
	private UniqueEffects() {
	}

	/**
	 * Applies a harmful effect, or extends the one a target has, and never lowers a stronger one. An effect of a
	 * lower amplifier than the target already has is dropped. An effect of the same amplifier keeps the longer
	 * duration. A higher amplifier replaces the old effect. Returns whether the target took the effect.
	 */
	public static boolean addHarmful(final LivingEntity target, final Holder<MobEffect> effect, final int ticks, final int amplifier) {
		MobEffectInstance current = target.getEffect(effect);
		if (current != null && current.getAmplifier() > amplifier) {
			return false;
		}
		int duration = current != null && current.getAmplifier() == amplifier ? Math.max(ticks, current.getDuration()) : ticks;
		return target.addEffect(new MobEffectInstance(effect, duration, amplifier));
	}
}
