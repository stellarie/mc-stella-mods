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

/** The Starfall Crown. Spell hits wither the target and heal the holder. Every number lives in {@link #TUNING}. */
public final class StarfallCrown {
	public static final String ID = "starfall_crown";

	/** The tuning. Amplifier 1 is Wither II. Times are in ticks. */
	public record Tuning(int baseTicks, int plus12Ticks, int baseAmplifier, int plus7Amplifier, double plus10Heal, double plus15Bonus) {
	}

	public static final Tuning TUNING = new Tuning(60, 100, 0, 1, 0.03, 0.30);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingSpell(final UniqueContext c, final LivingEntity target, final DamageSource s, final float amount) {
			return c.atLeast(Uniques.PLUS_15) && target.hasEffect(MobEffects.WITHER) ? (float) (amount * (1.0 + TUNING.plus15Bonus())) : amount;
		}

		@Override
		public void afterSpell(final UniqueContext c, final LivingEntity target, final DamageSource s, final float dealt, final boolean killed) {
			if (c.atLeast(Uniques.PLUS_10)) {
				MpKit.heal(c.holder(), dealt * TUNING.plus10Heal());
			}
			if (!killed) {
				UniqueEffects.addHarmful(target, MobEffects.WITHER, ticksAt(c.refine()), amplifierAt(c.refine()));
			}
		}
	};

	private StarfallCrown() {
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Starfall Crown", UniqueJob.MAGE, UniqueKind.HELMET, List.of(Identifier.parse("minecraft:wither")),
			"Spell hits give Wither I, " + MpKit.seconds(TUNING.baseTicks()),
			"Wither II",
			"Spell hits heal you " + MpKit.percent(TUNING.plus10Heal()) + " of the hit",
			MpKit.seconds(TUNING.plus12Ticks()),
			"Spell hits on withered targets deal +" + MpKit.percent(TUNING.plus15Bonus()),
			EquipmentAssets.NETHERITE, EFFECT));
	}

	public static int ticksAt(final int refine) {
		return refine >= Uniques.PLUS_12 ? TUNING.plus12Ticks() : TUNING.baseTicks();
	}

	public static int amplifierAt(final int refine) {
		return refine >= Uniques.PLUS_7 ? TUNING.plus7Amplifier() : TUNING.baseAmplifier();
	}
}
