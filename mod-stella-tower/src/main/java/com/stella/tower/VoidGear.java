package com.stella.tower;

import java.util.Map;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * Void equipment: a sword and a set of armor a little above netherite, made by upgrading netherite gear
 * with void ingots. A rune set into a piece of it does more than a rune does anywhere else: on a weapon
 * the rune's power counts half as much again and its element is struck in full even against its own; on
 * armor the power counts half as much again and its own-element cut doubles.
 */
public final class VoidGear {
	public static final TagKey<Item> GEAR = TagKey.create(Registries.ITEM, StellaTower.id("void_gear"));
	public static final TagKey<Item> REPAIR = TagKey.create(Registries.ITEM, StellaTower.id("void_repair"));
	/** What a rune's power counts for on void gear. */
	public static final double POWER_FACTOR = 1.5;
	/** What a piece's own-element cut counts for on void armor. */
	public static final double OWN_ELEMENT_FACTOR = 2.0;

	public static final int DURABILITY = 3000;
	public static final float MINING_SPEED = 9.5F;
	/** Added to the sword's base of 3, netherite's being 4. */
	public static final float ATTACK_BONUS = 5.0F;
	public static final int ENCHANTABILITY = 20;
	public static final float SWORD_DAMAGE = 3.0F;
	public static final float SWORD_SPEED = -2.4F;
	public static final int ARMOR_DURABILITY_FACTOR = 44;
	public static final float TOUGHNESS = 4.0F;
	public static final float KNOCKBACK_RESISTANCE = 0.1F;

	public static final ToolMaterial TOOL = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, DURABILITY, MINING_SPEED, ATTACK_BONUS, ENCHANTABILITY, REPAIR);
	public static final ArmorMaterial ARMOR = new ArmorMaterial(ARMOR_DURABILITY_FACTOR, Map.of(
		ArmorType.BOOTS, 4, ArmorType.LEGGINGS, 7, ArmorType.CHESTPLATE, 9, ArmorType.HELMET, 4, ArmorType.BODY, 11
	), ENCHANTABILITY, SoundEvents.ARMOR_EQUIP_NETHERITE, TOUGHNESS, KNOCKBACK_RESISTANCE, REPAIR, EquipmentAssets.NETHERITE);

	private VoidGear() {
	}

	public static boolean isVoid(final ItemStack gear) {
		return gear.is(GEAR);
	}

	/** A rune's power as this piece of gear carries it. */
	public static double power(final ItemStack gear, final double power) {
		return isVoid(gear) ? power * POWER_FACTOR : power;
	}
}
