package com.stella.equips.unique.af;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** The Withering Greatsword. */
public final class WitheringGreatsword {
	public static final String ID = "withering_greatsword";
	public record Tuning(int ticks, int ticksPlus12, int amplifier, int amplifierPlus7, double bonus, double spreadRadius, int spreadTicks, int spreadAmplifier) {}
	public static final Tuning TUNING = new Tuning(60, 100, 0, 1, 0.15, 3.0, 60, 1);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float amount) {
			return c.atLeast(Uniques.PLUS_10) && target.hasEffect(MobEffects.WITHER)
				? (float) (amount * (1.0 + TUNING.bonus())) : amount;
		}

		@Override
		public void afterMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float dealt, final boolean crit, final boolean killed) {
			int ticks = c.atLeast(Uniques.PLUS_12) ? TUNING.ticksPlus12() : TUNING.ticks();
			int amplifier = c.atLeast(Uniques.PLUS_7) ? TUNING.amplifierPlus7() : TUNING.amplifier();
			Af.curse(target, MobEffects.WITHER, ticks, amplifier);
		}

		@Override
		public void onKill(final UniqueContext c, final LivingEntity victim, final DamageSource s) {
			if (!c.atLeast(Uniques.PLUS_15) || !victim.hasEffect(MobEffects.WITHER)
				|| !(c.holder().level() instanceof ServerLevel level)) {
				return;
			}
			for (LivingEntity foe : Af.foesAround(level, c.holder(), victim.position(), TUNING.spreadRadius(), victim)) {
				Af.curse(foe, MobEffects.WITHER, TUNING.spreadTicks(), TUNING.spreadAmplifier());
			}
		}
	};

	private WitheringGreatsword() {}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Withering Greatsword", UniqueJob.FIGHTER, UniqueKind.SWORD,
			List.of(Identifier.parse("minecraft:wither_skeleton")),
			"Hits give Wither I, 3 s", "Wither II", "Withered targets take +15%", "5 s", "A kill on a withered target gives Wither II, 3 s, to foes within 3", null, EFFECT));
	}
}
