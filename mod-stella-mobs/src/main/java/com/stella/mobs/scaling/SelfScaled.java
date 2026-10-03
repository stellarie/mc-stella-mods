package com.stella.mobs.scaling;

import com.stella.mobs.StellaMobs;
import net.minecraft.world.entity.Mob;

/**
 * A mob another mod owns keeps its health and kit when it carries the no-roll tag. With the self-scaled tag as well, its
 * hits still follow its threat level: the damage multiplier, armor normalization, and piercing. Its melee does not read
 * the attack attribute, so the hurt hook scales that melee too.
 */
public final class SelfScaled {
	private SelfScaled() {
	}

	/** True when the mob's hits follow the level path. */
	public static boolean levelHits(final Mob mob) {
		return !mob.entityTags().contains(StellaMobs.NO_SPAWN_ROLL_TAG) || owned(mob);
	}

	/** True for a no-roll mob that scales its own hits. */
	public static boolean owned(final Mob mob) {
		return mob.entityTags().contains(StellaMobs.NO_SPAWN_ROLL_TAG) && mob.entityTags().contains(StellaMobs.SELF_SCALED_TAG);
	}
}
