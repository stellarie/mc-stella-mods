package com.stella.equips.unique.mp;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDamage;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.Comparator;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.phys.Vec3;

/** Split Gel Boots pass part of a spell hit to another foe. */
public final class SplitGelBoots {
	public static final String ID = "split_gel_boots";

	public record Tuning(double baseChance, double plus7Chance, double plus12Chance, double splitShare, double radius) {
	}

	public static final Tuning TUNING = new Tuning(0.20, 0.30, 0.40, 0.50, 6.0);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public void afterSpell(final UniqueContext c, final LivingEntity target, final DamageSource source, final float dealt, final boolean killed) {
			if (!MpRolls.chance(ID, c.holder(), chanceAt(c.refine()))) {
				return;
			}
			LivingEntity first = nearest(c, target.position(), target, null);
			float firstAmount = (float) (dealt * TUNING.splitShare());
			if (first == null || !UniqueDamage.deal(c.holder().level(), first, c.holder(), firstAmount, false)) {
				return;
			}
			if (c.atLeast(Uniques.PLUS_15)) {
				LivingEntity second = nearest(c, first.position(), first, target);
				if (second != null) {
					UniqueDamage.deal(c.holder().level(), second, c.holder(), (float) (firstAmount * TUNING.splitShare()), false);
				}
			}
		}

		@Override
		public float incoming(final UniqueContext c, final DamageSource source, final float amount) {
			return c.atLeast(Uniques.PLUS_10) && source.is(DamageTypeTags.IS_FALL) ? 0.0F : amount;
		}
	};

	private SplitGelBoots() {
	}

	private static LivingEntity nearest(final UniqueContext c, final Vec3 center, final LivingEntity skip, final LivingEntity otherSkip) {
		return MpKit.foesAround(c.holder().level(), c.holder(), center, TUNING.radius(), skip).stream()
			.filter(candidate -> candidate != otherSkip)
			.sorted(Comparator.comparingDouble((LivingEntity candidate) -> candidate.getBoundingBox().distanceToSqr(center))
				.thenComparing(candidate -> candidate.getUUID().toString()))
			.findFirst().orElse(null);
	}

	public static double chanceAt(final int refine) {
		if (refine >= Uniques.PLUS_12) {
			return TUNING.plus12Chance();
		}
		return refine >= Uniques.PLUS_7 ? TUNING.plus7Chance() : TUNING.baseChance();
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Split Gel Boots", UniqueJob.MAGE, UniqueKind.BOOTS, List.of(Identifier.parse("minecraft:slime")),
			MpKit.percent(TUNING.baseChance()) + " chance a spell hit splits: " + MpKit.percent(TUNING.splitShare()) + " of the hit to one other foe within " + (int) TUNING.radius(),
			MpKit.percent(TUNING.plus7Chance()),
			"No fall damage while worn",
			MpKit.percent(TUNING.plus12Chance()),
			"A split can split once more",
			EquipmentAssets.LEATHER, EFFECT));
	}
}
