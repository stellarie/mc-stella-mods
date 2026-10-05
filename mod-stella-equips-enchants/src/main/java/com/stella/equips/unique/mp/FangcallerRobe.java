package com.stella.equips.unique.mp;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDamage;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueEffects;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.UniqueScheduler;
import com.stella.equips.unique.Uniques;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** Fangcaller Robe raises a fang for a share of a spell hit. */
public final class FangcallerRobe {
	public static final String ID = "fangcaller_robe";

	public record Tuning(double baseChance, double plus7Chance, double plus12Chance, double hitShare,
		double plus10Radius, int plus15SlowTicks, int plus15SlowAmplifier, int visualTicks) {
	}

	public static final Tuning TUNING = new Tuning(0.20, 0.30, 0.40, 0.40, 2.0, 40, 2, 6);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public void afterSpell(final UniqueContext c, final LivingEntity target, final DamageSource source, final float dealt, final boolean killed) {
			if (!MpRolls.chance(ID, c.holder(), chanceAt(c.refine()))) {
				return;
			}
			float fangDamage = (float) (dealt * TUNING.hitShare());
			strike(c, target, fangDamage);
			if (c.atLeast(Uniques.PLUS_10)) {
				for (LivingEntity foe : MpKit.foesAround(c.holder().level(), c.holder(), target.position(), TUNING.plus10Radius(), target)) {
					strike(c, foe, fangDamage);
				}
			}
		}
	};

	private FangcallerRobe() {
	}

	private static void strike(final UniqueContext c, final LivingEntity target, final float amount) {
		ServerLevel level = c.holder().level();
		EvokerFangs fang = new EvokerFangs(level, target.getX(), target.getY(), target.getZ(), target.getYRot(), 0, c.holder());
		if (!level.addFreshEntity(fang)) {
			return;
		}
		UniqueScheduler.after(level.getServer(), TUNING.visualTicks(), fang::discard);
		if (UniqueDamage.deal(level, target, c.holder(), amount, false) && c.atLeast(Uniques.PLUS_15) && target.isAlive()) {
			UniqueEffects.addHarmful(target, MobEffects.SLOWNESS, TUNING.plus15SlowTicks(), TUNING.plus15SlowAmplifier());
		}
	}

	public static double chanceAt(final int refine) {
		if (refine >= Uniques.PLUS_12) {
			return TUNING.plus12Chance();
		}
		return refine >= Uniques.PLUS_7 ? TUNING.plus7Chance() : TUNING.baseChance();
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Fangcaller Robe", UniqueJob.MAGE, UniqueKind.LEGGINGS, List.of(Identifier.parse("minecraft:evoker")),
			MpKit.percent(TUNING.baseChance()) + " chance a spell hit raises fangs under the target: " + MpKit.percent(TUNING.hitShare()) + " of the hit",
			MpKit.percent(TUNING.plus7Chance()),
			"The fangs also hit foes within " + (int) TUNING.plus10Radius() + " of the target",
			MpKit.percent(TUNING.plus12Chance()),
			"The fangs give Slowness III, " + MpKit.seconds(TUNING.plus15SlowTicks()),
			EquipmentAssets.NETHERITE, EFFECT));
	}
}
