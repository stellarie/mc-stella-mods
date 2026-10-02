package com.stella.learn;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Finds a spot where a mob is out of a shooter's sight. A melee mob wants one nearer to the shooter than it stands
 * now, so it advances from cover to cover. A ranged mob wants one no nearer, so hiding never walks it at the shooter.
 * The search samples rings around the mob on the level it stands on, and takes the closest spot that qualifies.
 */
public final class Cover {
	/** Blocks the mob must gain on the player by moving to the spot, so cover is a step forward and not a hiding place. */
	public static final double MINIMUM_GAIN = 1.0;
	private static final double[] RADII = {2.5, 4.0, 5.5, 7.0, 8.0};
	private static final int ANGLES = 12;

	private Cover() {
	}

	/** How much of a full shooter this player is, from 0 to 1. */
	public static double shooterPressure(final ServerPlayer player) {
		return Mth.clamp(Profiles.value(player, Habit.RANGED) / LearnConfigStore.get().rangedSaturation(), 0.0, 1.0);
	}

	/** True when nothing solid stands between the player's eyes and this point. */
	public static boolean exposed(final Level level, final ServerPlayer player, final Vec3 eyeOfMob) {
		ClipContext context = new ClipContext(player.getEyePosition(), eyeOfMob, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
		return level.clip(context).getType() == HitResult.Type.MISS;
	}

	/**
	 * Cover for a melee mob.
	 * The spot is out of sight and `MINIMUM_GAIN` blocks nearer the player.
	 */
	public static @Nullable Vec3 find(final Mob mob, final ServerPlayer player) {
		return search(mob, player, true);
	}

	/**
	 * Cover for a ranged mob.
	 * The spot is out of sight and no nearer the player than the mob stands.
	 */
	public static @Nullable Vec3 findHide(final Mob mob, final ServerPlayer player) {
		return search(mob, player, false);
	}

	private static @Nullable Vec3 search(final Mob mob, final ServerPlayer player, final boolean advance) {
		Level level = mob.level();
		double now = mob.position().distanceTo(player.position());
		Vec3 best = null;
		double bestWalk = Double.MAX_VALUE;

		for (double radius : RADII) {
			for (int i = 0; i < ANGLES; i++) {
				double angle = Math.PI * 2.0 * i / ANGLES;
				Vec3 spot = new Vec3(mob.getX() + Math.cos(angle) * radius, mob.getY(), mob.getZ() + Math.sin(angle) * radius);
				double gain = now - spot.distanceTo(player.position());
				if ((advance ? gain < MINIMUM_GAIN : gain > 0.0) || !standable(level, spot)) {
					continue;
				}

				if (!exposed(level, player, spot.add(0.0, mob.getEyeHeight(), 0.0)) && radius < bestWalk) {
					best = spot;
					bestWalk = radius;
				}
			}
		}

		return best;
	}

	private static boolean standable(final Level level, final Vec3 spot) {
		BlockPos feet = BlockPos.containing(spot);
		return !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()
			&& level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
			&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
	}
}
