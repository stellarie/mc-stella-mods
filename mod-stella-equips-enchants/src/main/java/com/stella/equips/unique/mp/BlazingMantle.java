package com.stella.equips.unique.mp;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** The Blazing Mantle. Spell hits set the target alight. Every number lives in {@link #TUNING}. */
public final class BlazingMantle {
	public static final String ID = "blazing_mantle";

	/** The tuning. Fire lengths are in ticks. */
	public record Tuning(int baseTicks, int plus7Ticks, int plus12Ticks, double plus10Bonus) {
	}

	public static final Tuning TUNING = new Tuning(60, 100, 160, 0.20);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingSpell(final UniqueContext c, final LivingEntity target, final DamageSource s, final float amount) {
			return c.atLeast(Uniques.PLUS_10) && target.isOnFire() ? (float) (amount * (1.0 + TUNING.plus10Bonus())) : amount;
		}

		@Override
		public void afterSpell(final UniqueContext c, final LivingEntity target, final DamageSource s, final float dealt, final boolean killed) {
			if (!killed) {
				MpKit.ignite(target, fireAt(c.refine()));
			}
		}

		@Override
		public float incoming(final UniqueContext c, final DamageSource s, final float amount) {
			return c.atLeast(Uniques.PLUS_15) && s.is(DamageTypeTags.IS_FIRE) ? 0.0F : amount;
		}

		@Override
		public void everySecond(final UniqueContext c) {
			if (c.atLeast(Uniques.PLUS_15)) {
				MpKit.fireProof(c.holder());
			}
		}
	};

	private BlazingMantle() {
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Blazing Mantle", UniqueJob.MAGE, UniqueKind.CHESTPLATE, List.of(Identifier.parse("minecraft:blaze")),
			"Spell hits set the target alight, " + MpKit.seconds(TUNING.baseTicks()),
			MpKit.seconds(TUNING.plus7Ticks()),
			"Spell hits on burning targets deal +" + MpKit.percent(TUNING.plus10Bonus()),
			MpKit.seconds(TUNING.plus12Ticks()),
			"Fire immunity while worn",
			EquipmentAssets.GOLD, EFFECT));
	}

	public static int fireAt(final int refine) {
		if (refine >= Uniques.PLUS_12) {
			return TUNING.plus12Ticks();
		}
		return refine >= Uniques.PLUS_7 ? TUNING.plus7Ticks() : TUNING.baseTicks();
	}
}
