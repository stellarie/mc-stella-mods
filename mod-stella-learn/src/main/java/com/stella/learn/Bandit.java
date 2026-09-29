package com.stella.learn;

import java.util.List;
import net.minecraft.util.RandomSource;

/**
 * Picks a tactic from what has been learned: one never tried is tried first, and after that the best known one, except
 * that a share of the time, which shrinks with experience to a floor, it tries a random one.
 */
public final class Bandit {
	/** The share of decisions that explore for a mob with no experience. */
	public static final double START_EPSILON = 0.3;
	/** Outcomes at which the exploration above the floor has halved. */
	public static final double HALF_EXPERIENCE = 20.0;

	private Bandit() {
	}

	/** Why a tactic was chosen: `untried`, `explore` or `greedy`. */
	public record Choice(Tactic tactic, String why, double epsilon) {
	}

	/** The share of decisions that explore after `total` outcomes, never below `floor`. */
	public static double epsilon(final int total, final double floor) {
		return floor + (START_EPSILON - floor) / (1.0 + total / HALF_EXPERIENCE);
	}

	public static Choice choose(
		final Policy policy, final String mobKind, final Situation situation, final List<Tactic> available, final double epsilon, final RandomSource random
	) {
		List<Tactic> untried = available.stream().filter(tactic -> policy.get(Policy.key(mobKind, situation, tactic)).tries() == 0).toList();
		if (!untried.isEmpty()) {
			return new Choice(untried.get(random.nextInt(untried.size())), "untried", epsilon);
		}

		if (random.nextDouble() < epsilon) {
			return new Choice(available.get(random.nextInt(available.size())), "explore", epsilon);
		}

		Tactic best = available.get(0);
		for (Tactic tactic : available) {
			if (policy.get(Policy.key(mobKind, situation, tactic)).value() > policy.get(Policy.key(mobKind, situation, best)).value()) {
				best = tactic;
			}
		}

		return new Choice(best, "greedy", epsilon);
	}
}
