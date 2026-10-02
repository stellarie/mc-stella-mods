package com.stella.mobs.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class FleePositions {
	// Rejects a lateral or closer candidate, but does not demand a whole block per step.
	private static final double MINIMUM_AWAY_DISTANCE = 0.5;
	private static final double MAXIMUM_LEVEL_DIFFERENCE = 1.0;
	private static final int[][] DISTANCES = {{16, 7}, {8, 4}, {4, 2}};
	// A pack spot gains at least this much.
	private static final double PACK_MINIMUM_GAIN = 1.0;
	private static final double PACK_RADIUS = 24.0;
	// Stop short of the ally. Do not crowd it.
	private static final double PACK_STANDOFF = 2.0;
	private static final double PACK_MINIMUM_RUN = 0.5;

	private FleePositions() {
	}

	static @Nullable Vec3 away(final PathfinderMob mob, final Vec3 threatPosition) {
		double currentDistance = mob.position().distanceTo(threatPosition);
		Vec3 awayAxis = mob.position().subtract(threatPosition);

		for (int[] distance : DISTANCES) {
			Vec3 candidate = LandRandomPos.getPosAway(mob, distance[0], distance[1], threatPosition);
			if (candidate != null
				&& Math.abs(candidate.y - mob.getY()) <= MAXIMUM_LEVEL_DIFFERENCE
				&& candidate.distanceTo(threatPosition) > currentDistance + MINIMUM_AWAY_DISTANCE
				&& candidate.subtract(mob.position()).dot(awayAxis) > 0.0) {
				return candidate;
			}
		}

		return null;
	}

	/**
	 * A flee spot toward the nearest healthy ally within 24 blocks, one that still gains a block or more from the threat.
	 * With no such ally, or no such spot, it falls back to {@link #away}. A spot lies on the line to the ally, so the same
	 * call always picks the same direction. A witch is a monster, so it counts as an ally.
	 */
	static @Nullable Vec3 towardPack(final PathfinderMob mob, final Vec3 threatPosition) {
		Monster ally = nearestHealthyAlly(mob);
		if (ally != null) {
			Vec3 toAlly = new Vec3(ally.getX() - mob.getX(), 0.0, ally.getZ() - mob.getZ());
			double run = toAlly.length() - PACK_STANDOFF;
			if (run >= PACK_MINIMUM_RUN) {
				Vec3 direction = toAlly.normalize();
				double currentDistance = mob.position().distanceTo(threatPosition);
				for (int[] distance : DISTANCES) {
					double step = Math.min(distance[0], run);
					Vec3 candidate = new Vec3(mob.getX() + direction.x * step, mob.getY(), mob.getZ() + direction.z * step);
					if (candidate.distanceTo(threatPosition) >= currentDistance + PACK_MINIMUM_GAIN
						&& mob.getNavigation().isStableDestination(BlockPos.containing(candidate))) {
						return candidate;
					}
				}
			}
		}

		return away(mob, threatPosition);
	}

	/** The nearest healthy ally, or null. */
	private static @Nullable Monster nearestHealthyAlly(final PathfinderMob mob) {
		Monster nearest = null;
		double nearestSqr = PACK_RADIUS * PACK_RADIUS;
		for (Monster other : mob.level().getEntitiesOfClass(
			Monster.class,
			mob.getBoundingBox().inflate(PACK_RADIUS),
			monster -> monster != mob && monster.getTarget() != mob && SpiderSignal.eligible(null, monster)
		)) {
			double distanceSqr = mob.distanceToSqr(other);
			if (distanceSqr <= nearestSqr) {
				nearest = other;
				nearestSqr = distanceSqr;
			}
		}

		return nearest;
	}
}
