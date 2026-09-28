package com.stella.tower;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
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

	/** The floor a key opens. */
	public static final DataComponentType<Integer> FLOOR = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, StellaTower.id("floor"),
		DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
	public static final Identifier FLOOR_KEY_ID = StellaTower.id("floor_key");
	public static final FloorKeyItem FLOOR_KEY = Registry.register(BuiltInRegistries.ITEM, FLOOR_KEY_ID, new FloorKeyItem(
		new Item.Properties().setId(ResourceKey.create(Registries.ITEM, FLOOR_KEY_ID)).stacksTo(1).rarity(Rarity.EPIC)
	));

	private TowerItems() {
	}

	public static void register() {
		StellaTower.LOGGER.debug("Registered {}", VOID_KEY_ID);
	}
}
