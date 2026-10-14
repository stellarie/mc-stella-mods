package com.stella.mobs.behavior;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.spider.Spider;

/**
 * The bite of every spider class mob, cave spiders and elites included. The spider heals a share of the damage it
 * deals, and the victim slows down for a few seconds. A blocked hit deals no damage, so it does neither.
 */
public final class SpiderBite {
	public static final float LIFESTEAL = 0.1F;
	public static final int SLOWNESS_TICKS = 100;
	public static final int SLOWNESS_AMPLIFIER = 0;

	private SpiderBite() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(SpiderBite::afterDamage);
	}

	private static void afterDamage(
		final LivingEntity victim, final DamageSource source, final float baseDamage, final float damage, final boolean blocked
	) {
		if (blocked || damage <= 0.0F || !(source.getDirectEntity() instanceof Spider spider) || source.getEntity() != spider || !spider.isAlive()) {
			return;
		}

		spider.heal(damage * LIFESTEAL);
		victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOWNESS_TICKS, SLOWNESS_AMPLIFIER), spider);
	}
}
