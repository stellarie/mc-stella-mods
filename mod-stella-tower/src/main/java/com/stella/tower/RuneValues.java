package com.stella.tower;

/**
 * What each rune effect comes to for a stat, in percent unless the name says seconds. An armor value takes
 * the worn share (a quarter per piece, 1.0 for a full set) and applies its cap to the sum.
 */
public final class RuneValues {
	public static final double FREEZE_CAP = 40.0;
	public static final double DODGE_CAP = 30.0;
	public static final double RESIST_CAP = 30.0;
	public static final double UNDEAD_CAP = 60.0;
	public static final double HEAL_UNDEAD_FACTOR = 2.0;

	private RuneValues() {
	}

	public static double fireCombust(final int str) {
		return str * 2.0;
	}

	public static double fireThorns(final int vit, final double share) {
		return vit / 10.0 * share;
	}

	public static double iceBonus(final int intel) {
		return intel / 4.0;
	}

	public static double iceFreeze(final int agi, final double share) {
		return Math.min(FREEZE_CAP, agi / 10.0 * share);
	}

	public static double waterSoak(final int luk) {
		return luk / 3.0;
	}

	public static double waterRegen(final int vit, final double share) {
		return vit / 20.0 * share;
	}

	public static double lightningChain(final int dex) {
		return dex / 2.0;
	}

	public static double lightningCharge(final int dex, final double share) {
		return dex / 5.0 * share;
	}

	public static double windAirborne(final int agi) {
		return agi / 3.0;
	}

	public static double windDodge(final int agi, final double share) {
		return Math.min(DODGE_CAP, agi / 20.0 * share);
	}

	public static double voidShredCap(final int intel) {
		return intel / 5.0;
	}

	public static double voidResist(final int intel, final double share) {
		return Math.min(RESIST_CAP, intel / 20.0 * share);
	}

	public static double holyHeal(final int vit) {
		return vit / 10.0;
	}

	public static double holyUndead(final int vit, final double share) {
		return Math.min(UNDEAD_CAP, vit / 5.0 * share);
	}

	public static double darkLowHealth(final int str) {
		return str / 5.0;
	}

	public static double darkWeakSeconds(final int luk, final double share) {
		return Math.max(1.0, luk / 20.0 * share);
	}

	/** A number as the tooltip shows it: one decimal at most. */
	public static String show(final double value) {
		String text = String.format(java.util.Locale.ROOT, "%.1f", value);
		return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
	}
}
