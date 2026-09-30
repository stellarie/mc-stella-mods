package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.gear.GearTier;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;

/**
 * G01 to G04: an iron golem is elite every time rather than by roll, because it is not an Enemy and cannot
 * take the shipped elite path. The health and speed bonuses are guarded permanent modifiers with stable ids,
 * so a reload cannot stack them, and a melee hit applies a slow for ten ticks.
 */
public final class GolemElite {
	public static final Identifier HEALTH_ID = Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "golem_health");
	public static final Identifier SPEED_ID = Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "golem_speed");
	/** The 1.5 on top of the elite doubling, as a multiplier of the base that marking already doubled. */
	public static final double HEALTH_BONUS = 0.5;
	public static final double SPEED_BONUS = 0.25;
	public static final int SLOW_TICKS = 10;
	/** Seven levels of 15 percent is the literal 100 percent the request asks for. */
	public static final int SLOW_AMPLIFIER = 6;

	private GolemElite() {
	}

	/** Idempotent: the mark returns early on an elite, so an iron golem can never be marked twice. */
	public static void force(final Mob mob) {
		if (!(mob instanceof IronGolem golem) || Elite.isElite(golem)) {
			return;
		}

		Elite.mark(golem, GearTier.DIAMOND, Elite.HEALTH_MULTIPLIER);
		guard(golem, Attributes.MAX_HEALTH, HEALTH_ID, HEALTH_BONUS);
		guard(golem, Attributes.MOVEMENT_SPEED, SPEED_ID, SPEED_BONUS);
	}

	public static void afterDamage(
		final LivingEntity entity,
		final DamageSource source,
		final float baseDamageTaken,
		final float damageTaken,
		final boolean blocked
	) {
		if (blocked || !(source.getDirectEntity() instanceof IronGolem golem) || !Elite.isElite(golem)) {
			return;
		}

		entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, SLOW_AMPLIFIER), golem);
	}

	private static void guard(
		final Mob mob, final Holder<Attribute> attribute, final Identifier id, final double amount
	) {
		AttributeInstance instance = mob.getAttribute(attribute);

		if (instance != null && !instance.hasModifier(id)) {
			instance.addPermanentModifier(
				new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE)
			);
		}
	}
}
