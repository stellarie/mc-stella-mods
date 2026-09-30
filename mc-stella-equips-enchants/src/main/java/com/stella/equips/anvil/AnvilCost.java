package com.stella.equips.anvil;

/** The anvil price is one emerald per enchantment the operation applies. */
public final class AnvilCost {
	public static final int EMERALDS_PER_ENCHANTMENT = 1;

	private AnvilCost() {
	}

	public static int price(final int appliedEnchantments) {
		return appliedEnchantments * EMERALDS_PER_ENCHANTMENT;
	}
}
