package com.stella.mobs.scaling;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;

/**
 * Armor judges a mob's hit as if the hit were the mob's unscaled damage. Mob damage grows by the level multiplier, but
 * vanilla armor counts as about {@code armor - hit / 2} and collapses against a big hit. Dividing the hit by the
 * attacker's multiplier before the armor math, and scaling the result back, keeps armor's share at every level. Armor
 * wear uses the same unscaled hit, so a high-level mob does not shred armor durability.
 */
public final class ArmorNormalizer {
	private ArmorNormalizer() {
	}

	/** The divisor for a hit: the attacker's level multiplier, or 1 for anything that is not a scaled mob. */
	public static double scale(final DamageSource source) {
		if (!(source.getEntity() instanceof Mob mob) || !SelfScaled.levelHits(mob)) {
			return 1.0;
		}
		return Math.max(1.0, SpawnScaling.damageMultiplier(ThreatState.level(mob)));
	}
}
