package com.stella.ruins.world;

import net.minecraft.server.level.ServerLevel;

/** The tick count that claims and resets read. Tests swap the source, because the gametest clock stands still. */
public final class RuinClock {
	/** One tick count per level. */
	@FunctionalInterface
	public interface Source {
		long now(ServerLevel level);
	}

	private static volatile Source source = ServerLevel::getGameTime;

	private RuinClock() {
	}

	public static long now(final ServerLevel level) {
		return source.now(level);
	}

	/** Replaces the source and returns the old one, so the caller can restore it in a finally block. */
	public static Source replace(final Source replacement) {
		Source old = source;
		source = replacement;
		return old;
	}
}
