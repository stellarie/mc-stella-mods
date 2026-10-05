package com.stella.ruins.reward;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * The numbers of one material, for tools and armor alike. A build site writes it once and {@link RuinGear}
 * turns it into a tool material and an armor material.
 */
public record GearSpec(
	int toolDurability,
	float toolSpeed,
	float attackDamageBonus,
	TagKey<Block> incorrectBlocks,
	int armorDurabilityFactor,
	int helmet,
	int chestplate,
	int leggings,
	int boots,
	float toughness,
	float knockbackResistance,
	int enchantValue,
	TagKey<Item> repair,
	Identifier equipmentAsset
) {
}
