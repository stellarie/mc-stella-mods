package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.config.ScalingConfig;
import com.stella.mobs.config.ScalingConfigStore;
import com.stella.mobs.config.WitherConfig;
import com.stella.mobs.config.WitherConfigStore;
import com.stella.mobs.scaling.ThreatState;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * A Wither that joins a level fresh is an encounter: it is a levelled boss, set by the players present. A Wither read
 * from disk without a {@link WitherState} is a legacy one and stays vanilla.
 */
public final class WitherEncounter {
	private WitherEncounter() {
	}

	/**
	 * The summon level: the base, or the highest player level plus a margin, whichever is higher. Players count within
	 * the scaling floor radius. With none that near, the highest player in the level counts. Spectators never count.
	 */
	public static int levelFor(
		final List<? extends Player> players, final Vec3 at, final ScalingConfig scaling, final WitherConfig config
	) {
		double radiusSquared = (double) scaling.floorRadius() * scaling.floorRadius();
		int near = -1;
		int any = -1;
		for (Player player : players) {
			if (player.isSpectator()) {
				continue;
			}
			any = Math.max(any, player.experienceLevel);
			if (player.position().distanceToSqr(at) <= radiusSquared) {
				near = Math.max(near, player.experienceLevel);
			}
		}
		int highest = near >= 0 ? near : any;
		int base = config.getInt(WitherConfig.LEVEL_BASE);
		if (highest < 0) {
			return base;
		}
		return (int) Math.min(Integer.MAX_VALUE, Math.max(base, (long) highest + config.getInt(WitherConfig.LEVEL_OVER_PLAYER)));
	}

	/** Marks a fresh Wither as an encounter and fixes its level, before the spawn scaling reads it. */
	public static void begin(final Mob wither, final ServerLevel level) {
		int summonLevel = levelFor(level.players(), wither.position(), ScalingConfigStore.get(), WitherConfigStore.get());
		((AttachmentTarget) wither).setAttached(ThreatState.TYPE, new ThreatState(summonLevel, 0, 0, 0));
		WitherState.set(wither, new WitherState(false, 0.0));
		StellaMobs.LOGGER.info("[BEHAVIOR] test=witherEncounter BEGIN uuid={} level={}", wither.getUUID(), summonLevel);
	}

	public static void reportSpawn(final Mob mob) {
		if (WitherState.hasWitherFlag(mob)) {
			StellaMobs.LOGGER.info("[BEHAVIOR] test=witherEncounter SPAWN uuid={} level={} health={}",
				mob.getUUID(), ThreatState.level(mob), mob.getMaxHealth());
		}
	}
}
