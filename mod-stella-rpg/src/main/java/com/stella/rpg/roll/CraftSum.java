package com.stella.rpg.roll;

import com.stella.rpg.RpgConfigStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * The craft sum: a recipe result takes the bonus maps and the enchantments of the materials that made it.
 * Gear takes the full sum and always gets a stat map, so it never rolls at random.
 * A material result splits the sum by its count. Any other result carries nothing.
 */
public final class CraftSum {
	private CraftSum() {
	}

	/** Shaped and shapeless crafting. Returns an empty stack when an enchanted material would unpack into many. */
	public static ItemStack crafted(final ItemStack result, final List<ItemStack> inputs) {
		if (result.isEmpty()) {
			return result;
		}
		Affix.Kind kind = BonusCarrier.kind(result);
		if (kind == null && !BonusCarrier.isMaterial(result)) {
			return result;
		}
		List<Map<String, Integer>> stats = new ArrayList<>();
		List<Map<String, Integer>> affixes = new ArrayList<>();
		List<ItemEnchantments> enchantments = new ArrayList<>();
		for (ItemStack input : inputs) {
			if (BonusCarrier.isMaterial(input)) {
				stats.add(mapOf(input, WeaponRolls.STAT_ROLLS));
				affixes.add(mapOf(input, WeaponRolls.AFFIX_ROLLS));
				enchantments.add(input.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
			}
		}
		return apply(result, kind, BonusMath.sum(stats), BonusMath.sum(affixes), enchantments, ItemEnchantments.EMPTY);
	}

	/** A smithing transform: the base components are already on the result, and the addition adds to them. */
	public static ItemStack smithed(final ItemStack result, final ItemStack addition) {
		Affix.Kind kind = BonusCarrier.kind(result);
		if (kind == null) {
			return result;
		}
		List<Map<String, Integer>> stats = new ArrayList<>();
		List<Map<String, Integer>> affixes = new ArrayList<>();
		List<ItemEnchantments> enchantments = new ArrayList<>();
		stats.add(mapOf(result, WeaponRolls.STAT_ROLLS));
		affixes.add(mapOf(result, WeaponRolls.AFFIX_ROLLS));
		if (BonusCarrier.isMaterial(addition)) {
			stats.add(mapOf(addition, WeaponRolls.STAT_ROLLS));
			affixes.add(mapOf(addition, WeaponRolls.AFFIX_ROLLS));
			enchantments.add(addition.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
		}
		ItemEnchantments existing = result.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		return apply(result, kind, BonusMath.sum(stats), BonusMath.sum(affixes), enchantments, existing);
	}

	/** A furnace result keeps the whole bonus of a material input. Gear smelted into a material carries nothing. */
	public static ItemStack smelted(final ItemStack result, final ItemStack input) {
		if (result.isEmpty() || !BonusCarrier.isMaterial(result) || !BonusCarrier.isMaterial(input)) {
			return result;
		}
		return apply(
			result,
			null,
			mapOf(input, WeaponRolls.STAT_ROLLS),
			mapOf(input, WeaponRolls.AFFIX_ROLLS),
			List.of(input.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)),
			ItemEnchantments.EMPTY
		);
	}

	/** Grid repair keeps the bonus of the better input. It never sums, and it never rerolls. */
	public static ItemStack repaired(final ItemStack result, final List<ItemStack> inputs) {
		if (BonusCarrier.kind(result) == null) {
			return result;
		}
		ItemStack best = ItemStack.EMPTY;
		int bestTotal = -1;
		for (ItemStack input : inputs) {
			if (input.isEmpty()) {
				continue;
			}
			int total = BonusMath.total(mapOf(input, WeaponRolls.STAT_ROLLS));
			if (total > bestTotal) {
				best = input;
				bestTotal = total;
			}
		}
		result.set(WeaponRolls.STAT_ROLLS, Map.copyOf(mapOf(best, WeaponRolls.STAT_ROLLS)));
		Map<String, Integer> affixes = mapOf(best, WeaponRolls.AFFIX_ROLLS);
		if (!affixes.isEmpty()) {
			result.set(WeaponRolls.AFFIX_ROLLS, Map.copyOf(affixes));
		}
		return result;
	}

	private static ItemStack apply(
		final ItemStack result,
		final Affix.Kind kind,
		final Map<String, Integer> stats,
		final Map<String, Integer> affixes,
		final List<ItemEnchantments> added,
		final ItemEnchantments existing
	) {
		if (kind != null) {
			Map<String, Integer> fitting = BonusMath.keepKeys(affixes, key -> Affix.byKey(key).filter(affix -> affix.rollsOn(kind)).isPresent());
			// With the toggle on, gear from plain materials stays unrolled and takes the old random roll.
			boolean leaveUnrolled = stats.isEmpty() && fitting.isEmpty() && RpgConfigStore.current().materialRolls().craftRandomRoll();
			if (!leaveUnrolled) {
				result.set(WeaponRolls.STAT_ROLLS, Map.copyOf(stats));
			}
			if (!fitting.isEmpty()) {
				result.set(WeaponRolls.AFFIX_ROLLS, Map.copyOf(fitting));
			}
			setEnchantments(result, merge(existing, added, enchantment -> enchantment.value().canEnchant(result)));
			return result;
		}
		int count = result.getCount();
		if (count > 1 && added.stream().anyMatch(enchantments -> !enchantments.isEmpty())) {
			return ItemStack.EMPTY;
		}
		// A material with no bonus keeps no component, so it still stacks with a plain one.
		Map<String, Integer> splitStats = BonusMath.split(stats, count);
		if (!splitStats.isEmpty()) {
			result.set(WeaponRolls.STAT_ROLLS, Map.copyOf(splitStats));
		}
		Map<String, Integer> splitAffixes = BonusMath.split(affixes, count);
		if (!splitAffixes.isEmpty()) {
			result.set(WeaponRolls.AFFIX_ROLLS, Map.copyOf(splitAffixes));
		}
		setEnchantments(result, merge(existing, added, enchantment -> true));
		return result;
	}

	private static void setEnchantments(final ItemStack result, final ItemEnchantments merged) {
		if (!merged.isEmpty()) {
			result.set(DataComponents.ENCHANTMENTS, merged);
		}
	}

	/** Levels add per enchantment and stop at its maximum. A later enchantment that conflicts with a kept one is skipped. */
	private static ItemEnchantments merge(
		final ItemEnchantments existing,
		final List<ItemEnchantments> added,
		final Predicate<Holder<Enchantment>> fits
	) {
		Map<Holder<Enchantment>, Integer> levels = new LinkedHashMap<>();
		List<ItemEnchantments> all = new ArrayList<>();
		all.add(existing);
		all.addAll(added);
		for (ItemEnchantments enchantments : all) {
			for (var entry : enchantments.entrySet()) {
				levels.merge(entry.getKey(), entry.getIntValue(), Integer::sum);
			}
		}
		ItemEnchantments.Mutable kept = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		for (Map.Entry<Holder<Enchantment>, Integer> entry : levels.entrySet()) {
			Holder<Enchantment> enchantment = entry.getKey();
			boolean compatible = kept.keySet().stream().allMatch(other -> Enchantment.areCompatible(other, enchantment));
			if (compatible && fits.test(enchantment)) {
				kept.set(enchantment, Math.min(entry.getValue(), enchantment.value().getMaxLevel()));
			}
		}
		return kept.toImmutable();
	}

	private static Map<String, Integer> mapOf(final ItemStack stack, final DataComponentType<Map<String, Integer>> type) {
		Map<String, Integer> map = stack.isEmpty() ? null : stack.get(type);
		return map == null ? Map.of() : map;
	}
}
