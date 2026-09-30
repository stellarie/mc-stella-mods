package com.stella.equips.readout;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/** Which entities lose their vanilla floating name tag on the client. */
public final class ReadoutNames {
	private ReadoutNames() {
	}

	/** The plate names the mob, so a mob keeps no vanilla tag, name-tagged pets included. Players keep theirs. */
	public static boolean hidesVanillaTag(final Entity entity) {
		return entity instanceof Mob;
	}
}
