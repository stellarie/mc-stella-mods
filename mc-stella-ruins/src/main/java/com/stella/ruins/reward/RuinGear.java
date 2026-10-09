package com.stella.ruins.reward;

import java.util.Map;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** Builds tool and armor materials from one {@link GearSpec}, and registers the pieces for build sites. */
public final class RuinGear {
	public static final float SWORD_DAMAGE = 3.0F;
	public static final float SWORD_SPEED = -2.4F;
	public static final float AXE_DAMAGE = 6.0F;
	public static final float AXE_SPEED = -3.2F;
	public static final float PICKAXE_DAMAGE = 1.0F;
	public static final float PICKAXE_SPEED = -2.8F;

	private RuinGear() {
	}

	public static ToolMaterial toolMaterial(final GearSpec spec) {
		return new ToolMaterial(spec.incorrectBlocks(), spec.toolDurability(), spec.toolSpeed(), spec.attackDamageBonus(), spec.enchantValue(), spec.repair());
	}

	public static ArmorMaterial armorMaterial(final GearSpec spec) {
		return armor(spec.armorDurabilityFactor(), spec.helmet(), spec.chestplate(), spec.leggings(), spec.boots(), spec.toughness(),
			spec.knockbackResistance(), spec.enchantValue(), spec.repair(), spec.equipmentAsset());
	}

	public static ArmorMaterial armorMaterial(final SetSpec spec) {
		return armor(spec.durabilityFactor(), spec.helmet(), spec.chestplate(), spec.leggings(), spec.boots(), spec.toughness(),
			spec.knockbackResistance(), spec.enchantValue(), spec.repair(), spec.equipmentAsset());
	}

	private static ArmorMaterial armor(
		final int durabilityFactor, final int helmet, final int chestplate, final int leggings, final int boots, final float toughness,
		final float knockbackResistance, final int enchantValue, final net.minecraft.tags.TagKey<Item> repair, final net.minecraft.resources.Identifier asset
	) {
		Map<ArmorType, Integer> defense = Map.of(
			ArmorType.HELMET, helmet, ArmorType.CHESTPLATE, chestplate, ArmorType.LEGGINGS, leggings, ArmorType.BOOTS, boots,
			ArmorType.BODY, chestplate + leggings
		);
		return new ArmorMaterial(durabilityFactor, defense, enchantValue, SoundEvents.ARMOR_EQUIP_IRON, toughness, knockbackResistance, repair,
			ResourceKey.create(EquipmentAssets.ROOT_ID, asset));
	}

	public static Item sword(final String id, final GearSpec spec) {
		return RuinItems.register(id, Item::new, new Item.Properties().sword(toolMaterial(spec), SWORD_DAMAGE, SWORD_SPEED));
	}

	public static Item axe(final String id, final GearSpec spec) {
		return RuinItems.register(id, Item::new, new Item.Properties().axe(toolMaterial(spec), AXE_DAMAGE, AXE_SPEED));
	}

	public static Item pickaxe(final String id, final GearSpec spec) {
		return RuinItems.register(id, Item::new, new Item.Properties().pickaxe(toolMaterial(spec), PICKAXE_DAMAGE, PICKAXE_SPEED));
	}

	public static Item armorPiece(final String id, final GearSpec spec, final ArmorType type) {
		return RuinItems.register(id, Item::new, new Item.Properties().humanoidArmor(armorMaterial(spec), type));
	}

	/**
	 * True for an item with a melee attack damage of its own. Item tags only list vanilla items, so a modded sword or
	 * axe is found by its component.
	 */
	public static boolean isMeleeWeapon(final ItemStack stack) {
		return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
			.anyMatch(entry -> entry.modifier().is(Item.BASE_ATTACK_DAMAGE_ID));
	}

	/** True for an item that mines stone or wood well: a pickaxe or an axe, vanilla or modded. */
	public static boolean isPickaxeOrAxe(final ItemStack stack) {
		Tool tool = stack.get(DataComponents.TOOL);
		if (tool == null) {
			return false;
		}
		for (Tool.Rule rule : tool.rules()) {
			if (rule.blocks() instanceof HolderSet.Named<?> named
				&& (named.key().equals(BlockTags.MINEABLE_WITH_PICKAXE) || named.key().equals(BlockTags.MINEABLE_WITH_AXE))) {
				return true;
			}
		}
		return false;
	}
}
