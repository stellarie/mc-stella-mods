package com.stella.end;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The End's own tick counter, saved with the level. A timer of hundreds of ticks or more reads this clock and not the
 * game clock, which a game test level barely advances. A test skips time with {@link #advance}.
 */
public final class EndClock {
	private EndClock() {
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.dimension().equals(Level.END)) {
				advance(level, 1L);
			}
		});
	}

	public static long now(final ServerLevel end) {
		return EndCastles.state(end).clock();
	}

	/** Moves the clock on. The timers that read it see the time pass at their next check. */
	public static void advance(final ServerLevel end, final long ticks) {
		EndCastles.setState(end, EndCastles.state(end).withClock(now(end) + ticks));
	}
}
