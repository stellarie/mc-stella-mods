package com.stella.equips.unique.mp;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDamage;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.phys.Vec3;

/** Volatile Hood bursts when a spell kills, with one extra generation at +15. */
public final class VolatileHood {
	public static final String ID = "volatile_hood";

	public record Tuning(double baseShare, double plus7Share, double plus12Share, double baseRadius, double plus10Radius) {
	}

	public static final Tuning TUNING = new Tuning(0.40, 0.55, 0.70, 3.0, 4.0);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public void afterSpell(final UniqueContext c, final LivingEntity target, final DamageSource source, final float dealt, final boolean killed) {
			if (killed) {
				burst(c, target.position(), target, (float) (dealt * shareAt(c.refine())), c.atLeast(Uniques.PLUS_15));
			}
		}
	};

	private VolatileHood() {
	}

	private static void burst(final UniqueContext c, final Vec3 center, final LivingEntity skip, final float amount, final boolean chain) {
		for (LivingEntity foe : MpKit.foesAround(c.holder().level(), c.holder(), center, radiusAt(c.refine()), skip)) {
			boolean hurt = UniqueDamage.deal(c.holder().level(), foe, c.holder(), amount, false);
			if (hurt && !foe.isAlive() && chain) {
				burst(c, foe.position(), foe, amount, false);
			}
		}
	}

	public static double shareAt(final int refine) {
		if (refine >= Uniques.PLUS_12) {
			return TUNING.plus12Share();
		}
		return refine >= Uniques.PLUS_7 ? TUNING.plus7Share() : TUNING.baseShare();
	}

	public static double radiusAt(final int refine) {
		return refine >= Uniques.PLUS_10 ? TUNING.plus10Radius() : TUNING.baseRadius();
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Volatile Hood", UniqueJob.MAGE, UniqueKind.HELMET, List.of(Identifier.parse("minecraft:creeper")),
			"A spell kill explodes: " + MpKit.percent(TUNING.baseShare()) + " of the hit to foes within " + (int) TUNING.baseRadius() + ", no block damage",
			MpKit.percent(TUNING.plus7Share()),
			"Radius " + (int) TUNING.plus10Radius(),
			MpKit.percent(TUNING.plus12Share()),
			"A kill by the explosion explodes once more",
			EquipmentAssets.LEATHER, EFFECT));
	}
}
