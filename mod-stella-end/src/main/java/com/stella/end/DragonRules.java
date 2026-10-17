package com.stella.end;

import java.util.Set;

/** Every number of the dragon fight, and the pure rules that turn the live crystal buffs into them. */
public final class DragonRules {
	/** The dragon stays within this many blocks of the End origin. */
	public static final double LEASH = 48.0;
	public static final int ATTACK_PERIOD = 100;
	/** An awake dragon turns passive again once the roof has been empty this long. */
	public static final long CALM_TICKS = 200L;

	public static final float SWOOP_DAMAGE = 30.0F;
	public static final int SWOOP_SLOW_TICKS = 100;
	public static final int SWOOP_SLOW_AMPLIFIER = 1;
	public static final int SWOOP_SLOW_AMPLIFIER_BUFFED = 3;
	public static final int BARRAGE_SHOTS = 12;
	public static final int BARRAGE_SHOTS_BUFFED = 16;
	public static final int BARRAGE_GAP = 4;
	public static final double DIVE_RADIUS = 8.0;
	public static final double DIVE_LIFT = 1.6;
	public static final int DIVE_CHARGES = 8;

	public static final float ROOST_HEALTH = 0.5F;
	public static final float ROOST_DAMAGE = 0.25F;
	public static final long ROOST_TICKS = 2400L;
	public static final int ROOST_SUMMON_PERIOD = 100;
	public static final int ROOST_SKELETONS = 2;
	public static final int ROOST_ARCHERS = 2;
	public static final int ROOST_CAP = 12;

	public static final int WYVERNS = 4;
	public static final long WYVERN_RESPAWN = 600L;
	public static final long WYVERN_RESPAWN_BUFFED = 300L;
	public static final double WYVERN_SCALE = 3.0;

	public static final long REVIVE_TICKS = 144000L;
	public static final int XP_LEVEL = 120;
	public static final int SCALES = 4;

	/** The crystal buffs, numbered in vanilla spike order. */
	public static final int REGENERATION = 1;
	public static final int DAMAGE = 2;
	public static final int TOUGHNESS = 3;
	public static final int SPEED = 4;
	public static final int BARRAGE = 5;
	public static final int KNOCKBACK = 6;
	public static final int WYVERN = 7;
	public static final int SLOWNESS = 8;
	public static final int DIVE_FIRE = 9;
	public static final int ABSORPTION = 10;
	public static final int BUFFS = 10;

	public static final float DAMAGE_BUFF = 1.2F;
	public static final float TOUGHNESS_BUFF = 0.85F;
	public static final double SPEED_BUFF = 1.2;
	/** Regeneration heals this share of the dragon's health each second. */
	public static final float REGEN_PER_SECOND = 0.0025F;
	public static final int REGEN_PERIOD = 20;
	public static final float ABSORPTION_BUFF = 40.0F;

	private DragonRules() {
	}

	public static float damageMultiplier(final Set<Integer> buffs) {
		return buffs.contains(DAMAGE) ? DAMAGE_BUFF : 1.0F;
	}

	public static float damageTakenMultiplier(final Set<Integer> buffs) {
		return buffs.contains(TOUGHNESS) ? TOUGHNESS_BUFF : 1.0F;
	}

	public static double speedMultiplier(final Set<Integer> buffs) {
		return buffs.contains(SPEED) ? SPEED_BUFF : 1.0;
	}

	public static int barrageShots(final Set<Integer> buffs) {
		return buffs.contains(BARRAGE) ? BARRAGE_SHOTS_BUFFED : BARRAGE_SHOTS;
	}

	public static double knockbackResistance(final Set<Integer> buffs) {
		return buffs.contains(KNOCKBACK) ? 1.0 : 0.0;
	}

	public static long wyvernRespawn(final Set<Integer> buffs) {
		return buffs.contains(WYVERN) ? WYVERN_RESPAWN_BUFFED : WYVERN_RESPAWN;
	}

	public static int swoopSlownessAmplifier(final Set<Integer> buffs) {
		return buffs.contains(SLOWNESS) ? SWOOP_SLOW_AMPLIFIER_BUFFED : SWOOP_SLOW_AMPLIFIER;
	}

	public static boolean divesThrowFire(final Set<Integer> buffs) {
		return buffs.contains(DIVE_FIRE);
	}

	public static float absorption(final Set<Integer> buffs) {
		return buffs.contains(ABSORPTION) ? ABSORPTION_BUFF : 0.0F;
	}

	public static float regenPerSecond(final Set<Integer> buffs) {
		return buffs.contains(REGENERATION) ? REGEN_PER_SECOND : 0.0F;
	}
}
