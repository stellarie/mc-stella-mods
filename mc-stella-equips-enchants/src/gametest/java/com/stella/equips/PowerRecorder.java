package com.stella.equips;

import com.stella.equips.power.PowerLog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Formats and keeps the behaviour lines. The strings live here, in the gametest source set,
 * so the shipped jar and a real server write none of them.
 */
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
		PowerLog.setRecorder(new PowerLog.Recorder() {
			@Override
			public void powerLevel(final String player, final int level, final int from) {
				add("[BEHAVIOR] power level player=%s level=%d from=%d", player, level, from);
			}

			@Override
			public void powerHeartbeat(final int tick, final int players) {
				add("[BEHAVIOR] power heartbeat tick=%d players=%d", tick, players);
			}

			@Override
			public void readoutTarget(
				final String player,
				final String mob,
				final int entityId,
				final int threat,
				final String band,
				final boolean hostile,
				final float health,
				final float absorption,
				final boolean newTarget
			) {
				add(
					"[BEHAVIOR] readout target player=%s mob=%s entityId=%d threat=%d band=%s hostile=%s health=%s absorption=%s newTarget=%s",
					player, mob, entityId, threat, band, hostile, health, absorption, newTarget
				);
			}

			@Override
			public void readoutClear(final String player, final int entityId, final String reason) {
				add("[BEHAVIOR] readout clear player=%s entityId=%d reason=%s", player, entityId, reason);
			}
		});
	}

	private static void add(final String format, final Object... args) {
		String line = String.format(Locale.ROOT, format, args);
		LINES.add(line);
		StellaEquips.LOGGER.info(line);
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
