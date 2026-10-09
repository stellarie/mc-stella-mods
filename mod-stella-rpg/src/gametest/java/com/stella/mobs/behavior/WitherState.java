package com.stella.mobs.behavior;

import net.minecraft.world.entity.Mob;

/** Test stand-in for the mobs mod Wither flag. The real one is an attachment; this reads an entity tag. */
public final class WitherState {
	private WitherState() {
	}

	public static boolean hasWitherFlag(final Mob mob) {
		return mob.entityTags().contains("test.wither");
	}
}
