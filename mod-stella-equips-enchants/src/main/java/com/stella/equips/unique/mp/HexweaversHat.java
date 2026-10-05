package com.stella.equips.unique.mp;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueEffects;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** The Hexweaver's Hat. Spell hits give random harmful effects to the target. Every number lives in {@link #TUNING}. */
public final class HexweaversHat {
	public static final String ID = "hexweaver_hat";

	/** The tuning. Durations are in ticks. Amplifier 1 is level II. */
	public record Tuning(int baseTicks, int plus7Ticks, int baseEffects, int plus10Effects, int baseAmplifier, int plus12Amplifier, int harmfulNeeded,
		double plus15Bonus) {
	}

	public static final Tuning TUNING = new Tuning(80, 120, 1, 2, 0, 1, 3, 0.25);

	/** The pool that a spell hit draws from. */
	public static final List<Holder<MobEffect>> POOL = List.of(MobEffects.WEAKNESS, MobEffects.SLOWNESS, MobEffects.POISON, MobEffects.MINING_FATIGUE);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingSpell(final UniqueContext c, final LivingEntity target, final DamageSource s, final float amount) {
			return c.atLeast(Uniques.PLUS_15) && MpKit.harmfulCount(target) >= TUNING.harmfulNeeded() ? (float) (amount * (1.0 + TUNING.plus15Bonus())) : amount;
		}

		@Override
		public void afterSpell(final UniqueContext c, final LivingEntity target, final DamageSource s, final float dealt, final boolean killed) {
			if (killed) {
				return;
			}
			List<Holder<MobEffect>> left = new ArrayList<>(POOL);
			for (int i = 0; i < effectsAt(c.refine()); i++) {
				Holder<MobEffect> effect = left.remove(MpRolls.pick(ID, c.holder(), left.size()));
				UniqueEffects.addHarmful(target, effect, ticksAt(c.refine()), amplifierAt(c.refine()));
			}
		}
	};

	private HexweaversHat() {
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Hexweaver's Hat", UniqueJob.MAGE, UniqueKind.HELMET, List.of(Identifier.parse("minecraft:witch")),
			"Spell hits give one random harmful effect, " + MpKit.seconds(TUNING.baseTicks()) + ": Weakness, Slowness, Poison, or Mining Fatigue",
			MpKit.seconds(TUNING.plus7Ticks()),
			"Two effects per hit",
			"Amplifier II",
			"Spell hits on a target with " + TUNING.harmfulNeeded() + " or more harmful effects deal +" + MpKit.percent(TUNING.plus15Bonus()),
			EquipmentAssets.LEATHER, EFFECT));
	}

	public static int ticksAt(final int refine) {
		return refine >= Uniques.PLUS_7 ? TUNING.plus7Ticks() : TUNING.baseTicks();
	}

	public static int effectsAt(final int refine) {
		return refine >= Uniques.PLUS_10 ? TUNING.plus10Effects() : TUNING.baseEffects();
	}

	public static int amplifierAt(final int refine) {
		return refine >= Uniques.PLUS_12 ? TUNING.plus12Amplifier() : TUNING.baseAmplifier();
	}
}
