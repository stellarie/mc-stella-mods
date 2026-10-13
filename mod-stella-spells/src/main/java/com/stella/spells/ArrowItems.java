package com.stella.spells;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** The crafted arrows, and the table Arrow Crafting reads: one material, one kind, in the order the book prefers. */
public final class ArrowItems {
	/** One material turns into arrows of one item. */
	public record Craft(Item material, Item arrow) {
	}

	public static final Map<ArrowKind, CraftedArrowItem> BY_KIND = registerAll();
	/** Every catalog material, in table order. */
	public static final List<Craft> CRAFTS = List.of(
		new Craft(Items.BONE, Items.ARROW),
		new Craft(Items.FLINT, BY_KIND.get(ArrowKind.FLINT)),
		new Craft(Items.IRON_INGOT, BY_KIND.get(ArrowKind.IRON)),
		new Craft(Items.DIAMOND, BY_KIND.get(ArrowKind.DIAMOND)),
		new Craft(Items.AMETHYST_SHARD, BY_KIND.get(ArrowKind.KEEN)),
		new Craft(Items.BLAZE_POWDER, BY_KIND.get(ArrowKind.FIRE)),
		new Craft(Items.PACKED_ICE, BY_KIND.get(ArrowKind.FROST)),
		new Craft(Items.SPIDER_EYE, BY_KIND.get(ArrowKind.VENOM)),
		new Craft(Items.GUNPOWDER, BY_KIND.get(ArrowKind.BLAST)),
		new Craft(Items.GLOWSTONE_DUST, Items.SPECTRAL_ARROW)
	);

	private ArrowItems() {
	}

	/** Loads the class, which registers the items. */
	static void register() {
	}

	private static Map<ArrowKind, CraftedArrowItem> registerAll() {
		Map<ArrowKind, CraftedArrowItem> items = new EnumMap<>(ArrowKind.class);
		for (ArrowKind kind : ArrowKind.values()) {
			Identifier id = Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, kind.path());
			Item.Properties properties = new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id))
				.component(DataComponents.LORE, Spellbooks.lore(Spellbooks.block(kind.path(), ChatFormatting.GRAY)));
			items.put(kind, Registry.register(BuiltInRegistries.ITEM, id, new CraftedArrowItem(properties, kind)));
		}
		return items;
	}
}
