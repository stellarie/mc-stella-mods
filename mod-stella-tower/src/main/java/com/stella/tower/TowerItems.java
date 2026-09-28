package com.stella.tower;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class TowerItems {
	public static final Identifier VOID_KEY_ID = StellaTower.id("void_key");
	public static final VoidKeyItem VOID_KEY = Registry.register(BuiltInRegistries.ITEM, VOID_KEY_ID, new VoidKeyItem(
		new Item.Properties().setId(ResourceKey.create(Registries.ITEM, VOID_KEY_ID)).stacksTo(1).rarity(Rarity.RARE)
	));

	private TowerItems() {
	}

	public static void register() {
		StellaTower.LOGGER.debug("Registered {}", VOID_KEY_ID);
	}
}
