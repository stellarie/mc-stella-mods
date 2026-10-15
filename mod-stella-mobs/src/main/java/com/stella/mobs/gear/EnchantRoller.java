package com.stella.mobs.gear;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;

public final class EnchantRoller {
	public static final int MIN_ENCHANTS = 2;
	public static final int MAX_ENCHANTS = 3;
	private static final Map<Item, List<Holder<Enchantment>>> POOLS = new HashMap<>();
	private static final Object LOCK = new Object();
	private static @Nullable Registry<Enchantment> cachedRegistry;

	private EnchantRoller() {
	}

	public static List<Holder<Enchantment>> poolFor(final ItemStack stack, final Registry<Enchantment> registry) {
		synchronized (LOCK) {
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
	}

	public static ItemStack enchant(
		final ItemStack stack, final RandomSource random, final Registry<Enchantment> registry
	) {
		if (stack.isEmpty() || stack.get(DataComponents.ENCHANTABLE) == null) {
			return stack;
		}

		List<Holder<Enchantment>> pool = new ArrayList<>(poolFor(stack, registry));
		pool.removeIf(candidate -> candidate.is(EnchantmentTags.CURSE));
		Util.shuffle(pool, random);
		int wanted = Mth.nextInt(random, MIN_ENCHANTS, MAX_ENCHANTS);
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		for (Holder<Enchantment> candidate : pool) {
			if (mutable.keySet().size() >= wanted) {
				break;
			}
			boolean compatible = mutable.keySet().stream().allMatch(held -> Enchantment.areCompatible(held, candidate));
			if (compatible) {
				mutable.set(candidate, Mth.nextInt(random, candidate.value().getMinLevel(), candidate.value().getMaxLevel()));
			}
		}
		stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
		return stack;
	}

	public static void clearCache() {
		synchronized (LOCK) {
			POOLS.clear();
			cachedRegistry = null;
		}
	}
}
