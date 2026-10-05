package com.stella.equips.unique.af;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDamage;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.UniqueScheduler;
import com.stella.equips.unique.Uniques;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** The Rabid Tusk. Every number lives in {@link #TUNING}. */
public final class RabidTusk {
	public static final String ID = "rabid_tusk";
	private static final int BLEED_EVERY = 20;
	private static final Map<UUID, Streak> STREAKS = new ConcurrentHashMap<>();

	/** The last target of a holder, the stacks it built, and the tick the last hit landed on. */
	private record Streak(int targetId, int stacks, long tick) {
	}

	public record Tuning(double perHit, double perHitPlus7, double cap, double capPlus12, int windowTicks, double bleedShare, int bleedTicks) {
	}

	public static final Tuning TUNING = new Tuning(0.03, 0.04, 0.30, 0.45, 60, 0.10, 60);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float amount) {
			return (float) (amount * (1.0 + bonus(c.refine(), stacks(c.holder(), target))));
		}

		@Override
		public void afterMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float dealt, final boolean crit, final boolean killed) {
			ServerPlayer holder = c.holder();
			long now = holder.level().getServer().getTickCount();
			if (killed && !c.atLeast(Uniques.PLUS_15)) {
				STREAKS.remove(holder.getUUID());
				return;
			}
			int stacks = stacks(holder, target) + 1;
			STREAKS.put(holder.getUUID(), new Streak(target.getId(), stacks, now));
			if (!c.atLeast(Uniques.PLUS_10) || bonus(c.refine(), stacks) < cap(c.refine()) || !(holder.level() instanceof ServerLevel level)) {
				return;
			}
			for (int pulse = 1; pulse * BLEED_EVERY <= TUNING.bleedTicks(); pulse++) {
				UniqueScheduler.after(level.getServer(), pulse * BLEED_EVERY, () -> {
					if (target.isAlive()) {
						UniqueDamage.deal(level, target, holder, dealt * (float) TUNING.bleedShare(), false);
					}
				});
			}
		}
	};

	private RabidTusk() {
	}

	/** The stacks the holder already built against this target inside the window, or zero. */
	public static int stacks(final ServerPlayer holder, final LivingEntity target) {
		Streak streak = STREAKS.get(holder.getUUID());
		if (streak == null || streak.targetId() != target.getId()) {
			return 0;
		}
		return holder.level().getServer().getTickCount() - streak.tick() > TUNING.windowTicks() ? 0 : streak.stacks();
	}

	public static double perHit(final int refine) {
		return refine >= Uniques.PLUS_7 ? TUNING.perHitPlus7() : TUNING.perHit();
	}

	public static double cap(final int refine) {
		return refine >= Uniques.PLUS_12 ? TUNING.capPlus12() : TUNING.cap();
	}

	/** The damage share the stacks have built, never above the cap of the tier. */
	public static double bonus(final int refine, final int stacks) {
		return Math.min(cap(refine), stacks * perHit(refine));
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Rabid Tusk", UniqueJob.FIGHTER, UniqueKind.AXE, List.of(Identifier.parse("minecraft:zoglin")),
			"+" + Af.percent(TUNING.perHit()) + " damage per hit on the same target within " + Af.seconds(TUNING.windowTicks()) + ", max +" + Af.percent(TUNING.cap()),
			"+" + Af.percent(TUNING.perHitPlus7()) + " per hit",
			"At max, hits bleed " + Af.percent(TUNING.bleedShare()) + " of the hit each second for " + Af.seconds(TUNING.bleedTicks()),
			"Max +" + Af.percent(TUNING.capPlus12()),
			"A kill keeps the current bonus for the next target", null, EFFECT));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> STREAKS.clear());
	}
}
