package com.stella.equips.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.codec.RegistryCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Grants a fixed number of distinct random enchantments, each at a level inside its own range.
 */
public class RandomEnchantmentsFunction extends LootItemConditionalFunction {
	public static final MapCodec<RandomEnchantmentsFunction> CODEC = RecordCodecBuilder.mapCodec(instance ->
		commonFields(instance)
			.and(instance.group(
				RegistryCodecs.holderSet(Registries.ENCHANTMENT).fieldOf("options").forGetter(f -> f.options),
				Codec.INT.optionalFieldOf("count", 1).forGetter(f -> f.count)
			))
			.apply(instance, RandomEnchantmentsFunction::new)
	);
	private final HolderSet<Enchantment> options;
	private final int count;

	private RandomEnchantmentsFunction(
		final Optional<Holder<LootItemCondition>> condition,
		final HolderSet<Enchantment> options,
		final int count
	) {
		super(condition);
		this.options = options;
		this.count = count;
	}

	public static void register() {
		Registry.register(
			BuiltInRegistries.LOOT_FUNCTION_TYPE,
			Identifier.fromNamespaceAndPath("mod-stella-equips-enchants", "random_enchantments"),
			CODEC
		);
	}

	@Override
	protected ItemStack run(final ItemStack stack, final LootContext context) {
		boolean book = stack.is(Items.BOOK);
		ItemStack result = book ? new ItemStack(Items.ENCHANTED_BOOK) : stack;
		List<Holder<Enchantment>> pool = new ArrayList<>(candidates(stack, book, true));
		List<Holder<Enchantment>> granted = new ArrayList<>();
		RandomSource random = context.getRandom();
		boolean toppedUp = false;

		while (granted.size() < this.count) {
			if (pool.isEmpty()) {
				if (toppedUp) {
					break;
				}

				toppedUp = true;
				pool = new ArrayList<>(candidates(stack, book, false));
				pool.removeAll(granted);
			}

			if (pool.isEmpty()) {
				break;
			}

			Holder<Enchantment> pick = Util.getRandom(pool, random);
			pool.remove(pick);

			if (!compatible(granted, pick)) {
				continue;
			}

			granted.add(pick);
			result.enchant(pick, Mth.nextInt(random, pick.value().getMinLevel(), pick.value().getMaxLevel()));
		}

		return result;
	}

	/**
	 * The catalogue is thin for some item types, so the tier count is kept by falling back to
	 * enchantments that the item does not support.
	 */
	private List<Holder<Enchantment>> candidates(final ItemStack stack, final boolean book, final boolean supported) {
		List<Holder<Enchantment>> found = new ArrayList<>();

		for (Holder<Enchantment> enchantment : this.options) {
			if (!supported || book || enchantment.value().canEnchant(stack)) {
				found.add(enchantment);
			}
		}

		return found;
	}

	/** An outfit the anvil would refuse to build is not a reward. */
	private static boolean compatible(final List<Holder<Enchantment>> granted, final Holder<Enchantment> pick) {
		for (Holder<Enchantment> other : granted) {
			if (!Enchantment.areCompatible(other, pick)) {
				return false;
			}
		}

		return true;
	}

	@Override
	public MapCodec<? extends LootItemConditionalFunction> codec() {
		return CODEC;
	}
}
