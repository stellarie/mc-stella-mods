package com.stella.equips.unique;

import com.stella.equips.enchant.Foes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;

/** Who a unique may hurt and who it may help. */
public final class UniqueTargets {
	/** Tag of a hostile that is harmless, such as a blue slime. */
	public static final String HARMLESS_TAG = "mod-stella-mobs.harmless";

	private UniqueTargets() {
	}

	/**
	 * A foe is an {@link Enemy}, or a mob that targets the holder. Players, the holder's tamed animals, villagers, the
	 * harmless-tagged mobs, and the friendly tagged hostiles of the other mods (such as a summoned skeleton) are never foes.
	 */
	public static boolean isFoe(final ServerPlayer holder, final Entity entity) {
		if (!(entity instanceof LivingEntity living) || entity == holder || !living.isAlive() || entity instanceof Player || entity instanceof AbstractVillager) {
			return false;
		}
		if (entity instanceof OwnableEntity pet && pet.getOwner() != null && pet.getOwner().getUUID().equals(holder.getUUID())) {
			return false;
		}
		for (String tag : entity.entityTags()) {
			if (tag.equals(HARMLESS_TAG) || Foes.FRIENDLY_TAGS.contains(tag)) {
				return false;
			}
		}
		return entity instanceof Enemy || entity instanceof Mob mob && mob.getTarget() == holder;
	}

	/** Living, non-spectator players within the radius of the holder, the holder included. */
	public static List<ServerPlayer> allies(final ServerPlayer holder, final double radius) {
		List<ServerPlayer> allies = new ArrayList<>();
		double reach = radius * radius;
		if (holder.isAlive() && !holder.isSpectator()) {
			allies.add(holder);
		}
		for (ServerPlayer other : holder.level().players()) {
			if (other != holder && other.isAlive() && !other.isSpectator() && other.distanceToSqr(holder) <= reach) {
				allies.add(other);
			}
		}
		return allies;
	}
}
