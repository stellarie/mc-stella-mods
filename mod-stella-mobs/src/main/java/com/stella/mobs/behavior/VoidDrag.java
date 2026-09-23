package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The boss Enderman's void drag: a player who strikes it is pulled to its side and blinded, once per
 * hundred ticks. The destination is checked before the move, so the drag can never drop its target
 * inside a wall. The vanilla self-teleport is left alone, which is a decision and not an omission: the
 * boss both drags the player and dodges, and the request's "instead of teleporting itself away" needs a
 * mixin on Enderman.teleport() that this slice does not take.
 */
public final class VoidDrag {
	public static final int COOLDOWN_TICKS = 100;
	public static final int BLINDNESS_TICKS = 200;
	public static final int BLINDNESS_AMPLIFIER = 0;
	/** How far from the boss the target lands, close enough to be inside its reach. */
	public static final double LANDING_OFFSET = 1.5;

	private VoidDrag() {
	}

	public static void afterDamage(
		final LivingEntity victim,
		final DamageSource source,
		final float dealt,
		final float taken,
		final boolean blocked
	) {
		if (!(victim instanceof Enderman Enderman) || !Bosses.isBoss(Enderman)) {
			return;
		}

		if (!(source.getEntity() instanceof ServerPlayer player)) {
			return;
		}

		if (!(Enderman.level() instanceof ServerLevel level) || player.level() != level) {
			return;
		}

		ReactiveState state = ReactiveState.get(Enderman);
		long now = level.getGameTime();
		if (now < state.getVoidDragReadyTick()) {
			return;
		}

		Vec3 landing = landing(Enderman, player);
		AABB moved = player.getBoundingBox().move(landing.subtract(player.position()));
		if (!level.noCollision(player, moved)) {
			// A silent skip is better than a kill: the boss may stand in a wall.
			return;
		}

		state.setVoidDragReadyTick(now + COOLDOWN_TICKS);
		player.teleportTo(landing.x, landing.y, landing.z);
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLINDNESS_TICKS, BLINDNESS_AMPLIFIER), Enderman);

		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=voidDrag DRAG distanceBefore={} landing={} blinded=true cooldown={}",
			String.format(java.util.Locale.ROOT, "%.1f", Math.sqrt(player.distanceToSqr(Enderman))),
			String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f", landing.x, landing.y, landing.z),
			COOLDOWN_TICKS
		);
	}

	/** A spot beside the boss, on the line the player came from. */
	private static Vec3 landing(final Enderman Enderman, final ServerPlayer player) {
		Vec3 from = new Vec3(player.getX() - Enderman.getX(), 0.0, player.getZ() - Enderman.getZ());
		Vec3 direction = from.lengthSqr() < 1.0e-4 ? new Vec3(1.0, 0.0, 0.0) : from.normalize();
		return new Vec3(
			Enderman.getX() + direction.x * LANDING_OFFSET,
			Enderman.getY(),
			Enderman.getZ() + direction.z * LANDING_OFFSET
		);
	}
}
