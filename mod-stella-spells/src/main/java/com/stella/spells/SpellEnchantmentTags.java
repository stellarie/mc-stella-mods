package com.stella.spells;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.enchantment.Enchantment;

public final class SpellEnchantmentTags {
	public static final TagKey<Enchantment> SPELL_ENCHANTMENTS = TagKey.create(
		Registries.ENCHANTMENT,
		Identifier.parse(Spellbooks.MOD_ID + ":spell_enchantments")
	);

	private SpellEnchantmentTags() {
	}
}
