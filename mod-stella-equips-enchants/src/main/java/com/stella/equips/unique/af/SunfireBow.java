package com.stella.equips.unique.af;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDamage;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueDrops;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

/** The Sunfire Bow: arrows set the target alight and hit burning targets harder. Kills on them spread the fire. */
public final class SunfireBow {
	public static final String ID = "sunfire_bow";

	/** The tuning. The bonus on a burning target is {@code bonus}, {@code bonusPlus7} from +7 and {@code bonusPlus12} from +12. */
	public record Tuning(int seconds, double bonus, double bonusPlus7, double bonusPlus12, double killShare, double killRadius, int killSeconds) {
	}

	public static final Tuning TUNING = new Tuning(5, 0.10, 0.20, 0.30, 0.60, 3.0, 5);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingArrow(final UniqueContext c, final LivingEntity target, final AbstractArrow arrow, final float amount) {
			return burning(c, target) ? amount * (1.0F + (float) bonus(c.refine())) : amount;
		}

		@Override
		public void afterArrow(final UniqueContext c, final LivingEntity target, final AbstractArrow arrow, final float dealt, final boolean killed) {
			if (!killed) {
				target.igniteForSeconds(TUNING.seconds());
				return;
			}
			if (c.atLeast(Uniques.PLUS_10) && burning(c, target)) {
				for (LivingEntity foe : Af.foesAround(c.holder().level(), c.holder(), target.position(), TUNING.killRadius(), target)) {
					UniqueDamage.deal(c.holder().level(), foe, c.holder(), dealt * (float) TUNING.killShare(), false);
					foe.igniteForSeconds(TUNING.killSeconds());
				}
			}
		}
	};

	private SunfireBow() {
	}

	public static double bonus(final int refine) {
		return Af.tier(refine, TUNING.bonus(), TUNING.bonusPlus7(), TUNING.bonusPlus12());
	}

	/** From +15 a fire-immune target counts as burning. */
	public static boolean burning(final UniqueContext c, final LivingEntity target) {
		return target.isOnFire() || c.atLeast(Uniques.PLUS_15) && target.fireImmune();
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Sunfire Bow", UniqueJob.ARCHER, UniqueKind.BOW, List.of(UniqueDrops.SOVEREIGN_SOURCE),
			"Arrows set the target alight, " + TUNING.seconds() + " s, and deal +" + Af.percent(TUNING.bonus()) + " to burning targets",
			"+" + Af.percent(TUNING.bonusPlus7()),
			"A kill on a burning target deals " + Af.percent(TUNING.killShare()) + " of the hit to foes within " + (int) TUNING.killRadius()
				+ " and sets them alight",
			"+" + Af.percent(TUNING.bonusPlus12()),
			"A fire-immune target counts as burning",
			null, EFFECT));
	}
}
