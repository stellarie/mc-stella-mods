package com.stella.equips;

import com.stella.spells.SpellbookItem;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/** Items the game tests need registered before the registry freezes. */
public final class TestItems implements ModInitializer {
	private static final String NAMESPACE = "mod-stella-equips-enchants-test-mod-stella-equips-enchants";
	public static final Item BOOK = register("stand_in_book", 1);
	/** The spells mod's scroll shape: the same class as a book, but it stacks. */
	public static final Item STACKING = register("stand_in_stacking", 16);

	@Override
	public void onInitialize() {
		// Loading this class registers the items.
	}

	private static Item register(final String path, final int stack) {
		Identifier id = Identifier.fromNamespaceAndPath(NAMESPACE, path);
		return Registry.register(BuiltInRegistries.ITEM, id,
			new SpellbookItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).stacksTo(stack)));
	}
}
