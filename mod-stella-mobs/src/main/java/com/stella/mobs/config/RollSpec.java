package com.stella.mobs.config;

import org.jspecify.annotations.Nullable;

public record RollSpec(
	double rollChance,
	double dropChanceMultiplier,
	double eliteChance,
	double bossChance,
	double bossHealthMultiplier,
	double bossLevelBonus,
	double warCallBruteChance
) {
	public static final double DEFAULT_ROLL_CHANCE = 0.30;
	public static final double DEFAULT_DROP_CHANCE_MULTIPLIER = 1.0;
	/** The shipped elite rate: about one elite per twenty spawns of a mob that fights. */
	public static final double DEFAULT_ELITE_CHANCE = 0.05;
	/**
	 * B01: the code default keeps bosses off until a config asks for them, so no rig that forgets to pin
	 * the chance can start measuring netherite bosses. The shipped config file sets 0.01 for real play.
	 */
	public static final double DEFAULT_BOSS_CHANCE = 0.0;
	/** B03: twice an elite's health, whose own multiplier is 2.0. */
	public static final double DEFAULT_BOSS_HEALTH_MULTIPLIER = 4.0;
	/** Scales the ten level bonus on health and damage, so one key dials the whole boss level step. */
	public static final double DEFAULT_BOSS_LEVEL_BONUS = 1.0;
	/** R24: one called piglin in a hundred arrives as an elite piglin brute instead. */
	public static final double DEFAULT_WAR_CALL_BRUTE_CHANCE = 0.01;
	public static final RollSpec DEFAULT = new RollSpec(
		DEFAULT_ROLL_CHANCE,
		DEFAULT_DROP_CHANCE_MULTIPLIER,
		DEFAULT_ELITE_CHANCE,
		DEFAULT_BOSS_CHANCE,
		DEFAULT_BOSS_HEALTH_MULTIPLIER,
		DEFAULT_BOSS_LEVEL_BONUS,
		DEFAULT_WAR_CALL_BRUTE_CHANCE
	);

	public RollSpec(final double rollChance, final double dropChanceMultiplier) {
		this(
			rollChance,
			dropChanceMultiplier,
			DEFAULT_ELITE_CHANCE,
			DEFAULT_BOSS_CHANCE,
			DEFAULT_BOSS_HEALTH_MULTIPLIER,
			DEFAULT_BOSS_LEVEL_BONUS,
			DEFAULT_WAR_CALL_BRUTE_CHANCE
		);
	}

	public RollSpec overriddenBy(
		final @Nullable Double chance,
		final @Nullable Double multiplier,
		final @Nullable Double eliteChance,
		final @Nullable Double bossChance,
		final @Nullable Double bossHealthMultiplier,
		final @Nullable Double bossLevelBonus,
		final @Nullable Double warCallBruteChance
	) {
		return new RollSpec(
			chance != null ? chance : this.rollChance,
			multiplier != null ? multiplier : this.dropChanceMultiplier,
			eliteChance != null ? eliteChance : this.eliteChance,
			bossChance != null ? bossChance : this.bossChance,
			bossHealthMultiplier != null ? bossHealthMultiplier : this.bossHealthMultiplier,
			bossLevelBonus != null ? bossLevelBonus : this.bossLevelBonus,
			warCallBruteChance != null ? warCallBruteChance : this.warCallBruteChance
		);
	}
}
