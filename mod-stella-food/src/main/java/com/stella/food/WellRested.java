package com.stella.food;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Waking at dawn or in the morning after a full sleep gives Well Rested for {@link #DURATION_TICKS}: kills give
 * {@link #XP_FACTOR} times the experience. A nap during the day, or a sleep cut short, gives nothing. A night that
 * nobody skips ends at dawn, when the game wakes sleepers itself, so dawn counts as morning.
 */
public final class WellRested {
	public static final int DURATION_TICKS = 24000;
	public static final double XP_FACTOR = 1.2;
	/** A full sleep: the vanilla sleep timer that lets the night pass. */
	public static final int FULL_SLEEP_TICKS = 100;
	/** Waking before this day time counts as waking in the morning. */
	public static final long MORNING_END = 2000L;
	/** Waking after this day time counts too: the game wakes sleepers at about 23,470. */
	public static final long DAWN_START = 23000L;
	private static final long DAY_TICKS = 24000L;

	private WellRested() {
	}

	public static void register() {
		EntitySleepEvents.STOP_SLEEPING.register((entity, sleepingPos) -> {
			if (entity instanceof ServerPlayer player && earned(player.getSleepTimer(), player.level().getOverworldClockTime())) {
				grant(player);
			}
		});
	}

	/** True for a full sleep that ends at dawn or in the morning. */
	public static boolean earned(final int sleptTicks, final long clockTime) {
		long time = Math.floorMod(clockTime, DAY_TICKS);
		return sleptTicks >= FULL_SLEEP_TICKS && (time < MORNING_END || time >= DAWN_START);
	}

	public static void grant(final ServerPlayer player) {
		player.addEffect(new MobEffectInstance(FoodEffects.WELL_RESTED, DURATION_TICKS, 0, false, false, true));
	}

	/** The experience a kill gives a Well Rested killer; the fraction rounds up by chance. */
	public static int boosted(final int experience, final RandomSource random) {
		double scaled = experience * XP_FACTOR;
		int whole = Mth.floor(scaled);
		return whole + (random.nextDouble() < scaled - whole ? 1 : 0);
	}
}
