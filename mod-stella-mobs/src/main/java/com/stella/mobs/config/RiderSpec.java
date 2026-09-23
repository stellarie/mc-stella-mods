package com.stella.mobs.config;

import org.jspecify.annotations.Nullable;

public record RiderSpec(double chance) {
	public static final double DEFAULT_CHANCE = 0.35;
	public static final RiderSpec DEFAULT = new RiderSpec(DEFAULT_CHANCE);

	public RiderSpec overriddenBy(final @Nullable Double override) {
		return new RiderSpec(override != null ? override : this.chance);
	}
}
