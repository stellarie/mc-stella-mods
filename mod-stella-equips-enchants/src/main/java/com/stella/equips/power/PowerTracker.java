package com.stella.equips.power;

import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class PowerTracker {
	// The last level each live player object was scanned at, only so a change logs once.
	private static final Map<ServerPlayer, Integer> LAST_LEVEL = new WeakHashMap<>();
	private static int serverTicks;
	private static boolean registered;

	private PowerTracker() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(PowerTracker::onServerTick);
		registered = true;
	}

	public static boolean isRegistered() {
		return registered;
	}

	public static int serverTicks() {
		return serverTicks;
	}

	public static void onServerTick(final MinecraftServer server) {
		serverTicks++;
		PowerConfig config = PowerConfigStore.get();
		boolean changed = false;

		if (serverTicks % config.tracker().scanIntervalTicks() == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				changed |= scanPlayer(player, config);
			}
		}

		if (!changed && serverTicks % config.tracker().heartbeatTicks() == 0) {
			PowerLog.recorder().powerHeartbeat(serverTicks, server.getPlayerList().getPlayers().size());
		}
	}

	public static boolean scanPlayer(final ServerPlayer player, final PowerConfig config) {
		int level = playerLevel(player, config);
		PowerAttributes.apply(player, level, config);
		Integer previous;
		synchronized (LAST_LEVEL) {
			previous = LAST_LEVEL.put(player, level);
		}

		int from = previous == null ? 0 : previous;
		if (level == from) {
			return false;
		}

		PowerLog.recorder().powerLevel(player.getScoreboardName(), level, from);
		return true;
	}

	/** The vanilla experience level, capped, is the only input to player scaling. */
	public static int playerLevel(final ServerPlayer player, final PowerConfig config) {
		return Math.max(0, Math.min(config.levelCap(), player.experienceLevel));
	}
}
