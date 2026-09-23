package com.stella.mobs.config;

import org.jspecify.annotations.Nullable;

public record RiderSpec(double chance) {
	// T01: half the previous 0.35. A value set in the config file still wins.
	public static final double DEFAULT_CHANCE = 0.175;
	public static final RiderSpec DEFAULT = new RiderSpec(DEFAULT_CHANCE);

	public RiderSpec overriddenBy(final @Nullable Double override) {
		return new RiderSpec(override != null ? override : this.chance);
	}
}
