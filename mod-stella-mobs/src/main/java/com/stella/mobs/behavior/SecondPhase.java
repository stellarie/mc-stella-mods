package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The boss evoker's second phase: the first lethal hit does not kill. It returns once, sixteen blocks
 * away at half health, and the durable flag is written before the move so a chunk reload cannot give it
 * a third life. The destination is chosen and checked here rather than asked of randomTeleport, which
 * refuses to move at all when it cannot find a spot it likes.
 */
public final class SecondPhase {
	public static final double RETURN_DISTANCE = 16.0;
	public static final double RETURN_HEALTH_FRACTION = 0.5;
	private static final int DIRECTIONS = 8;

	private SecondPhase() {
	}

	public static boolean allowDeath(final LivingEntity entity, final DamageSource source, final float amount) {
		if (!(entity instanceof Evoker evoker) || !Bosses.isBoss(evoker)) {
			return true;
		}

		if (EliteState.isSecondPhaseUsed(evoker)) {
			return true;
		}

		if (!(evoker.level() instanceof ServerLevel level)) {
			return true;
		}

		EliteState.setSecondPhaseUsed(evoker, true);
		Vec3 start = evoker.position();
		Vec3 landing = landing(level, evoker);
		evoker.teleportTo(landing.x, landing.y, landing.z);
		evoker.setHealth(evoker.getMaxHealth() * (float) RETURN_HEALTH_FRACTION);

		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=evokerSecondPhase RETURN moved={} health={} max={}",
			String.format(java.util.Locale.ROOT, "%.1f", landing.distanceTo(start)), evoker.getHealth(),
			evoker.getMaxHealth()
		);
		return false;
	}

	/** The first of eight directions whose landing box is clear, or where it stands when none is. */
	private static Vec3 landing(final ServerLevel level, final Evoker evoker) {
		for (int i = 0; i < DIRECTIONS; i++) {
			double angle = 2.0 * Math.PI * i / DIRECTIONS;
			Vec3 spot = new Vec3(
				evoker.getX() + Math.cos(angle) * RETURN_DISTANCE,
				evoker.getY(),
				evoker.getZ() + Math.sin(angle) * RETURN_DISTANCE
			);
			AABB moved = evoker.getBoundingBox().move(spot.subtract(evoker.position()));

			if (level.noCollision(evoker, moved)) {
				return spot;
			}
		}

		return evoker.position();
	}
}
