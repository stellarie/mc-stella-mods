package com.stella.nether;

import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** The Sovereign's spoils: the Blaze Core it always leaves, and the Flame Mantle it now and then does. */
public final class NetherItems {
	public static final Identifier BLAZE_CORE_ID = StellaNether.id("blaze_core");
	public static final Item BLAZE_CORE = Registry.register(BuiltInRegistries.ITEM, BLAZE_CORE_ID, new Item(
		new Item.Properties().setId(ResourceKey.create(Registries.ITEM, BLAZE_CORE_ID)).stacksTo(16).rarity(Rarity.EPIC).fireResistant()
	));

	public static final TagKey<Item> REPAIRS_FLAME_MANTLE = TagKey.create(Registries.ITEM, StellaNether.id("repairs_flame_mantle"));
	/** Netherite's protection on the chest, worn with netherite's look until the Mantle has art of its own. */
	public static final ArmorMaterial FLAME_MANTLE_MATERIAL = new ArmorMaterial(40,
		Map.of(ArmorType.HELMET, 3, ArmorType.CHESTPLATE, 8, ArmorType.LEGGINGS, 6, ArmorType.BOOTS, 3, ArmorType.BODY, 11),
		20, SoundEvents.ARMOR_EQUIP_NETHERITE, 3.0F, 0.1F, REPAIRS_FLAME_MANTLE, EquipmentAssets.NETHERITE);
	public static final Identifier FLAME_MANTLE_ID = StellaNether.id("flame_mantle");
	public static final Item FLAME_MANTLE = Registry.register(BuiltInRegistries.ITEM, FLAME_MANTLE_ID, new Item(
		new Item.Properties().setId(ResourceKey.create(Registries.ITEM, FLAME_MANTLE_ID))
			.humanoidArmor(FLAME_MANTLE_MATERIAL, ArmorType.CHESTPLATE).rarity(Rarity.EPIC).fireResistant()
	));

	public static final Identifier SOUL_FIRE_CHARGE_ID = StellaNether.id("soul_fire_charge");
	public static final Item SOUL_FIRE_CHARGE = Registry.register(BuiltInRegistries.ITEM, SOUL_FIRE_CHARGE_ID, new Item(
		new Item.Properties().setId(ResourceKey.create(Registries.ITEM, SOUL_FIRE_CHARGE_ID))
	));

	private NetherItems() {
	}

	public static void register() {
		StellaNether.LOGGER.debug("Registered {} and {}", BLAZE_CORE_ID, FLAME_MANTLE_ID);
	}
}
