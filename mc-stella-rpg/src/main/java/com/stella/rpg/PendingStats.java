package com.stella.rpg;

import java.util.Arrays;

/** Points a player has staged on the client but not yet sent. Holds no server state. */
public final class PendingStats {
	private final int[] pending = new int[Stat.values().length];

	public int get(final Stat stat) {
		return this.pending[stat.ordinal()];
	}

	public int total() {
		int sum = 0;
		for (int value : this.pending) {
			sum += value;
		}
		return sum;
	}

	public int plus(final Stat stat, final int amount, final int unspent, final int base) {
		int room = Math.min(unspent - this.total(), StatSheet.CEILING - base - this.pending[stat.ordinal()]);
		int applied = Math.max(0, Math.min(amount, room));
		this.pending[stat.ordinal()] += applied;
		return applied;
	}

	public int minus(final Stat stat, final int amount) {
		int applied = Math.max(0, Math.min(amount, this.pending[stat.ordinal()]));
		this.pending[stat.ordinal()] -= applied;
		return applied;
	}

	public void clear() {
		Arrays.fill(this.pending, 0);
	}

	/** Trims pending points that a fresh sync made impossible. */
	public void clamp(final int unspent, final int[] base) {
		for (Stat stat : Stat.values()) {
			int room = Math.max(0, StatSheet.CEILING - base[stat.ordinal()]);
			this.pending[stat.ordinal()] = Math.min(this.pending[stat.ordinal()], room);
		}
		int excess = this.total() - Math.max(0, unspent);
		for (int i = this.pending.length - 1; i >= 0 && excess > 0; i--) {
			int cut = Math.min(excess, this.pending[i]);
			this.pending[i] -= cut;
			excess -= cut;
		}
	}

	public int effective(final Stat stat, final int base, final int bonus) {
		int total = base + bonus + this.pending[stat.ordinal()];
		return Math.max(0, Math.min(StatBonuses.EFFECTIVE_CEILING, total));
	}

	public int[] toArray() {
		return this.pending.clone();
	}
}
