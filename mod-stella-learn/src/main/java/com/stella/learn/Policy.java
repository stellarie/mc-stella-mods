package com.stella.learn;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;

/**
 * What one player's mobs have learned works against that player: for each kind of mob, each situation and each tactic, the
 * average outcome so far and how many times it has been tried. A value moves a fixed share of the way to each new outcome, so
 * recent outcomes count for more and a player who changes their ways is followed.
 */
public record Policy(Map<String, Entry> entries) {
	public static final Policy EMPTY = new Policy(Map.of());
	public static final Codec<Policy> CODEC = Codec.unboundedMap(Codec.STRING, Entry.CODEC).xmap(Policy::new, Policy::entries);

	/** The learned value of one tactic and the number of outcomes behind it. */
	public record Entry(double value, int tries) {
		public static final Codec<Entry> CODEC = Codec.DOUBLE.listOf(2, 2).xmap(
			list -> new Entry(list.get(0), (int) (double) list.get(1)),
			entry -> java.util.List.of(entry.value(), (double) entry.tries())
		);
	}

	public static String key(final String mobKind, final Situation situation, final Tactic tactic) {
		return mobKind + "|" + situation + "|" + tactic;
	}

	public Entry get(final String key) {
		return this.entries.getOrDefault(key, new Entry(0.0, 0));
	}

	/** Every outcome seen for this kind of mob in this situation, over all its tactics. */
	public int total(final String mobKind, final Situation situation) {
		String prefix = mobKind + "|" + situation + "|";
		return this.entries.entrySet().stream().filter(entry -> entry.getKey().startsWith(prefix)).mapToInt(entry -> entry.getValue().tries()).sum();
	}

	/** The policy after one more outcome for this key: the value moves `alpha` of the way to `reward`. */
	public Policy with(final String key, final double reward, final double alpha) {
		Entry old = get(key);
		Map<String, Entry> next = copy();
		next.put(key, new Entry(old.value() + alpha * (reward - old.value()), old.tries() + 1));
		return new Policy(next);
	}

	Map<String, Entry> copy() {
		return new HashMap<>(this.entries);
	}
}
