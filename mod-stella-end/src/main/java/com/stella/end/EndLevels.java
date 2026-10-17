package com.stella.end;

/** Every level the End assigns. Floor mobs climb with the floor; the bosses and rooftop mobs hold fixed levels. */
public final class EndLevels {
	public static final int FLOOR_BASE = 100;
	public static final int FLOOR_STEP = 3;
	/** The skeleton archers of the floors hold one level on every floor. */
	public static final int ARCHER = 110;
	/** The skeleton horse a castle wither skeleton rides. */
	public static final int MOUNT = 95;
	public static final int SOVEREIGN = 120;
	/** The two Wither Knights that rise with the Sovereign hold its level. */
	public static final int KNIGHT = 120;
	public static final int UNDEAD_BLAZE = 115;
	public static final int DRAGON = 150;
	public static final int WYVERN = 130;
	public static final int ROOFTOP = 120;

	private EndLevels() {
	}

	/** The level of a mob on a floor, 1 to 6. */
	public static int floorLevel(final int floor) {
		return FLOOR_BASE + FLOOR_STEP * (floor - 1);
	}
}
