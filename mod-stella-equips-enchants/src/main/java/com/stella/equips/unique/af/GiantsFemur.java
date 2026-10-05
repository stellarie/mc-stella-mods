package com.stella.equips.unique.af;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** The Giant's Femur. Every number lives in {@link #TUNING}. */
public final class GiantsFemur {
	public static final String ID = "giants_femur";
	private static final String PASSIVE = "giants_femur";

	public record Tuning(double damage, double damagePlus7, double damagePlus12, double slowSpeed, double knockback, int slowTicks, int slowAmplifier) {
	}

	public static final Tuning TUNING = new Tuning(0.40, 0.50, 0.60, -0.20, 2.0, 40, 2);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float amount) {
			return (float) (amount * (1.0 + Af.tier(c.refine(), TUNING.damage(), TUNING.damagePlus7(), TUNING.damagePlus12())));
		}

		@Override
		public void afterMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float dealt, final boolean crit, final boolean killed) {
			if (c.atLeast(Uniques.PLUS_10)) {
				Af.scaleKnockback(target, TUNING.knockback());
			}
			if (c.atLeast(Uniques.PLUS_15)) {
				Af.curse(target, MobEffects.SLOWNESS, TUNING.slowTicks(), TUNING.slowAmplifier());
			}
		}

		@Override
		public void everySecond(final UniqueContext c) {
			AfPassives.hold(c.holder(), PASSIVE, Attributes.ATTACK_SPEED, TUNING.slowSpeed());
		}
	};

	private GiantsFemur() {
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Giant's Femur", UniqueJob.FIGHTER, UniqueKind.MACE, List.of(Identifier.parse("minecraft:giant")),
			"+" + Af.percent(TUNING.damage()) + " damage, " + Af.percent(TUNING.slowSpeed()) + " attack speed",
			"+" + Af.percent(TUNING.damagePlus7()),
			"Hits knock the target back " + (int) TUNING.knockback() + " times as far",
			"+" + Af.percent(TUNING.damagePlus12()),
			"Hits give Slowness III, " + Af.seconds(TUNING.slowTicks()), null, EFFECT));
	}
}
