package com.stella.equips.unique;

import com.stella.equips.StellaEquips;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;

/** Builds the item of a unique from its kind: diamond tool or armor stats, rarity EPIC, repaired with diamond. */
final class UniqueItems {
	static final int BOW_DURABILITY = 768;
	static final int CROSSBOW_DURABILITY = 930;
	static final int MACE_DURABILITY = 1561;
	static final int ENCHANTABILITY = 10;
	static final String NAME_PREFIX = "item." + StellaEquips.MOD_ID + ".";

	private UniqueItems() {
	}

	static Identifier id(final UniqueDef def) {
		return Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, def.id());
	}

	static Component name(final UniqueDef def) {
		return Component.translatableWithFallback(NAME_PREFIX + def.id(), def.name());
	}

	static Item create(final UniqueDef def) {
		Item.Properties properties = new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id(def)));
		return switch (def.kind()) {
			case BOW -> new UniqueBowItem(finish(properties.durability(BOW_DURABILITY)));
			case CROSSBOW -> new UniqueCrossbowItem(finish(
				properties.stacksTo(1).durability(CROSSBOW_DURABILITY).component(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY)));
			case SWORD -> new Item(finish(properties.sword(ToolMaterial.DIAMOND, 3.0F, -2.4F)));
			case AXE -> new Item(finish(properties.axe(ToolMaterial.DIAMOND, 5.0F, -3.0F)));
			case SPEAR -> new Item(finish(
				properties.spear(ToolMaterial.DIAMOND, 1.05F, 1.075F, 0.5F, 3.0F, 10.0F, 6.5F, 5.1F, 10.0F, 4.6F)));
			case MACE -> new UniqueMaceItem(finish(
				properties.durability(MACE_DURABILITY)
					.component(DataComponents.TOOL, MaceItem.createToolProperties())
					.attributes(MaceItem.createAttributes())
					.component(DataComponents.WEAPON, new Weapon(1))));
			case HELMET -> new Item(finish(properties.humanoidArmor(material(def), ArmorType.HELMET)));
			case CHESTPLATE -> new Item(finish(properties.humanoidArmor(material(def), ArmorType.CHESTPLATE)));
			case LEGGINGS -> new Item(finish(properties.humanoidArmor(material(def), ArmorType.LEGGINGS)));
			case BOOTS -> new Item(finish(properties.humanoidArmor(material(def), ArmorType.BOOTS)));
		};
	}

	// Last, so these win over the diamond material's own repair and enchantment values.
	private static Item.Properties finish(final Item.Properties properties) {
		return properties.rarity(Rarity.EPIC).repairable(Items.DIAMOND).enchantable(ENCHANTABILITY);
	}

	private static ArmorMaterial material(final UniqueDef def) {
		ArmorMaterial diamond = ArmorMaterials.DIAMOND;
		return new ArmorMaterial(diamond.durability(), diamond.defense(), ENCHANTABILITY, diamond.equipSound(), diamond.toughness(),
			diamond.knockbackResistance(), ItemTags.REPAIRS_DIAMOND_ARMOR, def.look() == null ? diamond.assetId() : def.look());
	}
}
