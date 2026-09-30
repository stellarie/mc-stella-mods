package com.stella.mobs;

import net.minecraft.world.entity.Mob;

/** Test stand-in for the mobs mod class that MobsBridge finds by name. */
public final class RpgFacts {
	private RpgFacts() {
	}

	public static int threat(final Mob mob) {
		return 0;
	}

	public static String role(final Mob mob) {
		return "melee";
	}
}
