package com.stella.mobs.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** A mob's short teleport to a standing spot on a ring around an anchor, with a puff where it left and arrived. */
public final class Blink {
	private static final int ATTEMPTS = 32;
	private static final int SEARCH_UP = 3;
	private static final int SEARCH_DOWN = 5;
	/** The nearest a slip may land from where the mob stood. */
	private static final double NEAREST_STEP = 2.0;
	/** How much farther from its target a slip may leave the mob, so the slip stays inside its own band. */
	public static final double SLIP_GAIN = 4.0;

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
				return to(mob, spot);
			}
		}
		return false;
	}

	/** Teleports the mob to a spot it chose itself, with the puff and the sound; false without a server level. */
	public static boolean to(final Mob mob, final Vec3 spot) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		Vec3 from = mob.position();
		mob.teleportTo(spot.x, spot.y, spot.z);
		mob.getNavigation().stop();
		puff(level, from);
		puff(level, spot);
		level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ENDERMAN_TELEPORT, mob.getSoundSource(), 0.8F, 1.2F);
		return true;
	}

	/** A slip to a standing spot the target cannot see, at most maxStep away and inside the mob's own band. */
	public static boolean slipOutOfSight(final Mob mob, final Entity target, final double maxStep) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		double stood = mob.position().distanceTo(target.position());
		for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
			double angle = mob.getRandom().nextDouble() * Math.PI * 2.0;
			double step = NEAREST_STEP + mob.getRandom().nextDouble() * (maxStep - NEAREST_STEP);
			Vec3 spot = standing(level, mob, mob.getX() + Math.cos(angle) * step, mob.getBlockY(), mob.getZ() + Math.sin(angle) * step);
			if (spot == null || !insideBand(spot, target, stood) || !outOfSight(level, target, mob, spot)) {
				continue;
			}
			return to(mob, spot);
		}
		return false;
	}

	/**
	 * True when the spot keeps the mob's own distance to its target, or leaves it up to {@link #SLIP_GAIN}
	 * farther. A slip that came nearer would break the unit's band, and one that retreated would leave its
	 * firing position; either costs the unit the shot after this one.
	 */
	private static boolean insideBand(final Vec3 spot, final Entity target, final double stood) {
		double away = spot.distanceTo(target.position());
		return away >= stood && away <= stood + SLIP_GAIN;
	}

	/** True when a ray from the target's eye to the mob's own eye height meets a block at this spot. */
	private static boolean outOfSight(final ServerLevel level, final Entity target, final Mob mob, final Vec3 spot) {
		Vec3 at = spot.add(0.0, mob.getEyeHeight(), 0.0);
		return level.clip(new ClipContext(target.getEyePosition(), at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target))
			.getType() != HitResult.Type.MISS;
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
