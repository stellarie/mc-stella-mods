package com.stella.rpg;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/** VIT and half of STR shorten harmful effects, up to the curve's maximum of 75%. */
public final class DebuffResistance {
	private DebuffResistance() {
	}

	public static MobEffectInstance apply(final LivingEntity entity, final MobEffectInstance effect) {
		if (effect == null || effect.getEffect().value().getCategory() != MobEffectCategory.HARMFUL
			|| effect.isInfiniteDuration() || effect.getEffect().value().isInstantaneous()) {
			return effect;
		}
		int shortened = duration(effect.getDuration(), score(entity));
		if (shortened == effect.getDuration()) {
			return effect;
		}
		return new MobEffectInstance(effect.getEffect(), shortened, effect.getAmplifier(), effect.isAmbient(),
			effect.isVisible(), effect.showIcon());
	}

	public static double score(final LivingEntity entity) {
		return StatBonuses.effective(entity, Stat.VIT) + 0.5 * StatBonuses.effective(entity, Stat.STR);
	}

	public static int duration(final int ticks, final double score) {
		RpgConfig.Curve curve = RpgConfigStore.current().curve(RpgConfig.Effect.DEBUFF_RESISTANCE);
		double reduction = score <= 0.0 ? 0.0 : curve.max() * score / (score + curve.half());
		return (int) Math.max(1L, Math.round(ticks * (1.0 - reduction)));
	}
}
