package com.stella.equips.unique.af;

import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

/** The Phantom's bow. */
public final class NightwingBow {
	public static final String ID = "nightwing_bow";

	public record Tuning(double bonus, double plus7, double plus12, int slowFallingTicks) {
	}

	public static final Tuning TUNING = new Tuning(0.20, 0.30, 0.40, 60);
	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public void onShot(final UniqueContext c, final AbstractArrow arrow, final float power) {
			if (c.holder().onGround()) {
				return;
			}
			arrow.addTag(Af.AIRBORNE_TAG);
			if (c.atLeast(Uniques.PLUS_10) && Af.fullDraw(power)) {
				Af.bless(c.holder(), MobEffects.SLOW_FALLING, TUNING.slowFallingTicks(), 0);
			}
			if (c.atLeast(Uniques.PLUS_15)) {
				arrow.setCritArrow(true);
			}
		}

		@Override
		public float outgoingArrow(final UniqueContext c, final LivingEntity target, final AbstractArrow arrow, final float amount) {
			return arrow.entityTags().contains(Af.AIRBORNE_TAG) ? amount * (1.0F + (float) bonus(c.refine())) : amount;
		}
	};

	private NightwingBow() {
	}

	public static double bonus(final int refine) {
		return Af.tier(refine, TUNING.bonus(), TUNING.plus7(), TUNING.plus12());
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Nightwing Bow", UniqueJob.ARCHER, UniqueKind.BOW,
			List.of(Identifier.parse("minecraft:phantom")),
			"+" + Af.percent(TUNING.bonus()) + " arrow damage while you are airborne",
			"+" + Af.percent(TUNING.plus7()),
			"A full-draw shot while airborne gives you Slow Falling, " + Af.seconds(TUNING.slowFallingTicks()),
			"+" + Af.percent(TUNING.plus12()),
			"Every airborne shot is a critical arrow", null, EFFECT));
	}
}
