package com.stella.rpg.roll;

import java.util.Map;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/** The durability affix. A stored value of {@code p} percent keeps the expected life {@code 1 + p / 100} times longer. */
public final class Durability {
	private Durability() {
	}

	/** The use damage left after each point is skipped with chance {@code p / (100 + p)}. */
	public static int reduce(final int points, final ItemStack stack, final RandomSource random) {
		Map<String, Integer> affixes = stack.get(WeaponRolls.AFFIX_ROLLS);
		Integer stored = affixes == null ? null : affixes.get(Affix.DURABILITY_PCT.key());
		if (stored == null || stored <= 0 || points <= 0) {
			return points;
		}
		double percent = Affix.DURABILITY_PCT.shown(stored);
		double skip = percent / (100.0 + percent);
		int left = 0;
		for (int i = 0; i < points; i++) {
			if (random.nextDouble() >= skip) {
				left++;
			}
		}
		return left;
	}
}
