package com.stella.learn;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;

/** Writes each player's profile summary on a beat, so a server log shows what mobs have learned and when it changed. */
public final class LearnSummary {
	private LearnSummary() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int beat = LearnConfigStore.get().logSummaryTicks();
			if (beat <= 0 || server.getTickCount() % beat != 0) {
				return;
			}

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				emit(player);
			}
		});
	}

	/** Writes one player's summary line; false, and nothing written, for a player who has learned nothing. */
	public static boolean emit(final ServerPlayer player) {
		if (Profiles.get(player).counters().isEmpty()) {
			return false;
		}

		LearnLog.info("learnProfile", "SUMMARY", ProfileReport.line(player));
		return true;
	}
}
