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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** The Gel Mantle. Every number lives in {@link #TUNING}. */
public final class GelMantle {
	public static final String ID = "gel_mantle";
	private static final Map<UUID, Map<UUID, Long>> READY = new ConcurrentHashMap<>();

	public record Tuning(double share, double sharePlus7, int cooldownTicks, int cooldownPlus12, double threshold, double thresholdPlus15,
		int regenTicks, int regenAmplifier, double radius) {
	}

	public static final Tuning TUNING = new Tuning(0.15, 0.20, 600, 400, 0.30, 0.50, 60, 1, 8.0);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public void everySecond(final UniqueContext c) {
			ServerPlayer holder = c.holder();
			long now = holder.level().getServer().getTickCount();
			Map<UUID, Long> ready = READY.computeIfAbsent(holder.getUUID(), ignored -> new ConcurrentHashMap<>());
			double threshold = c.atLeast(Uniques.PLUS_15) ? TUNING.thresholdPlus15() : TUNING.threshold();
			double share = c.atLeast(Uniques.PLUS_7) ? TUNING.sharePlus7() : TUNING.share();
			int cooldown = c.atLeast(Uniques.PLUS_12) ? TUNING.cooldownPlus12() : TUNING.cooldownTicks();
			for (ServerPlayer ally : Priests.allies(holder, TUNING.radius())) {
				if (ally.getHealth() > ally.getMaxHealth() * threshold) {
					continue;
				}
				Long at = ready.get(ally.getUUID());
				if (at != null && now - at < cooldown) {
					continue;
				}
				ready.put(ally.getUUID(), now);
				grant(ally, share);
				if (c.atLeast(Uniques.PLUS_10)) {
					MpKit.buff(ally, MobEffects.REGENERATION, TUNING.regenTicks(), TUNING.regenAmplifier());
				}
			}
		}
	};

	private GelMantle() {
	}

	/** Gives an ally golden hearts, and raises their ceiling so the hearts are not clamped away. */
	public static void grant(final ServerPlayer ally, final double share) {
		float amount = (float) (ally.getMaxHealth() * share);
		AttributeInstance limit = ally.getAttribute(Attributes.MAX_ABSORPTION);
		if (limit != null && limit.getBaseValue() < amount) {
			limit.setBaseValue(amount);
		}
		ally.setAbsorptionAmount(Math.max(ally.getAbsorptionAmount(), amount));
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Gel Mantle", UniqueJob.PRIEST, UniqueKind.CHESTPLATE, List.of(Identifier.parse("mod-stella-mobs:blue_slime")),
			"An ally within " + (int) TUNING.radius() + " that drops below " + MpKit.percent(TUNING.threshold())
				+ " health gets Absorption " + MpKit.percent(TUNING.share()) + " of max health. Cooldown " + MpKit.seconds(TUNING.cooldownTicks()) + " per ally",
			MpKit.percent(TUNING.sharePlus7()),
			"The ally also gets Regeneration II, " + MpKit.seconds(TUNING.regenTicks()),
			"Cooldown " + MpKit.seconds(TUNING.cooldownPlus12()),
			"Threshold " + MpKit.percent(TUNING.thresholdPlus15()),
			EquipmentAssets.DIAMOND, EFFECT));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> READY.clear());
	}
}
