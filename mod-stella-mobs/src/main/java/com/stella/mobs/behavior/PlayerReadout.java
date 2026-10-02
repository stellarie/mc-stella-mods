package com.stella.mobs.behavior;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * What a mob reads off a player: how hurt the player is, and whether the player is coming for the mob. The health share is
 * a fraction of the player's own maximum, so it stays right when another mod scales health. A player who holds a raised
 * shield against the mob counts as healthier, because the mob's hits will not land. The chase test reads the movement that
 * the client reported, since a server player has no velocity of its own. The pure overloads take plain numbers, so a table
 * can check every threshold without a world.
 */
public final class PlayerReadout {
	public static final double SHIELD_BONUS = 0.25;
	public static final double CHASE_SPEED = 0.1;
	public static final double CHASE_RANGE = 6.0;

	// A player with no maximum health is not dying.
	private static final double FULL_SHARE = 1.0;

	private PlayerReadout() {
	}

	/** Health and absorption as a fraction of maximum health. Absorption can lift it above 1. */
	public static double share(final Player player) {
		double max = player.getMaxHealth();
		return max <= 0.0 ? FULL_SHARE : ((double) player.getHealth() + player.getAbsorptionAmount()) / max;
	}

	public static double effectiveShare(final Player player, final Mob mob) {
		boolean shieldFacing = player.isBlocking() && facing(player.getViewVector(1.0F), mob.position().subtract(player.position()));
		return effectiveShare(share(player), shieldFacing);
	}

	public static double effectiveShare(final double share, final boolean shieldFacing) {
		return shieldFacing ? share + SHIELD_BONUS : share;
	}

	/** True when the horizontal view and the horizontal line to the mob point the same way. */
	public static boolean facing(final Vec3 view, final Vec3 toMob) {
		return view.x * toMob.x + view.z * toMob.z > 0.0;
	}

	public static boolean chasing(final Player player, final Mob mob) {
		Vec3 toMob = mob.position().subtract(player.position());
		double flat = Math.hypot(toMob.x, toMob.z);
		Vec3 movement = player.getKnownMovement();
		double toward = flat < 1.0E-6 ? 0.0 : (movement.x * toMob.x + movement.z * toMob.z) / flat;
		return chasing(toward, player.distanceTo(mob));
	}

	/** A player chases when close, or when the player's movement toward the mob is fast enough. */
	public static boolean chasing(final double towardSpeed, final double distance) {
		return distance < CHASE_RANGE || towardSpeed >= CHASE_SPEED;
	}
}
