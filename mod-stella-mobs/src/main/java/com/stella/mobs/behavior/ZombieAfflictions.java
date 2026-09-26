package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * An elite zombie's melee hit spreads rot instead of calling for help: Hunger drains the target's food,
 * Wither and Poison eat its health. It replaced the reinforcement stream (M7), so an elite zombie is a
 * dangerous duel rather than a growing crowd.
 */
public final class ZombieAfflictions {
	public static final int HUNGER_TICKS = 200;
	public static final int HUNGER_AMPLIFIER = 0;
	public static final int WITHER_TICKS = 100;
	public static final int WITHER_AMPLIFIER = 0;
	public static final int POISON_TICKS = 100;
	public static final int POISON_AMPLIFIER = 0;

	private ZombieAfflictions() {
	}

	public static void afterDamage(
		final LivingEntity victim, final DamageSource source, final float dealt, final float taken, final boolean blocked
	) {
		if (!(source.getDirectEntity() instanceof Zombie attacker) || !Elite.isElite(attacker) || taken <= 0.0F) {
			return;
		}

		victim.addEffect(new MobEffectInstance(MobEffects.HUNGER, HUNGER_TICKS, HUNGER_AMPLIFIER), attacker);
		victim.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_TICKS, WITHER_AMPLIFIER), attacker);
		victim.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS, POISON_AMPLIFIER), attacker);
		StellaMobs.LOGGER.debug("Elite zombie {} afflicted {}", attacker.getUUID(), victim.getUUID());
	}
}
