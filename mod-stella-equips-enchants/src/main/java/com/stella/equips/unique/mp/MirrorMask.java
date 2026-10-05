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

/** Mirror Mask ignores some hits and blinds their attackers at +15. */
public final class MirrorMask {
	public static final String ID = "mirror_mask";

	public record Tuning(double baseChance, double plus7Chance, double plus12Chance, int plus10InvisibleTicks, int plus15BlindTicks) {
	}

	public static final Tuning TUNING = new Tuning(0.10, 0.15, 0.20, 40, 60);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float incoming(final UniqueContext c, final DamageSource source, final float amount) {
			if (amount <= 0.0F || !MpRolls.chance(ID, c.holder(), chanceAt(c.refine()))) {
				return amount;
			}
			if (c.atLeast(Uniques.PLUS_10)) {
				MpKit.buff(c.holder(), MobEffects.INVISIBILITY, TUNING.plus10InvisibleTicks(), 0);
			}
			if (c.atLeast(Uniques.PLUS_15) && source.getEntity() instanceof LivingEntity attacker && attacker != c.holder()) {
				UniqueEffects.addHarmful(attacker, MobEffects.BLINDNESS, TUNING.plus15BlindTicks(), 0);
			}
			return 0.0F;
		}
	};

	private MirrorMask() {
	}

	public static double chanceAt(final int refine) {
		if (refine >= Uniques.PLUS_12) {
			return TUNING.plus12Chance();
		}
		return refine >= Uniques.PLUS_7 ? TUNING.plus7Chance() : TUNING.baseChance();
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Mirror Mask", UniqueJob.MAGE, UniqueKind.HELMET, List.of(Identifier.parse("minecraft:illusioner")),
			MpKit.percent(TUNING.baseChance()) + " chance to ignore a hit",
			MpKit.percent(TUNING.plus7Chance()),
			"An ignored hit makes you invisible, " + MpKit.seconds(TUNING.plus10InvisibleTicks()),
			MpKit.percent(TUNING.plus12Chance()),
			"An ignored hit gives the attacker Blindness, " + MpKit.seconds(TUNING.plus15BlindTicks()),
			EquipmentAssets.IRON, EFFECT));
	}
}
