package com.stella.spells;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class SpellLog {
	private SpellLog() {
	}

	public static void craft(final ResourceKey<?> recipeId, final ItemStack result) {
		if (!(result.getItem() instanceof SpellbookItem)) {
			return;
		}

		Spellbooks.LOGGER.info(
			"[SPELL] event=CRAFT recipe_id={} item_id={}",
			recipeId.identifier(),
			BuiltInRegistries.ITEM.getKey(result.getItem())
		);
	}

	public static void anvil(final ItemStack input, final ItemStack result) {
		if (!(input.getItem() instanceof SpellbookItem) || !(result.getItem() instanceof SpellbookItem)) {
			return;
		}

		ItemEnchantments before = EnchantmentHelper.getEnchantmentsForCrafting(input);
		ItemEnchantments after = EnchantmentHelper.getEnchantmentsForCrafting(result);
		for (Holder<Enchantment> enchantment : after.keySet()) {
			Identifier id = enchantment.unwrapKey().map(ResourceKey::identifier).orElse(null);
			int level = after.getLevel(enchantment);
			if (id != null
				&& enchantment.is(SpellEnchantmentTags.SPELL_ENCHANTMENTS)
				&& level > before.getLevel(enchantment)) {
				Spellbooks.LOGGER.info(
					"[SPELL] event=ANVIL item_id={} enchantment_id={} level={}",
					BuiltInRegistries.ITEM.getKey(result.getItem()), id, level
				);
			}
		}
	}
}
