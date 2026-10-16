package com.stella.end;

/** Every level the End assigns. Floor mobs climb with the floor; the bosses and rooftop mobs hold fixed levels. */
public final class EndLevels {
	public static final int FLOOR_BASE = 100;
	public static final int FLOOR_STEP = 3;
	public static final int ELITE_WITCH_BONUS = 5;
	public static final int SOVEREIGN = 120;
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

	public static int eliteWitchLevel(final int floor) {
		return floorLevel(floor) + ELITE_WITCH_BONUS;
	}
}
