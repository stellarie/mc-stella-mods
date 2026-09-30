package com.stella.nether;

/**
 * Where the mod's behaviour lines go: nowhere in the shipped jar, so a real server prints none of them; the game
 * tests switch them on at INFO. Warnings a server's keeper needs go straight to the mod's logger instead.
 */
public final class BehaviorLog {
	public interface Sink {
		void line(String message, Object... args);
	}

	public static final Sink SILENT = (message, args) -> {
	};

	private static volatile Sink sink = SILENT;

	private BehaviorLog() {
	}

	public static void info(final String message, final Object... args) {
		sink.line(message, args);
	}

	/** Sets where lines go, and returns where they went before; null silences them. */
	public static Sink set(final Sink next) {
		Sink previous = sink;
		sink = next == null ? SILENT : next;
		return previous;
	}
}
