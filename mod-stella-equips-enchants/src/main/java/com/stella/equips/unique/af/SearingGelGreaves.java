package com.stella.equips.unique.af;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDamage;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import com.stella.equips.unique.mp.MpKit;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** The Searing Gel Greaves. Every number lives in {@link #TUNING}. */
public final class SearingGelGreaves {
	public static final String ID = "searing_gel_greaves";

	public record Tuning(int baseTicks, int plus7Ticks, int plus12Ticks, double reduce, double reflectShare) {
	}

	public static final Tuning TUNING = new Tuning(60, 100, 160, 0.15, 0.20);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float incoming(final UniqueContext c, final DamageSource s, final float amount) {
			LivingEntity attacker = Af.meleeAttacker(s);
			return c.atLeast(Uniques.PLUS_10) && attacker != null && attacker.isOnFire() ? (float) (amount * (1.0 - TUNING.reduce())) : amount;
		}

		@Override
		public void afterIncoming(final UniqueContext c, final DamageSource s, final float taken) {
			LivingEntity attacker = Af.meleeAttacker(s);
			if (attacker == null || attacker == c.holder() || !(c.holder().level() instanceof ServerLevel level)) {
				return;
			}
			MpKit.ignite(attacker, fireTicks(c.refine()));
			if (c.atLeast(Uniques.PLUS_15) && taken > 0.0F) {
				UniqueDamage.deal(level, attacker, c.holder(), taken * (float) TUNING.reflectShare(), false);
			}
		}
	};

	private SearingGelGreaves() {
	}

	/** How long a melee attacker burns at the given refine. */
	public static int fireTicks(final int refine) {
		return Af.tier(refine, TUNING.baseTicks(), TUNING.plus7Ticks(), TUNING.plus12Ticks());
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Searing Gel Greaves", UniqueJob.FIGHTER, UniqueKind.LEGGINGS, List.of(Identifier.parse("mod-stella-mobs:red_slime")),
			"Melee attackers are set alight, " + Af.seconds(TUNING.baseTicks()),
			Af.seconds(TUNING.plus7Ticks()),
			"Burning attackers deal you " + Af.percent(TUNING.reduce()) + " less damage",
			Af.seconds(TUNING.plus12Ticks()),
			"Melee attackers also take " + Af.percent(TUNING.reflectShare()) + " of the blow", null, EFFECT));
	}
}
