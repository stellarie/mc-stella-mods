package com.stella.equips.unique.af;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** The Abyssal Spear. Every number lives in {@link #TUNING}. */
public final class AbyssalSpear {
	public static final String ID = "abyssal_spear";
	/** A held buff is refreshed every second, so it lasts a little longer than this. */
	private static final int HELD_TICKS = 40;

	public record Tuning(double bonus, double bonusPlus7, double bonusPlus12, int slowTicks, int slowAmplifier, double slowRadius) {
	}

	public static final Tuning TUNING = new Tuning(0.30, 0.40, 0.55, 40, 1, 3.0);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float amount) {
			return wet(c.holder()) ? (float) (amount * (1.0 + Af.tier(c.refine(), TUNING.bonus(), TUNING.bonusPlus7(), TUNING.bonusPlus12()))) : amount;
		}

		@Override
		public void afterMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float dealt, final boolean crit, final boolean killed) {
			if (!c.atLeast(Uniques.PLUS_15) || !wet(c.holder()) || !(c.holder().level() instanceof ServerLevel level)) {
				return;
			}
			for (LivingEntity foe : Af.foesAround(level, c.holder(), target.position(), TUNING.slowRadius(), target)) {
				Af.curse(foe, MobEffects.SLOWNESS, TUNING.slowTicks(), TUNING.slowAmplifier());
			}
		}

		@Override
		public void everySecond(final UniqueContext c) {
			if (c.atLeast(Uniques.PLUS_10)) {
				Af.bless(c.holder(), MobEffects.DOLPHINS_GRACE, HELD_TICKS, 0);
				Af.bless(c.holder(), MobEffects.WATER_BREATHING, HELD_TICKS, 0);
			}
		}
	};

	private AbyssalSpear() {
	}

	/** In water or in rain, the two places the spear drinks from. */
	public static boolean wet(final LivingEntity holder) {
		return holder.isInWaterOrRain();
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Abyssal Spear", UniqueJob.FIGHTER, UniqueKind.SPEAR, List.of(Identifier.parse("minecraft:zombie_nautilus")),
			"+" + Af.percent(TUNING.bonus()) + " damage while you are in water or rain",
			"+" + Af.percent(TUNING.bonusPlus7()),
			"While held: Dolphin's Grace and Water Breathing",
			"+" + Af.percent(TUNING.bonusPlus12()),
			"Hits in water give Slowness II, " + Af.seconds(TUNING.slowTicks()) + ", to foes within " + (int) TUNING.slowRadius(), null, EFFECT));
	}
}
