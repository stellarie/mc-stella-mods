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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** The Three-Skull Reliquary. Every number lives in {@link #TUNING}. */
public final class ThreeSkullReliquary {
	public static final String ID = "three_skull_reliquary";

	public record Tuning(double healShare, double healSharePlus7, int witherTicks, int witherAmplifier, int witherAmplifierPlus12, double radius) {
	}

	public static final Tuning TUNING = new Tuning(0.04, 0.05, 60, 0, 1, 8.0);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public void afterMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float dealt, final boolean crit, final boolean killed) {
			if (dealt <= 0.0F) {
				return;
			}
			ServerPlayer holder = c.holder();
			double share = c.atLeast(Uniques.PLUS_7) ? TUNING.healSharePlus7() : TUNING.healShare();
			MpKit.heal(holder, dealt * share);
			UniqueEffects.addHarmful(target, MobEffects.WITHER, TUNING.witherTicks(),
				c.atLeast(Uniques.PLUS_12) ? TUNING.witherAmplifierPlus12() : TUNING.witherAmplifier());
			if (c.atLeast(Uniques.PLUS_10)) {
				for (ServerPlayer ally : Priests.allies(holder, TUNING.radius())) {
					if (ally != holder) {
						MpKit.heal(ally, dealt * share);
					}
				}
			}
		}

		@Override
		public void everySecond(final UniqueContext c) {
			if (!c.atLeast(Uniques.PLUS_15)) {
				return;
			}
			for (ServerPlayer ally : Priests.allies(c.holder(), TUNING.radius())) {
				Priests.cleanse(ally, MobEffects.WITHER);
			}
		}
	};

	private ThreeSkullReliquary() {
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Three-Skull Reliquary", UniqueJob.PRIEST, UniqueKind.MACE, List.of(Identifier.parse("minecraft:wither")),
			"Hits give Wither I, " + MpKit.seconds(TUNING.witherTicks()) + ", and heal you " + MpKit.percent(TUNING.healShare()) + " of the hit",
			MpKit.percent(TUNING.healSharePlus7()),
			"Allies within " + (int) TUNING.radius() + " also heal",
			"Wither II",
			"Allies within " + (int) TUNING.radius() + " are immune to Wither",
			null, EFFECT));
	}
}
