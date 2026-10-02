package com.stella.mobs.behavior;

/**
 * The pure decision of the stance. A mob reads two flags and one fact: is the mob low, is the player low, and does the
 * player chase. Each flag has a hysteresis band, so a share that hovers at a line does not flip the stance on every
 * look. The mob's line drops as allies join the hunt, which is its morale.
 *
 * <p>The table of {@link #decide(boolean, boolean, boolean, boolean)}:
 *
 * <ul>
 * <li>A low mob and a high player that chases give RETREAT, and one that does not chase gives KITE.
 * <li>A high mob and a high player give FIGHT, and so do a low mob and a low player.
 * <li>A high mob and a low player give PURSUE.
 * <li>A zombie turns RETREAT and KITE into FIGHT.
 * <li>A target that is not a player gives NORMAL.
 * </ul>
 */
public final class StanceRules {
	public static final double PLAYER_LOW_ENTER = 0.35;
	public static final double PLAYER_LOW_LEAVE = 0.50;
	public static final double HYSTERESIS = 0.15;
	public static final double MORALE_ALONE = 0.45;
	public static final double MORALE_STEP = 0.075;
	public static final double MORALE_FLOOR = 0.15;
	/** Allies are the monsters this close that hunt the same player. */
	public static final double MORALE_RADIUS = 12.0;

	private StanceRules() {
	}

	/** The health share at or below which a mob with this many allies counts as low. */
	public static double mobLowLine(final int allies) {
		return Math.max(MORALE_FLOOR, MORALE_ALONE - MORALE_STEP * Math.max(0, allies));
	}

	/** Low from the line down to the line plus the hysteresis. Between the two, the mob keeps its last answer. */
	public static boolean mobLow(final boolean wasLow, final double share, final int allies) {
		double line = mobLowLine(allies);
		if (share <= line) {
			return true;
		}

		return share < line + HYSTERESIS && wasLow;
	}

	public static boolean playerLow(final boolean wasLow, final double share) {
		if (share <= PLAYER_LOW_ENTER) {
			return true;
		}

		return share < PLAYER_LOW_LEAVE && wasLow;
	}

	public static Stance decide(final boolean mobLow, final boolean playerLow, final boolean chasing, final boolean zombie) {
		if (playerLow) {
			return mobLow ? Stance.FIGHT : Stance.PURSUE;
		}

		if (!mobLow || zombie) {
			return Stance.FIGHT;
		}

		return chasing ? Stance.RETREAT : Stance.KITE;
	}

	public static Stance decide(
		final boolean playerTarget, final boolean mobLow, final boolean playerLow, final boolean chasing, final boolean zombie
	) {
		return playerTarget ? decide(mobLow, playerLow, chasing, zombie) : Stance.NORMAL;
	}
}
