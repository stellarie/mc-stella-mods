package com.stella.equips.guardian;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

/**
 * The gear of the Tower Guardians: the Bone Shield and the Skeleton Crown. The tower mod drops both by item id,
 * so neither one is a unique with a drop source of its own.
 */
public final class GuardianGear {
	private GuardianGear() {
	}

	/** Registers both items. Called once from the mod initializer, before the item registry freezes. */
	public static void register() {
		BoneShield.register();
		SkeletonCrown.register();
	}

	/** The damage a hit carries before armor, after the crown or the shield of the attacker has changed it. */
	public static float outgoing(final LivingEntity victim, final DamageSource source, final float amount) {
		if (amount <= 0.0F || !(source.getEntity() instanceof ServerPlayer holder) || holder == victim || source.is(DamageTypes.THORNS)) {
			return amount;
		}
		Entity direct = source.getDirectEntity();
		if (direct instanceof AbstractArrow) {
			double bonus = SkeletonCrown.wornBonus(holder);
			return bonus > 0.0 ? (float) (amount * (1.0 + bonus)) : amount;
		}
		if (direct == holder && source.is(DamageTypeTags.IS_PLAYER_ATTACK)) {
			return BoneShield.strike(holder, victim, amount);
		}
		return amount;
	}
}
