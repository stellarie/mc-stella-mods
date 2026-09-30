package com.stella.learn;

/** One thing a player does that mobs can learn to counter. */
public enum Habit {
	BLOCK,
	MELEE,
	RANGED,
	RETREAT,
	/** A step sideways to the player's left, seen facing the monster. */
	STRAFE_LEFT,
	STRAFE_RIGHT,
	/** A step straight toward the monster. */
	CHARGE,
	/** A retreat straight after a shot: moving away while shooting. */
	KITE,
	/** A melee hit landed while falling from a jump. */
	CRIT
}
