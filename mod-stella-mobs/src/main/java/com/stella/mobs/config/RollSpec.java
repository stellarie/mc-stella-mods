package com.stella.mobs.config;

import org.jspecify.annotations.Nullable;

public record RollSpec(double rollChance, double dropChanceMultiplier, double eliteChance) {
	public static final double DEFAULT_ROLL_CHANCE = 0.30;
	public static final double DEFAULT_DROP_CHANCE_MULTIPLIER = 1.0;
	/** The shipped elite rate: about one elite per twenty spawns of a mob that fights. */
	public static final double DEFAULT_ELITE_CHANCE = 0.05;
	public static final RollSpec DEFAULT = new RollSpec(
		DEFAULT_ROLL_CHANCE, DEFAULT_DROP_CHANCE_MULTIPLIER, DEFAULT_ELITE_CHANCE
	);

	public RollSpec(final double rollChance, final double dropChanceMultiplier) {
		this(rollChance, dropChanceMultiplier, DEFAULT_ELITE_CHANCE);
	}

	public RollSpec overriddenBy(
		final @Nullable Double chance, final @Nullable Double multiplier, final @Nullable Double eliteChance
	) {
		return new RollSpec(
			chance != null ? chance : this.rollChance,
			multiplier != null ? multiplier : this.dropChanceMultiplier,
			eliteChance != null ? eliteChance : this.eliteChance
		);
	}
}
