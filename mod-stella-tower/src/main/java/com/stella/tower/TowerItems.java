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

	/** Left, now and then, by a slain ghost. What it does is still to be decided. */
	public static final Identifier GHOST_RUNE_ID = StellaTower.id("ghost_rune");
	public static final Item GHOST_RUNE = Registry.register(BuiltInRegistries.ITEM, GHOST_RUNE_ID, new Item(
		new Item.Properties().setId(ResourceKey.create(Registries.ITEM, GHOST_RUNE_ID)).rarity(Rarity.RARE)
	));

	/** A rune's element and floor, on the rune itself. */
	public static final DataComponentType<RuneData> RUNE_DATA = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, StellaTower.id("rune"),
		DataComponentType.<RuneData>builder().persistent(RuneData.CODEC).networkSynchronized(RuneData.STREAM_CODEC).build());
	/** The rune set into a weapon or armor piece. */
	public static final DataComponentType<RuneData> RUNED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, StellaTower.id("runed"),
		DataComponentType.<RuneData>builder().persistent(RuneData.CODEC).networkSynchronized(RuneData.STREAM_CODEC).build());
	public static final Identifier RUNE_ID = StellaTower.id("rune");
	public static final RuneItem RUNE = Registry.register(BuiltInRegistries.ITEM, RUNE_ID, new RuneItem(
		new Item.Properties().setId(ResourceKey.create(Registries.ITEM, RUNE_ID)).stacksTo(16).rarity(Rarity.EPIC)
	));

	private TowerItems() {
	}

	public static void register() {
		StellaTower.LOGGER.debug("Registered {}", VOID_KEY_ID);
	}
}
