package com.stella.equips.unique.af;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.effect.MobEffects;

/** The Tidewatcher Crossbow. */
public final class TidewatcherCrossbow {
	public static final String ID = "tidewatcher_crossbow";
	public record Tuning(double bonus, double bonusPlus7, double bonusPlus12, int heldTicks, int curseTicks, int fatigueAmplifier, int slowAmplifier) {}
	public static final Tuning TUNING = new Tuning(0.25, 0.35, 0.50, 40, 60, 1, 0);
	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingArrow(final UniqueContext c, final LivingEntity target, final AbstractArrow arrow, final float amount) {
			return target.isInWaterOrRain() ? amount * (1 + (float) Af.tier(c.refine(), TUNING.bonus(), TUNING.bonusPlus7(), TUNING.bonusPlus12())) : amount;
		}

		@Override
		public void everySecond(final UniqueContext c) {
			if (c.atLeast(Uniques.PLUS_10)) {
				Af.bless(c.holder(), MobEffects.WATER_BREATHING, TUNING.heldTicks(), 0);
				Af.bless(c.holder(), MobEffects.DOLPHINS_GRACE, TUNING.heldTicks(), 0);
			}
		}

		@Override
		public void afterArrow(final UniqueContext c, final LivingEntity target, final AbstractArrow arrow, final float dealt, final boolean killed) {
			if (c.atLeast(Uniques.PLUS_15) && !killed) {
				Af.curse(target, MobEffects.MINING_FATIGUE, TUNING.curseTicks(), TUNING.fatigueAmplifier());
				Af.curse(target, MobEffects.SLOWNESS, TUNING.curseTicks(), TUNING.slowAmplifier());
			}
		}
	};
	private TidewatcherCrossbow() {}
	public static void register() {
		Uniques.register(new UniqueDef(ID, "Tidewatcher Crossbow", UniqueJob.ARCHER, UniqueKind.CROSSBOW,
			List.of(Identifier.parse("minecraft:guardian")),
			"+25% bolt damage against a target in water or rain", "+35%", "While held: Water Breathing and Dolphin's Grace", "+50%", "Bolt hits give Mining Fatigue II and Slowness I, 3 s", null, EFFECT));
	}
}
