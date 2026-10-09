package com.stella.rpg.roll;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Pure arithmetic for stored bonus values. It holds no Minecraft state. */
public final class BonusMath {
	/** One key of one item never stores more than this. */
	public static final int KEY_CAP = 100000;

	private BonusMath() {
	}

	/** The sum of a stored value and a delta, clamped to {@code 0..KEY_CAP}. A long sum cannot overflow. */
	public static int addClamped(final int current, final long delta) {
		long sum = (long) current + delta;
		return (int) Math.max(0L, Math.min(KEY_CAP, sum));
	}

	/** The key-wise sum of the maps. A key that sums to zero is dropped. */
	public static Map<String, Integer> sum(final List<Map<String, Integer>> maps) {
		Map<String, Integer> total = new LinkedHashMap<>();
		for (Map<String, Integer> map : maps) {
			for (Map.Entry<String, Integer> entry : map.entrySet()) {
				int value = addClamped(total.getOrDefault(entry.getKey(), 0), entry.getValue());
				if (value > 0) {
					total.put(entry.getKey(), value);
				}
			}
		}
		return total;
	}

	/** Floor division by the result count. A key that falls to zero is dropped. */
	public static Map<String, Integer> split(final Map<String, Integer> map, final int parts) {
		Map<String, Integer> out = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> entry : map.entrySet()) {
			int value = entry.getValue() / Math.max(1, parts);
			if (value > 0) {
				out.put(entry.getKey(), value);
			}
		}
		return out;
	}

	public static Map<String, Integer> keepKeys(final Map<String, Integer> map, final Predicate<String> keep) {
		Map<String, Integer> out = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> entry : map.entrySet()) {
			if (keep.test(entry.getKey())) {
				out.put(entry.getKey(), entry.getValue());
			}
		}
		return out;
	}

	public static int total(final Map<String, Integer> map) {
		long total = 0;
		for (int value : map.values()) {
			total += Math.max(0, value);
		}
		return (int) Math.min(Integer.MAX_VALUE, total);
	}
}
