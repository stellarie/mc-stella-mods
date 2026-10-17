package com.stella.end;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;

/**
 * How the dragon moves. Vanilla steers it with phases that fly through walls and out of the arena; here the controller
 * sets its place every tick instead, easing its velocity toward a target, so it goes only where the fight sends it.
 */
final class DragonFlight {
	private static final double EASE = 0.2;
	private static final double ARRIVE_GAIN = 0.2;
	/** The ring the dragon circles on, round the hover point. */
	static final double ORBIT_RADIUS = 22.0;
	private static final double ORBIT_LEAD = 0.5;
	/** The cruise had this speed before the polish, and now has {@link #CRUISE_SHARE} of it. */
	private static final double CRUISE_BEFORE = 1.2;
	private static final double CRUISE_SHARE = 0.3;
	static final double CRUISE = CRUISE_BEFORE * CRUISE_SHARE;
	static final double RUSH = 2.6;
	private static final float TURN = 12.0F;

	private DragonFlight() {
	}

	static void arrive(final EnderDragon dragon, final DragonState state, final Vec3 target, final double maxSpeed) {
		Vec3 to = target.subtract(dragon.position());
		double distance = to.length();
		double speed = Math.min(maxSpeed, distance * ARRIVE_GAIN);
		Vec3 wanted = distance < 1.0E-6 ? Vec3.ZERO : to.scale(speed / distance);
		move(dragon, state, wanted);
	}

	static void rush(final EnderDragon dragon, final DragonState state, final Vec3 target, final double speed) {
		Vec3 to = target.subtract(dragon.position());
		double distance = to.length();
		move(dragon, state, distance < 1.0E-6 ? Vec3.ZERO : to.scale(speed / distance));
	}

	/** Circles over the roof at one height: it chases a point a little ahead of itself on the ring round the hover point. */
	static void orbit(final EnderDragon dragon, final DragonState state, final double speed) {
		Vec3 centre = Rooftop.hoverPoint();
		Vec3 here = dragon.position();
		double angle = Math.atan2(here.z - centre.z, here.x - centre.x) + ORBIT_LEAD;
		Vec3 ahead = new Vec3(centre.x + Math.cos(angle) * ORBIT_RADIUS, centre.y, centre.z + Math.sin(angle) * ORBIT_RADIUS);
		arrive(dragon, state, ahead, speed);
	}

	private static void move(final EnderDragon dragon, final DragonState state, final Vec3 wanted) {
		state.velocity = state.velocity.add(wanted.subtract(state.velocity).scale(EASE));
		Vec3 next = dragon.position().add(state.velocity);
		double floor = Rooftop.DECK_Y + 1.0;
		if (next.y < floor) {
			// Never below the deck.
			// The castle below is no place for it.
			next = new Vec3(next.x, floor, next.z);
			state.velocity = new Vec3(state.velocity.x, Math.max(0.0, state.velocity.y), state.velocity.z);
		}
		dragon.setPos(next);
		dragon.setDeltaMovement(state.velocity);
		face(dragon, state.velocity);
	}

	/** Turns the dragon to the way it flies. Vanilla draws it facing along the opposite of its yaw's own direction. */
	static void face(final EnderDragon dragon, final Vec3 heading) {
		if (heading.horizontalDistanceSqr() < 1.0E-4) {
			dragon.setYRot(Mth.wrapDegrees(dragon.getYRot() + 0.5F));
			return;
		}
		float wanted = 180.0F - (float) (Mth.atan2(heading.x, heading.z) * (180.0F / Math.PI));
		float turn = Mth.clamp(Mth.wrapDegrees(wanted - dragon.getYRot()), -TURN, TURN);
		dragon.setYRot(Mth.wrapDegrees(dragon.getYRot() + turn));
		dragon.yRotA = 0.0F;
	}

	/** Pulls a dragon that strayed past the leash back onto it, and takes the outward part from its velocity. */
	static void leash(final EnderDragon dragon, final DragonState state) {
		double reach = Math.hypot(dragon.getX(), dragon.getZ());
		if (reach <= DragonRules.LEASH) {
			return;
		}
		double back = DragonRules.LEASH / reach;
		dragon.setPos(dragon.getX() * back, dragon.getY(), dragon.getZ() * back);
		Vec3 outward = new Vec3(dragon.getX(), 0.0, dragon.getZ()).normalize();
		double leaving = Math.max(0.0, state.velocity.dot(outward));
		state.velocity = state.velocity.subtract(outward.scale(leaving));
	}
}
