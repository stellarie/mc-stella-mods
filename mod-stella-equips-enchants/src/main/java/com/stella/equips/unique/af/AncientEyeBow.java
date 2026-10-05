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

/** The Ancient Eye Bow: arrow hits tire the target, a tired target takes more, and from +15 the hit also weakens. */
public final class AncientEyeBow {
	public static final String ID = "ancient_eye_bow";

	/** The tuning. Mining Fatigue has level {@code amplifier} and lasts {@code ticks}, {@code ticksPlus7} from +7. */
	public record Tuning(int ticks, int ticksPlus7, int amplifier, double tiredBonus, double tiredBonusPlus12, int weaknessTicks) {
	}

	public static final Tuning TUNING = new Tuning(100, 160, 2, 0.30, 0.45, 100);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingArrow(final UniqueContext c, final LivingEntity target, final AbstractArrow arrow, final float amount) {
			return c.atLeast(Uniques.PLUS_10) && target.hasEffect(MobEffects.MINING_FATIGUE) ? amount * (1.0F + (float) bonus(c.refine())) : amount;
		}

		@Override
		public void afterArrow(final UniqueContext c, final LivingEntity target, final AbstractArrow arrow, final float dealt, final boolean killed) {
			if (killed) {
				return;
			}
			Af.curse(target, MobEffects.MINING_FATIGUE, ticks(c.refine()), TUNING.amplifier());
			if (c.atLeast(Uniques.PLUS_15)) {
				Af.curse(target, MobEffects.WEAKNESS, TUNING.weaknessTicks(), 0);
			}
		}
	};

	private AncientEyeBow() {
	}

	public static int ticks(final int refine) {
		return refine >= Uniques.PLUS_7 ? TUNING.ticksPlus7() : TUNING.ticks();
	}

	public static double bonus(final int refine) {
		return refine >= Uniques.PLUS_12 ? TUNING.tiredBonusPlus12() : TUNING.tiredBonus();
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Ancient Eye Bow", UniqueJob.ARCHER, UniqueKind.BOW, List.of(Identifier.parse("minecraft:elder_guardian")),
			"Arrow hits give Mining Fatigue " + Af.roman(TUNING.amplifier() + 1) + ", " + Af.seconds(TUNING.ticks()),
			Af.seconds(TUNING.ticksPlus7()),
			"Targets with Mining Fatigue take +" + Af.percent(TUNING.tiredBonus()) + " arrow damage",
			"+" + Af.percent(TUNING.tiredBonusPlus12()),
			"Arrow hits also give Weakness I, " + Af.seconds(TUNING.weaknessTicks()),
			null, EFFECT));
	}
}
