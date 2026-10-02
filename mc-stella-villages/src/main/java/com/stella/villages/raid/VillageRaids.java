package com.stella.villages.raid;

import com.stella.villages.McStellaVillages;
import com.stella.villages.data.VillageWorldData;
import com.stella.villages.data.VillageWorldData.VillageRecord;
import com.stella.villages.mixin.RaidAccessor;
import com.stella.villages.mixin.RaidsInvoker;
import java.util.Comparator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * V3: one 10% roll a day for a single-wave raid at a village a player is standing in. The wave is a mini-raid
 * of {@link #MINI_RAID_MIN} to {@link #MINI_RAID_MAX} pillagers. A raid with one wave is a mini-raid, because
 * every vanilla raid has three waves or more.
 */
public final class VillageRaids {
	public static final double RAID_CHANCE = 0.10;
	public static final double ATTENDANCE_RANGE = 64.0;
	public static final long TICKS_PER_DAY = 24_000L;
	public static final int MINI_RAID_MIN = 3;
	public static final int MINI_RAID_MAX = 5;
	private static final long DAY_STRIDE = 0x9E3779B97F4A7C15L;
	private static final double UNIT = 0x1.0p-53;
	// True only while this mod builds a raid, so vanilla's own omen raids keep their wave count.
	private static final ThreadLocal<Boolean> ONE_WAVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

	public enum Outcome {
		FIRST_DAY,
		ALREADY_ROLLED,
		NO_RAID,
		RAID
	}

	private VillageRaids() {
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(VillageRaids::onLevelTick);
	}

	public static boolean oneWave() {
		return ONE_WAVE.get();
	}

	/** Vanilla raids never have one wave. */
	public static boolean isMiniRaid(final Raid raid) {
		return ((RaidAccessor) raid).mcStellaVillages$numGroups() == 1;
	}

	/** Like start, but not in the raid registry. */
	public static Raid newMiniRaid(final BlockPos center, final Difficulty difficulty) {
		ONE_WAVE.set(Boolean.TRUE);
		try {
			return new Raid(center, difficulty);
		} finally {
			ONE_WAVE.set(Boolean.FALSE);
		}
	}

	/**
	 * Only pillagers spawn.
	 * Their count is uniform in the mini-raid range.
	 */
	public static int miniRaidSpawns(final String raiderType, final RandomSource random) {
		return raiderType.equals("PILLAGER") ? MINI_RAID_MIN + random.nextInt(MINI_RAID_MAX - MINI_RAID_MIN + 1) : 0;
	}

	/** The day's roll, fixed by the world seed and the day, so a restart can never roll the same day again. */
	public static boolean rolls(final long seed, final long day) {
		return (mix(seed + day * DAY_STRIDE) >>> 11) * UNIT < RAID_CHANCE;
	}

	/** Records the day and says what it brings; the first day a world is seen only records it. */
	public static Outcome decide(final VillageWorldData data, final long day, final boolean rolled) {
		long last = data.lastAttemptedDay();
		if (last == day) {
			return Outcome.ALREADY_ROLLED;
		}

		data.setLastAttemptedDay(day);
		if (last < 0L) {
			return Outcome.FIRST_DAY;
		}

		return rolled ? Outcome.RAID : Outcome.NO_RAID;
	}

	/** The nearest living, non-spectator player within ATTENDANCE_RANGE of the village anchor, or null. */
	public static @Nullable ServerPlayer attendant(final ServerLevel level, final VillageRecord village) {
		Vec3 anchor = Vec3.atBottomCenterOf(village.anchor());
		return level.players().stream()
			.filter(player -> player.isAlive() && !player.isSpectator())
			.filter(player -> player.position().distanceTo(anchor) <= ATTENDANCE_RANGE)
			.min(Comparator.comparingDouble(player -> player.position().distanceToSqr(anchor)))
			.orElse(null);
	}

	/** Registers a one-wave raid at the village, unless a raid already holds that spot. */
	public static @Nullable Raid start(final ServerLevel level, final VillageRecord village) {
		if (level.getRaidAt(village.anchor()) != null) {
			return null;
		}

		Raids raids = level.getRaids();
		RaidsInvoker registry = (RaidsInvoker) raids;
		Raid raid;
		ONE_WAVE.set(Boolean.TRUE);
		try {
			raid = registry.mcStellaVillages$getOrCreateRaid(level, village.anchor());
		} finally {
			ONE_WAVE.set(Boolean.FALSE);
		}

		if (raid.isStarted() || registry.mcStellaVillages$raidMap().containsValue(raid)) {
			return null;
		}

		registry.mcStellaVillages$raidMap().put(registry.mcStellaVillages$getUniqueId(), raid);
		raid.setRaidOmenLevel(1);
		raids.setDirty();
		McStellaVillages.LOGGER.info(
			"[BEHAVIOR] mod={} event=village_raid_started village={} center={} omen=1",
			McStellaVillages.MOD_ID, village.id(), village.anchor()
		);
		return raid;
	}

	private static void onLevelTick(final ServerLevel level) {
		if (!level.dimension().equals(Level.OVERWORLD)) {
			return;
		}

		long day = level.getOverworldClockTime() / TICKS_PER_DAY;
		VillageWorldData data = VillageWorldData.get(level);
		if (data.lastAttemptedDay() == day) {
			return;
		}

		Outcome outcome = decide(data, day, rolls(level.getSeed(), day));
		String village = "none";
		if (outcome == Outcome.RAID) {
			VillageRecord attended = data.villages().stream()
				.filter(candidate -> candidate.dimension().equals(level.dimension()))
				.sorted(Comparator.comparing(candidate -> candidate.id().toString()))
				.filter(candidate -> attendant(level, candidate) != null)
				.findFirst()
				.orElse(null);
			// An unattended world skips the day rather than raid a village nobody is defending.
			village = attended == null ? "unattended" : String.valueOf(start(level, attended) == null ? "blocked" : attended.id());
		}

		McStellaVillages.LOGGER.info(
			"[BEHAVIOR] mod={} event=village_raid_roll day={} outcome={} village={}",
			McStellaVillages.MOD_ID, day, outcome, village
		);
	}

	private static long mix(final long value) {
		long z = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}
}
