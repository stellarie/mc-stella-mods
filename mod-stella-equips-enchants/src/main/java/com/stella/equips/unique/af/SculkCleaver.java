package com.stella.equips.unique.af;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDamage;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** The Sculk Cleaver. Every number lives in {@link #TUNING}. */
public final class SculkCleaver {
	public static final String ID = "sculk_cleaver";
	private static final Map<UUID, List<Long>> TAKEN = new ConcurrentHashMap<>();
	private static final Map<UUID, Float> ARMOR_LOSS = new ConcurrentHashMap<>();

	public record Tuning(double perHit, double perHitPlus7, double cap, double capPlus12, int windowTicks, double burstShare, double burstRadius) {
	}

	public static final Tuning TUNING = new Tuning(0.02, 0.03, 0.40, 0.60, 200, 0.80, 5.0);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float amount) {
			int hits = taken(c.holder());
			double bonus = bonus(c.refine(), hits);
			float out = (float) (amount * (1.0 + bonus));
			if (c.atLeast(Uniques.PLUS_10) && bonus >= cap(c.refine())) {
				float mitigated = CombatRules.getDamageAfterAbsorb(target, out, s, target.getArmorValue(),
					(float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
				ARMOR_LOSS.put(c.holder().getUUID(), out - mitigated);
			}
			return out;
		}

		@Override
		public void afterMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float dealt, final boolean crit, final boolean killed) {
			Float armorLoss = ARMOR_LOSS.remove(c.holder().getUUID());
			if (armorLoss != null && armorLoss > 0.0F && target.isAlive() && c.holder().level() instanceof ServerLevel level) {
				UniqueDamage.deal(level, target, c.holder(), armorLoss, true);
			}
			if (killed && c.atLeast(Uniques.PLUS_15) && bonus(c.refine(), taken(c.holder())) >= cap(c.refine())
				&& c.holder().level() instanceof ServerLevel level && dealt > 0.0F) {
				for (LivingEntity foe : Af.foesAround(level, c.holder(), target.position(), TUNING.burstRadius(), target)) {
					UniqueDamage.deal(level, foe, c.holder(), dealt * (float) TUNING.burstShare(), false);
				}
			}
		}

		@Override
		public void afterIncoming(final UniqueContext c, final DamageSource s, final float taken) {
			List<Long> hits = TAKEN.computeIfAbsent(c.holder().getUUID(), ignored -> new ArrayList<>());
			synchronized (hits) {
				long now = c.holder().level().getServer().getTickCount();
				hits.removeIf(at -> now - at > TUNING.windowTicks());
				if (taken > 0.0F) {
					hits.add(now);
				}
			}
		}
	};

	private SculkCleaver() {
	}

	/** The blows the holder took inside the window. */
	public static int taken(final net.minecraft.server.level.ServerPlayer holder) {
		List<Long> hits = TAKEN.get(holder.getUUID());
		if (hits == null) {
			return 0;
		}
		long now = holder.level().getServer().getTickCount();
		synchronized (hits) {
			hits.removeIf(at -> now - at > TUNING.windowTicks());
			return hits.size();
		}
	}

	public static double perHit(final int refine) {
		return refine >= Uniques.PLUS_7 ? TUNING.perHitPlus7() : TUNING.perHit();
	}

	public static double cap(final int refine) {
		return refine >= Uniques.PLUS_12 ? TUNING.capPlus12() : TUNING.cap();
	}

	public static double bonus(final int refine, final int hits) {
		return Math.min(cap(refine), hits * perHit(refine));
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Sculk Cleaver", UniqueJob.FIGHTER, UniqueKind.AXE, List.of(Identifier.parse("minecraft:warden")),
			"+" + Af.percent(TUNING.perHit()) + " damage per hit you took in the last " + Af.seconds(TUNING.windowTicks()) + ", max +" + Af.percent(TUNING.cap()),
			"+" + Af.percent(TUNING.perHitPlus7()) + " per hit",
			"At max, hits ignore armor",
			"Max +" + Af.percent(TUNING.capPlus12()),
			"A kill at max deals " + Af.percent(TUNING.burstShare()) + " of the hit to foes within " + (int) TUNING.burstRadius(), null, EFFECT));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TAKEN.clear();
			ARMOR_LOSS.clear();
		});
	}
}
