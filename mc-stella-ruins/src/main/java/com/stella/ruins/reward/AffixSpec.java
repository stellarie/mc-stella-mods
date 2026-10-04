package com.stella.ruins.reward;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jspecify.annotations.Nullable;

/**
 * What one affix does and what it takes. {@code accepts} must pass the default stack of every item that the
 * affix may go on, because the smithing table builds its slot filter from those stacks.
 */
public record AffixSpec(Predicate<ItemStack> accepts, Ingredient addition, List<AttributeBonus> attributes, @Nullable String effectId) {
	public AffixSpec {
		attributes = List.copyOf(attributes);
	}
}
