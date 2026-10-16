package com.stella.end;

import com.stella.end.mixin.ShulkerPeekAccessor;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Shulker;

/**
 * A closed castle shulker throws back whoever strikes it in melee. A shell shut as the blow lands, a blow struck by a
 * living attacker itself, and an attacker within reach are all needed; an arrow or a thrown snowball is not melee.
 */
public final class ShulkerKnockback {
	public static final double STRENGTH = 1.5;
	public static final double RANGE = 4.0;
	private static final int OPEN_PEEK = 30;

	private ShulkerKnockback() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
			if (!(entity instanceof Shulker shulker) || !EndMobs.isCastleMob(shulker) || !isClosed(shulker)) {
				return;
			}
			Entity direct = source.getDirectEntity();
			if (direct instanceof LivingEntity attacker && attacker.distanceTo(shulker) <= RANGE) {
				attacker.knockback(STRENGTH, shulker.getX() - attacker.getX(), shulker.getZ() - attacker.getZ(), source, 0.0F);
				BehaviorLog.info("[END] shulkerKnockback attacker={} strength={}", attacker.getType().getDescriptionId(), STRENGTH);
			}
		});
	}

	/** Whether the shell is shut: the peek amount is 0. */
	public static boolean isClosed(final Shulker shulker) {
		return ((ShulkerPeekAccessor) shulker).stellaEnd$getRawPeekAmount() == 0;
	}

	/** Opens or shuts the shell. The shulker's own goals move it again later. */
	public static void setClosed(final Shulker shulker, final boolean closed) {
		((ShulkerPeekAccessor) shulker).stellaEnd$setRawPeekAmount(closed ? 0 : OPEN_PEEK);
	}
}
