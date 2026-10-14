package com.stella.mobs.behavior;

import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/**
 * Lifts an arrow over a mob that stands in its line of fire. The flight is stepped tick by tick with vanilla
 * arrow drag and gravity. When the flat shot first meets a mob that is not the target, the aim rises in small
 * steps until a flight reaches the target before any other mob. A short shot at full speed cannot come down
 * again on a near target, so each lift is tried at full speed first and then at lower speeds, which lobs the
 * arrow. A slower arrow hits softer. A shot with no such flight keeps its aim and its speed.
 */
public final class ShotClearance {
	public static final double ARROW_DRAG = 0.99;
	public static final double ARROW_GRAVITY = 0.05;
	public static final int FLIGHT_TICKS = 60;
	public static final double STEP_DEGREES = 2.5;
	public static final double MAX_LIFT_DEGREES = 55.0;
	// Shares of the vanilla speed, fastest first.
	public static final double[] SPEED_SHARES = {1.0, 0.8, 0.65, 0.5, 0.4, 0.3};
	// Half the arrow's box, plus a margin against its spread.
	private static final double TARGET_MARGIN = 0.3;
	private static final double BLOCKER_MARGIN = 0.45;
	private static final double SCAN_MARGIN = 2.0;

	private ShotClearance() {
	}

	/** A direction and the speed to shoot it at. */
	public record Shot(Vec3 aim, double power) {
	}

	/** Returns the shot to take: the given one when nothing blocks it or nothing clears the block. */
	public static Shot raise(final Mob shooter, final Vec3 origin, final LivingEntity target, final Vec3 aim, final double power) {
		Shot plain = new Shot(aim, power);
		if (aim.lengthSqr() < 1.0E-6 || !(power > 0.0)) {
			return plain;
		}

		AABB corridor = new AABB(origin, target.getBoundingBox().getCenter()).inflate(SCAN_MARGIN);
		List<Mob> blockers = shooter.level().getEntitiesOfClass(
			Mob.class, corridor, mob -> mob != shooter && mob != target && mob.isAlive() && !mob.isPassengerOfSameVehicle(shooter)
		);
		if (blockers.isEmpty() || !blocked(shooter.level(), origin, aim, power, target, blockers)) {
			return plain;
		}

		double horizontal = Math.hypot(aim.x, aim.z);
		if (horizontal < 1.0E-6) {
			return plain;
		}

		double pitch = Math.atan2(aim.y, horizontal);
		double length = aim.length();
		for (double share : SPEED_SHARES) {
			double speed = power * share;
			for (double lift = STEP_DEGREES; lift <= MAX_LIFT_DEGREES; lift += STEP_DEGREES) {
				double raised = pitch + Math.toRadians(lift);
				double flat = Math.cos(raised) * length / horizontal;
				Vec3 candidate = new Vec3(aim.x * flat, Math.sin(raised) * length, aim.z * flat);
				if (reaches(shooter.level(), origin, candidate, speed, target, blockers)) {
					return new Shot(candidate, speed);
				}
			}
		}

		return plain;
	}

	/** True when the flight meets one of `blockers` before the target. */
	private static boolean blocked(
		final Level level, final Vec3 origin, final Vec3 aim, final double power, final LivingEntity target, final List<Mob> blockers
	) {
		LivingEntity met = first(level, origin, aim, power, target, blockers);
		return met != null && met != target;
	}

	private static boolean reaches(
		final Level level, final Vec3 origin, final Vec3 aim, final double power, final LivingEntity target, final List<Mob> blockers
	) {
		return first(level, origin, aim, power, target, blockers) == target;
	}

	/** The first entity the flight meets, or null when a block or the end of the flight comes first. */
	private static @Nullable LivingEntity first(
		final Level level, final Vec3 origin, final Vec3 aim, final double power, final LivingEntity target, final List<Mob> blockers
	) {
		Vec3 velocity = aim.normalize().scale(power);
		Vec3 position = origin;
		for (int tick = 0; tick < FLIGHT_TICKS; tick++) {
			Vec3 next = position.add(velocity);
			double best = Double.MAX_VALUE;
			LivingEntity found = null;
			double hit = distance(position, next, target.getBoundingBox().inflate(TARGET_MARGIN));
			if (hit >= 0.0) {
				best = hit;
				found = target;
			}

			for (Mob blocker : blockers) {
				double along = distance(position, next, blocker.getBoundingBox().inflate(BLOCKER_MARGIN));
				if (along >= 0.0 && along < best) {
					best = along;
					found = blocker;
				}
			}

			HitResult block = level.clip(new ClipContext(position, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
			if (block.getType() != HitResult.Type.MISS && (found == null || block.getLocation().distanceTo(position) < best)) {
				return null;
			}

			if (found != null) {
				return found;
			}

			position = next;
			velocity = velocity.scale(ARROW_DRAG).subtract(0.0, ARROW_GRAVITY, 0.0);
		}

		return null;
	}

	/** The distance along the segment to the box, or -1 for a miss. */
	private static double distance(final Vec3 from, final Vec3 to, final AABB box) {
		if (box.contains(from)) {
			return 0.0;
		}

		return box.clip(from, to).map(point -> point.distanceTo(from)).orElse(-1.0);
	}
}
