package com.stella.rpg;

import java.util.Locale;
import java.util.Optional;

public enum Stat {
	STR, AGI, VIT, DEX, INT, LUK;

	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Optional<Stat> byKey(final String key) {
		for (Stat stat : values()) {
			if (stat.key().equalsIgnoreCase(key)) {
				return Optional.of(stat);
			}
		}
		return Optional.empty();
	}
}
