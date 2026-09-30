package com.stella.mobs.scaling;

import com.stella.mobs.config.ScalingConfig;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** The lowest level a new mob may roll: the lowest nearby player's level minus an offset. */
public final class LevelFloor {
	private LevelFloor() {
	}

	/** Only players in the mob's own level count, so another dimension never sets a floor. */
	public static int floorFor(final Mob mob, final ScalingConfig config) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return 0;
		}
		return floorOf(mob.position(), level.players(), config);
	}

	public static int floorOf(final Vec3 at, final List<? extends Player> players, final ScalingConfig config) {
		double radiusSquared = (double) config.floorRadius() * config.floorRadius();
		int lowest = Integer.MAX_VALUE;
		for (Player player : players) {
			if (player.isSpectator() || player.position().distanceToSqr(at) > radiusSquared) {
				continue;
			}
			lowest = Math.min(lowest, player.experienceLevel);
		}
		return lowest == Integer.MAX_VALUE ? 0 : Math.max(0, lowest - config.floorOffset());
	}
}
