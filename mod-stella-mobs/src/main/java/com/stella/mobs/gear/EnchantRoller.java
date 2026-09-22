package com.stella.mobs.gear;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jspecify.annotations.Nullable;

public final class EnchantRoller {
	private static final Map<Item, List<Holder<Enchantment>>> POOLS = new HashMap<>();
	private static @Nullable Registry<Enchantment> cachedRegistry;

	private EnchantRoller() {
	}

	public static List<Holder<Enchantment>> poolFor(final ItemStack stack, final Registry<Enchantment> registry) {
		if (cachedRegistry != registry) {
			POOLS.clear();
			cachedRegistry = registry;
		}

		return POOLS.computeIfAbsent(
			stack.getItem(),
			item -> registry.listElements()
				.filter(holder -> holder.value().canEnchant(stack))
				.map(holder -> (Holder<Enchantment>) holder)
				.toList()
		);
	}

	public static ItemStack enchant(
		final ItemStack stack, final RandomSource random, final Registry<Enchantment> registry
	) {
		if (stack.isEmpty() || stack.get(DataComponents.ENCHANTABLE) == null) {
			return stack;
		}

		Optional<Holder<Enchantment>> chosen = Util.getRandomSafe(poolFor(stack, registry), random);
		if (chosen.isEmpty()) {
			return stack;
		}

		Holder<Enchantment> enchantment = chosen.get();
		int level = Mth.nextInt(random, enchantment.value().getMinLevel(), enchantment.value().getMaxLevel());
		stack.enchant(enchantment, level);
		return stack;
	}

	public static void clearCache() {
		POOLS.clear();
		cachedRegistry = null;
	}
}
