package com.stella.mobs.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.pathfinder.Path;
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
	// Degrees off the away axis, straightest first.
	private static final double[] RING_ANGLES = {0.0, 35.0, -35.0, 70.0, -70.0, 100.0, -100.0};
	private static final double[] RING_DISTANCES = {8.0, 5.0, 3.0};
	// A path search is the costly step.
	private static final int MAX_PATH_TESTS = 6;
	private static final int[] FLOOR_STEPS = {0, -1, 1};

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
	 * A path away from the threat that reaches its end, or null when the mob is cornered. A fixed ring of spots comes
	 * first: straight away, then at wider angles, at 8, 5, and 3 blocks. The random flee spot is the last try. One random
	 * miss therefore never reads as a corner. At most {@link #MAX_PATH_TESTS} spots are tested for a path.
	 */
	static @Nullable Path reachableAway(final PathfinderMob mob, final Vec3 threatPosition) {
		int tests = 0;
		double currentDistance = mob.position().distanceTo(threatPosition);
		Vec3 axis = new Vec3(mob.getX() - threatPosition.x, 0.0, mob.getZ() - threatPosition.z);
		// On top of the threat: any way is away.
		axis = axis.lengthSqr() < 1.0E-6 ? mob.getForward().multiply(-1.0, 0.0, -1.0) : axis;
		axis = axis.normalize();
		ring:
		for (double distance : RING_DISTANCES) {
			for (double angle : RING_ANGLES) {
				// One test stays for the random spot.
				if (tests >= MAX_PATH_TESTS - 1) {
					break ring;
				}

				Vec3 direction = axis.yRot((float) Math.toRadians(angle));
				BlockPos feet = standing(mob, BlockPos.containing(mob.getX() + direction.x * distance, mob.getY(), mob.getZ() + direction.z * distance));
				if (feet == null || Vec3.atBottomCenterOf(feet).distanceTo(threatPosition) <= currentDistance + MINIMUM_AWAY_DISTANCE) {
					continue;
				}

				tests++;
				Path path = reachable(mob, feet);
				if (path != null) {
					return path;
				}
			}
		}

		Vec3 random = away(mob, threatPosition);
		return random == null ? null : reachable(mob, BlockPos.containing(random));
	}

	private static @Nullable Path reachable(final PathfinderMob mob, final BlockPos target) {
		Path path = mob.getNavigation().createPath(target, 0);
		return path != null && path.canReach() ? path : null;
	}

	/** The feet position in this column, a step up or down at most. */
	private static @Nullable BlockPos standing(final PathfinderMob mob, final BlockPos column) {
		for (int step : FLOOR_STEPS) {
			BlockPos feet = column.above(step);
			if (mob.getNavigation().isStableDestination(feet)
				&& mob.level().getBlockState(feet).getCollisionShape(mob.level(), feet).isEmpty()
				&& mob.level().getBlockState(feet.above()).getCollisionShape(mob.level(), feet.above()).isEmpty()) {
				return feet;
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
