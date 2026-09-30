package com.stella.mobs.config;

import org.jspecify.annotations.Nullable;

public enum CombatRole {
	MELEE,
	RANGED,
	MAGIC;

	public static @Nullable CombatRole byName(final String name) {
		for (CombatRole role : values()) {
			if (role.name().equalsIgnoreCase(name)) {
				return role;
			}
		}

		return null;
	}
}
