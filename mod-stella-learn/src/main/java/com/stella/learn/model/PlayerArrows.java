package com.stella.learn.model;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

/**
 * The arrows players have loosed, kept from load to unload so an observation finds the nearest two without a world
 * query per archer. An arrow that sits still in a block is skipped when the observation reads it.
 */
final class PlayerArrows {
	static final int SLOTS = 2;
	static final double RANGE = 32.0;
	private static final double FLYING_SPEED_SQR = 0.01;
	private static final Set<AbstractArrow> LIVE = ConcurrentHashMap.newKeySet();
	private static final AbstractArrow[] NEAREST = new AbstractArrow[SLOTS];
	private static final double[] NEAREST_DISTANCE = new double[SLOTS];

	private PlayerArrows() {
	}

	static void loaded(final Entity entity) {
		if (entity instanceof AbstractArrow arrow && arrow.getOwner() instanceof Player) {
			LIVE.add(arrow);
		}
	}

	static void unloaded(final Entity entity) {
		if (entity instanceof AbstractArrow arrow) {
			LIVE.remove(arrow);
		}
	}

	static int tracked() {
		return LIVE.size();
	}

	/**
	 * Writes present, relative position, and velocity for each slot into `out` from `at`, in the mob frame given by
	 * its forward and right unit vectors. Positions are relative to the mob eye.
	 */
	static int fill(final Mob mob, final float[] out, final int at, final double forwardX, final double forwardZ, final double rightX, final double rightZ) {
		java.util.Arrays.fill(NEAREST, null);
		java.util.Arrays.fill(NEAREST_DISTANCE, Double.MAX_VALUE);
		double eyeX = mob.getX();
		double eyeY = mob.getEyeY();
		double eyeZ = mob.getZ();
		double limit = RANGE * RANGE;

		for (AbstractArrow arrow : LIVE) {
			if (arrow.isRemoved() || arrow.level() != mob.level() || arrow.getDeltaMovement().lengthSqr() < FLYING_SPEED_SQR) {
				continue;
			}

			double dx = arrow.getX() - eyeX;
			double dy = arrow.getY() - eyeY;
			double dz = arrow.getZ() - eyeZ;
			double distance = dx * dx + dy * dy + dz * dz;
			if (distance > limit) {
				continue;
			}

			for (int slot = 0; slot < SLOTS; slot++) {
				if (distance < NEAREST_DISTANCE[slot]) {
					for (int lower = SLOTS - 1; lower > slot; lower--) {
						NEAREST[lower] = NEAREST[lower - 1];
						NEAREST_DISTANCE[lower] = NEAREST_DISTANCE[lower - 1];
					}

					NEAREST[slot] = arrow;
					NEAREST_DISTANCE[slot] = distance;
					break;
				}
			}
		}

		int i = at;
		for (int slot = 0; slot < SLOTS; slot++) {
			AbstractArrow arrow = NEAREST[slot];
			if (arrow == null) {
				for (int k = 0; k < 7; k++) {
					out[i++] = 0.0F;
				}

				continue;
			}

			double dx = arrow.getX() - eyeX;
			double dy = arrow.getY() - eyeY;
			double dz = arrow.getZ() - eyeZ;
			var velocity = arrow.getDeltaMovement();
			out[i++] = 1.0F;
			out[i++] = (float) (dx * rightX + dz * rightZ);
			out[i++] = (float) dy;
			out[i++] = (float) (dx * forwardX + dz * forwardZ);
			out[i++] = (float) (velocity.x * rightX + velocity.z * rightZ);
			out[i++] = (float) velocity.y;
			out[i++] = (float) (velocity.x * forwardX + velocity.z * forwardZ);
		}

		java.util.Arrays.fill(NEAREST, null);
		return i;
	}
}
