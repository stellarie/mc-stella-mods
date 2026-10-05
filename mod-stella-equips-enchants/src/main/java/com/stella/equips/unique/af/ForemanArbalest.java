package com.stella.equips.unique.af;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

/** The Foreman's Arbalest. */
public final class ForemanArbalest {
	public static final String ID = "foreman_arbalest";
	public record Tuning(double reload, double reloadPlus7, double reloadPlus12, int armorThreshold, double armorBonus, int bolts, float spreadDegrees) {}
	public static final Tuning TUNING = new Tuning(0.30, 0.45, 0.60, 10, 0.20, 3, 10.0F);
	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingArrow(final UniqueContext c, final LivingEntity target, final AbstractArrow arrow, final float amount) {
			return c.atLeast(Uniques.PLUS_10) && target.getArmorValue() >= TUNING.armorThreshold()
				? amount * (1 + (float) TUNING.armorBonus()) : amount;
		}
	};

	public static int chargeTicks(final int original, final int refine) {
		return Math.max(1, (int) Math.floor(original * (1 - Af.tier(refine, TUNING.reload(), TUNING.reloadPlus7(), TUNING.reloadPlus12()))));
	}
	private ForemanArbalest() {}
	public static void register() {
		Uniques.register(new UniqueDef(ID, "Foreman's Arbalest", UniqueJob.ARCHER, UniqueKind.CROSSBOW,
			List.of(Identifier.parse("mod-stella-mobs:skeleton_worker")),
			"Reloads 30% faster", "45%", "Bolts deal +20% against a target with 10 or more armor", "60%", "Every shot looses 3 bolts, like Multishot", null, EFFECT));
	}
}
