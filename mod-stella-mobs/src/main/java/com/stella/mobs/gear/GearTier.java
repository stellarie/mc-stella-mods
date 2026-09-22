package com.stella.mobs.gear;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public enum GearTier {
	LEATHER(
		40, 0.10F,
		Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS,
		Items.WOODEN_SWORD, Items.BOW
	),
	COPPER(
		24, 0.08F,
		Items.COPPER_HELMET, Items.COPPER_CHESTPLATE, Items.COPPER_LEGGINGS, Items.COPPER_BOOTS,
		Items.STONE_SWORD, Items.BOW
	),
	CHAINMAIL(
		15, 0.06F,
		Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS,
		Items.COPPER_SWORD, Items.CROSSBOW
	),
	IRON(
		10, 0.045F,
		Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
		Items.IRON_SWORD, Items.CROSSBOW
	),
	GOLD(
		6, 0.03F,
		Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS,
		Items.GOLDEN_SWORD, Items.CROSSBOW
	),
	DIAMOND(
		4, 0.02F,
		Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
		Items.DIAMOND_SWORD, Items.BOW
	),
	NETHERITE(
		1, 0.01F,
		Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS,
		Items.NETHERITE_SWORD, Items.BOW
	);

	private static final EquipmentSlot[] ARMOR_SLOTS = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private final int weight;
	private final float dropChance;
	private final Item helmet;
	private final Item chestplate;
	private final Item leggings;
	private final Item boots;
	private final Item meleeWeapon;
	private final Item rangedWeapon;

	GearTier(
		final int weight,
		final float dropChance,
		final Item helmet,
		final Item chestplate,
		final Item leggings,
		final Item boots,
		final Item meleeWeapon,
		final Item rangedWeapon
	) {
		this.weight = weight;
		this.dropChance = dropChance;
		this.helmet = helmet;
		this.chestplate = chestplate;
		this.leggings = leggings;
		this.boots = boots;
		this.meleeWeapon = meleeWeapon;
		this.rangedWeapon = rangedWeapon;
	}

	public static EquipmentSlot[] armorSlots() {
		return ARMOR_SLOTS.clone();
	}

	public int weight() {
		return this.weight;
	}

	public float dropChance() {
		return this.dropChance;
	}

	public Item meleeWeapon() {
		return this.meleeWeapon;
	}

	public Item rangedWeapon() {
		return this.rangedWeapon;
	}

	public Item armorFor(final EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> this.helmet;
			case CHEST -> this.chestplate;
			case LEGS -> this.leggings;
			case FEET -> this.boots;
			default -> throw new IllegalArgumentException("Not an armor slot: " + slot);
		};
	}

	public boolean allowsShield() {
		return this.ordinal() >= CHAINMAIL.ordinal();
	}
}
