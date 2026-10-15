package com.stella.mobs.scaling;

import com.stella.mobs.StellaMobs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * A friendly NPC another mod owns levels with the nearest player: its level is the higher of that player's level and
 * the area level where it stands. The scaling is {@link SpawnScaling#setLevel}, so its health and damage follow the
 * same curve, tier spikes included, as a hostile mob of that level.
 */
public final class FriendlyLevels {
	private FriendlyLevels() {
	}

	/** Only players in the mob's own level count. A spectator never does. */
	public static int levelFor(final Mob mob) {
		int area = SpawnScaling.areaLevel(mob);
		if (!(mob.level() instanceof ServerLevel level)) {
			return area;
		}
		Player nearest = level.getNearestPlayer(mob.getX(), mob.getY(), mob.getZ(), -1.0, entity -> !entity.isSpectator());
		return nearest == null ? area : Math.max(area, nearest.experienceLevel);
	}

	/** Claims the mob from the natural roll, then levels it. Returns true when its level changed. */
	public static boolean refresh(final Mob mob) {
		mob.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		mob.addTag(StellaMobs.FRIENDLY_LEVEL_TAG);
		return SpawnScaling.setLevel(mob, Math.min(SpawnScaling.MAX_FIXED_LEVEL, levelFor(mob)));
	}
}
