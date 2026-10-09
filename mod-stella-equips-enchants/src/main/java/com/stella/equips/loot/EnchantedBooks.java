package com.stella.equips.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Publishes {@code mod-stella-equips-enchants:enchanted-book}, a {@code Function<Object[], ItemStack>}.
 * The arguments are {@code {ServerLevel, RandomSource, Integer count}}.
 * It returns an enchanted book with {@code count} distinct, compatible, non-curse enchantments from every enchantment in the registry,
 * each at a random level in its own range.
 * A book holds fewer when the pool runs out. Bad input returns an empty stack. The call never throws.
 */
public final class EnchantedBooks {
	public static final String SHARE = "mod-stella-equips-enchants:enchanted-book";

	private EnchantedBooks() {
	}

	public static void register() {
		Function<Object[], ItemStack> share = EnchantedBooks::fromArgs;
		FabricLoader.getInstance().getObjectShare().put(SHARE, share);
	}

	private static ItemStack fromArgs(final Object[] args) {
		if (args == null || args.length != 3 || !(args[0] instanceof ServerLevel level)
			|| !(args[1] instanceof RandomSource random) || !(args[2] instanceof Integer count)) {
			return ItemStack.EMPTY;
		}
		try {
			return create(level, random, count);
		} catch (RuntimeException failure) {
			return ItemStack.EMPTY;
		}
	}

	public static ItemStack create(final ServerLevel level, final RandomSource random, final int count) {
		if (count <= 0) {
			return ItemStack.EMPTY;
		}
		List<Holder<Enchantment>> pool = new ArrayList<>();
		level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().forEach(enchantment -> {
			if (!enchantment.is(EnchantmentTags.CURSE) && !ContainerLoot.isTestFixture(enchantment)) {
				pool.add(enchantment);
			}
		});
		ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
		List<Holder<Enchantment>> granted = new ArrayList<>();
		while (granted.size() < count && !pool.isEmpty()) {
			Holder<Enchantment> pick = pool.remove(random.nextInt(pool.size()));
			if (granted.stream().allMatch(other -> Enchantment.areCompatible(other, pick))) {
				granted.add(pick);
				book.enchant(pick, Mth.nextInt(random, pick.value().getMinLevel(), pick.value().getMaxLevel()));
			}
		}
		return granted.isEmpty() ? ItemStack.EMPTY : book;
	}
}
