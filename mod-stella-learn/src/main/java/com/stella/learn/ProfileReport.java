package com.stella.learn;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;

/** What a player's profile holds and what mobs make of it. Stub: written after its tests fail. */
public final class ProfileReport {
	private ProfileReport() {
	}

	/** One line: every counter, and the pressure each one puts on mobs. */
	public static String line(final ServerPlayer player) {
		return "";
	}

	/** The same, one counter to a line, for a player who asks. */
	public static List<String> describe(final ServerPlayer player) {
		return List.of();
	}
}
