package com.stella.ruins.site.spidernest;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.reward.GearSpec;
import com.stella.ruins.reward.RuinGear;
import com.stella.ruins.reward.RuinItems;
import com.stella.ruins.reward.RuinSets;
import com.stella.ruins.reward.RuinUniques;
import com.stella.ruins.reward.UniqueEffects;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import java.util.Map;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;

/**
 * The exclusive items of the nest: three materials, the Silkweave light armor, the Silkstring Bow, and the unique
 * Widow's Fang. The materials drop only from the nest's mobs and sit only in its vaults.
 */
public final class NestItems {
	/** Each Silkweave piece adds this share of the base movement speed. */
	public static final double SILKWEAVE_SPEED = 0.05;
	public static final TagKey<Item> SILKWEAVE_REPAIR = TagKey.create(Registries.ITEM, StellaRuins.id("silkweave_repair"));
	/** Leather-tier armor, a little better: hood 2, vest 5, leggings 4, boots 2. */
	public static final GearSpec SILKWEAVE = new GearSpec(
		0, 1.0F, 0.0F, BlockTags.INCORRECT_FOR_WOODEN_TOOL,
		9, 2, 5, 4, 2, 0.0F, 0.0F, 15,
		SILKWEAVE_REPAIR, StellaRuins.id("silkweave"));

	public static Item SILKWEAVE_ITEM;
	public static Item VENOM_SAC;
	public static Item MATRIARCH_FANG;
	public static Item SILKWEAVE_HELMET;
	public static Item SILKWEAVE_CHESTPLATE;
	public static Item SILKWEAVE_LEGGINGS;
	public static Item SILKWEAVE_BOOTS;
	public static SilkstringBow SILKSTRING_BOW;
	public static Item WIDOWS_FANG;

	private NestItems() {
	}

	public static void register() {
		SILKWEAVE_ITEM = RuinItems.register("silkweave", Item::new, new Item.Properties());
		VENOM_SAC = RuinItems.register("venom_sac", Item::new, new Item.Properties());
		MATRIARCH_FANG = RuinItems.register("matriarch_fang", Item::new, new Item.Properties());
		SILKWEAVE_HELMET = armor("silkweave_helmet", ArmorType.HELMET);
		SILKWEAVE_CHESTPLATE = armor("silkweave_chestplate", ArmorType.CHESTPLATE);
		SILKWEAVE_LEGGINGS = armor("silkweave_leggings", ArmorType.LEGGINGS);
		SILKWEAVE_BOOTS = armor("silkweave_boots", ArmorType.BOOTS);
		RuinSets.adopt("silkweave", SILKWEAVE, Map.of(
			EquipmentSlot.HEAD, SILKWEAVE_HELMET, EquipmentSlot.CHEST, SILKWEAVE_CHESTPLATE, EquipmentSlot.LEGS, SILKWEAVE_LEGGINGS, EquipmentSlot.FEET, SILKWEAVE_BOOTS));
		SILKSTRING_BOW = RuinItems.register("silkstring_bow", SilkstringBow::new, new Item.Properties().durability(480).enchantable(1));
		UniqueEffects.define(WidowEffect.ID, new WidowEffect());
		WIDOWS_FANG = RuinUniques.register("widows_fang", Item::new,
			new Item.Properties().stacksTo(1).sword(ToolMaterial.IRON, RuinGear.SWORD_DAMAGE, RuinGear.SWORD_SPEED), WidowEffect.ID);
	}

	/** One armor piece: the material's own defense, plus the movement speed of its slot. */
	private static Item armor(final String id, final ArmorType type) {
		ArmorMaterial material = RuinGear.armorMaterial(SILKWEAVE);
		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
		for (ItemAttributeModifiers.Entry entry : material.createAttributes(type).modifiers()) {
			builder.add(entry.attribute(), entry.modifier(), entry.slot());
		}
		builder.add(Attributes.MOVEMENT_SPEED,
			new AttributeModifier(StellaRuins.id("silkweave_speed_" + type.getName()), SILKWEAVE_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
			EquipmentSlotGroup.bySlot(type.getSlot()));
		return RuinItems.register(id, Item::new, new Item.Properties().humanoidArmor(material, type).attributes(builder.build()));
	}
}
