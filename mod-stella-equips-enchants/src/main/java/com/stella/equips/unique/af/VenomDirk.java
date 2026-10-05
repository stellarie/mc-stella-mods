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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** The Venom Dirk. */
public final class VenomDirk {
	public static final String ID = "venom_dirk";
	public record Tuning(int stackTicks, int maxTicks, int maxTicksPlus12, int amplifier, int amplifierPlus7, double poisonedBonus, double spreadRadius) {}
	public static final Tuning TUNING = new Tuning(60, 200, 300, 0, 1, 0.15, 4.0);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float amount) {
			return c.atLeast(Uniques.PLUS_10) && target.hasEffect(MobEffects.POISON)
				? (float) (amount * (1.0 + TUNING.poisonedBonus())) : amount;
		}

		@Override
		public void afterMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float dealt, final boolean crit, final boolean killed) {
			MobEffectInstance current = target.getEffect(MobEffects.POISON);
			int now = current == null ? 0 : current.getDuration();
			int cap = c.atLeast(Uniques.PLUS_12) ? TUNING.maxTicksPlus12() : TUNING.maxTicks();
			Af.curse(target, MobEffects.POISON, Math.min(cap, now + TUNING.stackTicks()), amplifier(c));
		}

		@Override
		public void onKill(final UniqueContext c, final LivingEntity victim, final DamageSource s) {
			if (!c.atLeast(Uniques.PLUS_15) || !victim.hasEffect(MobEffects.POISON)
				|| !(c.holder().level() instanceof ServerLevel level)) {
				return;
			}
			for (LivingEntity foe : Af.foesAround(level, c.holder(), victim.position(), TUNING.spreadRadius(), victim)) {
				Af.curse(foe, MobEffects.POISON, TUNING.maxTicksPlus12(), TUNING.amplifierPlus7());
			}
		}
	};

	private VenomDirk() {}

	private static int amplifier(final UniqueContext c) {
		return c.atLeast(Uniques.PLUS_7) ? TUNING.amplifierPlus7() : TUNING.amplifier();
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Venom Dirk", UniqueJob.FIGHTER, UniqueKind.SWORD,
			List.of(Identifier.parse("minecraft:cave_spider")),
			"Hits give Poison I for 3 s. Each hit adds 3 s, max 10 s", "Poison II", "Hits on a poisoned target deal +15%", "Max 15 s", "A kill gives its Poison to foes within 4", null, EFFECT));
	}
}
