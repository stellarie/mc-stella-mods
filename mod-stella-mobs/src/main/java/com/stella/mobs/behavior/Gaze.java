package com.stella.mobs.behavior;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Who looks at whom. A viewer looks at a target when the target's eye lies inside the viewer's view cone and
 * nothing solid blocks the line between the two eyes. All angles are half angles in degrees. The class keeps no
 * state, so any goal may ask at any time.
 */
public final class Gaze {
	public static final double PLAYER_GAZE_HALF_ANGLE = 25.0;
	public static final double MOB_FACE_HALF_ANGLE = 60.0;
	/** The wide cone in which a player notices a mob. */
	public static final double SEEN_HALF_ANGLE = 55.0;

	private Gaze() {
	}

	public static boolean looksAt(final LivingEntity viewer, final Entity target, final double halfAngleDegrees) {
		Vec3 toTarget = target.getEyePosition().subtract(viewer.getEyePosition());
		double distance = toTarget.length();
		if (distance > 1.0E-6) {
			double cosine = viewer.getViewVector(1.0F).dot(toTarget) / distance;
			if (cosine < Math.cos(Math.toRadians(halfAngleDegrees))) {
				return false;
			}
		}

		return viewer.hasLineOfSight(target);
	}

	public static boolean mutual(final Mob mob, final Player player) {
		return looksAt(player, mob, PLAYER_GAZE_HALF_ANGLE) && looksAt(mob, player, MOB_FACE_HALF_ANGLE);
	}

	/** True when the mob stands in the player's wide view cone with a clear line between the eyes. */
	public static boolean seenBy(final Player player, final Mob mob) {
		return looksAt(player, mob, SEEN_HALF_ANGLE);
	}
}
