package com.stella.equips.unique.mp;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDamage;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueDrops;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.List;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** The Sovereign's Crown burns spell targets and bursts on burning kills. */
public final class SovereignCrown {
	public static final String ID = "sovereign_crown";

	public record Tuning(int fireTicks, double baseBonus, double plus7Bonus, double plus12Bonus,
		double plus15BurstShare, double plus15Radius) {
	}

	public static final Tuning TUNING = new Tuning(100, 0.20, 0.30, 0.45, 0.60, 3.0);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingSpell(final UniqueContext c, final LivingEntity target, final DamageSource source, final float amount) {
			return target.isOnFire() ? (float) (amount * (1.0 + bonusAt(c.refine()))) : amount;
		}

		@Override
		public void afterSpell(final UniqueContext c, final LivingEntity target, final DamageSource source, final float dealt, final boolean killed) {
			if (killed && c.atLeast(Uniques.PLUS_15) && target.isOnFire()) {
				UniqueDamage.area(c.holder().level(), target.position(), TUNING.plus15Radius(), c.holder(),
					(float) (dealt * TUNING.plus15BurstShare()), false, target);
			}
			if (!killed) {
				MpKit.ignite(target, TUNING.fireTicks());
			}
		}

		@Override
		public float incoming(final UniqueContext c, final DamageSource source, final float amount) {
			return c.atLeast(Uniques.PLUS_10) && source.is(DamageTypeTags.IS_FIRE) ? 0.0F : amount;
		}

		@Override
		public void everySecond(final UniqueContext c) {
			if (c.atLeast(Uniques.PLUS_10)) {
				MpKit.fireProof(c.holder());
			}
		}
	};

	private SovereignCrown() {
	}

	public static double bonusAt(final int refine) {
		if (refine >= Uniques.PLUS_12) {
			return TUNING.plus12Bonus();
		}
		return refine >= Uniques.PLUS_7 ? TUNING.plus7Bonus() : TUNING.baseBonus();
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Crown of the Sovereign", UniqueJob.MAGE, UniqueKind.HELMET,
			List.of(UniqueDrops.SOVEREIGN_SOURCE),
			"Spell hits set the target alight, " + MpKit.seconds(TUNING.fireTicks()) + ". +" + MpKit.percent(TUNING.baseBonus()) + " against burning targets",
			"+" + MpKit.percent(TUNING.plus7Bonus()),
			"Fire immunity while worn",
			"+" + MpKit.percent(TUNING.plus12Bonus()),
			"A spell kill on a burning target deals " + MpKit.percent(TUNING.plus15BurstShare()) + " of the hit to foes within " + (int) TUNING.plus15Radius(),
			EquipmentAssets.GOLD, EFFECT));
	}
}
