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

/** Magma Greaves splash a spell hit onto nearby foes. */
public final class MagmaGreaves {
	public static final String ID = "magma_greaves";

	public record Tuning(double baseShare, double plus7Share, double plus15Share, double baseRadius,
		double plus12Radius, int plus10FireTicks) {
	}

	public static final Tuning TUNING = new Tuning(0.25, 0.35, 0.50, 2.0, 3.0, 60);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public void afterSpell(final UniqueContext c, final LivingEntity target, final DamageSource source, final float dealt, final boolean killed) {
			for (LivingEntity foe : MpKit.foesAround(c.holder().level(), c.holder(), target.position(), radiusAt(c.refine()), target)) {
				boolean hurt = UniqueDamage.deal(c.holder().level(), foe, c.holder(), (float) (dealt * shareAt(c.refine())), false);
				if (hurt && c.atLeast(Uniques.PLUS_10)) {
					MpKit.ignite(foe, TUNING.plus10FireTicks());
				}
			}
		}
	};

	private MagmaGreaves() {
	}

	public static double shareAt(final int refine) {
		if (refine >= Uniques.PLUS_15) {
			return TUNING.plus15Share();
		}
		return refine >= Uniques.PLUS_7 ? TUNING.plus7Share() : TUNING.baseShare();
	}

	public static double radiusAt(final int refine) {
		return refine >= Uniques.PLUS_12 ? TUNING.plus12Radius() : TUNING.baseRadius();
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Magma Greaves", UniqueJob.MAGE, UniqueKind.LEGGINGS, List.of(Identifier.parse("minecraft:magma_cube")),
			"Spell hits splash " + MpKit.percent(TUNING.baseShare()) + " of the hit to foes within " + (int) TUNING.baseRadius(),
			MpKit.percent(TUNING.plus7Share()),
			"The splash sets foes alight, " + MpKit.seconds(TUNING.plus10FireTicks()),
			"Radius " + (int) TUNING.plus12Radius(),
			MpKit.percent(TUNING.plus15Share()),
			EquipmentAssets.NETHERITE, EFFECT));
	}
}
