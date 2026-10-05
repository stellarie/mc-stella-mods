package com.stella.equips.unique.mp;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDamage;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** The Conduit Mantle. Every number lives in {@link #TUNING}. */
public final class ConduitMantle {
	public static final String ID = "conduit_mantle";

	public record Tuning(double radius, double radiusPlus7, double healShare, double healSharePlus12, double foeShare, double foeRadius,
		int pulseTicks, int powerTicks) {
	}

	public static final Tuning TUNING = new Tuning(8.0, 16.0, 0.01, 0.02, 0.02, 8.0, 40, 60);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public void everySecond(final UniqueContext c) {
			ServerPlayer holder = c.holder();
			double radius = c.atLeast(Uniques.PLUS_7) ? TUNING.radiusPlus7() : TUNING.radius();
			for (ServerPlayer ally : Priests.allies(holder, radius)) {
				MpKit.buff(ally, MobEffects.CONDUIT_POWER, TUNING.powerTicks(), 0);
			}
			if (Priests.due(ID, holder, TUNING.pulseTicks())) {
				pulse(c);
			}
		}
	};

	private ConduitMantle() {
	}

	/** One swell: wet allies mend, and at the top tier the wet foes pay for it. */
	public static void pulse(final UniqueContext c) {
		ServerPlayer holder = c.holder();
		double radius = c.atLeast(Uniques.PLUS_7) ? TUNING.radiusPlus7() : TUNING.radius();
		if (c.atLeast(Uniques.PLUS_10)) {
			double share = c.atLeast(Uniques.PLUS_12) ? TUNING.healSharePlus12() : TUNING.healShare();
			for (ServerPlayer ally : Priests.allies(holder, radius)) {
				if (Priests.wet(ally)) {
					MpKit.heal(ally, ally.getMaxHealth() * share);
				}
			}
		}
		if (c.atLeast(Uniques.PLUS_15) && holder.level() instanceof ServerLevel level) {
			for (LivingEntity foe : MpKit.foesAround(level, holder, holder.position(), TUNING.foeRadius(), null)) {
				if (Priests.wet(foe)) {
					UniqueDamage.deal(level, foe, holder, foe.getMaxHealth() * (float) TUNING.foeShare(), false);
				}
			}
		}
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Conduit Mantle", UniqueJob.PRIEST, UniqueKind.CHESTPLATE, List.of(Identifier.parse("minecraft:elder_guardian")),
			"You and allies within " + (int) TUNING.radius() + " get Conduit Power",
			"Radius " + (int) TUNING.radiusPlus7(),
			"Every " + MpKit.seconds(TUNING.pulseTicks()) + ", allies in water or rain heal " + MpKit.percent(TUNING.healShare()) + " of max health",
			MpKit.percent(TUNING.healSharePlus12()),
			"Every " + MpKit.seconds(TUNING.pulseTicks()) + ", foes within " + (int) TUNING.foeRadius() + " in water take "
				+ MpKit.percent(TUNING.foeShare()) + " of max health",
			EquipmentAssets.DIAMOND, EFFECT));
	}
}
