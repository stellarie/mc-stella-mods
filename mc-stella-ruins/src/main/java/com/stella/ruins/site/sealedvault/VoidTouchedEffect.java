package com.stella.ruins.site.sealedvault;

import com.stella.ruins.reward.EffectHook;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Void-touched affix. Every hit of the holder adds 15 percent of the damage it dealt as magic damage, which
 * armor does not reduce, and heals the holder by 5 percent of the damage it dealt.
 */
public final class VoidTouchedEffect implements EffectHook {
	public static final String ID = "void_touched";
	public static final float BONUS_SHARE = 0.15F;
	public static final float STEAL_SHARE = 0.05F;

	@Override
	public void onAttack(final LivingEntity user, final LivingEntity target, final DamageSource source, final float dealt) {
		if (dealt <= 0.0F || !(user.level() instanceof ServerLevel level) || !target.isAlive()) {
			return;
		}
		target.damageCooldownTime = 0;
		target.hurtServer(level, level.damageSources().indirectMagic(user, user), dealt * BONUS_SHARE);
		user.heal(dealt * STEAL_SHARE);
	}
}
