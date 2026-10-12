package com.stella.tower;

import net.fabricmc.fabric.api.item.v1.ItemComponentTooltipProviderRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.component.ItemLore;

/**
 * The description lines of an item, shown where the vanilla lore shows.
 * A lore component holds fixed lines. This component wraps them when the tooltip builds,
 * so the width limit holds in every language.
 */
public final class ItemDescription {
	public static final DataComponentType<ItemLore> TYPE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
		StellaTower.id("description"),
		DataComponentType.<ItemLore>builder().persistent(ItemLore.CODEC).networkSynchronized(ItemLore.STREAM_CODEC).build());

	private ItemDescription() {
	}

	public static void register() {
		ItemComponentTooltipProviderRegistry.addBefore(DataComponents.LORE, TYPE,
			lore -> (context, lines, flag, components) -> lore.styledLines().forEach(line -> DescriptionLines.add(lines, line)));
	}
}
