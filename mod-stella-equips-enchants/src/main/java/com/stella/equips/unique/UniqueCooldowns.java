package com.stella.equips.unique;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;

/** Named cooldowns per player, counted in server ticks. The game time is never used: it barely moves in a test level. */
public final class UniqueCooldowns {
	private static final Map<UUID, Map<String, Long>> READY_AT = new HashMap<>();

	private UniqueCooldowns() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> READY_AT.clear());
	}

	/** True and restarts the cooldown when it is over. False while it runs. */
	public static boolean ready(final ServerPlayer player, final String key, final int ticks) {
		long now = player.level().getServer().getTickCount();
		Map<String, Long> keys = READY_AT.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
		Long readyAt = keys.get(key);
		if (readyAt != null && now < readyAt) {
			return false;
		}
		keys.put(key, now + ticks);
		return true;
	}
}
