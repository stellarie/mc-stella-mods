package com.stella.learn;

/** One thing a mob can do in a situation. Which are open to a mob depends on the situation and on what it carries. */
public enum Tactic {
	/** Raise the shield. */
	SHIELD,
	/** Step away from the player. */
	BACK_OFF,
	/** Stand and fight on. */
	TRADE,
	/** Sidestep the arrow. */
	EVADE,
	/** Take the arrow and carry on. */
	TANK,
	/** Go to cover after a shot. */
	HIDE,
	/** Stay where it is and keep shooting. */
	HOLD
}
