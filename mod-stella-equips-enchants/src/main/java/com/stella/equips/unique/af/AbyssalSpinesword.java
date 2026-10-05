package com.stella.equips.unique.af;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDamage;
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

/** The Abyssal Spinesword. Every number lives in {@link #TUNING}. */
public final class AbyssalSpinesword {
	public static final String ID = "abyssal_spinesword";

	public record Tuning(double bonus, double bonusPlus7, double bonusPlus12, double spinesShare, int fatigueTicks, int fatigueAmplifier) {
	}

	public static final Tuning TUNING = new Tuning(0.40, 0.50, 0.65, 0.15, 100, 2);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float amount) {
			return AbyssalSpear.wet(c.holder())
				? (float) (amount * (1.0 + Af.tier(c.refine(), TUNING.bonus(), TUNING.bonusPlus7(), TUNING.bonusPlus12()))) : amount;
		}

		@Override
		public void afterMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float dealt, final boolean crit, final boolean killed) {
			if (c.atLeast(Uniques.PLUS_15)) {
				Af.curse(target, MobEffects.MINING_FATIGUE, TUNING.fatigueTicks(), TUNING.fatigueAmplifier());
			}
		}

		@Override
		public void afterIncoming(final UniqueContext c, final DamageSource s, final float taken) {
			LivingEntity attacker = Af.meleeAttacker(s);
			if (!c.atLeast(Uniques.PLUS_10) || attacker == null || attacker == c.holder() || taken <= 0.0F
				|| !(c.holder().level() instanceof ServerLevel level)) {
				return;
			}
			UniqueDamage.deal(level, attacker, c.holder(), taken * (float) TUNING.spinesShare(), false);
		}
	};

	private AbyssalSpinesword() {
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Abyssal Spinesword", UniqueJob.FIGHTER, UniqueKind.SWORD, List.of(Identifier.parse("minecraft:elder_guardian")),
			"+" + Af.percent(TUNING.bonus()) + " damage while you are in water or rain",
			"+" + Af.percent(TUNING.bonusPlus7()),
			"Melee attackers take " + Af.percent(TUNING.spinesShare()) + " of the blow",
			"+" + Af.percent(TUNING.bonusPlus12()),
			"Hits give Mining Fatigue III, " + Af.seconds(TUNING.fatigueTicks()), null, EFFECT));
	}
}
