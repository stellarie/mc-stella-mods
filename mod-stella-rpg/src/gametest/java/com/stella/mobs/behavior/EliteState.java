package com.stella.mobs.behavior;

import net.minecraft.world.entity.Mob;

/** Test stand-in for the mobs mod flags. The real ones are attachments; this reads entity tags. */
public final class EliteState {
	private EliteState() {
	}

	public static boolean hasEliteFlag(final Mob mob) {
		return mob.entityTags().contains("test.elite");
	}

	public static boolean hasBossFlag(final Mob mob) {
		return mob.entityTags().contains("test.boss");
	}
}
