package com.stella.tower;

/**
 * The traps a floor is laid with, by the tens of its number, starting over every hundred floors: the first
 * ten floors of each hundred have none, the last ten a mix of every kind.
 */
public enum TrapTheme {
	NONE,
	DESERT,
	INFERNO,
	VOID,
	FROST,
	BOG,
	STORM,
	SHADOW,
	SPIKES,
	CHAOS;

	public static final int FLOORS_PER_THEME = 10;

	/** The theme of a floor. */
	public static TrapTheme of(final int floor) {
		if (floor < 1) {
			return NONE;
		}
		return values()[((floor - 1) / FLOORS_PER_THEME) % values().length];
	}

	/** Whether a trap of this theme can be laid: every theme but none and the mix. */
	public boolean laid() {
		return this != NONE && this != CHAOS;
	}
}
