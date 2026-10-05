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

/** The Nether Star Blade. Every number lives in {@link #TUNING}. */
public final class NetherStarBlade {
	public static final String ID = "nether_star_blade";

	public record Tuning(double chance, double chancePlus7, double chancePlus12, int burstTicks, int burstAmplifier,
		double burstShare, double burstRadius, double healShare, double burstRadiusPlus15, int burstAmplifierPlus15) {
	}

	public static final Tuning TUNING = new Tuning(0.10, 0.15, 0.20, 60, 0, 0.60, 3.0, 0.03, 5.0, 1);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public void afterMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float dealt, final boolean crit, final boolean killed) {
			if (dealt <= 0.0F) {
				return;
			}
			if (c.atLeast(Uniques.PLUS_10)) {
				c.holder().heal((float) (dealt * TUNING.healShare()));
			}
			double chance = Af.tier(c.refine(), TUNING.chance(), TUNING.chancePlus7(), TUNING.chancePlus12());
			if (!AfChance.roll(c.holder(), ID, chance) || !(c.holder().level() instanceof ServerLevel level)) {
				return;
			}
			double radius = c.atLeast(Uniques.PLUS_15) ? TUNING.burstRadiusPlus15() : TUNING.burstRadius();
			int amplifier = c.atLeast(Uniques.PLUS_15) ? TUNING.burstAmplifierPlus15() : TUNING.burstAmplifier();
			for (LivingEntity foe : Af.foesAround(level, c.holder(), target.position(), radius, target)) {
				UniqueDamage.deal(level, foe, c.holder(), dealt * (float) TUNING.burstShare(), false);
				Af.curse(foe, MobEffects.WITHER, TUNING.burstTicks(), amplifier);
			}
		}
	};

	private NetherStarBlade() {
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Nether Star Blade", UniqueJob.FIGHTER, UniqueKind.SWORD, List.of(Identifier.parse("minecraft:wither")),
			Af.percent(TUNING.chance()) + " chance a hit fires a wither burst: " + Af.percent(TUNING.burstShare()) + " of the hit and Wither I, "
				+ Af.seconds(TUNING.burstTicks()) + ", to foes within " + (int) TUNING.burstRadius(),
			Af.percent(TUNING.chancePlus7()),
			"Hits heal you " + Af.percent(TUNING.healShare()) + " of the hit",
			Af.percent(TUNING.chancePlus12()),
			"The burst has radius " + (int) TUNING.burstRadiusPlus15() + " and gives Wither II", null, EFFECT));
	}
}
