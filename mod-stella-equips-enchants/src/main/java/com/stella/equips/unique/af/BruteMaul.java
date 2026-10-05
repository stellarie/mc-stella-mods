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
import com.stella.equips.unique.UniquePassives;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** The Brute Maul. Every number lives in {@link #TUNING}. */
public final class BruteMaul {
	public static final String ID = "brute_maul";
	private static final String PASSIVE = "brute_maul";
	/** The fall distance a mace smash needs, as the vanilla mace reads it. */
	private static final float SMASH_FALL = 1.5F;

	public record Tuning(double damage, double damagePlus7, double damagePlus12, int slowTicks, int slowAmplifier, double smashShare, double smashRadius) {
	}

	public static final Tuning TUNING = new Tuning(0.0, 0.15, 0.25, 40, 1, 0.50, 3.0);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float amount) {
			double bonus = Af.tier(c.refine(), TUNING.damage(), TUNING.damagePlus7(), TUNING.damagePlus12());
			return (float) (amount * (1.0 + bonus));
		}

		@Override
		public void afterMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float dealt, final boolean crit, final boolean killed) {
			if (!smash(c.holder()) || !(c.holder().level() instanceof ServerLevel level)) {
				return;
			}
			for (LivingEntity foe : Af.foesAround(level, c.holder(), target.position(), TUNING.smashRadius(), target)) {
				if (c.atLeast(Uniques.PLUS_10)) {
					Af.curse(foe, MobEffects.SLOWNESS, TUNING.slowTicks(), TUNING.slowAmplifier());
				}
				if (c.atLeast(Uniques.PLUS_15) && dealt > 0.0F) {
					UniqueDamage.deal(level, foe, c.holder(), dealt * (float) TUNING.smashShare(), false);
				}
			}
		}

		@Override
		public void everySecond(final UniqueContext c) {
			UniquePassives.hold(c.holder(), PASSIVE, Attributes.KNOCKBACK_RESISTANCE, 1.0);
		}
	};

	private BruteMaul() {
	}

	/** A smash attack: a mace blow that landed while the holder fell. */
	public static boolean smash(final LivingEntity holder) {
		return holder.fallDistance > SMASH_FALL && !holder.isFallFlying();
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Brute Maul", UniqueJob.FIGHTER, UniqueKind.MACE, List.of(Identifier.parse("minecraft:piglin_brute")),
			"Full knockback resistance while held",
			"+" + Af.percent(TUNING.damagePlus7()) + " damage",
			"A smash attack gives Slowness II, " + Af.seconds(TUNING.slowTicks()) + ", to foes within " + (int) TUNING.smashRadius(),
			"+" + Af.percent(TUNING.damagePlus12()) + " damage",
			"A smash attack deals " + Af.percent(TUNING.smashShare()) + " of the hit to foes within " + (int) TUNING.smashRadius(), null, EFFECT));
	}
}
