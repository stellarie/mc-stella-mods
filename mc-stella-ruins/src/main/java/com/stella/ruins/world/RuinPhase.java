package com.stella.ruins.world;

import java.util.Locale;

/** Where a core is in its life. */
public enum RuinPhase {
	/** Waiting for a player. */
	DORMANT,
	/** The objective runs and the roster lives. */
	ACTIVE,
	/** The objective is done and the vault is open. */
	COMPLETE,
	/** A reset site whose delay ran out while a player still stood in the radius. */
	COOLDOWN;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static RuinPhase byId(final String id) {
		for (RuinPhase phase : values()) {
			if (phase.id().equals(id)) {
				return phase;
			}
		}
		return DORMANT;
	}
}
