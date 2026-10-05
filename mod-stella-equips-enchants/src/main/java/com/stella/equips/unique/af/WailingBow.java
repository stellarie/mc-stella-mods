package com.stella.equips.unique.af;

import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDamage;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

/** The Ghast's bow. */
public final class WailingBow {
	public static final String ID = "wailing_bow";

	public record Tuning(double chance, double plus7, double plus12, float burstShare, double radius, double radiusPlus10, int guaranteedEvery) {
	}

	public static final Tuning TUNING = new Tuning(0.10, 0.15, 0.20, 0.50F, 2.0, 3.0, 4);
	private static final String FOURTH_TAG = "stella_wailing_fourth";
	private static final Map<UUID, Integer> SHOTS = new HashMap<>();
	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public void onShot(final UniqueContext c, final AbstractArrow arrow, final float power) {
			int shot = SHOTS.merge(c.holder().getUUID(), 1, Integer::sum);
			if (c.atLeast(Uniques.PLUS_15) && shot % TUNING.guaranteedEvery() == 0) {
				arrow.addTag(FOURTH_TAG);
			}
		}

		@Override
		public void afterArrow(final UniqueContext c, final LivingEntity target, final AbstractArrow arrow, final float dealt, final boolean killed) {
			boolean guaranteed = arrow != null && arrow.entityTags().contains(FOURTH_TAG);
			if (guaranteed || AfChance.roll(c.holder(), ID, chance(c.refine()))) {
				UniqueDamage.area(c.holder().level(), target.position(), radius(c.refine()), c.holder(), dealt * TUNING.burstShare(), false, target);
			}
		}
	};

	private WailingBow() {
	}

	public static double chance(final int refine) {
		return Af.tier(refine, TUNING.chance(), TUNING.plus7(), TUNING.plus12());
	}

	public static double radius(final int refine) {
		return refine >= Uniques.PLUS_10 ? TUNING.radiusPlus10() : TUNING.radius();
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Wailing Bow", UniqueJob.ARCHER, UniqueKind.BOW,
			List.of(Identifier.parse("minecraft:ghast")),
			Af.percent(TUNING.chance()) + " chance an arrow bursts on hit: " + Af.percent(TUNING.burstShare())
				+ " of the hit to foes within " + (int) TUNING.radius() + ", no block damage",
			Af.percent(TUNING.plus7()), "Radius " + (int) TUNING.radiusPlus10(), Af.percent(TUNING.plus12()),
			"Every " + Af.ordinal(TUNING.guaranteedEvery()) + " arrow bursts", null, EFFECT));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SHOTS.clear());
	}
}
