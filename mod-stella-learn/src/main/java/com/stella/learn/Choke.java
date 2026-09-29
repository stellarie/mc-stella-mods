package com.stella.learn;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Finds the narrow gap on a mob's way to a player, and a place to gather before it. It walks the path the mob would
 * take and looks for the first node with solid ground-level blocks on both sides of the way through: a doorway, a
 * one-wide corridor, a gap in a wall.
 */
public final class Choke {
	/** Path nodes before the gap at which a group gathers. */
	public static final int STAGING_BACK = 5;
	/** Blocks from the staging point within which a mob counts as gathered. */
	public static final double GATHER_RADIUS = 5.0;
	/** Blocks from the player inside which a narrow spot is the player's own, not a way in to be held. */
	public static final double MINIMUM_FROM_PLAYER = 3.0;

	private Choke() {
	}

	/** The gap on the way in, and where to gather before it. */
	public record Mouth(BlockPos gap, Vec3 staging) {
	}

	/** How much of a full retreater this player is, from 0 to 1. */
	public static double retreatPressure(final ServerPlayer player) {
		return Mth.clamp(Profiles.value(player, Habit.RETREAT) / LearnConfigStore.get().retreatSaturation(), 0.0, 1.0);
	}

	public static @Nullable Mouth findMouth(final Mob mob, final ServerPlayer player) {
		Path path = mob.getNavigation().createPath(player, 0);
		if (path == null || path.getNodeCount() <= STAGING_BACK + 1) {
			return null;
		}

		for (int i = STAGING_BACK; i < path.getNodeCount() - 1; i++) {
			BlockPos here = path.getNode(i).asBlockPos();
			if (Vec3.atCenterOf(here).distanceTo(player.position()) < MINIMUM_FROM_PLAYER) {
				return null;
			}

			if (narrow(mob.level(), here, path.getNode(i + 1))) {
				Node back = path.getNode(i - STAGING_BACK);
				return new Mouth(here, new Vec3(back.x + 0.5, back.y, back.z + 0.5));
			}
		}

		return null;
	}

	/** True when solid blocks stand on both sides of the way through this node, at foot or head height. */
	static boolean narrow(final Level level, final BlockPos here, final Node next) {
		int dx = next.x - here.getX();
		int dz = next.z - here.getZ();
		if (dx == 0 && dz == 0) {
			return false;
		}

		// The side axis is the one the way through does not run along.
		int sideX = Math.abs(dx) >= Math.abs(dz) ? 0 : 1;
		int sideZ = 1 - sideX;
		return blocked(level, here.offset(sideX, 0, sideZ)) && blocked(level, here.offset(-sideX, 0, -sideZ));
	}

	private static boolean blocked(final Level level, final BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
			|| !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
	}
}
