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
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** The Mummy Wraps. Every number lives in {@link #TUNING}. */
public final class MummyWraps {
	public static final String ID = "mummy_wraps";
	private static final Map<UUID, Map<Holder<MobEffect>, Integer>> SEEN = new ConcurrentHashMap<>();

	public record Tuning(double shorter, double shorterPlus7, double shorterPlus12, double radius) {
	}

	public static final Tuning TUNING = new Tuning(0.25, 0.35, 0.45, 8.0);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public int harmfulDuration(final UniqueContext c, final MobEffectInstance effect, final int ticks) {
			return (int) (ticks * (1.0 - shorter(c.refine())));
		}

		@Override
		public void everySecond(final UniqueContext c) {
			if (c.atLeast(Uniques.PLUS_10)) {
				Priests.cleanse(c.holder(), MobEffects.HUNGER);
			}
			if (!c.atLeast(Uniques.PLUS_15)) {
				return;
			}
			for (ServerPlayer ally : Priests.allies(c.holder(), TUNING.radius())) {
				if (ally != c.holder()) {
					trim(ally, shorter(c.refine()));
				}
			}
		}
	};

	private MummyWraps() {
	}

	/** The share a harmful effect loses at the given refine. */
	public static double shorter(final int refine) {
		if (refine >= Uniques.PLUS_12) {
			return TUNING.shorterPlus12();
		}
		return refine >= Uniques.PLUS_7 ? TUNING.shorterPlus7() : TUNING.shorter();
	}

	/** Trims a fresh harmful effect on an ally once, so an effect already running out is left alone. */
	public static int trim(final ServerPlayer ally, final double shorter) {
		Map<Holder<MobEffect>, Integer> seen = SEEN.computeIfAbsent(ally.getUUID(), ignored -> new ConcurrentHashMap<>());
		int trimmed = 0;
		for (MobEffectInstance effect : List.copyOf(ally.getActiveEffects())) {
			if (effect.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) {
				continue;
			}
			Integer last = seen.get(effect.getEffect());
			int now = effect.getDuration();
			if (last != null && now < last) {
				seen.put(effect.getEffect(), now);
				continue;
			}
			int shorterTicks = (int) (now * (1.0 - shorter));
			seen.put(effect.getEffect(), shorterTicks);
			ally.removeEffect(effect.getEffect());
			ally.addEffect(new MobEffectInstance(effect.getEffect(), shorterTicks, effect.getAmplifier(),
				effect.isAmbient(), effect.isVisible(), effect.showIcon()));
			trimmed++;
		}
		return trimmed;
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Mummy Wraps", UniqueJob.PRIEST, UniqueKind.CHESTPLATE, List.of(Identifier.parse("minecraft:husk")),
			"Harmful effects on you last " + MpKit.percent(TUNING.shorter()) + " shorter",
			MpKit.percent(TUNING.shorterPlus7()),
			"Immunity to Hunger",
			MpKit.percent(TUNING.shorterPlus12()),
			"Allies within " + (int) TUNING.radius() + " also get the shorter durations",
			EquipmentAssets.DIAMOND, EFFECT));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SEEN.clear());
	}
}
