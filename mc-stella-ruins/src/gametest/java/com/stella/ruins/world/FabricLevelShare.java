package com.stella.ruins.world;

import net.fabricmc.loader.api.FabricLoader;

/** Swaps an object share for the length of a test, so a level can be pinned without the mobs mod. */
final class FabricLevelShare {
	private FabricLevelShare() {
	}

	/** Puts a value and returns the one it replaced, or null. */
	static Object put(final String key, final Object value) {
		return FabricLoader.getInstance().getObjectShare().put(key, value);
	}

	/** Puts the old value back, or removes the key when there was none. */
	static void restore(final String key, final Object old) {
		if (old == null) {
			FabricLoader.getInstance().getObjectShare().remove(key);
		} else {
			FabricLoader.getInstance().getObjectShare().put(key, old);
		}
	}
}
