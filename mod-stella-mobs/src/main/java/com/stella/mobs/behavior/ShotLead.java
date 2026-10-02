package com.stella.mobs.behavior;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.phys.Vec3;

/**
 * Where to aim at a moving target so a shot arrives where the target will be. The lead is the target's velocity
 * times the flight time. The flight time is the distance over the shot's speed, and the distance is read twice:
 * once to the target and once to the point that first pass found.
 *
 * <p>A player's own velocity on the server is stale: the client moves the player and the server learns the movement
 * from packets. `Entity.getKnownMovement` carries the movement the client reported for a player, and the delta
 * movement for any other entity. A target on the ground has no vertical lead, since its small downward speed
 * is only the floor holding it.
 */
public final class ShotLead {
	private static final int PASSES = 2;
	// Three seconds, so a fall cannot throw the aim.
	private static final double MAX_FLIGHT_TICKS = 60.0;
	// A vanilla fireball starts at this push
	// and adds it every tick, then keeps 95 percent.
	private static final double FIREBALL_PUSH = AbstractHurtingProjectile.INITAL_ACCELERATION_POWER;
	private static final double FIREBALL_INERTIA = 0.95;
	private static final int MAX_FIREBALL_TICKS = 200;

	private ShotLead() {
	}

	public static Vec3 velocity(final LivingEntity target) {
		return target.getKnownMovement();
	}

	/** How far the target moves in `ticks`. */
	public static Vec3 lead(final LivingEntity target, final double ticks) {
		Vec3 velocity = velocity(target);
		double vertical = target.onGround() ? 0.0 : velocity.y;
		return new Vec3(velocity.x, vertical, velocity.z).scale(Math.min(ticks, MAX_FLIGHT_TICKS));
	}

	/** The lead for a shot of `projectileSpeed` blocks per tick, from the shooter's eye to the target's centre. */
	public static Vec3 offset(final Entity shooter, final LivingEntity target, final double projectileSpeed) {
		if (!(projectileSpeed > 0.0)) {
			return Vec3.ZERO;
		}

		Vec3 from = shooter.getEyePosition();
		Vec3 centre = target.getBoundingBox().getCenter();
		Vec3 offset = Vec3.ZERO;
		for (int pass = 0; pass < PASSES; pass++) {
			offset = lead(target, from.distanceTo(centre.add(offset)) / projectileSpeed);
		}

		return offset;
	}

	/** The target's position after the flight time. */
	public static Vec3 point(final Entity shooter, final LivingEntity target, final double projectileSpeed) {
		return target.position().add(offset(shooter, target, projectileSpeed));
	}

	/**
	 * The mean speed of a vanilla fireball over `distance`. A fireball is slow at the start, so the mean over a
	 * short flight is far under the speed it reaches.
	 */
	public static double fireballSpeed(final double distance) {
		double speed = FIREBALL_PUSH;
		double covered = 0.0;
		int ticks = 0;
		while (covered < distance && ticks < MAX_FIREBALL_TICKS) {
			speed = (speed + FIREBALL_PUSH) * FIREBALL_INERTIA;
			covered += speed;
			ticks++;
		}

		if (ticks == 0) {
			return FIREBALL_PUSH;
		}

		// The last tick is only partly flown.
		double flown = (ticks - 1) + (distance - (covered - speed)) / speed;
		return distance / flown;
	}
}
