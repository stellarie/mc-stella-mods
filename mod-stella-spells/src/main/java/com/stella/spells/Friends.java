package com.stella.spells;

import java.util.Set;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Who a spell treats as a foe. A hostile by kind is a foe, unless another mod marks it a friend of the
 * village: a wild patrol pair, a village patrol member, the village witch, a skeleton a player summoned,
 * or a harmless blue slime.
 */
public final class Friends {
	public static final Set<String> FRIEND_TAGS = Set.of(
		"mod-stella-mobs.friendly_patrol", "mc-stella-villages.patrol", "mc-stella-villages.village_witch", SummonSkeleton.SUMMON_TAG,
		"mod-stella-mobs.harmless"
	);

	private Friends() {
	}

	public static boolean isFriend(final Entity entity) {
		for (String tag : entity.entityTags()) {
			if (FRIEND_TAGS.contains(tag)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isFoe(final Entity entity) {
		return entity instanceof Enemy && !isFriend(entity);
	}
}
