package com.stella.mobs.behavior;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Delivers a mod-chosen potion through vanilla's own spawn call, so the game still owns the
 * projectile, the splash box, and the effect application.
 *
 * <p>A potion flies as vanilla flies it: gravity, then drag, then the move, every tick. The aim solves that flight
 * for the launch angle that reaches the target's height at the target's distance. It aims at the spot the target
 * reaches by the time the potion arrives. Vanilla's own aim is a linear term, and a vanilla throw falls short
 * past about ten blocks.
 */
public final class WitchThrow {
	// Reaches the throw range, even uphill.
	private static final float THROW_SPEED = 1.5F;
	// Zero spread, so a throw lands on the mark. Vanilla uses 8.0.
	public static final float UNCERTAINTY = 0.0F;
	// AbstractThrownPotion.getDefaultGravity.
	// ThrowableProjectile.getAirDrag.
	private static final double GRAVITY_PER_TICK = 0.05;
	private static final double AIR_DRAG = 0.99;
	private static final int MAX_FLIGHT_TICKS = 80;
	private static final int BISECTIONS = 40;
	private static final int LEAD_PASSES = 2;
	// A thrown item spawns this far below the eyes.
	private static final double SPAWN_BELOW_EYES = 0.1F;
	// Point blank: the arc split is unstable.
	private static final double POINT_BLANK = 0.5;
	private static final double STEEPEST_DOWN = Math.toRadians(85.0);

	private WitchThrow() {
	}

	/**
	 * Aims at the target's mid body, where it will be when the potion arrives, and arcs the potion to it. Vanilla
	 * aims from the feet with a linear term, which overshoots a level target by more than a block.
	 */
	public static void atTarget(final Witch witch, final ItemStack stack, final LivingEntity target) {
		shoot(witch, stack, aim(witch, target), false);
	}

	/** W12: the same aim and speed, but the projectile leaves a lingering cloud where it lands. */
	public static void lingerAtTarget(final Witch witch, final ItemStack stack, final LivingEntity target) {
		shoot(witch, stack, aim(witch, target), true);
	}

	/** Straight down, so the splash box is centred on the witch itself. */
	public static void atSelf(final Witch witch, final ItemStack stack) {
		shoot(witch, stack, new Aim(0.0, -1.0, 0.0), false);
	}

	private static Aim aim(final Witch witch, final LivingEntity target) {
		Vec3 origin = new Vec3(witch.getX(), witch.getEyeY() - SPAWN_BELOW_EYES, witch.getZ());
		Vec3 body = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		Arc arc = solve(origin, body);
		for (int pass = 0; pass < LEAD_PASSES; pass++) {
			arc = solve(origin, body.add(ShotLead.lead(target, arc.ticks())));
		}

		return arc.aim();
	}

	/**
	 * The launch angle at which the potion crosses `point`'s distance at `point`'s height. The height at a fixed
	 * distance climbs with the angle up to `top`, the angle that peaks it, so the search brackets from steeply down
	 * to `top` and takes the flat arc. A point out of reach gets `top`.
	 */
	private static Arc solve(final Vec3 origin, final Vec3 point) {
		double dx = point.x - origin.x;
		double dy = point.y - origin.y;
		double dz = point.z - origin.z;
		double flat = Math.sqrt(dx * dx + dz * dz);
		if (flat < POINT_BLANK) {
			return new Arc(new Aim(dx, dy, dz), 1.0);
		}

		double top = Math.atan(THROW_SPEED * THROW_SPEED / (GRAVITY_PER_TICK * flat));
		double angle = top;
		Flight peak = flight(top, flat);
		if (peak != null && peak.rise() >= dy) {
			double low = -STEEPEST_DOWN;
			double high = top;
			for (int step = 0; step < BISECTIONS; step++) {
				double middle = (low + high) / 2.0;
				Flight flight = flight(middle, flat);
				if (flight == null || flight.rise() < dy) {
					low = middle;
				} else {
					high = middle;
				}
			}

			angle = high;
		}

		Flight flown = flight(angle, flat);
		double ticks = flown == null ? flat / (THROW_SPEED * Math.cos(angle)) : flown.ticks();
		double along = Math.cos(angle) / flat;
		return new Arc(new Aim(dx * along, Math.sin(angle), dz * along), ticks);
	}

	/** The potion's rise and flight time when it first crosses `flat` blocks, or none if it never gets there. */
	private static @Nullable Flight flight(final double angle, final double flat) {
		double along = THROW_SPEED * Math.cos(angle);
		double up = THROW_SPEED * Math.sin(angle);
		double x = 0.0;
		double y = 0.0;
		for (int tick = 1; tick <= MAX_FLIGHT_TICKS; tick++) {
			up -= GRAVITY_PER_TICK;
			along *= AIR_DRAG;
			up *= AIR_DRAG;
			if (along <= 1.0E-3) {
				return null;
			}

			if (x + along >= flat) {
				double part = (flat - x) / along;
				return new Flight(y + up * part, tick - 1 + part);
			}

			x += along;
			y += up;
		}

		return null;
	}

	private static void shoot(final Witch witch, final ItemStack stack, final Aim aim, final boolean lingering) {
		if (!(witch.level() instanceof ServerLevel level)) {
			return;
		}

		if (lingering) {
			Projectile.spawnProjectileUsingShoot(
				ThrownLingeringPotion::new, level, stack, witch, aim.x(), aim.y(), aim.z(), THROW_SPEED, UNCERTAINTY
			);
		} else {
			Projectile.spawnProjectileUsingShoot(
				ThrownSplashPotion::new, level, stack, witch, aim.x(), aim.y(), aim.z(), THROW_SPEED, UNCERTAINTY
			);
		}

		if (!witch.isSilent()) {
			witch.level()
				.playSound(null, witch.getX(), witch.getY(), witch.getZ(), SoundEvents.WITCH_THROW, SoundSource.HOSTILE, 1.0F, 1.0F);
		}
	}

	/** One throw's direction in the witch's own frame. */
	private record Aim(double x, double y, double z) {
	}

	/** An aim and the ticks the potion takes to arrive. */
	private record Arc(Aim aim, double ticks) {
	}

	/** Where a flown arc stands when it crosses the target's distance. */
	private record Flight(double rise, double ticks) {
	}
}
