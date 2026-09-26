package com.stella.mobs.behavior;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;

/**
 * An equal-weight random pick that never starves an entry: every entry comes out once, in a shuffled
 * order, before any comes out twice. A player meets the whole kit within one cycle.
 */
final class WitchBag<T> {
	private final List<T> entries;
	private final List<T> left = new ArrayList<>();

	WitchBag(final List<T> entries) {
		this.entries = List.copyOf(entries);
	}

	T next(final RandomSource random) {
		if (this.left.isEmpty()) {
			this.left.addAll(this.entries);
		}

		return this.left.remove(random.nextInt(this.left.size()));
	}
}
