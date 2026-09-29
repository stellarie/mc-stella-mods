package com.stella.learn;

import net.minecraft.server.level.ServerPlayer;

/** Writes each player's profile summary on a beat. Stub: written after its tests fail. */
public final class LearnSummary {
	private LearnSummary() {
	}

	public static void register() {
	}

	/** Writes one player's summary line; false, and nothing written, for a player who has learned nothing. */
	public static boolean emit(final ServerPlayer player) {
		return false;
	}
}
