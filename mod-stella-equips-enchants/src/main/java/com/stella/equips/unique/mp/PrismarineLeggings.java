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

/** Prismarine Leggings strengthen wet-target spells and grant water buffs. */
public final class PrismarineLeggings {
	public static final String ID = "prismarine_leggings";

	public record Tuning(double baseBonus, double plus7Bonus, double plus12Bonus, int buffTicks,
		int plus15FatigueTicks, int plus15FatigueAmplifier) {
	}

	public static final Tuning TUNING = new Tuning(0.30, 0.40, 0.55, 40, 100, 2);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingSpell(final UniqueContext c, final LivingEntity target, final DamageSource source, final float amount) {
			return target.isInWaterOrRain() ? (float) (amount * (1.0 + bonusAt(c.refine()))) : amount;
		}

		@Override
		public void afterSpell(final UniqueContext c, final LivingEntity target, final DamageSource source, final float dealt, final boolean killed) {
			if (c.atLeast(Uniques.PLUS_15) && target.isAlive()) {
				UniqueEffects.addHarmful(target, MobEffects.MINING_FATIGUE, TUNING.plus15FatigueTicks(), TUNING.plus15FatigueAmplifier());
			}
		}

		@Override
		public void everySecond(final UniqueContext c) {
			if (c.atLeast(Uniques.PLUS_10) && c.holder().isInWaterOrRain()) {
				MpKit.buff(c.holder(), MobEffects.WATER_BREATHING, TUNING.buffTicks(), 0);
				MpKit.buff(c.holder(), MobEffects.NIGHT_VISION, TUNING.buffTicks(), 0);
			}
		}
	};

	private PrismarineLeggings() {
	}

	public static double bonusAt(final int refine) {
		if (refine >= Uniques.PLUS_12) {
			return TUNING.plus12Bonus();
		}
		return refine >= Uniques.PLUS_7 ? TUNING.plus7Bonus() : TUNING.baseBonus();
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Prismarine Leggings", UniqueJob.MAGE, UniqueKind.LEGGINGS,
			List.of(Identifier.parse("minecraft:elder_guardian")),
			"Spell hits on a target in water or rain deal +" + MpKit.percent(TUNING.baseBonus()),
			"+" + MpKit.percent(TUNING.plus7Bonus()),
			"While worn: Water Breathing and Night Vision in water",
			"+" + MpKit.percent(TUNING.plus12Bonus()),
			"Spell hits give Mining Fatigue III, " + MpKit.seconds(TUNING.plus15FatigueTicks()),
			EquipmentAssets.DIAMOND, EFFECT));
	}
}
