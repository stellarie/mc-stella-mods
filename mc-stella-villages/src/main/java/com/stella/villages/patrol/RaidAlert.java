package com.stella.villages.patrol;

import com.stella.villages.McStellaVillages;
import com.stella.villages.raid.VillageRaids;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The raid alert. While a raid threatens a village, each guard hunts the nearest raider within
 * {@link #ALERT_RANGE} blocks of the village bell, whether or not that raider has a target. Two illagers are allies
 * in vanilla, so no vanilla target goal ever picks a raider for a guard. Any raid counts: a mini-raid, or a
 * vanilla raid that a Raid Omen started.
 */
public final class RaidAlert {
	public static final double ALERT_RANGE = 48.0;
	/** Ticks between raid checks of one guard. */
	static final int CHECK_INTERVAL_TICKS = 5;
	private static final Map<UUID, Raid> ALERTS = new ConcurrentHashMap<>();

	private RaidAlert() {
	}

	/** The map holds raids, so a stop clears it. */
	public static void clear() {
		ALERTS.clear();
	}

	/** Vanilla's raid lookup reaches 96 blocks. */
	static @Nullable Raid activeRaid(final ServerLevel level, final BlockPos bell) {
		Raid raid = level.getRaidAt(bell);
		return raid != null && raid.isStarted() && !raid.isOver() ? raid : null;
	}

	static boolean inRange(final BlockPos bell, final Entity raider) {
		return raider.position().distanceToSqr(Vec3.atCenterOf(bell)) <= ALERT_RANGE * ALERT_RANGE;
	}

	/**
	 * Range counts from the bell.
	 * Nearness counts from the guard.
	 */
	static @Nullable Raider nearestRaider(final Mob guard, final Raid raid, final BlockPos bell) {
		Raider nearest = null;
		double best = Double.MAX_VALUE;
		for (Raider raider : raid.getAllRaiders()) {
			if (!raider.isAlive() || raider.level() != guard.level() || !inRange(bell, raider)) {
				continue;
			}

			double distance = raider.distanceToSqr(guard);
			if (distance < best) {
				best = distance;
				nearest = raider;
			}
		}

		return nearest;
	}

	/**
	 * Logs when a village first sees a raid,
	 * and when it first sees the raid end.
	 */
	static void note(final UUID villageId, final BlockPos bell, final @Nullable Raid raid) {
		if (raid != null) {
			if (ALERTS.put(villageId, raid) != raid) {
				McStellaVillages.LOGGER.info(
					"[BEHAVIOR] mod={} event=raid_alert_started village={} bell={} raiders={} mini_raid={}",
					McStellaVillages.MOD_ID, villageId, bell, raid.getTotalRaidersAlive(), VillageRaids.isMiniRaid(raid)
				);
			}
		} else if (ALERTS.remove(villageId) != null) {
			McStellaVillages.LOGGER.info(
				"[BEHAVIOR] mod={} event=raid_alert_ended village={} bell={}", McStellaVillages.MOD_ID, villageId, bell
			);
		}
	}
}
