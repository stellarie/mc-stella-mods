package com.stella.equips.unique.af;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** The Dunerider Lance. Every number lives in {@link #TUNING}. */
public final class DuneriderLance {
	public static final String ID = "dunerider_lance";

	public record Tuning(double bonus, double bonusPlus7, double bonusPlus12, double knockback, int speedTicks, int speedAmplifier) {
	}

	public static final Tuning TUNING = new Tuning(0.40, 0.50, 0.65, 2.0, 100, 1);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float amount) {
			return moving(c.holder()) ? (float) (amount * (1.0 + Af.tier(c.refine(), TUNING.bonus(), TUNING.bonusPlus7(), TUNING.bonusPlus12()))) : amount;
		}

		@Override
		public void afterMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float dealt, final boolean crit, final boolean killed) {
			if (c.atLeast(Uniques.PLUS_10) && c.holder().isSprinting()) {
				Af.scaleKnockback(target, TUNING.knockback());
			}
			if (killed && c.atLeast(Uniques.PLUS_15) && moving(c.holder())) {
				Af.bless(c.holder(), MobEffects.SPEED, TUNING.speedTicks(), TUNING.speedAmplifier());
			}
		}
	};

	private DuneriderLance() {
	}

	/** Riding or sprinting, the two ways the lance is braced. */
	public static boolean moving(final ServerPlayer holder) {
		return holder.isPassenger() || holder.isSprinting();
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Dunerider Lance", UniqueJob.FIGHTER, UniqueKind.SPEAR, List.of(Identifier.parse("minecraft:camel_husk")),
			"+" + Af.percent(TUNING.bonus()) + " damage while you ride or sprint",
			"+" + Af.percent(TUNING.bonusPlus7()),
			"A sprint hit knocks the target back " + (int) TUNING.knockback() + " times as far",
			"+" + Af.percent(TUNING.bonusPlus12()),
			"A kill while riding or sprinting gives you Speed II, " + Af.seconds(TUNING.speedTicks()), null, EFFECT));
	}
}
