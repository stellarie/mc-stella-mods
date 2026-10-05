package com.stella.ruins.world;

import java.util.ArrayList;
import java.util.List;

/** The themed mobs a site spawns, as kinds and counts. */
public final class Roster {
	private static final Roster EMPTY = new Roster(List.of());

	/** A kind and how many of it. */
	public record Entry(RuinMobKind kind, int count) {
		public Entry {
			if (count < 1) {
				throw new IllegalArgumentException("a roster entry needs at least one mob");
			}
		}
	}

	private final List<Entry> entries;

	private Roster(final List<Entry> entries) {
		this.entries = List.copyOf(entries);
	}

	public static Roster empty() {
		return EMPTY;
	}

	public static Builder builder() {
		return new Builder();
	}

	public List<Entry> entries() {
		return this.entries;
	}

	/** The total number of mobs. */
	public int size() {
		return this.entries.stream().mapToInt(Entry::count).sum();
	}

	public boolean isEmpty() {
		return this.entries.isEmpty();
	}

	public static final class Builder {
		private final List<Entry> entries = new ArrayList<>();

		public Builder add(final RuinMobKind kind, final int count) {
			this.entries.add(new Entry(kind, count));
			return this;
		}

		public Roster build() {
			return this.entries.isEmpty() ? EMPTY : new Roster(this.entries);
		}
	}
}
