package com.stella.mobs.gear;

import java.util.List;
import java.util.Optional;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedRandom;

public final class TierRoller {
	private TierRoller() {
	}

	public static List<GearTier> tiers() {
		return List.of(GearTier.values());
	}

	public static int totalWeight(final List<GearTier> tiers) {
		return WeightedRandom.getTotalWeight(tiers, GearTier::weight);
	}

	public static GearTier pick(final RandomSource random) {
		List<GearTier> tiers = tiers();
		return pick(tiers, totalWeight(tiers), random);
	}

	public static GearTier pick(final List<GearTier> tiers, final int totalWeight, final RandomSource random) {
		Optional<GearTier> tier = WeightedRandom.getRandomItem(random, tiers, totalWeight, GearTier::weight);
		return tier.orElseThrow(() -> new IllegalStateException("No gear tier carries a weight"));
	}
}
