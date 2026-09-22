package com.stella.mobs.config;

import org.jspecify.annotations.Nullable;

public record RollSpec(double rollChance, double dropChanceMultiplier) {
	public static final double DEFAULT_ROLL_CHANCE = 0.30;
	public static final double DEFAULT_DROP_CHANCE_MULTIPLIER = 1.0;
	public static final RollSpec DEFAULT = new RollSpec(DEFAULT_ROLL_CHANCE, DEFAULT_DROP_CHANCE_MULTIPLIER);

	public RollSpec overriddenBy(final @Nullable Double chance, final @Nullable Double multiplier) {
		return new RollSpec(
			chance != null ? chance : this.rollChance,
			multiplier != null ? multiplier : this.dropChanceMultiplier
		);
	}
}
