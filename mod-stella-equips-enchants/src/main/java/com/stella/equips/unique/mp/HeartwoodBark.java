package com.stella.equips.unique.mp;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** The Heartwood Bark. Every number lives in {@link #TUNING}. */
public final class HeartwoodBark {
	public static final String ID = "heartwood_bark";
	/** How far a holder may drift and still count as standing still. */
	private static final double STILL_EPSILON = 0.01;
	private static final Map<UUID, Still> STILL = new ConcurrentHashMap<>();

	/** Where a holder stood, and the tick it arrived there. */
	private record Still(double x, double y, double z, long since) {
	}

	public record Tuning(int baseTicks, int plus7Ticks, int plus12Ticks, int resistTicks, int regenTicks, int slowTicks, int slowAmplifier) {
	}

	public static final Tuning TUNING = new Tuning(40, 30, 20, 60, 60, 40, 2);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public void everySecond(final UniqueContext c) {
			ServerPlayer holder = c.holder();
			long now = holder.level().getServer().getTickCount();
			Still last = STILL.get(holder.getUUID());
			boolean same = last != null && Math.abs(last.x() - holder.getX()) < STILL_EPSILON
				&& Math.abs(last.y() - holder.getY()) < STILL_EPSILON && Math.abs(last.z() - holder.getZ()) < STILL_EPSILON;
			STILL.put(holder.getUUID(), new Still(holder.getX(), holder.getY(), holder.getZ(), same ? last.since() : now));
			if (!still(holder)) {
				return;
			}
			MpKit.buff(holder, MobEffects.RESISTANCE, TUNING.resistTicks(), 0);
			if (c.atLeast(Uniques.PLUS_10)) {
				MpKit.buff(holder, MobEffects.REGENERATION, TUNING.regenTicks(), 0);
			}
		}

		@Override
		public void afterIncoming(final UniqueContext c, final DamageSource s, final float taken) {
			LivingEntity attacker = s.getEntity() instanceof LivingEntity living ? living : null;
			if (attacker == null || attacker == c.holder() || !c.atLeast(Uniques.PLUS_15) || !still(c.holder())) {
				return;
			}
			MpKit.buff(attacker, MobEffects.SLOWNESS, TUNING.slowTicks(), TUNING.slowAmplifier());
		}
	};

	private HeartwoodBark() {
	}

	/** How long a holder must stand still at the given refine. */
	public static int stillTicks(final int refine) {
		if (refine >= Uniques.PLUS_12) {
			return TUNING.plus12Ticks();
		}
		return refine >= Uniques.PLUS_7 ? TUNING.plus7Ticks() : TUNING.baseTicks();
	}

	/** True while the holder has stood in one place for long enough. */
	public static boolean still(final ServerPlayer holder) {
		Still last = STILL.get(holder.getUUID());
		if (last == null) {
			return false;
		}
		return holder.level().getServer().getTickCount() - last.since() >= TUNING.baseTicks();
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Heartwood Bark", UniqueJob.PRIEST, UniqueKind.LEGGINGS, List.of(Identifier.parse("minecraft:creaking")),
			"After " + MpKit.seconds(TUNING.baseTicks()) + " without moving, you get Resistance I while you stay still",
			MpKit.seconds(TUNING.plus7Ticks()),
			"While still, you also get Regeneration I",
			MpKit.seconds(TUNING.plus12Ticks()),
			"Attackers that hit you while you are still get Slowness III, " + MpKit.seconds(TUNING.slowTicks()),
			EquipmentAssets.DIAMOND, EFFECT));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> STILL.clear());
	}
}
