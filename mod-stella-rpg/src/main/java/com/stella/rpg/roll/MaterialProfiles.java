package com.stella.rpg.roll;

import com.stella.rpg.RpgConfig;
import com.stella.rpg.RpgConfigStore;
import com.stella.rpg.Stat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * The fixed bonus sets a mined material can roll. A mined stack copies one profile, so equal rolls are equal maps
 * and stack together. The count of profiles per tier is the cap on stack variety.
 */
public final class MaterialProfiles {
	/** One bonus set. {@code worth} ranks the sets by their strength. */
	public record Profile(Map<String, Integer> stats, Map<String, Integer> affixes, double worth) {
	}

	/** The gear tier whose affix ranges a material tier scales. */
	public static final Map<String, String> AFFIX_TIER = Map.of(
		"copper", "copper", "iron", "iron", "gold", "gold", "diamond", "diamond", "debris", "netherite"
	);
	/** The typed affixes a material can carry. */
	private static final List<Affix> AFFIX_POOL = List.of(
		Affix.DAMAGE_FLAT, Affix.DAMAGE_PCT, Affix.ARMOR_PEN, Affix.HP_FLAT, Affix.HP_PCT, Affix.REDUCTION_FLAT, Affix.REDUCTION_PCT
	);
	/** A flat affix at the top of its gear range counts as this many stat points. */
	private static final double AFFIX_WORTH = 3.0;

	private record Cache(RpgConfig config, Map<String, List<Profile>> byTier) {
	}

	private static final AtomicReference<Cache> CACHE = new AtomicReference<>();

	private MaterialProfiles() {
	}

	public static List<Profile> generate(final String tier, final RpgConfig.MaterialRolls config, final Map<Affix, RpgConfig.AffixRange> gearAffixes) {
		RpgConfig.MaterialTier row = config.tiers().get(tier);
		if (row == null) {
			return List.of();
		}
		List<Profile> profiles = new ArrayList<>();
		if (!row.explicit().isEmpty()) {
			for (Map<String, Integer> entry : row.explicit()) {
				profiles.add(profileOf(entry, gearAffixes));
			}
			return List.copyOf(profiles);
		}
		for (int index = 0; index < row.profiles(); index++) {
			long mixed = config.seed() * 0x9E3779B97F4A7C15L + tier.hashCode() * 0xBF58476D1CE4E5B9L + index * 0x94D049BB133111EBL;
			profiles.add(generateOne(row, config, gearAffixes, RandomSource.create(mixed)));
		}
		return List.copyOf(profiles);
	}

	/** The profiles of a tier under the current config. */
	public static List<Profile> profiles(final String tier) {
		RpgConfig config = RpgConfigStore.current();
		Cache cache = CACHE.get();
		if (cache == null || cache.config() != config) {
			Map<String, List<Profile>> byTier = new LinkedHashMap<>();
			for (String name : config.materialRolls().tiers().keySet()) {
				String gearTier = AFFIX_TIER.getOrDefault(name, name);
				RpgConfig.RollTier gear = config.weaponRolls().tiers().get(gearTier);
				byTier.put(name, generate(name, config.materialRolls(), gear == null ? Map.of() : gear.affixes()));
			}
			cache = new Cache(config, Map.copyOf(byTier));
			CACHE.set(cache);
		}
		return cache.byTier().getOrDefault(tier, List.of());
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

	/** Picks a profile. Depth 0 weighs all equally. Depth 1 weighs the strongest profile {@code 1 + rankWeight} times. */
	public static Profile pick(final List<Profile> list, final double depth, final double rankWeight, final RandomSource random) {
		if (list.isEmpty()) {
			return null;
		}
		int size = list.size();
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			order.add(i);
		}
		order.sort(Comparator.comparingDouble(index -> list.get(index).worth()));
		double clamped = Math.max(0.0, Math.min(1.0, depth));
		double[] weights = new double[size];
		double total = 0.0;
		for (int position = 0; position < size; position++) {
			double rank = size == 1 ? 0.0 : position / (double) (size - 1);
			weights[order.get(position)] = 1.0 + rankWeight * clamped * rank;
			total += weights[order.get(position)];
		}
		double pick = random.nextDouble() * total;
		for (int i = 0; i < size; i++) {
			pick -= weights[i];
			if (pick < 0.0) {
				return list.get(i);
			}
		}
		return list.get(size - 1);
	}

	private static Profile generateOne(
		final RpgConfig.MaterialTier row, final RpgConfig.MaterialRolls config, final Map<Affix, RpgConfig.AffixRange> gearAffixes,
		final RandomSource random
	) {
		int count = pickCount(row.countWeights(), random);
		List<Stat> statPool = new ArrayList<>(List.of(Stat.values()));
		List<Affix> affixPool = new ArrayList<>();
		for (Affix affix : AFFIX_POOL) {
			if (gearAffixes.containsKey(affix)) {
				affixPool.add(affix);
			}
		}
		Map<String, Integer> picked = new LinkedHashMap<>();
		for (int i = 0; i < count; i++) {
			boolean affix = !affixPool.isEmpty() && (statPool.isEmpty() || random.nextDouble() < config.affixShare());
			if (affix) {
				Affix chosen = affixPool.remove(random.nextInt(affixPool.size()));
				picked.put(chosen.key(), affixValue(chosen, gearAffixes.get(chosen), config.affixScale(), random));
			} else if (!statPool.isEmpty()) {
				Stat stat = statPool.remove(random.nextInt(statPool.size()));
				picked.put(stat.key(), row.min() + random.nextInt(row.max() - row.min() + 1));
			}
		}
		return profileOf(picked, gearAffixes);
	}

	private static int affixValue(final Affix affix, final RpgConfig.AffixRange range, final double scale, final RandomSource random) {
		double units = affix.percent() ? Affix.TENTHS : 1;
		int low = (int) Math.max(1, Math.round(range.min() * units * scale));
		int high = (int) Math.max(low, Math.round(range.max() * units * scale));
		return low + random.nextInt(high - low + 1);
	}

	private static Profile profileOf(final Map<String, Integer> entry, final Map<Affix, RpgConfig.AffixRange> gearAffixes) {
		Map<String, Integer> stats = new LinkedHashMap<>();
		Map<String, Integer> affixes = new LinkedHashMap<>();
		double worth = 0.0;
		for (Map.Entry<String, Integer> field : entry.entrySet()) {
			Affix affix = Affix.byKey(field.getKey()).orElse(null);
			if (affix == null) {
				stats.put(field.getKey(), field.getValue());
				worth += field.getValue();
				continue;
			}
			affixes.put(field.getKey(), field.getValue());
			RpgConfig.AffixRange range = gearAffixes.get(affix);
			if (range != null) {
				double top = Math.max(1.0, Math.round(range.max() * (affix.percent() ? Affix.TENTHS : 1)));
				worth += field.getValue() / top * AFFIX_WORTH;
			}
		}
		return new Profile(Map.copyOf(stats), Map.copyOf(affixes), worth);
	}

	private static int pickCount(final List<Integer> weights, final RandomSource random) {
		int total = weights.stream().mapToInt(Integer::intValue).sum();
		int pick = random.nextInt(total);
		for (int i = 0; i < weights.size(); i++) {
			pick -= weights.get(i);
			if (pick < 0) {
				return i + 1;
			}
		}
		return weights.size();
	}
}
