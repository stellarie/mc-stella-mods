package com.stella.mobs.behavior;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** Infinite effects are the mob's own kit, such as oozing, so they pass. Bosses ignore harmful effects. Elites ignore three in four of them. */
public final class StatusResist {
	public static final double ELITE_RESIST_CHANCE = 0.75;

	private StatusResist() {
	}

	public static boolean resists(final LivingEntity entity, final MobEffectInstance instance) {
		if (!(entity instanceof Mob mob) || instance.getEffect().value().getCategory() != MobEffectCategory.HARMFUL
			|| instance.isInfiniteDuration()) {
			return false;
		}
		if (EliteState.hasBossFlag(mob)) {
			return true;
		}
		return EliteState.hasEliteFlag(mob) && mob.getRandom().nextDouble() < ELITE_RESIST_CHANCE;
	}
}
