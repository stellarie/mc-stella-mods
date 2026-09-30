package com.stella.nether;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.phys.Vec3;

/**
 * Every blaze but the Sovereign keeps low and keeps its distance: it never flies upward, it closes on its
 * target when farther than {@link #PURSUE_DISTANCE}, and backs away when the target comes within
 * {@link #MIN_DISTANCE}. Its attack is left as it is; only where it goes changes.
 */
public final class BlazeSkirmish {
	public static final double MIN_DISTANCE = 5.0;
	/** Beyond this a blaze closes in; between the two it holds where it is. */
	public static final double PURSUE_DISTANCE = 10.0;
	/** How far past the least distance a backing blaze aims, so it does not stop on the line. */
	private static final double BACK_OFF_MARGIN = 2.0;

	public enum Move {
		NONE,
		HOLD,
		PURSUE,
		RETREAT
	}

	private BlazeSkirmish() {
	}

	/** Runs after a blaze's own thinking each tick, before it moves; returns what it chose. */
	public static Move step(final Blaze blaze) {
		if (BlazeSovereign.isSovereign(blaze)) {
			return Move.NONE;
		}
		// Never upward: a blaze rises toward a target above it, and that lift is taken away.
		Vec3 motion = blaze.getDeltaMovement();
		if (motion.y > 0.0) {
			blaze.setDeltaMovement(motion.x, 0.0, motion.z);
		}
		LivingEntity target = blaze.getTarget();
		if (target == null || !target.isAlive()) {
			return Move.NONE;
		}
		double dx = target.getX() - blaze.getX();
		double dz = target.getZ() - blaze.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		if (flat < MIN_DISTANCE) {
			double away = MIN_DISTANCE - flat + BACK_OFF_MARGIN;
			double ux = flat < 1.0E-3 ? 1.0 : dx / flat;
			double uz = flat < 1.0E-3 ? 0.0 : dz / flat;
			blaze.getNavigation().stop();
			blaze.getMoveControl().setWantedPosition(blaze.getX() - ux * away, blaze.getY(), blaze.getZ() - uz * away, 1.0);
			drift(blaze, -ux, -uz);
			return Move.RETREAT;
		}
		if (flat > PURSUE_DISTANCE) {
			blaze.getMoveControl().setWantedPosition(target.getX(), blaze.getY(), target.getZ(), 1.0);
			drift(blaze, dx / flat, dz / flat);
			return Move.PURSUE;
		}
		return Move.HOLD;
	}

	/** Blocks a tick a blaze moves under its own power while it is off the ground. */
	public static final double AIR_SPEED = 0.12;

	/**
	 * Off the ground, lifted by a blast or settling slowly as blazes do, walking gets almost no grip, so a blaze
	 * moves under its own power along the way it means to go (seen: a retreat of 2 blocks in 4 seconds while it
	 * hung in the air).
	 */
	private static void drift(final Blaze blaze, final double ux, final double uz) {
		if (blaze.onGround()) {
			return;
		}
		Vec3 motion = blaze.getDeltaMovement();
		blaze.setDeltaMovement(ux * AIR_SPEED, motion.y, uz * AIR_SPEED);
	}
}
