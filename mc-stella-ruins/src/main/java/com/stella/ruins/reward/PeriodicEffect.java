package com.stella.ruins.reward;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/** A potion effect that a set bonus renews every 20 ticks. It shows no particles. */
public record PeriodicEffect(Holder<MobEffect> effect, int amplifier, int durationTicks) {
	public PeriodicEffect {
		if (amplifier < 0 || durationTicks < 20) {
			throw new IllegalArgumentException("A periodic effect needs amplifier >= 0 and duration >= 20 ticks");
		}
	}

	MobEffectInstance instance() {
		return new MobEffectInstance(effect, durationTicks, amplifier, true, false, true);
	}
}
