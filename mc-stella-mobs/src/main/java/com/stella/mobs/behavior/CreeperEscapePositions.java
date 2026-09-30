package com.stella.mobs.behavior;

import com.stella.mobs.mixin.CreeperRadiusAccessor;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.animal.dolphin.Dolphin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class CreeperEscapePositions {
	public static final double SAFE_MARGIN = 1.0;
	private static final double TARGET_CLEARANCE = 0.5;
	private static final double FLIGHT_CLEARANCE = 1.0;
	private static final double CREEPER_SEARCH_RADIUS = 32.0;
	private static final int MAX_SEARCH_RADIUS = 16;
	private static final int[] GROUND_LEVEL_OFFSETS = {0, -1, -2};
	private static final int[] WATER_LEVEL_OFFSETS = {0, 1, -1, 2, -2, 3, -3};
	private static final int[] FLIGHT_LEVEL_OFFSETS = {0, 2, -2, 4, -4, 6, -6, 8, -8};

	private CreeperEscapePositions() {
	}

	public static @Nullable Creeper threatFor(final Mob mob) {
		ReactiveState state = ReactiveState.get(mob);
		List<Creeper> threats = state.cachedR02Threats(mob.tickCount);
		if (threats == null) {
			threats = mob.level().getEntitiesOfClass(
				Creeper.class,
				mob.getBoundingBox().inflate(CREEPER_SEARCH_RADIUS),
				CreeperEscapePositions::isActiveThreat
			);
			state.cacheR02Threats(mob.tickCount, threats);
		}

		Creeper nearest = null;
		Creeper remembered = null;
		double nearestDistance = Double.MAX_VALUE;
		for (Creeper creeper : threats) {
			if (creeper.getUUID().equals(state.getR02ThreatId())) {
				remembered = creeper;
			}

			double radius = safeDistance(creeper);
			double distance = mob.distanceToSqr(creeper);
			if (distance <= radius * radius && distance < nearestDistance) {
				nearest = creeper;
				nearestDistance = distance;
			}
		}

		if (nearest != null) {
			state.setR02ThreatId(nearest.getUUID());
			return nearest;
		}

		if (remembered != null) {
			return remembered;
		}

		state.setR02ThreatId(null);
		return null;
	}

	public static double safeDistance(final Creeper creeper) {
		int radius = ((CreeperRadiusAccessor)(Object)creeper).stella$getExplosionRadius();
		return radius * (creeper.isPowered() ? 2.0 : 1.0) * 2.0 + SAFE_MARGIN;
	}

	public static @Nullable Path findNavigationPath(final PathfinderMob mob, final Creeper threat) {
		PathNavigation navigation = mob.getNavigation();
		Vec3 away = mob.position().subtract(threat.position());
		if (away.horizontalDistanceSqr() < 0.0001) {
			away = new Vec3(1.0, 0.0, 0.0);
		}

		double baseAngle = Math.atan2(away.z, away.x);
		BlockPos origin = mob.blockPosition();
		for (int ring = 1; ring <= MAX_SEARCH_RADIUS; ring++) {
			Set<BlockPos> checked = new HashSet<>();
			for (int turn = -6; turn <= 6; turn++) {
				double angle = baseAngle + Math.toRadians(turn * 15.0);
				int dx = (int)Math.round(Math.cos(angle) * ring);
				int dz = (int)Math.round(Math.sin(angle) * ring);
				if (dx == 0 && dz == 0) {
					continue;
				}

				for (int dy : GROUND_LEVEL_OFFSETS) {
					BlockPos target = origin.offset(dx, dy, dz);
					if (!checked.add(target)) {
						continue;
					}

					Vec3 position = Vec3.atBottomCenterOf(target);
					if (!isSafe(position, threat) || !isCollisionFree(mob, position)) {
						continue;
					}

					Path path = navigation.createPath(target, 0);
					if (path != null && path.canReach() && isSafe(Vec3.atBottomCenterOf(path.getTarget()), threat)) {
						return path;
					}
				}
			}
		}

		return null;
	}

	public static @Nullable Path findDolphinPath(final Dolphin dolphin, final Creeper threat) {
		if (dolphin.getAirSupply() <= 0) {
			return null;
		}

		PathNavigation navigation = dolphin.getNavigation();
		Vec3 away = dolphin.position().subtract(threat.position());
		if (away.horizontalDistanceSqr() < 0.0001) {
			away = new Vec3(1.0, 0.0, 0.0);
		}

		double baseAngle = Math.atan2(away.z, away.x);
		BlockPos origin = dolphin.blockPosition();
		for (int ring = 1; ring <= MAX_SEARCH_RADIUS; ring++) {
			Set<BlockPos> checked = new HashSet<>();
			for (int turn = -6; turn <= 6; turn++) {
				double angle = baseAngle + Math.toRadians(turn * 15.0);
				int dx = (int)Math.round(Math.cos(angle) * ring);
				int dz = (int)Math.round(Math.sin(angle) * ring);
				if (dx == 0 && dz == 0) {
					continue;
				}

				for (int dy : WATER_LEVEL_OFFSETS) {
					BlockPos target = origin.offset(dx, dy, dz);
					if (!checked.add(target) || !dolphin.level().getBlockState(target).is(Blocks.WATER)) {
						continue;
					}

					Vec3 position = Vec3.atBottomCenterOf(target);
					if (!isSafe(position, threat) || !isCollisionFree(dolphin, position)) {
						continue;
					}

					Path path = navigation.createPath(target, 0);
					if (path != null && path.canReach() && isSafe(Vec3.atBottomCenterOf(path.getTarget()), threat)) {
						return path;
					}
				}
			}
		}

		return null;
	}

	public static @Nullable Vec3 findSupportedTarget(final Mob mob, final Creeper threat) {
		Vec3 away = mob.position().subtract(threat.position());
		if (away.horizontalDistanceSqr() < 0.0001) {
			away = new Vec3(1.0, 0.0, 0.0);
		}

		double baseAngle = Math.atan2(away.z, away.x);
		BlockPos origin = mob.blockPosition();
		for (int ring = 1; ring <= MAX_SEARCH_RADIUS; ring++) {
			Set<BlockPos> checked = new HashSet<>();
			for (int turn = -6; turn <= 6; turn++) {
				double angle = baseAngle + Math.toRadians(turn * 15.0);
				int dx = (int)Math.round(Math.cos(angle) * ring);
				int dz = (int)Math.round(Math.sin(angle) * ring);
				if (dx == 0 && dz == 0) {
					continue;
				}

				BlockPos target = origin.offset(dx, 0, dz);
				if (!checked.add(target)) {
					continue;
				}
				BlockPos below = target.below();
				if (!mob.level().getBlockState(below).isFaceSturdy(mob.level(), below, Direction.UP)) {
					continue;
				}

				Vec3 position = Vec3.atBottomCenterOf(target);
				if (isSafe(position, threat) && isCollisionFree(mob, position)) {
					return position;
				}
			}
		}

		return null;
	}

	public static @Nullable Vec3 findDirectSupportedTarget(final Mob mob, final Creeper threat) {
		Vec3 away = mob.position().subtract(threat.position());
		if (away.horizontalDistanceSqr() < 0.0001) {
			away = new Vec3(1.0, 0.0, 0.0);
		}

		double baseAngle = Math.atan2(away.z, away.x);
		BlockPos origin = mob.blockPosition();
		for (int ring = 1; ring <= MAX_SEARCH_RADIUS; ring++) {
			Set<BlockPos> checked = new HashSet<>();
			for (int turn = -6; turn <= 6; turn++) {
				double angle = baseAngle + Math.toRadians(turn * 15.0);
				int dx = (int)Math.round(Math.cos(angle) * ring);
				int dz = (int)Math.round(Math.sin(angle) * ring);
				if (dx == 0 && dz == 0) {
					continue;
				}

				for (int dy : GROUND_LEVEL_OFFSETS) {
					BlockPos target = origin.offset(dx, dy, dz);
					if (!checked.add(target)) {
						continue;
					}
					BlockPos below = target.below();
					if (!mob.level().getBlockState(below).isFaceSturdy(mob.level(), below, Direction.UP)) {
						continue;
					}

					Vec3 position = Vec3.atBottomCenterOf(target);
					if (isSafe(position, threat) && isDirectPathClear(mob, position)) {
						return position;
					}
				}
			}
		}

		return null;
	}

	public static @Nullable Vec3 findFlightTarget(final Mob mob, final Creeper threat) {
		Vec3 away = mob.position().subtract(threat.position());
		if (away.lengthSqr() < 0.0001) {
			away = new Vec3(1.0, 0.0, 0.0);
		}

		double baseAngle = Math.atan2(away.z, away.x);
		int minimumRadius = (int)Math.ceil(safeDistance(threat)) + 1;
		int maximumRadius = Math.min(MAX_SEARCH_RADIUS, minimumRadius + 4);
		for (int radius = minimumRadius; radius <= maximumRadius; radius += 2) {
			for (int turn = -6; turn <= 6; turn++) {
				double angle = baseAngle + Math.toRadians(turn * 15.0);
				for (int dy : FLIGHT_LEVEL_OFFSETS) {
					Vec3 position = new Vec3(
						mob.getX() + Math.cos(angle) * radius,
						mob.getY() + dy,
						mob.getZ() + Math.sin(angle) * radius
					);
					if (isFlightSafe(position, threat) && isCollisionFree(mob, position) && isDirectPathClear(mob, position)) {
						return position;
					}
				}
			}
		}

		return null;
	}

	public static boolean isSafe(final Vec3 position, final Creeper threat) {
		double minimumDistance = safeDistance(threat) + TARGET_CLEARANCE;
		return position.distanceToSqr(threat.position()) >= minimumDistance * minimumDistance;
	}

	private static boolean isFlightSafe(final Vec3 position, final Creeper threat) {
		double minimumDistance = safeDistance(threat) + FLIGHT_CLEARANCE;
		return position.distanceToSqr(threat.position()) >= minimumDistance * minimumDistance;
	}

	private static boolean isActiveThreat(final Creeper creeper) {
		LivingEntity target = creeper.getTarget();
		return creeper.isAlive() && creeper.getSwellDir() > 0 && target instanceof Player && target.isAlive();
	}

	private static boolean isDirectPathClear(final Mob mob, final Vec3 destination) {
		Vec3 start = mob.position();
		Vec3 delta = destination.subtract(start);
		int steps = Math.max(1, (int)Math.ceil(delta.length() * 2.0));
		for (int step = 1; step <= steps; step++) {
			Vec3 position = start.add(delta.scale((double)step / steps));
			if (!isCollisionFree(mob, position)) {
				return false;
			}
		}

		return true;
	}

	private static boolean isCollisionFree(final Mob mob, final Vec3 destination) {
		AABB box = mob.getBoundingBox().move(
			destination.x - mob.getX(), destination.y - mob.getY(), destination.z - mob.getZ()
		);
		return mob.level().noBlockCollision(mob, box);
	}
}
