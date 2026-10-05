package com.stella.equips.unique;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * A passive attribute of a held unique. The unique refreshes it every second, and one sweep removes the attribute of
 * every holder that stopped refreshing it, so an item that leaves the hand takes its attribute with it.
 */
public final class UniquePassives {
	private static final int GRACE_TICKS = 40;
	private static final String NAMESPACE = "mod-stella-equips-enchants";
	private static final Map<String, Map<UUID, Long>> SEEN = new ConcurrentHashMap<>();
	private static final Map<String, Boolean> SWEEPING = new ConcurrentHashMap<>();

	private UniquePassives() {
	}

	/** Keeps the attribute on the holder while the unique stays held. */
	public static void hold(final ServerPlayer holder, final String key, final Holder<Attribute> attribute, final double amount) {
		AttributeInstance instance = holder.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		instance.addOrReplacePermanentModifier(new AttributeModifier(id(key), amount, AttributeModifier.Operation.ADD_VALUE));
		Map<UUID, Long> seen = SEEN.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>());
		seen.put(holder.getUUID(), (long) holder.level().getServer().getTickCount());
		if (SWEEPING.putIfAbsent(key, Boolean.TRUE) == null) {
			sweep(holder.level().getServer(), key, attribute);
		}
	}

	private static void sweep(final MinecraftServer server, final String key, final Holder<Attribute> attribute) {
		UniqueScheduler.after(server, GRACE_TICKS, () -> {
			SWEEPING.remove(key);
			Map<UUID, Long> seen = SEEN.get(key);
			if (seen == null) {
				return;
			}
			long now = server.getTickCount();
			for (Map.Entry<UUID, Long> entry : Map.copyOf(seen).entrySet()) {
				if (now - entry.getValue() < GRACE_TICKS) {
					continue;
				}
				seen.remove(entry.getKey());
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				AttributeInstance instance = player == null ? null : player.getAttribute(attribute);
				if (instance != null) {
					instance.removeModifier(id(key));
				}
			}
			if (!seen.isEmpty()) {
				SWEEPING.put(key, Boolean.TRUE);
				sweep(server, key, attribute);
			}
		});
	}

	private static Identifier id(final String key) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, "hold_" + key);
	}
}
