package com.stella.mobs.entity;

import com.stella.mobs.StellaMobs;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The black slime: always elite, solitary, fast, and hard hitting. Its hit gives a player Weakness IV,
 * and it keeps Resistance IV on itself.
 */
public final class BlackSlime extends StellaSlime {
	/** Weakness IV and Resistance IV: the amplifier is the level minus one. */
	public static final int WEAKNESS_AMPLIFIER = 3;
	public static final int RESISTANCE_AMPLIFIER = 3;
	public static final int WEAKNESS_DURATION_TICKS = 200;
	public static final int RESISTANCE_DURATION_TICKS = 100;
	/** The resistance is renewed once less than this much of it remains. */
	public static final int RESISTANCE_RENEW_BELOW_TICKS = 40;
	public static final double BASE_HEALTH = 20.0;
	public static final double ATTACK_DAMAGE = 6.0;
	public static final double MOVEMENT_SPEED = 0.6;

	public BlackSlime(final EntityType<? extends Slime> type, final Level level) {
		super(type, level);
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(BlackSlime::afterDamage);
	}

	@Override
	protected void applyStats() {
		setBase(Attributes.MAX_HEALTH, BASE_HEALTH);
		setBase(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE);
		setBase(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED);
	}

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);

		MobEffectInstance current = getEffect(MobEffects.RESISTANCE);
		if (current == null || current.getAmplifier() < RESISTANCE_AMPLIFIER
			|| current.getDuration() < RESISTANCE_RENEW_BELOW_TICKS) {
			addEffect(new MobEffectInstance(MobEffects.RESISTANCE, RESISTANCE_DURATION_TICKS, RESISTANCE_AMPLIFIER, true, false));
		}
	}

	private static void afterDamage(
		final LivingEntity victim, final DamageSource source, final float dealt, final float taken, final boolean blocked
	) {
		if (!(source.getDirectEntity() instanceof BlackSlime slime) || !(victim instanceof Player) || taken <= 0.0F) {
			return;
		}

		victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAKNESS_DURATION_TICKS, WEAKNESS_AMPLIFIER), slime);
		StellaMobs.LOGGER.info("[BEHAVIOR] test=blackSlime WEAKEN slime={} victim={}", slime.getUUID(), victim.getUUID());
	}
}
