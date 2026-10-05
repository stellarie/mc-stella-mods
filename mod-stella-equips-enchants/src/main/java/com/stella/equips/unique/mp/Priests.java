package com.stella.equips.unique.mp;

import com.stella.equips.unique.UniqueTargets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Helpers that the priest uniques share: a pulse clock, the ally list, and the removal rules. */
public final class Priests {
	private static final Map<String, Map<UUID, Long>> LAST = new ConcurrentHashMap<>();

	private Priests() {
	}

	/** True once everyTicks for this key and holder. The pulse then runs. */
	public static boolean due(final String key, final ServerPlayer holder, final int everyTicks) {
		Map<UUID, Long> last = LAST.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>());
		long now = holder.level().getServer().getTickCount();
		Long at = last.get(holder.getUUID());
		if (at != null && now - at < everyTicks) {
			return false;
		}
		last.put(holder.getUUID(), now);
		return true;
	}

	/** Living, non-spectator players within the radius of the holder, the holder included. */
	public static List<ServerPlayer> allies(final ServerPlayer holder, final double radius) {
		return UniqueTargets.allies(holder, radius);
	}

	/** Undead, as the vanilla undead tag reads it. */
	public static boolean undead(final Entity entity) {
		return entity.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD);
	}

	/** Removes one harmful effect and returns it, or null when the target carries none. */
	public static MobEffectInstance removeOneHarmful(final LivingEntity target) {
		for (MobEffectInstance effect : List.copyOf(target.getActiveEffects())) {
			if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
				target.removeEffect(effect.getEffect());
				return effect;
			}
		}
		return null;
	}

	/** Drops an effect of the given type, whatever put it there. */
	public static void cleanse(final LivingEntity target, final Holder<MobEffect> effect) {
		if (target.hasEffect(effect)) {
			target.removeEffect(effect);
		}
	}

	/** True when the entity stands in water or in the rain. */
	public static boolean wet(final LivingEntity entity) {
		return entity.isInWaterOrRain();
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST.clear());
	}
}
