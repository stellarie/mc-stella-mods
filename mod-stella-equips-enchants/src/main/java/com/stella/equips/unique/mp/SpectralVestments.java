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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** The Spectral Vestments. Every number lives in {@link #TUNING}. */
public final class SpectralVestments {
	public static final String ID = "spectral_vestments";
	private static final Map<UUID, Long> READY = new ConcurrentHashMap<>();

	public record Tuning(double threshold, int cooldown, int cooldownPlus7, int cooldownPlus12, int effectTicks, int resistAmplifier, double radius) {
	}

	public static final Tuning TUNING = new Tuning(0.30, 2400, 1800, 1200, 60, 1, 8.0);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public void afterIncoming(final UniqueContext c, final DamageSource s, final float taken) {
			ServerPlayer holder = c.holder();
			if (holder.getHealth() > holder.getMaxHealth() * TUNING.threshold()) {
				return;
			}
			long now = holder.level().getServer().getTickCount();
			Long at = READY.get(holder.getUUID());
			if (at != null && now - at < cooldown(c.refine())) {
				return;
			}
			READY.put(holder.getUUID(), now);
			veil(holder);
			if (c.atLeast(Uniques.PLUS_10)) {
				clearTargets(holder);
			}
			if (c.atLeast(Uniques.PLUS_15)) {
				for (ServerPlayer ally : Priests.allies(holder, TUNING.radius())) {
					if (ally != holder) {
						veil(ally);
					}
				}
			}
		}
	};

	private SpectralVestments() {
	}

	/** The rest between two veils at the given refine. */
	public static int cooldown(final int refine) {
		if (refine >= Uniques.PLUS_12) {
			return TUNING.cooldownPlus12();
		}
		return refine >= Uniques.PLUS_7 ? TUNING.cooldownPlus7() : TUNING.cooldown();
	}

	private static void veil(final ServerPlayer target) {
		MpKit.buff(target, MobEffects.INVISIBILITY, TUNING.effectTicks(), 0);
		MpKit.buff(target, MobEffects.RESISTANCE, TUNING.effectTicks(), TUNING.resistAmplifier());
	}

	/** Drops the target of every mob that was hunting the holder. Returns the count released. */
	public static int clearTargets(final ServerPlayer holder) {
		int cleared = 0;
		for (Mob mob : holder.level().getEntitiesOfClass(Mob.class, holder.getBoundingBox().inflate(32.0), mob -> mob.getTarget() == holder)) {
			mob.setTarget(null);
			cleared++;
		}
		return cleared;
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Spectral Vestments", UniqueJob.PRIEST, UniqueKind.LEGGINGS,
			List.of(Identifier.parse("mod-stella-tower:tower_ghost")),
			"A hit that leaves you below " + MpKit.percent(TUNING.threshold()) + " health gives Invisibility and Resistance II, "
				+ MpKit.seconds(TUNING.effectTicks()) + ". Cooldown " + MpKit.seconds(TUNING.cooldown()),
			MpKit.seconds(TUNING.cooldownPlus7()),
			"Mobs that target you lose their target",
			MpKit.seconds(TUNING.cooldownPlus12()),
			"Allies within " + (int) TUNING.radius() + " also get the effects",
			EquipmentAssets.DIAMOND, EFFECT));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> READY.clear());
	}
}
