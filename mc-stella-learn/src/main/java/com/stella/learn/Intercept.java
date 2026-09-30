package com.stella.learn;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where a melee mob should aim to meet a moving player. A player who kites or strafes is never where they were a moment
 * ago, so a mob that walks at their current spot only ever trails them. The point is the player's position plus their
 * last measured velocity times a lead, in ticks.
 */
public final class Intercept {
	/** Blocks per tick below which a player counts as standing still and there is nothing to lead. */
	public static final double MINIMUM_SPEED = 0.08;

	private Intercept() {
	}

	/** How much a player's movement habits ask mobs to lead them: the larger of the kite and the strafe pressure. */
	public static double pressure(final ServerPlayer player) {
		return Math.max(Movement.kitePressure(player), Movement.strafePressure(player));
	}

	/** The point `interceptLeadTicks` ahead of the player along their velocity, or null when there is nothing to lead. */
	public static @Nullable Vec3 pointFor(final Mob mob, final ServerPlayer player) {
		Vec3 velocity = MovementRecorder.velocityOf(player);
		if (velocity == null || velocity.horizontalDistance() < MINIMUM_SPEED || pressure(player) < LearnConfigStore.get().moveThreshold()) {
			return null;
		}

		return player.position().add(velocity.x * LearnConfigStore.get().interceptLeadTicks(), 0.0, velocity.z * LearnConfigStore.get().interceptLeadTicks());
	}
}
