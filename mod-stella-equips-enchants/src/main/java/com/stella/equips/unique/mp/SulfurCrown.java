package com.stella.equips.unique.mp;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueEffects;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** The Sulfur Crown. Spell hits poison the foes around the target. Every number lives in {@link #TUNING}. */
public final class SulfurCrown {
	public static final String ID = "sulfur_crown";

	/** The tuning. Poison lengths are in ticks. Amplifier 1 is Poison II. */
	public record Tuning(int baseTicks, int plus12Ticks, int baseAmplifier, int plus7Amplifier, double baseRadius, double plus10Radius, double plus15Bonus) {
	}

	public static final Tuning TUNING = new Tuning(60, 100, 0, 1, 2.0, 3.0, 0.20);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingSpell(final UniqueContext c, final LivingEntity target, final DamageSource s, final float amount) {
			return c.atLeast(Uniques.PLUS_15) && target.hasEffect(MobEffects.POISON) ? (float) (amount * (1.0 + TUNING.plus15Bonus())) : amount;
		}

		@Override
		public void afterSpell(final UniqueContext c, final LivingEntity target, final DamageSource s, final float dealt, final boolean killed) {
			for (LivingEntity foe : MpKit.foesAround(c.holder().level(), c.holder(), target.position(), radiusAt(c.refine()), target)) {
				UniqueEffects.addHarmful(foe, MobEffects.POISON, ticksAt(c.refine()), amplifierAt(c.refine()));
			}
		}
	};

	private SulfurCrown() {
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Sulfur Crown", UniqueJob.MAGE, UniqueKind.HELMET, List.of(Identifier.parse("minecraft:sulfur_cube")),
			"Spell hits give Poison I, " + MpKit.seconds(TUNING.baseTicks()) + ", to foes within " + (int) TUNING.baseRadius() + " of the target",
			"Poison II",
			"Radius " + (int) TUNING.plus10Radius(),
			MpKit.seconds(TUNING.plus12Ticks()),
			"Poisoned targets take +" + MpKit.percent(TUNING.plus15Bonus()) + " spell damage",
			EquipmentAssets.GOLD, EFFECT));
	}

	public static int ticksAt(final int refine) {
		return refine >= Uniques.PLUS_12 ? TUNING.plus12Ticks() : TUNING.baseTicks();
	}

	public static int amplifierAt(final int refine) {
		return refine >= Uniques.PLUS_7 ? TUNING.plus7Amplifier() : TUNING.baseAmplifier();
	}

	public static double radiusAt(final int refine) {
		return refine >= Uniques.PLUS_10 ? TUNING.plus10Radius() : TUNING.baseRadius();
	}
}
