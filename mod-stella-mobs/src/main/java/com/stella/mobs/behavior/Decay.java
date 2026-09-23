package com.stella.mobs.behavior;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;

/**
 * The boss wither skeleton's blade: a melee hit from it applies Wither II for ten seconds, one
 * amplifier above the vanilla wither skeleton's own Wither. A victim that refuses the effect through its
 * own canBeAffected, which is every undead including this boss, simply keeps refusing it.
 */
public final class Decay {
	public static final int BLADE_TICKS = 200;
	public static final int BLADE_AMPLIFIER = 1;

	private Decay() {
	}

	public static void afterDamage(
		final LivingEntity victim,
		final DamageSource source,
		final float dealt,
		final float taken,
		final boolean blocked
	) {
		if (!(source.getDirectEntity() instanceof WitherSkeleton attacker) || !Bosses.isBoss(attacker)) {
			return;
		}

		victim.addEffect(new MobEffectInstance(MobEffects.WITHER, BLADE_TICKS, BLADE_AMPLIFIER), attacker);
	}
}
