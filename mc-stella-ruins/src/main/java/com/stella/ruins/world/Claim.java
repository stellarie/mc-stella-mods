package com.stella.ruins.world;

/** How a site pays out: once for the life of the world, or again after a delay. */
public final class Claim {
	/** One day, in ticks. */
	public static final long DAY_TICKS = 24000L;

	private final boolean once;
	private final int days;

	private Claim(final boolean once, final int days) {
		this.once = once;
		this.days = days;
	}

	public static Claim once() {
		return new Claim(true, 0);
	}

	public static Claim resetAfterDays(final int days) {
		if (days < 1) {
			throw new IllegalArgumentException("a reset claim needs at least one day");
		}
		return new Claim(false, days);
	}

	public boolean isOnce() {
		return this.once;
	}

	/** The days to a reset when the config names none, or 0 for a once site. */
	public int days() {
		return this.days;
	}

	/** The ticks from completion to rearming, or -1 for a once site. The config's reset_days entry wins. */
	public long resetTicks(final String siteId) {
		if (this.once) {
			return -1L;
		}
		return RuinConfig.get().resetDays().getOrDefault(siteId, this.days) * DAY_TICKS;
	}
}
