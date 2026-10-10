package com.stella.mobs.behavior;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** The cold of this mod: powder-snow freezing for a body that can freeze, and Slowness for every body. */
public final class Chill {
	private Chill() {
	}

	public static void apply(final LivingEntity target, final Entity source, final int freezeTicks, final int slowTicks, final int slowAmplifier) {
		if (target.canFreeze()) {
			// The vanilla fullness reading: the freeze lasts freezeTicks past the point of being frozen.
			target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + freezeTicks));
		}
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, slowTicks, slowAmplifier), source);
	}
}
