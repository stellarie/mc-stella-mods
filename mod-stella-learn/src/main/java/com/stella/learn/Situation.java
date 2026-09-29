package com.stella.learn;

/** A moment in a fight when a mob has more than one sensible thing to do. */
public enum Situation {
	/** A player is swinging at a mob within reach of it. */
	SWING,
	/** A player's arrow is on its way to a mob. */
	PROJECTILE,
	/** A ranged mob has just loosed an arrow. */
	AFTER_SHOT
}
