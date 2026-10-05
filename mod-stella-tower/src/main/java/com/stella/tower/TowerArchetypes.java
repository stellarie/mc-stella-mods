package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Which archetype a floor takes: a pure function of the tower's seed and the floor number. Every fifth floor is a
 * {@link TowerArchetype#HALL}. An ordinary floor draws one of the {@link TowerArchetype#ORDINARY} archetypes
 * evenly, less the one the floor below it took, so no two floors in a row look alike. A hall floor breaks the
 * chain, so a floor is found by walking back at most four floors.
 */
public final class TowerArchetypes {
	private static final long SALT = 0x41524348L;

	private TowerArchetypes() {
	}

	public static TowerArchetype of(final long seed, final int floor) {
		if (TowerMaze.hall(floor) > 0) {
			return TowerArchetype.HALL;
		}
		TowerArchetype before = floor <= 1 ? null : of(seed, floor - 1);
		List<TowerArchetype> pool = new ArrayList<>(TowerArchetype.ORDINARY);
		pool.remove(before);
		return pool.get((int) (roll(seed, floor) * pool.size()));
	}

	private static double roll(final long seed, final int floor) {
		long z = seed ^ SALT ^ (floor * 0x9E3779B97F4A7C15L);
		z += 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		z ^= z >>> 31;
		return new Random(z).nextDouble();
	}
}
