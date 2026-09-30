package com.stella.learn.gym;

import com.stella.learn.Habit;
import com.stella.learn.LearnClock;
import com.stella.learn.LearnConfig;
import com.stella.learn.LearnConfigStore;
import com.stella.learn.LearnLog;
import com.stella.learn.PlayerProfile;
import com.stella.learn.Profiles;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * Gives the bot a random player profile in a share of episodes, so the model learns to read the nine profile inputs
 * from data that has nothing to do with the bot's style. The other episodes start empty. The Phase 1 recorders fill the
 * profile during the fight, because a bot is a real player.
 */
final class ProfileNoise {
	static final double SHARE = 0.3;

	private ProfileNoise() {
	}

	/** Sets the profile for a new episode. Returns true when it is a noisy one. */
	static boolean apply(final ServerPlayer player, final RandomSource random, final boolean allowed, final int arena, final long seed) {
		if (!allowed || random.nextDouble() >= SHARE) {
			Profiles.set(player, PlayerProfile.EMPTY);
			return false;
		}

		LearnConfig config = LearnConfigStore.get();
		long now = LearnClock.now();
		Map<Habit, PlayerProfile.Counter> counters = new EnumMap<>(Habit.class);
		StringBuilder detail = new StringBuilder();
		for (Habit habit : Habit.values()) {
			double value = random.nextDouble() * saturation(config, habit);
			counters.put(habit, new PlayerProfile.Counter(value, now));
			detail.append(' ').append(habit.name().toLowerCase(java.util.Locale.ROOT)).append('=').append(LearnLog.num(value));
		}

		Profiles.set(player, new PlayerProfile(counters));
		GymLog.info(GymLog.PROFILE, "NOISE", "arena=" + arena + " seed=" + seed + " " + LearnLog.who(player) + detail);
		return true;
	}

	private static double saturation(final LearnConfig config, final Habit habit) {
		return switch (habit) {
			case BLOCK -> config.shieldSaturation();
			case MELEE -> config.rushSaturation();
			case RANGED -> config.rangedSaturation();
			case RETREAT -> config.retreatSaturation();
			case STRAFE_LEFT, STRAFE_RIGHT, CHARGE, KITE -> config.moveSaturation();
			case CRIT -> config.critSaturation();
		};
	}
}
