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

/** The Galeshot Crossbow. */
public final class GaleshotCrossbow {
	public static final String ID = "galeshot_crossbow";
	public record Tuning(double knockback, double knockbackPlus7, double knockbackPlus12, int speedTicks, int speedAmplifier, double liftRadius, int liftTicks) {}
	public static final Tuning TUNING = new Tuning(2.0, 3.0, 4.0, 60, 0, 3.0, 20);
	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public void onShot(final UniqueContext c, final AbstractArrow arrow, final float power) {
			if (c.atLeast(Uniques.PLUS_10)) {
				Af.bless(c.holder(), MobEffects.SPEED, TUNING.speedTicks(), TUNING.speedAmplifier());
			}
		}

		@Override
		public void afterArrow(final UniqueContext c, final LivingEntity target, final AbstractArrow arrow, final float dealt, final boolean killed) {
			if (c.atLeast(Uniques.PLUS_15)) {
				for (LivingEntity foe : Af.foesAround(c.holder().level(), c.holder(), target.position(), TUNING.liftRadius(), target)) {
					Af.curse(foe, MobEffects.LEVITATION, TUNING.liftTicks(), 0);
				}
			}
		}
	};

	public static double knockback(final int refine) {
		return Af.tier(refine, TUNING.knockback(), TUNING.knockbackPlus7(), TUNING.knockbackPlus12());
	}
	private GaleshotCrossbow() {}
	public static void register() {
		Uniques.register(new UniqueDef(ID, "Galeshot Crossbow", UniqueJob.ARCHER, UniqueKind.CROSSBOW,
			List.of(Identifier.parse("minecraft:breeze")),
			"Bolt hits knock the target back 2 times as far", "3 times", "Each shot gives you Speed I, 3 s", "4 times", "Bolt hits give Levitation, 1 s, to foes within 3 of the target", null, EFFECT));
	}
}
