package com.stella.tower;

/**
 * The tower's one growth curve: an S that is flat over the first floors, steepest at floor
 * {@link #MIDPOINT}, and done at floor {@link #END}. Mobs and runes both grow along it; past its end only
 * the per-floor health and damage keep climbing.
 */
public final class TowerCurve {
	public static final double WIDTH = 150.0;
	public static final double MIDPOINT = 500.0;
	public static final double END = 1000.0;
	/** Health grows up to this many times the vanilla mob's, damage up to {@link #DAMAGE_MAX}. */
	public static final double HEALTH_MAX = 100.0;
	public static final double DAMAGE_MAX = 20.0;
	/** What never stops: health and damage added per floor. */
	public static final double HEALTH_PER_FLOOR = 2.0;
	public static final double DAMAGE_PER_FLOOR = 0.02;
	/** One point of armor per this many floors, up to the game's cap. */
	public static final int FLOORS_PER_ARMOR = 5;
	public static final double ARMOR_CAP = 30.0;
	public static final double SPEED_PER_FLOOR = 0.002;
	public static final double SPEED_CAP = 0.5;

	private TowerCurve() {
	}

	private static double sigmoid(final double x) {
		return 1.0 / (1.0 + Math.exp(-x));
	}

	/** 0 at the ground floor, 0.5 at the midpoint, 1 at the end and beyond. */
	public static double s(final int floor) {
		double low = sigmoid(-MIDPOINT / WIDTH);
		double high = sigmoid((END - MIDPOINT) / WIDTH);
		double raw = (sigmoid((floor - MIDPOINT) / WIDTH) - low) / (high - low);
		return Math.max(0.0, Math.min(1.0, raw));
	}

	/** A mob's health on a floor: up to {@link #HEALTH_MAX} times its own, plus a little per floor for ever. */
	public static double health(final double base, final int floor) {
		return base * (1.0 + (HEALTH_MAX - 1.0) * s(floor)) + HEALTH_PER_FLOOR * floor;
	}

	/** A hit's damage on a floor: up to {@link #DAMAGE_MAX} times its own, plus a little per floor for ever. */
	public static double damage(final double base, final int floor) {
		return base * (1.0 + (DAMAGE_MAX - 1.0) * s(floor)) + DAMAGE_PER_FLOOR * floor;
	}

	public static double armorBonus(final int floor) {
		return Math.min(ARMOR_CAP, (double) (floor / FLOORS_PER_ARMOR));
	}

	/** Extra speed as a share of the mob's own. */
	public static double speedBonus(final int floor) {
		return Math.min(SPEED_CAP, SPEED_PER_FLOOR * floor);
	}
}
