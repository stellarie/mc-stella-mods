package com.stella.learn;

import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * The tick the profile decays against: the Overworld's game time, which the world saves, plus an offset a test
 * moves. The game test level's own clock barely advances, so a test steps the offset instead.
 */
public final class LearnClock {
	private static volatile @Nullable MinecraftServer server;
	private static volatile long offset;

	private LearnClock() {
	}

	public static void bind(final @Nullable MinecraftServer value) {
		server = value;
	}

	public static long now() {
		MinecraftServer bound = server;
		return (bound == null ? 0L : bound.overworld().getGameTime()) + offset;
	}

	public static void advance(final long ticks) {
		offset += ticks;
	}

	public static void reset() {
		offset = 0L;
	}
}
