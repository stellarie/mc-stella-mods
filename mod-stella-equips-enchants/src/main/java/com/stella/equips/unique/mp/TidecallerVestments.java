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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** The Tidecaller Vestments. Every number lives in {@link #TUNING}. */
public final class TidecallerVestments {
	public static final String ID = "tidecaller_vestments";

	public record Tuning(double share, double sharePlus7, int everyTicks, double radius, double radiusPlus12, double wetMultiplier) {
	}

	public static final Tuning TUNING = new Tuning(0.02, 0.03, 100, 6.0, 10.0, 2.0);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public void everySecond(final UniqueContext c) {
			if (Priests.due(ID, c.holder(), TUNING.everyTicks())) {
				pulse(c);
			}
		}
	};

	private TidecallerVestments() {
	}

	/** One tide: every ally in reach heals, and the higher tiers add to it. */
	public static void pulse(final UniqueContext c) {
		double radius = c.atLeast(Uniques.PLUS_12) ? TUNING.radiusPlus12() : TUNING.radius();
		double share = c.atLeast(Uniques.PLUS_7) ? TUNING.sharePlus7() : TUNING.share();
		for (ServerPlayer ally : Priests.allies(c.holder(), radius)) {
			MpKit.heal(ally, ally.getMaxHealth() * share * (Priests.wet(ally) ? TUNING.wetMultiplier() : 1.0));
			if (c.atLeast(Uniques.PLUS_10)) {
				MpKit.buff(ally, MobEffects.WATER_BREATHING, 3 * MpKit.SECOND, 0);
			}
			if (c.atLeast(Uniques.PLUS_15)) {
				Priests.removeOneHarmful(ally);
			}
		}
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Tidecaller Vestments", UniqueJob.PRIEST, UniqueKind.CHESTPLATE, List.of(Identifier.parse("minecraft:drowned")),
			"Every " + MpKit.seconds(TUNING.everyTicks()) + ", allies within " + (int) TUNING.radius() + " heal " + MpKit.percent(TUNING.share())
				+ " of max health, doubled in water or rain",
			MpKit.percent(TUNING.sharePlus7()),
			"Allies within " + (int) TUNING.radius() + " also get Water Breathing",
			"Radius " + (int) TUNING.radiusPlus12(),
			"Each pulse also removes one harmful effect from each ally",
			EquipmentAssets.DIAMOND, EFFECT));
	}
}
