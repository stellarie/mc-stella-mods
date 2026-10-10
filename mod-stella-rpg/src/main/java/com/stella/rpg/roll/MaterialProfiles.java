package com.stella.rpg.roll;

import com.stella.rpg.RpgConfig;
import com.stella.rpg.RpgConfigStore;
import com.stella.rpg.Stat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/** The rare roll of a mined drop. A rolled drop carries 1 to 4 bonuses drawn freely within the tier. */
public final class MaterialProfiles {
	public record Profile(Map<String, Integer> stats, Map<String, Integer> affixes) {
	}

	/** The gear tier whose affix ranges a material tier scales. */
	public static final Map<String, String> AFFIX_TIER = Map.of(
		"copper", "copper", "iron", "iron", "gold", "gold", "diamond", "diamond", "debris", "netherite"
	);

	/** The typed affixes a material can carry. */
	private static final List<Affix> AFFIX_POOL = List.of(
		Affix.DAMAGE_FLAT, Affix.DAMAGE_PCT, Affix.ARMOR_PEN, Affix.HP_FLAT, Affix.HP_PCT, Affix.REDUCTION_FLAT, Affix.REDUCTION_PCT,
		Affix.DURABILITY_PCT
	);

	private MaterialProfiles() {
	}

	/**
	 * The free roll of one rare drop. The count follows the tier weights and each value falls in the tier range.
	 * Depth skews the count and the values up and never lowers them.
	 */
	public static Profile roll(
		final String tier, final RpgConfig.MaterialRolls config, final Map<Affix, RpgConfig.AffixRange> gearAffixes,
		final double depth, final RandomSource random
	) {
		RpgConfig.MaterialTier row = config.tiers().get(tier);
		if (row == null) {
			return null;
		}
		double lift = config.depthRankWeight() * Math.max(0.0, Math.min(1.0, depth));
		int count = pickCount(row.countWeights(), lift, random);
		List<Stat> statPool = new ArrayList<>(List.of(Stat.values()));
		List<Affix> affixPool = new ArrayList<>();
		for (Affix affix : AFFIX_POOL) {
			if (gearAffixes.containsKey(affix)) {
				affixPool.add(affix);
			}
		}
		Map<String, Integer> stats = new LinkedHashMap<>();
		Map<String, Integer> affixes = new LinkedHashMap<>();
		for (int i = 0; i < count; i++) {
			boolean affix = !affixPool.isEmpty() && (statPool.isEmpty() || random.nextDouble() < config.affixShare());
			if (affix) {
				Affix chosen = affixPool.remove(random.nextInt(affixPool.size()));
				RpgConfig.AffixRange range = gearAffixes.get(chosen);
				double units = chosen.percent() ? Affix.TENTHS : 1;
				int low = (int) Math.max(1, Math.round(range.min() * units * config.affixScale()));
				int high = (int) Math.max(low, Math.round(range.max() * units * config.affixScale()));
				affixes.put(chosen.key(), skewed(low, high, lift, random));
			} else if (!statPool.isEmpty()) {
				Stat stat = statPool.remove(random.nextInt(statPool.size()));
				stats.put(stat.key(), skewed(row.min(), row.max(), lift, random));
			}
		}
		return new Profile(Map.copyOf(stats), Map.copyOf(affixes));
	}

	/** A value from {@code low} to {@code high}. A lift above 0 moves the draw toward the top of the range. */
	private static int skewed(final int low, final int high, final double lift, final RandomSource random) {
		double draw = Math.pow(random.nextDouble(), 1.0 / (1.0 + lift));
		return low + Math.min(high - low, (int) Math.floor(draw * (high - low + 1)));
	}

	/** Counts 1 to n by weight. A lift multiplies the weight of a larger count by up to {@code 1 + lift}. */
	private static int pickCount(final List<Integer> weights, final double lift, final RandomSource random) {
		int size = weights.size();
		double[] scaled = new double[size];
		double total = 0.0;
		for (int i = 0; i < size; i++) {
			scaled[i] = weights.get(i) * (1.0 + lift * (size == 1 ? 0.0 : i / (double) (size - 1)));
			total += scaled[i];
		}
		double pick = random.nextDouble() * total;
		for (int i = 0; i < size; i++) {
			pick -= scaled[i];
			if (pick < 0.0) {
				return i + 1;
			}
		}
		return size;
	}

	public static Map<Affix, RpgConfig.AffixRange> gearAffixes(final String tier) {
		RpgConfig.RollTier gear = RpgConfigStore.current().weaponRolls().tiers().get(AFFIX_TIER.getOrDefault(tier, tier));
		return gear == null ? Map.of() : gear.affixes();
	}

	/** The tier name of a mined drop, or null when the item is not listed in any tier. */
	public static String tierOf(final ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		for (Map.Entry<String, RpgConfig.MaterialTier> tier : RpgConfigStore.current().materialRolls().tiers().entrySet()) {
			if (tier.getValue().items().contains(id)) {
				return tier.getKey();
			}
		}
		return null;
	}
}
