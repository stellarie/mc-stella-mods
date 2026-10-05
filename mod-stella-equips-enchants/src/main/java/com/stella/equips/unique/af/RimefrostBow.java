package com.stella.equips.unique.af;

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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

/** The Rimefrost Bow: arrow hits chill the target, a chilled target takes more, and a kill chills the foes around. */
public final class RimefrostBow {
	public static final String ID = "rimefrost_bow";

	/** The tuning. Slowness lasts {@code ticks}, {@code ticksPlus12} from +12, and is one level higher from +7. */
	public record Tuning(int ticks, int ticksPlus12, int amplifierPlus7, double slowedBonus, int killTicks, int killAmplifier, double killRadius) {
	}

	public static final Tuning TUNING = new Tuning(60, 100, 1, 0.20, 60, 1, 4.0);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingArrow(final UniqueContext c, final LivingEntity target, final AbstractArrow arrow, final float amount) {
			return c.atLeast(Uniques.PLUS_10) && target.hasEffect(MobEffects.SLOWNESS) ? amount * (1.0F + (float) TUNING.slowedBonus()) : amount;
		}

		@Override
		public void afterArrow(final UniqueContext c, final LivingEntity target, final AbstractArrow arrow, final float dealt, final boolean killed) {
			ServerPlayer holder = c.holder();
			if (!killed) {
				Af.curse(target, MobEffects.SLOWNESS, ticks(c.refine()), amplifier(c.refine()));
				return;
			}
			if (c.atLeast(Uniques.PLUS_15) && target.hasEffect(MobEffects.SLOWNESS)) {
				for (LivingEntity foe : Af.foesAround(holder.level(), holder, target.position(), TUNING.killRadius(), target)) {
					Af.curse(foe, MobEffects.SLOWNESS, TUNING.killTicks(), TUNING.killAmplifier());
				}
			}
		}
	};

	private RimefrostBow() {
	}

	public static int ticks(final int refine) {
		return refine >= Uniques.PLUS_12 ? TUNING.ticksPlus12() : TUNING.ticks();
	}

	public static int amplifier(final int refine) {
		return refine >= Uniques.PLUS_7 ? TUNING.amplifierPlus7() : 0;
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Rimefrost Bow", UniqueJob.ARCHER, UniqueKind.BOW, List.of(Identifier.parse("minecraft:stray")),
			"Arrow hits give Slowness " + Af.roman(1) + ", " + Af.seconds(TUNING.ticks()),
			"Slowness " + Af.roman(TUNING.amplifierPlus7() + 1),
			"Hits on a slowed target deal +" + Af.percent(TUNING.slowedBonus()),
			Af.seconds(TUNING.ticksPlus12()),
			"A kill on a slowed target gives Slowness " + Af.roman(TUNING.killAmplifier() + 1) + ", " + Af.seconds(TUNING.killTicks())
				+ ", to foes within " + (int) TUNING.killRadius(),
			null, EFFECT));
	}
}
