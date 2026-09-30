package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Which variation a floor takes: a pure function of the tower's seed and the floor number.
 * <p>
 * The rules: floor 1 is always the plain maze; a floor never takes the variation of the floor before it; an elite
 * floor never takes the spiral, and a boss floor neither that nor wide rooms. Among what is left a floor draws by the
 * variations' weights.
 * <p>
 * Never repeating the last floor's variation, drawn naively from the weights, pulls every share toward an even one
 * (the plain maze's 35 would come out near 30). So a floor after another draws among the variations it may take, not
 * by the weights but by a second set, {@link #lambda}, worked out once so that a chain of floors that each draw so,
 * from what the floor before left, settles to exactly the weights: the chain is reversible, with the pair
 * (from a, to b) moving at a rate proportional to lambda(a) * lambda(b), and the lambdas are the roots of
 * {@code l(a) * (sum - l(a)) = weight(a)}.
 * <p>
 * The floor before is itself decided the same way, so a floor's variation is found by walking a chain. The chain is
 * cut into blocks of {@link #BLOCK} floors: the first floor of a block draws by the weights alone, and the last
 * floor of a block also keeps clear of the variation the next block's first floor drew, which is known without
 * walking. So the most any floor costs is one block, and the rule holds across every block's edge too.
 */
final class TowerVariations {
	/** The floors in a block of the chain. */
	static final int BLOCK = 64;
	private static final long SALT = 0x5641524CL;
	private static final TowerPlan.Variation[] ALL = TowerPlan.Variation.values();
	private static final double[] LAMBDA = lambda();

	private TowerVariations() {
	}

	/** The share of floors a variation is drawn for, among all five: its weight over the sum. */
	static double share(final TowerPlan.Variation variation) {
		double sum = 0.0;
		for (TowerPlan.Variation each : ALL) {
			sum += each.weight();
		}
		return variation.weight() / sum;
	}

	/** The variations a floor may take at all, by its rank: not the spiral on an elite or boss floor, nor wide rooms on a boss floor. */
	static List<TowerPlan.Variation> allowed(final int floor) {
		int hall = TowerMaze.hall(floor);
		List<TowerPlan.Variation> allowed = new ArrayList<>();
		for (TowerPlan.Variation each : ALL) {
			boolean barred = hall > 0 && each == TowerPlan.Variation.SPIRAL
				|| hall == TowerMaze.BOSS_HALL && each == TowerPlan.Variation.WIDE_ROOMS;
			if (!barred) {
				allowed.add(each);
			}
		}
		return allowed;
	}

	/** The variation of a floor in a tower of this seed. */
	static TowerPlan.Variation of(final long seed, final int floor) {
		if (floor <= 1) {
			return TowerPlan.Variation.MAZE;
		}
		int first = 1 + (floor - 1) / BLOCK * BLOCK;
		TowerPlan.Variation last = first(seed, first);
		for (int at = first + 1; at <= floor; at++) {
			TowerPlan.Variation keepClear = at == first + BLOCK - 1 ? first(seed, first + BLOCK) : null;
			last = step(seed, at, last, keepClear);
		}
		return last;
	}

	/** The first floor of a block: drawn by the weights alone (the plain maze for floor 1). */
	private static TowerPlan.Variation first(final long seed, final int floor) {
		if (floor <= 1) {
			return TowerPlan.Variation.MAZE;
		}
		List<TowerPlan.Variation> pool = allowed(floor);
		double total = 0.0;
		for (TowerPlan.Variation each : pool) {
			total += each.weight();
		}
		double roll = roll(seed, floor) * total;
		for (TowerPlan.Variation each : pool) {
			roll -= each.weight();
			if (roll < 0.0) {
				return each;
			}
		}
		return pool.get(pool.size() - 1);
	}

	/** One floor after another: drawn by the lambdas among what it may take, less what the floor before had and, at a block's end, what the next block opens with. */
	private static TowerPlan.Variation step(final long seed, final int floor, final TowerPlan.Variation before, final TowerPlan.Variation keepClear) {
		List<TowerPlan.Variation> pool = new ArrayList<>(allowed(floor));
		pool.remove(before);
		pool.remove(keepClear);
		if (pool.isEmpty()) {
			return TowerPlan.Variation.MAZE;
		}
		double total = 0.0;
		for (TowerPlan.Variation each : pool) {
			total += LAMBDA[each.ordinal()];
		}
		double roll = roll(seed, floor) * total;
		for (TowerPlan.Variation each : pool) {
			roll -= LAMBDA[each.ordinal()];
			if (roll < 0.0) {
				return each;
			}
		}
		return pool.get(pool.size() - 1);
	}

	private static double roll(final long seed, final int floor) {
		long z = seed ^ SALT ^ (floor * 0x9E3779B97F4A7C15L);
		z += 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		z ^= z >>> 31;
		return new Random(z).nextDouble();
	}

	/** The second set of weights, one to a variation, by ordinal, for a floor that follows another. */
	static double[] lambda() {
		int n = ALL.length;
		double[] w = new double[n];
		double sum = 0.0;
		double top = 0.0;
		for (int i = 0; i < n; i++) {
			w[i] = ALL[i].weight();
			sum += w[i];
		}
		for (int i = 0; i < n; i++) {
			w[i] /= sum;
			top = Math.max(top, w[i]);
		}
		// The roots are real only from sum >= 2 * sqrt(top weight); the total of the roots equals the sum at one point past it.
		double low = 2.0 * Math.sqrt(top);
		double high = low + 4.0;
		for (int step = 0; step < 200; step++) {
			double mid = (low + high) / 2.0;
			double roots = 0.0;
			for (int i = 0; i < n; i++) {
				roots += (mid - Math.sqrt(Math.max(0.0, mid * mid - 4.0 * w[i]))) / 2.0;
			}
			if (roots > mid) {
				low = mid;
			} else {
				high = mid;
			}
		}
		double total = (low + high) / 2.0;
		double[] lambda = new double[n];
		for (int i = 0; i < n; i++) {
			lambda[i] = (total - Math.sqrt(Math.max(0.0, total * total - 4.0 * w[i]))) / 2.0;
		}
		return lambda;
	}
}
