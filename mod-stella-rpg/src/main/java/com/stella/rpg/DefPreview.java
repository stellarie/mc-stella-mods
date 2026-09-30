package com.stella.rpg;

/** The share of a small hit that armor and VIT remove together, as the stats screen shows it. */
public final class DefPreview {
	/** Vanilla armor removes at most 20 points of 25 from a small hit. */
	public static final double MAX_ARMOR = 20.0;
	public static final double ARMOR_DIVIDER = 25.0;

	private DefPreview() {
	}

	public static double armorShare(final double armor) {
		return Math.max(0.0, Math.min(MAX_ARMOR, armor)) / ARMOR_DIVIDER;
	}

	/** {@code 1 - (1 - armorShare) * (1 - vitShare)}. */
	public static double combined(final double armor, final double vitShare) {
		return 1.0 - (1.0 - armorShare(armor)) * (1.0 - vitShare);
	}
}
