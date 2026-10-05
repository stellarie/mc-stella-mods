package com.stella.equips.unique.mp;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** The Cleric's Mitre. Every number lives in {@link #TUNING}. */
public final class ClericsMitre {
	public static final String ID = "clerics_mitre";

	public record Tuning(double reduce, double reducePlus7, double reducePlus12, double radius, double undeadBonus) {
	}

	public static final Tuning TUNING = new Tuning(0.15, 0.20, 0.25, 8.0, 0.25);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float incoming(final UniqueContext c, final DamageSource s, final float amount) {
			return s.getEntity() instanceof LivingEntity attacker && Priests.undead(attacker)
				? (float) (amount * (1.0 - reduce(c.refine()))) : amount;
		}

		@Override
		public float outgoingMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float amount) {
			return c.atLeast(Uniques.PLUS_15) && Priests.undead(target) ? (float) (amount * (1.0 + TUNING.undeadBonus())) : amount;
		}

		@Override
		public void everySecond(final UniqueContext c) {
			if (!c.atLeast(Uniques.PLUS_10)) {
				return;
			}
			for (ServerPlayer ally : Priests.allies(c.holder(), TUNING.radius())) {
				MpKit.buff(ally, MobEffects.RESISTANCE, 3 * MpKit.SECOND, 0);
			}
		}
	};

	private ClericsMitre() {
	}

	/** The share an undead blow loses at the given refine. */
	public static double reduce(final int refine) {
		return refine >= Uniques.PLUS_12 ? TUNING.reducePlus12() : refine >= Uniques.PLUS_7 ? TUNING.reducePlus7() : TUNING.reduce();
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Cleric's Mitre", UniqueJob.PRIEST, UniqueKind.HELMET, List.of(Identifier.parse("minecraft:zombie_villager")),
			"Undead mobs deal you " + MpKit.percent(TUNING.reduce()) + " less damage",
			MpKit.percent(TUNING.reducePlus7()),
			"Allies within " + (int) TUNING.radius() + " also get the reduction",
			MpKit.percent(TUNING.reducePlus12()),
			"Your melee hits on undead mobs deal +" + MpKit.percent(TUNING.undeadBonus()),
			EquipmentAssets.DIAMOND, EFFECT));
	}
}
