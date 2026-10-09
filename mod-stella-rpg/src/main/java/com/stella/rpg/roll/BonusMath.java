package com.stella.rpg.roll;

/** Pure arithmetic for stored bonus values. It holds no Minecraft state. */
public final class BonusMath {
	/** One key of one item never stores more than this. */
	public static final int KEY_CAP = 100000;

	private BonusMath() {
	}

	/** The sum of a stored value and a delta, clamped to {@code 0..KEY_CAP}. A long sum cannot overflow. */
	public static int addClamped(final int current, final long delta) {
		long sum = (long) current + delta;
		return (int) Math.max(0L, Math.min(KEY_CAP, sum));
	}
}
