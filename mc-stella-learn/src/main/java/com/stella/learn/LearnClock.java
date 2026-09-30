package com.stella.learn;

import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * The tick the profile decays against: the Overworld's game time, which the world saves. Game tests run in
 * parallel on one server, so a test that needs elapsed time ages a profile's stamps instead of moving this clock.
 */
public final class LearnClock {
	private static volatile @Nullable MinecraftServer server;

	private LearnClock() {
	}

	public static void bind(final @Nullable MinecraftServer value) {
		server = value;
	}

	public static long now() {
		MinecraftServer bound = server;
		return bound == null ? 0L : bound.overworld().getGameTime();
	}
}
