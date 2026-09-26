package com.stella.villages.raid;

import com.stella.villages.data.VillageWorldData;
import com.stella.villages.data.VillageWorldData.VillageRecord;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.raid.Raid;
import org.jspecify.annotations.Nullable;

/** V3: one 10% roll a day for a single-wave raid at a village a player is standing in. */
public final class VillageRaids {
	public static final double RAID_CHANCE = 0.10;
	public static final double ATTENDANCE_RANGE = 64.0;

	public enum Outcome {
		FIRST_DAY,
		ALREADY_ROLLED,
		NO_RAID,
		RAID
	}

	private VillageRaids() {
	}

	public static void register() {
	}

	public static boolean oneWave() {
		return false;
	}

	public static boolean rolls(final long seed, final long day) {
		return false;
	}

	public static Outcome decide(final VillageWorldData data, final long day, final boolean rolled) {
		return Outcome.NO_RAID;
	}

	public static @Nullable ServerPlayer attendant(final ServerLevel level, final VillageRecord village) {
		return null;
	}

	public static @Nullable Raid start(final ServerLevel level, final VillageRecord village) {
		return null;
	}
}
