package com.stella.mobs.behavior;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class FleePositions {
	// Rejects a lateral or closer candidate, but does not demand a whole block per step.
	private static final double MINIMUM_AWAY_DISTANCE = 0.5;
	private static final double MAXIMUM_LEVEL_DIFFERENCE = 1.0;
	private static final int[][] DISTANCES = {{16, 7}, {8, 4}, {4, 2}};

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
}
