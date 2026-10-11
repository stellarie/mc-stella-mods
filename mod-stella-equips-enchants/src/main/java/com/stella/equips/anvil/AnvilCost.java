package com.stella.equips.anvil;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * The anvil price doubles with each enchantment the item holds: the next one costs {@code 2^n} emeralds, where n counts
 * the item's enchantments. Spell enchantments do not count; runes are components and never do.
 */
public final class AnvilCost {
	/** The spells mod's tag, read by name so this mod loads without it. */
	public static final TagKey<Enchantment> SPELL_ENCHANTMENTS =
		TagKey.create(Registries.ENCHANTMENT, Identifier.parse("mod-stella-spells:spell_enchantments"));
	/** Past this the price would overflow an int. */
	private static final int MAX_EXPONENT = 30;

	private AnvilCost() {
	}

	/** Each applied step costs the next power of two, starting at 2^held. */
	public static int price(final int held, final int applied) {
		long total = 0;
		for (int step = 0; step < applied; step++) {
			total += 1L << Math.min(MAX_EXPONENT, held + step);
		}
		return (int) Math.min(Integer.MAX_VALUE, total);
	}

	/** The price of applying enchantments to this input. */
	public static int price(final ItemStack input, final int applied) {
		return price(held(input), applied);
	}

	/** The enchantments on the item that count toward the price. */
	public static int held(final ItemStack input) {
		int held = 0;
		for (var enchantment : EnchantmentHelper.getEnchantmentsForCrafting(input).keySet()) {
			if (!enchantment.is(SPELL_ENCHANTMENTS)) {
				held++;
			}
		}
		return held;
	}
}
