package com.stella.equips.unique.af;

import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueFields;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;

/** The Ender Dragon's bow. */
public final class DragonbreathBow {
	public static final String ID = "dragonbreath_bow";

	public record Tuning(double chance, double chancePlus7, double radius, double radiusPlus12, int ticks, int ticksPlus10,
		float damageShare, double pullBlocks) {
	}

	public static final Tuning TUNING = new Tuning(0.20, 0.30, 2.0, 3.0, 80, 120, 0.05F, 1.0);
	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public void afterArrow(final UniqueContext c, final LivingEntity target, final AbstractArrow arrow, final float dealt, final boolean killed) {
			if (!AfChance.roll(c.holder(), ID, chance(c.refine()))) {
				return;
			}
			Vec3 center = target.position();
			UniqueFields.field(c.holder().level(), center, radius(c.refine()), ticks(c.refine()), dealt * TUNING.damageShare(),
				c.holder(), c.atLeast(Uniques.PLUS_15) ? foes -> {
					for (LivingEntity foe : foes) {
						Vec3 toward = center.subtract(foe.position());
						Vec3 horizontal = new Vec3(toward.x, 0.0, toward.z);
						if (horizontal.lengthSqr() > 0.001) {
							Vec3 pull = horizontal.normalize().scale(TUNING.pullBlocks());
							foe.move(MoverType.SELF, pull);
						}
					}
				} : null);
		}
	};

	private DragonbreathBow() {
	}

	public static double chance(final int refine) {
		return refine >= Uniques.PLUS_7 ? TUNING.chancePlus7() : TUNING.chance();
	}

	public static double radius(final int refine) {
		return refine >= Uniques.PLUS_12 ? TUNING.radiusPlus12() : TUNING.radius();
	}

	public static int ticks(final int refine) {
		return refine >= Uniques.PLUS_10 ? TUNING.ticksPlus10() : TUNING.ticks();
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Dragonbreath Bow", UniqueJob.ARCHER, UniqueKind.BOW,
			List.of(Identifier.parse("minecraft:ender_dragon")),
			Af.percent(TUNING.chance()) + " chance an arrow leaves a breath field: radius " + (int) TUNING.radius()
				+ ", " + Af.seconds(TUNING.ticks()) + ", " + Af.percent(TUNING.damageShare()) + " of the hit each second",
			Af.percent(TUNING.chancePlus7()),
			"The field lasts " + Af.seconds(TUNING.ticksPlus10()),
			"Radius " + (int) TUNING.radiusPlus12(),
			"The field pulls foes " + (int) TUNING.pullBlocks() + " block toward its center each second", null, EFFECT));
	}
}
