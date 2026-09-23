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
			public void powerLevel(
				final String player,
				final int level,
				final int from,
				final long survivabilityPoints,
				final double combatPoints
			) {
				add(
					"[BEHAVIOR] power level player=%s level=%d from=%d survivabilityPoints=%d combatPoints=%.2f",
					player, level, from, survivabilityPoints, combatPoints
				);
			}

			@Override
			public void powerKill(
				final String mob,
				final boolean boss,
				final int mobLevel,
				final int playerLevel,
				final double multiplier,
				final double points,
				final double totalCombatPoints
			) {
				add(
					"[BEHAVIOR] power kill mob=%s boss=%s mobLevel=%d playerLevel=%d multiplier=%.3f points=%.3f totalCombatPoints=%.3f",
					mob, boss, mobLevel, playerLevel, multiplier, points, totalCombatPoints
				);
			}

			@Override
			public void powerHeartbeat(final int tick, final int players) {
				add("[BEHAVIOR] power heartbeat tick=%d players=%d", tick, players);
			}

			@Override
			public void readoutSpawn(
				final String player,
				final String mob,
				final String mobName,
				final int entityId,
				final int mobLevel,
				final int playerLevel,
				final int gap,
				final String band,
				final String color,
				final String text
			) {
				add(
					"[BEHAVIOR] readout spawn player=%s mob=%s name=%s entityId=%d mobLevel=%d playerLevel=%d gap=%d band=%s color=%s text=%s",
					player, mob, mobName, entityId, mobLevel, playerLevel, gap, band, color, text
				);
			}

			@Override
			public void readoutBand(
				final String player,
				final String mob,
				final int entityId,
				final int gap,
				final String bandFrom,
				final String bandTo,
				final int mobLevel,
				final int playerLevel,
				final String text
			) {
				add(
					"[BEHAVIOR] readout band player=%s mob=%s entityId=%d gap=%d bandFrom=%s bandTo=%s mobLevel=%d playerLevel=%d text=%s",
					player, mob, entityId, gap, bandFrom, bandTo, mobLevel, playerLevel, text
				);
			}

			@Override
			public void readoutRemove(
				final String player,
				final String mob,
				final int entityId,
				final String reason,
				final int remaining
			) {
				add(
					"[BEHAVIOR] readout remove player=%s mob=%s entityId=%d reason=%s remaining=%d",
					player, mob, entityId, reason, remaining
				);
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
