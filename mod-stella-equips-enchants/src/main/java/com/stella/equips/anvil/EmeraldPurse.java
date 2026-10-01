package com.stella.equips.anvil;

import com.stella.equips.bag.EmeraldBag;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Emerald custody: loose emeralds in the inventory plus every emerald bag. Spending takes loose emeralds first.
 * The two ObjectShare keys let the other mods count and spend the same way.
 */
public final class EmeraldPurse {
	public static final String COUNT_SHARE = "mod-stella-equips-enchants:emerald-count";
	public static final String SPEND_SHARE = "mod-stella-equips-enchants:emerald-spend";

	private EmeraldPurse() {
	}

	public static void register() {
		Function<Player, Integer> count = EmeraldPurse::count;
		BiFunction<Player, Integer, Boolean> spend = EmeraldPurse::trySpend;
		FabricLoader.getInstance().getObjectShare().put(COUNT_SHARE, count);
		FabricLoader.getInstance().getObjectShare().put(SPEND_SHARE, spend);
	}

	public static int count(final Player player) {
		Inventory inventory = player.getInventory();
		long emeralds = EmeraldBag.loose(inventory);
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			emeralds += EmeraldBag.stored(inventory.getItem(slot));
		}

		return (int) Math.min(Integer.MAX_VALUE, emeralds);
	}

	/** Spends the whole price, or nothing when the player carries too few. */
	public static boolean trySpend(final Player player, final int price) {
		if (price > 0 && !player.hasInfiniteMaterials() && count(player) < price) {
			return false;
		}
		spend(player, price);
		return true;
	}

	public static void spend(final Player player, final int price) {
		if (price <= 0 || player.hasInfiniteMaterials()) {
			return;
		}

		Inventory inventory = player.getInventory();
		int fromLoose = Math.min(price, EmeraldBag.loose(inventory));
		EmeraldBag.removeLoose(inventory, fromLoose);
		int left = price - fromLoose;
		for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
			ItemStack bag = inventory.getItem(slot);
			int taken = Math.min(left, EmeraldBag.stored(bag));
			if (taken > 0) {
				bag.set(EmeraldBag.EMERALDS, EmeraldBag.stored(bag) - taken);
				left -= taken;
			}
		}
		inventory.setChanged();
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
