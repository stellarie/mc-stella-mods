package com.stella.mobs.behavior;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.spider.Spider;

/**
 * The bite of every spider class mob, cave spiders and elites included. The spider heals a share of the damage it
 * deals, and the victim slows down for a few seconds. A blocked hit deals no damage, so it does neither.
 */
public final class SpiderBite {
	public static final float LIFESTEAL = 0.1F;
	public static final float ELITE_LIFESTEAL = 0.2F;
	public static final float BOSS_LIFESTEAL = 0.5F;
	/** Speed II and Speed IV. */
	public static final int ELITE_SPEED_AMPLIFIER = 1;
	public static final int BOSS_SPEED_AMPLIFIER = 3;
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

		spider.heal(damage * lifesteal(spider));
		victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOWNESS_TICKS, SLOWNESS_AMPLIFIER), spider);
	}

	public static float lifesteal(final Spider spider) {
		if (EliteState.isBoss(spider)) {
			return BOSS_LIFESTEAL;
		}
		return EliteState.isElite(spider) ? ELITE_LIFESTEAL : LIFESTEAL;
	}

	/** Gives an elite or boss spider its endless speed. The boss flag is set before the elite mark. */
	public static void applyRank(final Mob mob) {
		if (!(mob instanceof Spider)) {
			return;
		}
		int amplifier = EliteState.isBoss(mob) ? BOSS_SPEED_AMPLIFIER : ELITE_SPEED_AMPLIFIER;
		mob.addEffect(new MobEffectInstance(MobEffects.SPEED, MobEffectInstance.INFINITE_DURATION, amplifier, false, false));
	}
}
