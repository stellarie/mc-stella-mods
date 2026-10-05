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

/** The Tar Shroud. Spell hits slow the target, and slowed targets take more spell damage. Every number lives in {@link #TUNING}. */
public final class TarShroud {
	public static final String ID = "tar_shroud";

	/** The tuning. Amplifier 1 is Slowness II. Times are in ticks. */
	public record Tuning(int baseTicks, int plus7Ticks, int baseAmplifier, int plus12Amplifier, double plus10Bonus, int spreadTicks, int spreadAmplifier,
		double spreadRadius) {
	}

	public static final Tuning TUNING = new Tuning(40, 60, 1, 2, 0.15, 60, 1, 4.0);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingSpell(final UniqueContext c, final LivingEntity target, final DamageSource s, final float amount) {
			return c.atLeast(Uniques.PLUS_10) && target.hasEffect(MobEffects.SLOWNESS) ? (float) (amount * (1.0 + TUNING.plus10Bonus())) : amount;
		}

		@Override
		public void afterSpell(final UniqueContext c, final LivingEntity target, final DamageSource s, final float dealt, final boolean killed) {
			if (!killed) {
				UniqueEffects.addHarmful(target, MobEffects.SLOWNESS, ticksAt(c.refine()), amplifierAt(c.refine()));
			} else if (c.atLeast(Uniques.PLUS_15) && target.hasEffect(MobEffects.SLOWNESS)) {
				for (LivingEntity foe : MpKit.foesAround(c.holder().level(), c.holder(), target.position(), TUNING.spreadRadius(), target)) {
					UniqueEffects.addHarmful(foe, MobEffects.SLOWNESS, TUNING.spreadTicks(), TUNING.spreadAmplifier());
				}
			}
		}
	};

	private TarShroud() {
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Tar Shroud", UniqueJob.MAGE, UniqueKind.CHESTPLATE, List.of(Identifier.parse("mod-stella-mobs:black_slime")),
			"Spell hits give Slowness II, " + MpKit.seconds(TUNING.baseTicks()),
			MpKit.seconds(TUNING.plus7Ticks()),
			"Slowed targets take +" + MpKit.percent(TUNING.plus10Bonus()) + " spell damage",
			"Slowness III",
			"A kill on a slowed target gives Slowness II, " + MpKit.seconds(TUNING.spreadTicks()) + ", to foes within " + (int) TUNING.spreadRadius(),
			EquipmentAssets.NETHERITE, EFFECT));
	}

	public static int ticksAt(final int refine) {
		return refine >= Uniques.PLUS_7 ? TUNING.plus7Ticks() : TUNING.baseTicks();
	}

	public static int amplifierAt(final int refine) {
		return refine >= Uniques.PLUS_12 ? TUNING.plus12Amplifier() : TUNING.baseAmplifier();
	}
}
