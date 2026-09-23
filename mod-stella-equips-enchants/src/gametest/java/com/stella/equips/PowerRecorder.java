package com.stella.equips;

import com.stella.equips.power.PowerLog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class PowerRecorder {
	private static final List<String> LINES = Collections.synchronizedList(new ArrayList<>());
	private static boolean installed;

	private PowerRecorder() {
	}

	public static synchronized void install() {
		if (installed) {
			return;
		}

		installed = true;
		PowerLog.setSink((format, args) -> {
			String line = String.format(Locale.ROOT, format, args);
			LINES.add(line);
			StellaEquips.LOGGER.info(line);
		});
	}

	public static List<String> matching(final String marker) {
		List<String> found = new ArrayList<>();
		synchronized (LINES) {
			for (String line : LINES) {
				if (line.contains(marker)) {
					found.add(line);
				}
			}
		}

		return found;
	}

	public static int count(final String marker) {
		return matching(marker).size();
	}

	public static String last(final String marker) {
		List<String> found = matching(marker);
		return found.isEmpty() ? "none" : found.get(found.size() - 1);
	}
}
