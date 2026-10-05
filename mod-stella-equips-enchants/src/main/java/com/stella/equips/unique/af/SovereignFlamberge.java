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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** The Sovereign's Flamberge. Every number lives in {@link #TUNING}. */
public final class SovereignFlamberge {
	public static final String ID = "sovereign_flamberge";
	private static final int HELD_TICKS = 60;
	private static final Map<UUID, Integer> HITS = new ConcurrentHashMap<>();

	public record Tuning(double bonus, double bonusPlus7, double bonusPlus12, int fireTicks, int every, double burstShare, double burstRadius) {
	}

	public static final Tuning TUNING = new Tuning(0.25, 0.35, 0.50, 100, 4, 0.70, 3.0);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float amount) {
			return target.isOnFire()
				? (float) (amount * (1.0 + Af.tier(c.refine(), TUNING.bonus(), TUNING.bonusPlus7(), TUNING.bonusPlus12()))) : amount;
		}

		@Override
		public void afterMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float dealt, final boolean crit, final boolean killed) {
			MpKit.ignite(target, TUNING.fireTicks());
			if (!c.atLeast(Uniques.PLUS_15) || !(c.holder().level() instanceof ServerLevel level)) {
				return;
			}
			int hits = HITS.merge(c.holder().getUUID(), 1, Integer::sum);
			if (hits % TUNING.every() != 0) {
				return;
			}
			for (LivingEntity foe : Af.foesAround(level, c.holder(), target.position(), TUNING.burstRadius(), target)) {
				UniqueDamage.deal(level, foe, c.holder(), dealt * (float) TUNING.burstShare(), false);
				MpKit.ignite(foe, TUNING.fireTicks());
			}
		}

		@Override
		public float incoming(final UniqueContext c, final DamageSource s, final float amount) {
			return c.atLeast(Uniques.PLUS_10) && s.is(DamageTypeTags.IS_FIRE) ? 0.0F : amount;
		}

		@Override
		public void everySecond(final UniqueContext c) {
			if (c.atLeast(Uniques.PLUS_10)) {
				Af.bless(c.holder(), MobEffects.FIRE_RESISTANCE, HELD_TICKS, 0);
			}
		}
	};

	private SovereignFlamberge() {
	}

	/** Forgets one holder's hit count. */
	public static void reset(final UUID holder) {
		HITS.remove(holder);
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Sovereign's Flamberge", UniqueJob.FIGHTER, UniqueKind.SWORD,
			List.of(Identifier.parse("mod-stella-nether:sovereign")),
			"Hits set the target alight, " + Af.seconds(TUNING.fireTicks()) + ". +" + Af.percent(TUNING.bonus()) + " against burning targets",
			"+" + Af.percent(TUNING.bonusPlus7()),
			"Fire immunity while held",
			"+" + Af.percent(TUNING.bonusPlus12()),
			"Every " + Af.ordinal(TUNING.every()) + " hit deals " + Af.percent(TUNING.burstShare()) + " of the hit to foes within "
				+ (int) TUNING.burstRadius() + " and sets them alight", null, EFFECT));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> HITS.clear());
	}
}
