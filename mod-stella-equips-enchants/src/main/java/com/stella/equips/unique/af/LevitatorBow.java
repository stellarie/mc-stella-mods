package com.stella.equips.unique.af;

import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Prediction;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The Shulker's bow. */
public final class LevitatorBow {
	public static final String ID = "levitator_bow";

	public record Tuning(double chance, double chancePlus12, int ticks, int ticksPlus7, double bonus) {
	}

	public static final Tuning TUNING = new Tuning(0.15, 0.25, 20, 40, 0.25);
	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingArrow(final UniqueContext c, final LivingEntity target, final AbstractArrow arrow, final float amount) {
			return c.atLeast(Uniques.PLUS_10) && target.hasEffect(MobEffects.LEVITATION)
				? amount * (1.0F + (float) TUNING.bonus()) : amount;
		}

		@Override
		public void afterArrow(final UniqueContext c, final LivingEntity target, final AbstractArrow arrow, final float dealt, final boolean killed) {
			boolean floating = target.hasEffect(MobEffects.LEVITATION);
			if (!killed && AfChance.roll(c.holder(), ID, chance(c.refine()))) {
				Af.curse(target, MobEffects.LEVITATION, ticks(c.refine()), 0);
			}
			if (c.atLeast(Uniques.PLUS_15) && floating && arrow != null && arrow.isCritArrow()) {
				ItemStack gift = new ItemStack(Items.ARROW);
				if (!c.holder().getInventory().add(gift)) {
					c.holder().drop(gift, false, Prediction.SERVER_ONLY);
				}
			}
		}
	};

	private LevitatorBow() {
	}

	public static double chance(final int refine) {
		return refine >= Uniques.PLUS_12 ? TUNING.chancePlus12() : TUNING.chance();
	}

	public static int ticks(final int refine) {
		return refine >= Uniques.PLUS_7 ? TUNING.ticksPlus7() : TUNING.ticks();
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Levitator Bow", UniqueJob.ARCHER, UniqueKind.BOW,
			List.of(Identifier.parse("minecraft:shulker")),
			Af.percent(TUNING.chance()) + " chance an arrow gives Levitation, " + Af.seconds(TUNING.ticks()),
			Af.seconds(TUNING.ticksPlus7()),
			"Levitating targets take +" + Af.percent(TUNING.bonus()) + " arrow damage",
			Af.percent(TUNING.chancePlus12()),
			"A critical hit on a levitating target gives you 1 arrow", null, EFFECT));
	}
}
