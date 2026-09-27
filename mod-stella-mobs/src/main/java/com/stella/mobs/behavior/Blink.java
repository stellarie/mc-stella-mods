package com.stella.mobs.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** A mob's short teleport to a standing spot on a ring around an anchor, with a puff where it left and arrived. */
public final class Blink {
	private static final int ATTEMPTS = 32;
	private static final int SEARCH_UP = 3;
	private static final int SEARCH_DOWN = 5;

	private Blink() {
	}

	/** Teleports the mob to a free, floored spot between the two distances from the anchor; false when none was found. */
	public static boolean around(final Mob mob, final Entity anchor, final double minDistance, final double maxDistance) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
			double angle = mob.getRandom().nextDouble() * Math.PI * 2.0;
			double distance = minDistance + mob.getRandom().nextDouble() * (maxDistance - minDistance);
			Vec3 spot = standing(level, mob, anchor.getX() + Math.cos(angle) * distance, anchor.getBlockY(), anchor.getZ() + Math.sin(angle) * distance);
			if (spot != null) {
				Vec3 from = mob.position();
				mob.teleportTo(spot.x, spot.y, spot.z);
				mob.getNavigation().stop();
				puff(level, from);
				puff(level, spot);
				level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ENDERMAN_TELEPORT, mob.getSoundSource(), 0.8F, 1.2F);
				return true;
			}
		}
		return false;
	}

	private static @Nullable Vec3 standing(final ServerLevel level, final Mob mob, final double x, final int y, final double z) {
		for (int dy = SEARCH_UP; dy >= -SEARCH_DOWN; dy--) {
			BlockPos feet = BlockPos.containing(x, y + dy, z);
			if (!level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)) {
				continue;
			}
			Vec3 at = Vec3.atBottomCenterOf(feet);
			if (level.noCollision(mob, mob.getDimensions(mob.getPose()).makeBoundingBox(at)) && !level.containsAnyLiquid(mob.getDimensions(mob.getPose()).makeBoundingBox(at))) {
				return at;
			}
		}
		return null;
	}

	private static void puff(final ServerLevel level, final Vec3 at) {
		level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y + 1.0, at.z, 20, 0.4, 0.8, 0.4, 0.05);
	}
}
