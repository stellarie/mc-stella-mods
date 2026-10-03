package com.stella.equips.compare;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

/**
 * The stat and affix difference between a held item and the item in its slot.
 * It reads the rpg mod's roll components by id, so this mod needs no compile link to it.
 */
public final class GearCompare {
	private static final Identifier STAT_ROLLS = Identifier.fromNamespaceAndPath("mod-stella-rpg", "stat_rolls");
	private static final Identifier AFFIX_ROLLS = Identifier.fromNamespaceAndPath("mod-stella-rpg", "affix_rolls");
	/** A percent affix stores tenths of a percent. */
	private static final int TENTHS = 10;

	/** Stat key to value, and affix key to stored value. */
	public record Rolls(Map<String, Integer> stats, Map<String, Integer> affixes) {
		public static final Rolls EMPTY = new Rolls(Map.of(), Map.of());
	}

	public record Line(boolean affix, String key, int delta) {
	}

	private GearCompare() {
	}

	/** Affixes first, then stats, each by key. A roll that did not change has no line. */
	public static List<Line> diff(final Rolls held, final Rolls equipped) {
		List<Line> lines = new ArrayList<>();
		collect(true, held.affixes(), equipped.affixes(), lines);
		collect(false, held.stats(), equipped.stats(), lines);
		return lines;
	}

	/** The lines to show for a held stack against the stack in its slot; empty when the held stack is not rolled. */
	public static List<Line> compare(final ItemStack held, final ItemStack equipped) {
		Rolls heldRolls = rollsOf(held);

		if (heldRolls == null) {
			return List.of();
		}

		Rolls equippedRolls = rollsOf(equipped);
		return diff(heldRolls, equippedRolls == null ? Rolls.EMPTY : equippedRolls);
	}

	/** Null when the stack has no stat roll component, which is every stack when the rpg mod is absent. */
	public static Rolls rollsOf(final ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}

		Map<String, Integer> stats = read(stack, STAT_ROLLS);

		if (stats == null) {
			return null;
		}

		Map<String, Integer> affixes = read(stack, AFFIX_ROLLS);
		return new Rolls(stats, affixes == null ? Map.of() : affixes);
	}

	/** Armor compares with the slot it wears in; everything else compares with the main hand. */
	public static EquipmentSlot slotOf(final ItemStack stack) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		return equippable == null ? EquipmentSlot.MAINHAND : equippable.slot();
	}

	/** The rpg mod names its flat affixes with a `_flat` suffix; every other affix is a percent. */
	public static boolean percent(final String affixKey) {
		return !affixKey.endsWith("_flat");
	}

	/** The signed number a line shows, such as `+4`, `-3` or `+1.5%`. */
	public static String amount(final Line line) {
		String sign = line.delta() < 0 ? "-" : "+";
		int size = Math.abs(line.delta());

		if (!line.affix() || !percent(line.key())) {
			return sign + size;
		}

		String number = size % TENTHS == 0
			? Integer.toString(size / TENTHS)
			: String.format(Locale.ROOT, "%.1f", size / (double) TENTHS);
		return sign + number + "%";
	}

	private static void collect(
		final boolean affix, final Map<String, Integer> held, final Map<String, Integer> equipped, final List<Line> lines
	) {
		TreeSet<String> keys = new TreeSet<>(held.keySet());
		keys.addAll(equipped.keySet());

		for (String key : keys) {
			int delta = held.getOrDefault(key, 0) - equipped.getOrDefault(key, 0);

			if (delta != 0) {
				lines.add(new Line(affix, key, delta));
			}
		}
	}

	private static Map<String, Integer> read(final ItemStack stack, final Identifier id) {
		DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id);

		if (type == null) {
			return null;
		}

		Object value = stack.get(type);

		if (!(value instanceof Map<?, ?> raw)) {
			return null;
		}

		Map<String, Integer> copy = new TreeMap<>();

		for (Map.Entry<?, ?> entry : raw.entrySet()) {
			if (entry.getKey() instanceof String key && entry.getValue() instanceof Integer number) {
				copy.put(key, number);
			}
		}

		return copy;
	}
}
