package com.stella.rpg.job;

import java.util.Locale;

/** The innate skill of a job. A job grants one innate. It never expires, and death does not remove it. */
public enum Innate {
	NONE,
	FALCON_GUARD,
	DOUBLE_STRIKE,
	ARCANE_WARD,
	SANCTUARY;

	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}
}
