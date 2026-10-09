package com.stella.ruins.site.meteorcrater;

import com.stella.ruins.reward.EffectHook;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** The Emberward affix: a wearer who burns is put out and shielded from fire for a short while. */
public final class EmberwardEffect implements EffectHook {
	public static final String ID = "emberward";
	public static final int FIRE_RESISTANCE_TICKS = 100;

	@Override
	public void onTick(final LivingEntity user) {
		if (user.isOnFire()) {
			user.clearFire();
			user.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, FIRE_RESISTANCE_TICKS, 0, true, true));
		}
	}
}
