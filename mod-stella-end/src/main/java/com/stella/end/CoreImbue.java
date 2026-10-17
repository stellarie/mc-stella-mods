package com.stella.end;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;

/**
 * The anvil step of the two cores. A weapon and a dark blaze core give the weapon Blaze Soul. A weapon and a wither
 * core give it the Wither Drain enchantment. A weapon takes each imbue once.
 */
public final class CoreImbue {
	/** Levels, or emeralds when the equips mod runs the anvil. */
	public static final int COST = 8;

	private CoreImbue() {
	}

	public static boolean isCore(final ItemStack stack) {
		return stack.is(EndItems.darkBlazeCore()) || stack.is(EndItems.witherCore());
	}

	/** The imbued copy of a weapon, or empty when the addition is no core, the weapon is no weapon, or it holds the imbue. */
	public static ItemStack result(final RegistryAccess access, final ItemStack weapon, final ItemStack core) {
		if (!isCore(core) || !BlazeSoul.eligible(weapon)) {
			return ItemStack.EMPTY;
		}
		if (core.is(EndItems.darkBlazeCore())) {
			return BlazeSoul.has(weapon) ? ItemStack.EMPTY : BlazeSoul.imbued(weapon);
		}
		return WitherDrain.has(weapon) ? ItemStack.EMPTY : WitherDrain.enchanted(weapon, access);
	}
}
