package com.stella.equips;

import com.mojang.serialization.Codec;
import com.stella.spells.SpellbookItem;
import java.util.Map;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
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
	/** Another mod's bow, such as the rpg mod's mechanical bow. */
	public static final Item MOD_BOW = Registry.register(BuiltInRegistries.ITEM,
		Identifier.fromNamespaceAndPath(NAMESPACE, "stand_in_bow"),
		new net.minecraft.world.item.BowItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM,
			Identifier.fromNamespaceAndPath(NAMESPACE, "stand_in_bow"))).durability(384)));

	/** Stand-ins for the rpg mod's roll components, registered under its ids so the compare reads them by key. */
	public static final DataComponentType<Map<String, Integer>> STAT_ROLLS = rolls("stat_rolls");
	public static final DataComponentType<Map<String, Integer>> AFFIX_ROLLS = rolls("affix_rolls");

	@Override
	public void onInitialize() {
		// Loading this class registers the items.
	}

	private static Item register(final String path, final int stack) {
		Identifier id = Identifier.fromNamespaceAndPath(NAMESPACE, path);
		return Registry.register(BuiltInRegistries.ITEM, id,
			new SpellbookItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).stacksTo(stack)));
	}

	private static DataComponentType<Map<String, Integer>> rolls(final String path) {
		return Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			Identifier.fromNamespaceAndPath("mod-stella-rpg", path),
			DataComponentType.<Map<String, Integer>>builder().persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).build()
		);
	}
}
