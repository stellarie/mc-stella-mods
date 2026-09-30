package com.stella.learn;

import com.mojang.serialization.Codec;
import java.util.EnumMap;
import java.util.Map;

/** What one player has been seen doing: counters that halve every half life and never grow from a clock that runs backward. */
public record PlayerProfile(Map<Habit, Counter> counters) {
	public static final PlayerProfile EMPTY = new PlayerProfile(Map.of());
	public static final Codec<PlayerProfile> CODEC = Codec.unboundedMap(Codec.STRING, Counter.CODEC).xmap(
		names -> {
			Map<Habit, Counter> counters = new EnumMap<>(Habit.class);
			names.forEach((name, counter) -> {
				try {
					counters.put(Habit.valueOf(name), counter);
				} catch (IllegalArgumentException ignored) {
					// An unknown habit in a saved profile is skipped.
				}
			});
			return new PlayerProfile(counters);
		},
		profile -> {
			Map<String, Counter> names = new java.util.HashMap<>();
			profile.counters().forEach((habit, counter) -> names.put(habit.name(), counter));
			return names;
		}
	);

	/** A counter's value as of `stamp`, a server tick. */
	public record Counter(double value, long stamp) {
		public static final Codec<Counter> CODEC = Codec.DOUBLE.listOf(2, 2).xmap(
			list -> new Counter(list.get(0), (long) (double) list.get(1)),
			counter -> java.util.List.of(counter.value(), (double) counter.stamp())
		);
	}

	public double value(final Habit habit, final long now, final long halfLifeTicks) {
		Counter counter = this.counters.get(habit);
		if (counter == null) {
			return 0.0;
		}

		double elapsed = Math.max(0L, now - counter.stamp());
		return counter.value() * Math.pow(0.5, elapsed / halfLifeTicks);
	}

	/** The old count is decayed to `now` first, so a fresh record never revives a forgotten habit. */
	public PlayerProfile record(final Habit habit, final double amount, final long now, final long halfLifeTicks) {
		Map<Habit, Counter> next = new EnumMap<>(Habit.class);
		next.putAll(this.counters);
		Counter old = this.counters.get(habit);
		long stamp = old == null ? now : Math.max(now, old.stamp());
		next.put(habit, new Counter(value(habit, stamp, halfLifeTicks) + amount, stamp));
		return new PlayerProfile(next);
	}
}
