package com.stella.equips.unique.af;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

/** The Witherbone Longbow: arrow hits wither the target, a withered target takes more, and a kill withers the foes around. */
public final class WitherboneLongbow {
	public static final String ID = "witherbone_longbow";

	/** The tuning. Wither lasts {@code ticks}, {@code ticksPlus7} from +7. Its level is {@code amplifier}, {@code amplifierPlus12} from +12. */
	public record Tuning(int ticks, int ticksPlus7, int amplifier, int amplifierPlus12, double witheredBonus, int killTicks, int killAmplifier, double killRadius) {
	}

	public static final Tuning TUNING = new Tuning(80, 120, 1, 2, 0.20, 80, 1, 4.0);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingArrow(final UniqueContext c, final LivingEntity target, final AbstractArrow arrow, final float amount) {
			return c.atLeast(Uniques.PLUS_10) && target.hasEffect(MobEffects.WITHER) ? amount * (1.0F + (float) TUNING.witheredBonus()) : amount;
		}

		@Override
		public void afterArrow(final UniqueContext c, final LivingEntity target, final AbstractArrow arrow, final float dealt, final boolean killed) {
			if (!killed) {
				Af.curse(target, MobEffects.WITHER, ticks(c.refine()), amplifier(c.refine()));
				return;
			}
			if (c.atLeast(Uniques.PLUS_15) && target.hasEffect(MobEffects.WITHER)) {
				for (LivingEntity foe : Af.foesAround(c.holder().level(), c.holder(), target.position(), TUNING.killRadius(), target)) {
					Af.curse(foe, MobEffects.WITHER, TUNING.killTicks(), TUNING.killAmplifier());
				}
			}
		}
	};

	private WitherboneLongbow() {
	}

	public static int ticks(final int refine) {
		return refine >= Uniques.PLUS_7 ? TUNING.ticksPlus7() : TUNING.ticks();
	}

	public static int amplifier(final int refine) {
		return refine >= Uniques.PLUS_12 ? TUNING.amplifierPlus12() : TUNING.amplifier();
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Witherbone Longbow", UniqueJob.ARCHER, UniqueKind.BOW, List.of(Identifier.parse("minecraft:wither")),
			"Arrow hits give Wither " + Af.roman(TUNING.amplifier() + 1) + ", " + Af.seconds(TUNING.ticks()),
			Af.seconds(TUNING.ticksPlus7()),
			"Withered targets take +" + Af.percent(TUNING.witheredBonus()) + " arrow damage",
			"Wither " + Af.roman(TUNING.amplifierPlus12() + 1),
			"A kill on a withered target gives Wither " + Af.roman(TUNING.killAmplifier() + 1) + ", " + Af.seconds(TUNING.killTicks())
				+ ", to foes within " + (int) TUNING.killRadius(),
			null, EFFECT));
	}
}
