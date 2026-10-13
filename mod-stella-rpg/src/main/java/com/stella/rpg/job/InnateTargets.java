package com.stella.rpg.job;

import com.stella.rpg.PercentEffects;
import com.stella.rpg.RpgCombat;
import com.stella.rpg.RpgDamageTypes;
import com.stella.rpg.RpgScale;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * Who a job innate helps, who it hurts, and how it delivers a percent of max health. A sibling mod marks its friends
 * with an entity tag, so this class needs no class of a sibling mod. A friend tag beats the hostile kind.
 */
final class InnateTargets {
	private static final Set<String> FRIEND_TAGS = Set.of(
		RpgCombat.SUMMONED_TAG, "mod-stella-mobs.friendly_patrol", "mc-stella-villages.patrol",
		"mc-stella-villages.village_witch", "mod-stella-mobs.harmless"
	);
	private static final String FOE_TAG = "mod-stella-ruins.hostile";

	private InnateTargets() {
	}

	/** A player, a pet that a player owns, or a servant that a player summoned. */
	static boolean ally(final LivingEntity entity) {
		if (!entity.isAlive()) {
			return false;
		}
		if (entity instanceof Player player) {
			return !player.isSpectator();
		}
		return entity instanceof OwnableEntity pet && pet.getOwner() instanceof Player
			|| entity.entityTags().contains(RpgCombat.SUMMONED_TAG);
	}

	static boolean hostile(final LivingEntity entity) {
		if (!entity.isAlive() || entity instanceof Player || ally(entity)) {
			return false;
		}
		for (String tag : entity.entityTags()) {
			if (FRIEND_TAGS.contains(tag)) {
				return false;
			}
		}
		return entity instanceof Enemy || entity.entityTags().contains(FOE_TAG);
	}

	/** Every living entity within the radius of the player, the player included. */
	static List<LivingEntity> around(final ServerPlayer center, final double radius) {
		return new ArrayList<>(center.level().getEntitiesOfClass(
			LivingEntity.class, center.getBoundingBox().inflate(radius),
			candidate -> candidate.isAlive() && candidate.distanceToSqr(center) <= radius * radius
		));
	}

	/**
	 * Hurts the victim by a share of its max health. An elite or a boss takes the usual quarter of a percent hit.
	 * The hit leaves the recent-hit frames of the victim as they were, so a pulse neither shields the victim from the
	 * next blow of the player nor waits behind the last one.
	 */
	static boolean hurt(final ServerPlayer source, final LivingEntity victim, final double share) {
		return hurtBy(source, victim, share * victim.getMaxHealth());
	}

	/** Hurts the victim by this many health points. An elite or a boss takes the usual quarter of the hit. */
	static boolean hurtBy(final ServerPlayer source, final LivingEntity victim, final double healthPoints) {
		float amount = (float) (healthPoints * PercentEffects.eliteShare(victim) / RpgScale.k());
		if (amount <= 0.0F) {
			return false;
		}
		ServerLevel level = source.level();
		DamageSource damage = new DamageSource(
			level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(RpgDamageTypes.INNATE), source, source
		);
		int frames = victim.damageCooldownTime;
		victim.damageCooldownTime = 0;
		try {
			return PercentEffects.fixedHit(() -> victim.hurtServer(level, damage, amount));
		} finally {
			victim.damageCooldownTime = frames;
		}
	}

	/** Heals the target by a share of its max health. A target at full health gains nothing. */
	static boolean heal(final LivingEntity target, final double share) {
		if (target.getHealth() >= target.getMaxHealth()) {
			return false;
		}
		target.heal((float) (share * target.getMaxHealth() / RpgScale.k()));
		return true;
	}
}
