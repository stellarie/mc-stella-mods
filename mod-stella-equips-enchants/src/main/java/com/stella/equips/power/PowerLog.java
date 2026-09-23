package com.stella.equips.power;

public final class PowerLog {
	public interface Sink {
		void line(String format, Object... args);
	}

	private static final Sink SILENT = (format, args) -> {
	};
	private static volatile Sink sink = SILENT;

	private PowerLog() {
	}

	public static void setSink(final Sink newSink) {
		sink = newSink == null ? SILENT : newSink;
	}

	public static void line(final String format, final Object... args) {
		sink.line(format, args);
	}
}
