package com.stella.equips.unique.mp;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDamage;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.UniquePassives;
import com.stella.equips.unique.Uniques;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** The Revenant Greaves. Every number lives in {@link #TUNING}. */
public final class RevenantGreaves {
	public static final String ID = "revenant_greaves";

	public record Tuning(double speed, double speedPlus7, double radius, int pulseTicks, int effectTicks, int slowAmplifier, int slowAmplifierPlus12,
		double damageShare) {
	}

	public static final Tuning TUNING = new Tuning(0.10, 0.15, 8.0, 40, 60, 0, 1, 0.01);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public void everySecond(final UniqueContext c) {
			UniquePassives.hold(c.holder(), ID, Attributes.MOVEMENT_SPEED, c.atLeast(Uniques.PLUS_7) ? TUNING.speedPlus7() : TUNING.speed());
			if (Priests.due(ID, c.holder(), TUNING.pulseTicks())) {
				pulse(c);
			}
		}
	};

	private RevenantGreaves() {
	}

	/** One step: the undead in reach slow, the living in reach hurry. */
	public static void pulse(final UniqueContext c) {
		ServerPlayer holder = c.holder();
		int amplifier = c.atLeast(Uniques.PLUS_12) ? TUNING.slowAmplifierPlus12() : TUNING.slowAmplifier();
		if (holder.level() instanceof ServerLevel level) {
			for (LivingEntity foe : MpKit.foesAround(level, holder, holder.position(), TUNING.radius(), null)) {
				if (!Priests.undead(foe)) {
					continue;
				}
				MpKit.buff(foe, MobEffects.SLOWNESS, TUNING.effectTicks(), amplifier);
				if (c.atLeast(Uniques.PLUS_15)) {
					UniqueDamage.deal(level, foe, holder, foe.getMaxHealth() * (float) TUNING.damageShare(), false);
				}
			}
		}
		if (c.atLeast(Uniques.PLUS_10)) {
			for (ServerPlayer ally : Priests.allies(holder, TUNING.radius())) {
				MpKit.buff(ally, MobEffects.SPEED, TUNING.effectTicks(), 0);
			}
		}
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Revenant Greaves", UniqueJob.PRIEST, UniqueKind.BOOTS, List.of(Identifier.parse("minecraft:zombie_horse")),
			"+" + MpKit.percent(TUNING.speed()) + " movement speed. Every " + MpKit.seconds(TUNING.pulseTicks())
				+ ", undead mobs within " + (int) TUNING.radius() + " get Slowness I, " + MpKit.seconds(TUNING.effectTicks()),
			"+" + MpKit.percent(TUNING.speedPlus7()) + " speed",
			"Allies within " + (int) TUNING.radius() + " get Speed I, " + MpKit.seconds(TUNING.effectTicks()) + ", every " + MpKit.seconds(TUNING.pulseTicks()),
			"Slowness II",
			"Undead mobs within " + (int) TUNING.radius() + " take " + MpKit.percent(TUNING.damageShare()) + " of max health each second",
			EquipmentAssets.DIAMOND, EFFECT));
	}
}
