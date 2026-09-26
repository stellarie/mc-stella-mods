package com.stella.equips.power;

/**
 * The behaviour log seam. The recorder that names and formats a line lives in the gametest
 * source set, so the shipped jar carries no behaviour string.
 */
public final class PowerLog {
	public interface Recorder {
		default void powerLevel(final String player, final int level, final int from) {
		}

		default void powerHeartbeat(final int tick, final int players) {
		}

		default void configProblem(final String message) {
		}

		default void readoutSpawn(
			final String player,
			final String mob,
			final String mobName,
			final int entityId,
			final int threat,
			final int playerLevel,
			final int gap,
			final String band,
			final String color,
			final String text
		) {
		}

		default void readoutBand(
			final String player,
			final String mob,
			final int entityId,
			final int gap,
			final String bandFrom,
			final String bandTo,
			final int threat,
			final int playerLevel,
			final String text
		) {
		}

		default void readoutHostility(
			final String player,
			final String mob,
			final int entityId,
			final boolean from,
			final boolean to
		) {
		}

		default void readoutRemove(
			final String player,
			final String mob,
			final int entityId,
			final String reason,
			final int remaining
		) {
		}
	}

	private static final Recorder SILENT = new Recorder() {
	};
	private static volatile Recorder recorder = SILENT;

	private PowerLog() {
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
	}

	public static Recorder recorder() {
		return recorder;
	}
}
