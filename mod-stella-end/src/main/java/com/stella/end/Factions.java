package com.stella.end;

import net.minecraft.world.entity.Entity;

/**
 * Which creatures belong to the castle. Every mob this mod makes carries a tag that starts with {@link #PREFIX}: the
 * floor mobs, the Sovereign and its undead blazes, the dragon, and the wyverns, riders and summons on its side. The
 * castle never turns on its own; a player, and anything else outside it, stays a valid target.
 */
public final class Factions {
	public static final String PREFIX = "mod-stella-end.";

	private Factions() {
	}

	public static boolean isCastle(final Entity entity) {
		for (String tag : entity.entityTags()) {
			if (tag.startsWith(PREFIX)) {
				return true;
			}
		}
		return false;
	}

	/** Whether both belong to the castle. A mob may not hunt such a creature, nor answer a blow from it. */
	public static boolean sameSide(final Entity one, final Entity other) {
		return isCastle(one) && isCastle(other);
	}
}
