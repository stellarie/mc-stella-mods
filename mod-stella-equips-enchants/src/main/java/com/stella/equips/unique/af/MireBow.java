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

/** The Mire Bow: arrow hits poison the target and, from +10, one foe near it. A poisoned target takes more at +15. */
public final class MireBow {
	public static final String ID = "mire_bow";

	/** The tuning. Poison lasts {@code ticks}, {@code ticksPlus12} from +12, and is one level higher from +7. */
	public record Tuning(int ticks, int ticksPlus12, int amplifierPlus7, double spreadRadius, double poisonedBonus) {
	}

	public static final Tuning TUNING = new Tuning(80, 120, 1, 4.0, 0.20);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingArrow(final UniqueContext c, final LivingEntity target, final AbstractArrow arrow, final float amount) {
			return c.atLeast(Uniques.PLUS_15) && target.hasEffect(MobEffects.POISON) ? amount * (1.0F + (float) TUNING.poisonedBonus()) : amount;
		}

		@Override
		public void afterArrow(final UniqueContext c, final LivingEntity target, final AbstractArrow arrow, final float dealt, final boolean killed) {
			if (killed) {
				return;
			}
			ServerPlayer holder = c.holder();
			Af.curse(target, MobEffects.POISON, ticks(c.refine()), amplifier(c.refine()));
			if (c.atLeast(Uniques.PLUS_10)) {
				List<LivingEntity> near = Af.foesAround(holder.level(), holder, target.position(), TUNING.spreadRadius(), target);
				if (!near.isEmpty()) {
					Af.curse(near.get(0), MobEffects.POISON, ticks(c.refine()), amplifier(c.refine()));
				}
			}
		}
	};

	private MireBow() {
	}

	public static int ticks(final int refine) {
		return refine >= Uniques.PLUS_12 ? TUNING.ticksPlus12() : TUNING.ticks();
	}

	public static int amplifier(final int refine) {
		return refine >= Uniques.PLUS_7 ? TUNING.amplifierPlus7() : 0;
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Mire Bow", UniqueJob.ARCHER, UniqueKind.BOW, List.of(Identifier.parse("minecraft:bogged")),
			"Arrow hits give Poison " + Af.roman(1) + ", " + Af.seconds(TUNING.ticks()),
			"Poison " + Af.roman(TUNING.amplifierPlus7() + 1),
			"A hit also poisons one foe within " + (int) TUNING.spreadRadius(),
			Af.seconds(TUNING.ticksPlus12()),
			"Poisoned targets take +" + Af.percent(TUNING.poisonedBonus()) + " arrow damage",
			null, EFFECT));
	}
}
