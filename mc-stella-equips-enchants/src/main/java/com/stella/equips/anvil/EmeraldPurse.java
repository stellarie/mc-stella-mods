package com.stella.equips.anvil;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Emerald custody for the enchanting containers. */
public final class EmeraldPurse {
	private EmeraldPurse() {
	}

	public static int count(final Player player) {
		Inventory inventory = player.getInventory();
		int emeralds = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(Items.EMERALD)) {
				emeralds += stack.getCount();
			}
		}

		return emeralds;
	}

	public static void spend(final Player player, final int price) {
		if (price <= 0 || player.hasInfiniteMaterials()) {
			return;
		}

		Inventory inventory = player.getInventory();
		int left = price;
		for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.is(Items.EMERALD)) {
				continue;
			}

			int taken = Math.min(left, stack.getCount());
			inventory.removeItem(slot, taken);
			left -= taken;
		}
	}

	/** The enchantments the operation applies: new ones plus level raises. */
	public static int appliedEnchantments(final ItemStack before, final ItemStack after) {
		if (after.isEmpty()) {
			return 0;
		}

		ItemEnchantments current = EnchantmentHelper.getEnchantmentsForCrafting(before);
		ItemEnchantments merged = EnchantmentHelper.getEnchantmentsForCrafting(after);
		int applied = 0;
		for (Holder<Enchantment> enchantment : merged.keySet()) {
			if (merged.getLevel(enchantment) > current.getLevel(enchantment)) {
				applied++;
			}
		}

		return applied;
	}

	/** The same count, taken from the input and the addition before the result exists. */
	public static int offeredEnchantments(final ItemStack input, final ItemStack addition) {
		if (input.isEmpty() || addition.isEmpty()) {
			return 0;
		}

		ItemEnchantments current = EnchantmentHelper.getEnchantmentsForCrafting(input);
		ItemEnchantments incoming = EnchantmentHelper.getEnchantmentsForCrafting(addition);
		int applied = 0;
		for (Holder<Enchantment> enchantment : incoming.keySet()) {
			int existing = current.getLevel(enchantment);
			int offered = incoming.getLevel(enchantment);
			int merged = existing == offered ? offered + 1 : Math.max(offered, existing);
			if (Math.min(merged, enchantment.value().getMaxLevel()) > existing) {
				applied++;
			}
		}

		return applied;
	}
}
