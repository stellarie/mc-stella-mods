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

	/** The hit size the flat per-level damage is measured against: a zombie's base hit. */
	public static final double REFERENCE_HIT = 3.0;

	/** The factor on an owned mob's hit, so each attack keeps its size next to the others. */
	public static double factor(final int threatLevel) {
		return SpawnScaling.damageMultiplier(threatLevel) * (1.0 + SpawnScaling.tailFlat(threatLevel) / REFERENCE_HIT);
	}

	/** True for a no-roll mob that scales its own hits. */
	public static boolean owned(final Mob mob) {
		return mob.entityTags().contains(StellaMobs.NO_SPAWN_ROLL_TAG) && mob.entityTags().contains(StellaMobs.SELF_SCALED_TAG);
	}
}
