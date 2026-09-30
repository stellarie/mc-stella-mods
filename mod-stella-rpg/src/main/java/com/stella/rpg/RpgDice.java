package com.stella.rpg;

import net.minecraft.util.RandomSource;

public final class RpgDice {
	public record Dice(int count, int sides) {
		public int roll(final RandomSource random) {
			return this.roll(random, 0.0);
		}

		/** Each die rolls its maximum with the chance, and a fair roll otherwise. */
		public int roll(final RandomSource random, final double maxChance) {
			int total = 0;
			for (int i = 0; i < this.count; i++) {
				total += maxChance > 0.0 && random.nextDouble() < maxChance ? this.sides : 1 + random.nextInt(this.sides);
			}
			return total;
		}

		public int min() {
			return this.count;
		}

		public int max() {
			return this.count * this.sides;
		}

		public double mean() {
			return this.count * (this.sides + 1) / 2.0;
		}

		@Override
		public String toString() {
			return this.count + "d" + this.sides;
		}
	}

	private RpgDice() {
	}

	public static Dice of(final int stat) {
		RpgConfig.DiceRules rules = RpgConfigStore.current().dice();
		int s = Math.max(0, stat);
		return new Dice(rules.baseCount() + s / rules.countStep(), rules.baseSides() + s / rules.sidesStep());
	}
}
