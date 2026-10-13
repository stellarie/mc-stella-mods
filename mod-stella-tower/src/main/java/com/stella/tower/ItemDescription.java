package com.stella.tower;

import java.util.List;
import net.fabricmc.fabric.api.item.v1.ItemComponentTooltipProviderRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
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

	/** A Requires line and a Desc line as two entries. The tooltip splits each one at the width. */
	public static ItemLore block(final Component requires, final Component desc) {
		return new ItemLore(List.of(
			DescriptionLines.requires(requires.copy().withStyle(ChatFormatting.WHITE)),
			DescriptionLines.desc(desc.copy().withStyle(ChatFormatting.GRAY))
		));
	}

	/** The block of an item whose lang holds {@code item.mod-stella-tower.<name>.requires} and {@code .desc}. */
	public static ItemLore block(final String name) {
		return block(Component.translatable("item.mod-stella-tower." + name + ".requires"), Component.translatable("item.mod-stella-tower." + name + ".desc"));
	}

	public static void register() {
		ItemComponentTooltipProviderRegistry.addBefore(DataComponents.LORE, TYPE,
			lore -> (context, lines, flag, components) -> lore.styledLines().forEach(line -> DescriptionLines.add(lines, line)));
	}
}
