package com.stella.ruins.site.deepforge;

import com.stella.ruins.reward.EffectHook;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** The Cinderbrand affix: a hit sets its target afire. */
public final class CinderbrandEffect implements EffectHook {
	public static final String ID = "cinderbrand";
	public static final int FIRE_TICKS = 100;

	@Override
	public void onAttack(final LivingEntity user, final LivingEntity target, final DamageSource source, final float dealt) {
		target.igniteForTicks(FIRE_TICKS);
	}
}
