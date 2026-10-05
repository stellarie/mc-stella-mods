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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

/** The Sunscorch Bow: arrow hits set the target alight, a burning target takes more, and a kill on one scorches foes near it. */
public final class SunscorchBow {
	public static final String ID = "sunscorch_bow";

	/** The tuning. The fire lasts {@code seconds}, {@code secondsPlus7} from +7 and {@code secondsPlus12} from +12. */
	public record Tuning(int seconds, int secondsPlus7, int secondsPlus12, double burningBonus, double killShare, double killRadius) {
	}

	public static final Tuning TUNING = new Tuning(3, 5, 8, 0.15, 0.50, 3.0);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingArrow(final UniqueContext c, final LivingEntity target, final AbstractArrow arrow, final float amount) {
			return c.atLeast(Uniques.PLUS_10) && target.isOnFire() ? amount * (1.0F + (float) TUNING.burningBonus()) : amount;
		}

		@Override
		public void afterArrow(final UniqueContext c, final LivingEntity target, final AbstractArrow arrow, final float dealt, final boolean killed) {
			if (!killed) {
				target.igniteForSeconds(seconds(c.refine()));
				return;
			}
			if (c.atLeast(Uniques.PLUS_15) && target.isOnFire()) {
				UniqueDamage.area(c.holder().level(), target.position(), TUNING.killRadius(), c.holder(), dealt * (float) TUNING.killShare(), false, target);
			}
		}
	};

	private SunscorchBow() {
	}

	public static int seconds(final int refine) {
		return Af.tier(refine, TUNING.seconds(), TUNING.secondsPlus7(), TUNING.secondsPlus12());
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Sunscorch Bow", UniqueJob.ARCHER, UniqueKind.BOW, List.of(Identifier.parse("minecraft:parched")),
			"Arrow hits set the target alight, " + TUNING.seconds() + " s",
			TUNING.secondsPlus7() + " s",
			"Burning targets take +" + Af.percent(TUNING.burningBonus()) + " arrow damage",
			TUNING.secondsPlus12() + " s",
			"A kill on a burning target deals " + Af.percent(TUNING.killShare()) + " of the hit to foes within " + (int) TUNING.killRadius(),
			null, EFFECT));
	}
}
