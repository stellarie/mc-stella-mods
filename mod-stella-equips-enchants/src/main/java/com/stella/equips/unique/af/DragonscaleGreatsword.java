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
import net.minecraft.world.entity.player.Player;

/** The Dragonscale Greatsword. Every number lives in {@link #TUNING}. */
public final class DragonscaleGreatsword {
	public static final String ID = "dragonscale_greatsword";
	/** The charge a vanilla sweep needs, as the vanilla sweep reads it. */
	private static final float CHARGED = 0.9F;

	public record Tuning(double sweep, double sweepPlus7, double sweepPlus12, int resistTicks, double burstShare, double burstRadius) {
	}

	public static final Tuning TUNING = new Tuning(0.30, 0.40, 0.55, 40, 0.50, 4.0);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float amount) {
			return sweep(c.holder())
				? (float) (amount * (1.0 + Af.tier(c.refine(), TUNING.sweep(), TUNING.sweepPlus7(), TUNING.sweepPlus12()))) : amount;
		}

		@Override
		public void afterMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float dealt, final boolean crit, final boolean killed) {
			if (!charged(c.holder()) || !(c.holder().level() instanceof ServerLevel level)) {
				return;
			}
			if (!crit && c.atLeast(Uniques.PLUS_10)) {
				Af.bless(c.holder(), MobEffects.RESISTANCE, TUNING.resistTicks(), 0);
			}
			if (c.atLeast(Uniques.PLUS_15) && dealt > 0.0F) {
				for (LivingEntity foe : Af.foesAround(level, c.holder(), target.position(), TUNING.burstRadius(), target)) {
					UniqueDamage.deal(level, foe, c.holder(), dealt * (float) TUNING.burstShare(), false);
				}
			}
		}
	};

	private DragonscaleGreatsword() {
	}

	/** A charged blow that is not a sprint hit, the shape a vanilla sweep takes. */
	public static boolean sweep(final LivingEntity holder) {
		return charged(holder) && !holder.isSprinting();
	}

	/** A blow with the attack meter full. */
	public static boolean charged(final LivingEntity holder) {
		return holder instanceof Player player && player.getAttackStrengthScale(0.5F) > CHARGED;
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Dragonscale Greatsword", UniqueJob.FIGHTER, UniqueKind.SWORD, List.of(Identifier.parse("minecraft:ender_dragon")),
			"Sweep attacks deal +" + Af.percent(TUNING.sweep()),
			"+" + Af.percent(TUNING.sweepPlus7()),
			"Each sweep gives you Resistance I, " + Af.seconds(TUNING.resistTicks()),
			"+" + Af.percent(TUNING.sweepPlus12()),
			"A fully charged hit deals " + Af.percent(TUNING.burstShare()) + " of the hit to foes within " + (int) TUNING.burstRadius(), null, EFFECT));
	}
}
