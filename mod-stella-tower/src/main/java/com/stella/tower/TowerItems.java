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

	/** Left, now and then, by a slain ghost; set into a chestplate or a weapon, or made into a wisp. */
	public static final Identifier GHOST_RUNE_ID = StellaTower.id("ghost_rune");
	public static final Item GHOST_RUNE = Registry.register(BuiltInRegistries.ITEM, GHOST_RUNE_ID, new Item(
		new Item.Properties().setId(ResourceKey.create(Registries.ITEM, GHOST_RUNE_ID)).rarity(Rarity.RARE)
			.component(ItemDescription.TYPE, ItemDescription.block(
				net.minecraft.network.chat.Component.translatable("item.mod-stella-tower.ghost_rune.requires", TowerRunes.EMERALDS),
				net.minecraft.network.chat.Component.translatable("item.mod-stella-tower.ghost_rune.desc")))
	));

	/** A rune's element and floor, on the rune itself. */
	public static final DataComponentType<RuneData> RUNE_DATA = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, StellaTower.id("rune"),
		DataComponentType.<RuneData>builder().persistent(RuneData.CODEC).networkSynchronized(RuneData.STREAM_CODEC).build());
	/** The rune set into a weapon or armor piece. */
	/** A wisp's uses left: its own count, not wear, so nothing mends it back. */
	public static final DataComponentType<Integer> WISP_USES = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, StellaTower.id("wisp_uses"),
		DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

	public static final Identifier WISP_ID = StellaTower.id("wisp");
	public static final WispItem WISP = Registry.register(BuiltInRegistries.ITEM, WISP_ID, new WispItem(
		new Item.Properties().setId(ResourceKey.create(Registries.ITEM, WISP_ID)).rarity(Rarity.RARE).stacksTo(1)
			.component(WISP_USES, TowerWisps.USES)
			.component(ItemDescription.TYPE, ItemDescription.block("wisp"))
	));

	public static final Identifier VOID_INGOT_ID = StellaTower.id("void_ingot");
	public static final Item VOID_INGOT = Registry.register(BuiltInRegistries.ITEM, VOID_INGOT_ID, new Item(
		new Item.Properties().setId(ResourceKey.create(Registries.ITEM, VOID_INGOT_ID)).rarity(Rarity.EPIC)
	));
	public static final Item VOID_SWORD = voidGear("void_sword", new Item.Properties().sword(VoidGear.TOOL, VoidGear.SWORD_DAMAGE, VoidGear.SWORD_SPEED));
	public static final Item VOID_HELMET = voidGear("void_helmet", new Item.Properties().humanoidArmor(VoidGear.ARMOR, net.minecraft.world.item.equipment.ArmorType.HELMET));
	public static final Item VOID_CHESTPLATE = voidGear("void_chestplate", new Item.Properties().humanoidArmor(VoidGear.ARMOR, net.minecraft.world.item.equipment.ArmorType.CHESTPLATE));
	public static final Item VOID_LEGGINGS = voidGear("void_leggings", new Item.Properties().humanoidArmor(VoidGear.ARMOR, net.minecraft.world.item.equipment.ArmorType.LEGGINGS));
	public static final Item VOID_BOOTS = voidGear("void_boots", new Item.Properties().humanoidArmor(VoidGear.ARMOR, net.minecraft.world.item.equipment.ArmorType.BOOTS));

	private static Item voidGear(final String path, final Item.Properties properties) {
		Identifier id = StellaTower.id(path);
		return Registry.register(BuiltInRegistries.ITEM, id, new Item(properties.setId(ResourceKey.create(Registries.ITEM, id)).rarity(Rarity.EPIC).fireResistant()));
	}

	/** A ghost rune set into a chestplate or a weapon. */
	public static final DataComponentType<net.minecraft.util.Unit> GHOSTED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, StellaTower.id("ghosted"),
		DataComponentType.<net.minecraft.util.Unit>builder().persistent(net.minecraft.util.Unit.CODEC)
			.networkSynchronized(net.minecraft.network.codec.StreamCodec.unit(net.minecraft.util.Unit.INSTANCE)).build());

	/** What a piece of gear or a spellbook has counted in the tower: its kills and the ascension they earned. */
	public static final DataComponentType<TowerGrowth.Ascension> ASCENDED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, StellaTower.id("ascended"),
		DataComponentType.<TowerGrowth.Ascension>builder().persistent(TowerGrowth.Ascension.CODEC).networkSynchronized(TowerGrowth.Ascension.STREAM_CODEC).build());

	public static final DataComponentType<RuneData> RUNED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, StellaTower.id("runed"),
		DataComponentType.<RuneData>builder().persistent(RuneData.CODEC).networkSynchronized(RuneData.STREAM_CODEC).build());
	public static final Identifier RUNE_ID = StellaTower.id("rune");
	public static final RuneItem RUNE = Registry.register(BuiltInRegistries.ITEM, RUNE_ID, new RuneItem(
		new Item.Properties().setId(ResourceKey.create(Registries.ITEM, RUNE_ID)).stacksTo(16).rarity(Rarity.EPIC)
	));

	private TowerItems() {
	}

	public static void register() {
		ItemDescription.register();
		StellaTower.LOGGER.debug("Registered {}", VOID_KEY_ID);
	}
}
