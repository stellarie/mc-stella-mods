package com.stella.spells;

/**
 * The tick counts and radii that mob casts and mob imbues use. They are literals, frozen at the values the
 * spells had before the RPG duration change, so a player's 30-minute buff never reaches a mob.
 */
final class MobSpellTimings {
	static final int SPARK_BURN_TICKS = 40;
	static final int ICE_LANCE_FREEZE_TICKS = 140;
	static final int VENOM_TICKS = 100;
	static final int HEX_TICKS_PER_FOE = 60;
	static final int HEX_MAX_TICKS = 300;
	static final int GRAVITY_TICKS = 160;
	static final int FLASH_BLIND_TICKS = 100;
	static final int FLASH_GLOW_TICKS = 200;
	static final int EMBER_BURN_TICKS = 200;
	static final int QUICKSILVER_TICKS = 400;
	static final int BERSERK_TICKS = 200;
	static final int VEIL_TICKS = 600;
	static final int FROST_NOVA_SLOW_TICKS = 60;
	static final int PLAGUE_POISON_TICKS = 100;
	static final int FLAME_RING_BURN_TICKS = 60;
	static final int BULWARK_TICKS = 400;
	static final int TAILWIND_TICKS = 600;
	static final int BATTLE_HYMN_TICKS = 400;
	static final double FLAME_RING_RADIUS = 3.0;

	private MobSpellTimings() {
	}
}
