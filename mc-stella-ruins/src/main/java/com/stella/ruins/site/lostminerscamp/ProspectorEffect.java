package com.stella.ruins.site.lostminerscamp;

import com.stella.ruins.reward.EffectHook;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** The Prospector's Pick: Haste I while it is held. */
public final class ProspectorEffect implements EffectHook {
	public static final String ID = "prospector";
	/** Haste I for this long, renewed on every tick the dispatcher runs. */
	public static final int HASTE_TICKS = 60;

	@Override
	public void onTick(final LivingEntity user) {
		user.addEffect(new MobEffectInstance(MobEffects.HASTE, HASTE_TICKS, 0, false, false));
	}
}
