package com.stella.equips.enchant;

import java.util.Set;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/** Who an aura or a wave strikes: a hostile, or anything hunting the wearer, but never a friend of the village. */
public final class Foes {
	/**
	 * Tags the other mods give their friendly hostiles: village patrols, wild patrol pairs, the village witch, and a
	 * skeleton a player summoned.
	 */
	public static final Set<String> FRIENDLY_TAGS = Set.of(
		"mod-stella-mobs.friendly_patrol", "mc-stella-villages.patrol", "mc-stella-villages.village_witch",
		"mod-stella-spells.summoned"
	);

	private Foes() {
	}

	public static boolean isFoe(final LivingEntity wearer, final LivingEntity other) {
		if (other == wearer || !other.isAlive() || other instanceof Player) {
			return false;
		}
		if (other instanceof Mob mob && mob.getTarget() == wearer) {
			return true;
		}
		if (!(other instanceof Enemy)) {
			return false;
		}
		for (String tag : other.entityTags()) {
			if (FRIENDLY_TAGS.contains(tag)) {
				return false;
			}
		}
		return true;
	}
}
