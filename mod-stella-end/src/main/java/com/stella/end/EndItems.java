package com.stella.end;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * The items only the End holds: the void shard, the smithing template of the ultimate gear, four relics that give fixed
 * bonuses in the off hand, the dragon scale, the draconic weapons and armour, the withering sword and bow, and the dark
 * blaze core and wither core.
 */
public final class EndItems {
	public static final String SHARD_ID = "void_shard";
	public static final List<String> RELIC_IDS = List.of("ender_heart", "shulker_carapace", "wither_fang", "void_walker_charm");
	public static final Identifier SMITHING_ID = StellaEnd.id("draconic_smithing");
	public static final String DARK_BLAZE_CORE = "dark_blaze_core";
	public static final String WITHER_CORE = "wither_core";

	/** A sword swings at 1.6 a second; the staff keeps that speed so a stat that scales swings scales every staff alike. */
	private static final double SWORD_SPEED_MODIFIER = -2.4;
	private static final int ARMOR_DURABILITY = 45;
	private static final int ARMOR_ENCHANTABILITY = 15;
	private static final int STAFF_DURABILITY = 640;
	private static final int STAFF_ENCHANTABILITY = 14;
	private static final int BOW_DURABILITY = 2031;
	private static final int WITHERING_BOW_DURABILITY = 1024;
	private static final TagKey<Item> REPAIRS_ARMOR = TagKey.create(Registries.ITEM, StellaEnd.id("repairs_draconic_armor"));
	private static final ResourceKey<EquipmentAsset> ARMOR_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, StellaEnd.id("draconic"));

	private static Item shard;
	private static Item darkBlazeCore;
	private static Item witherCore;
	private static final List<Item> RELICS = new ArrayList<>();

	private EndItems() {
	}

	public static void register() {
		shard = Registry.register(BuiltInRegistries.ITEM, StellaEnd.id(SHARD_ID), new Item(properties(SHARD_ID).rarity(Rarity.RARE)));
		for (String name : RELIC_IDS) {
			Item relic = new Item(properties(name).stacksTo(1).rarity(Rarity.EPIC).component(DataComponents.ATTRIBUTE_MODIFIERS, bonuses(name)));
			RELICS.add(Registry.register(BuiltInRegistries.ITEM, StellaEnd.id(name), relic));
		}
		registerGear();
		registerArms();
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, SMITHING_ID, DraconicSmithingRecipe.SERIALIZER);
	}

	private static void registerGear() {
		put(DraconicGear.DRAGON_SCALE, new Item(properties(DraconicGear.DRAGON_SCALE).rarity(Rarity.EPIC)));
		// The tooltip adds the player's own 1 damage
		// and 4 speed. The baselines subtract them,
		// and the netherite bonus.
		float baseline = (float) (DraconicGear.BLADE_DAMAGE - 1.0 - ToolMaterial.NETHERITE.attackDamageBonus());
		put(DraconicGear.BLADE, new Item(properties(DraconicGear.BLADE).sword(ToolMaterial.NETHERITE, baseline, (float) (DraconicGear.BLADE_SPEED - 4.0))
			.rarity(Rarity.EPIC).fireResistant()));
		put(DraconicGear.BOW, new DraconicBow(properties(DraconicGear.BOW).durability(BOW_DURABILITY).enchantable(ToolMaterial.NETHERITE.enchantmentValue())
			.rarity(Rarity.EPIC).fireResistant()));
		put(DraconicGear.STAFF, new Item(properties(DraconicGear.STAFF).durability(STAFF_DURABILITY).repairable(Items.NETHERITE_INGOT).enchantable(STAFF_ENCHANTABILITY)
			.component(DataComponents.ATTRIBUTE_MODIFIERS, staffSpeed()).component(DataComponents.CUSTOM_DATA, staffProfile())
			.rarity(Rarity.EPIC).fireResistant()));
		Map<ArmorType, Integer> defense = new EnumMap<>(ArmorType.class);
		ArmorType[] types = {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS};
		for (int i = 0; i < types.length; i++) {
			defense.put(types[i], DraconicGear.ARMOR_POINTS[i]);
		}
		ArmorMaterial material = new ArmorMaterial(ARMOR_DURABILITY, defense, ARMOR_ENCHANTABILITY, SoundEvents.ARMOR_EQUIP_NETHERITE,
			(float) DraconicGear.ARMOR_TOUGHNESS, (float) DraconicGear.ARMOR_KNOCKBACK, REPAIRS_ARMOR, ARMOR_ASSET);
		for (int i = 0; i < types.length; i++) {
			String id = DraconicGear.ARMOR.get(i);
			put(id, new Item(properties(id).humanoidArmor(material, types[i]).attributes(armorAttributes(material, types[i])).rarity(Rarity.EPIC).fireResistant()));
		}
	}

	private static void registerArms() {
		// The numbers of a vanilla netherite sword; the ingot that repairs it comes from the netherite tool material.
		put(WitheringArms.SWORD_ID, new Item(properties(WitheringArms.SWORD_ID).sword(ToolMaterial.NETHERITE, 3.0F, -2.4F).rarity(Rarity.RARE).fireResistant()));
		put(WitheringArms.BOW_ID, new BowItem(properties(WitheringArms.BOW_ID).durability(WITHERING_BOW_DURABILITY).repairable(Items.NETHERITE_INGOT)
			.enchantable(ToolMaterial.NETHERITE.enchantmentValue()).rarity(Rarity.RARE).fireResistant()));
		darkBlazeCore = Registry.register(BuiltInRegistries.ITEM, StellaEnd.id(DARK_BLAZE_CORE), new Item(properties(DARK_BLAZE_CORE).rarity(Rarity.EPIC)));
		witherCore = Registry.register(BuiltInRegistries.ITEM, StellaEnd.id(WITHER_CORE), new Item(properties(WITHER_CORE).rarity(Rarity.EPIC)));
	}

	private static void put(final String id, final Item item) {
		Registry.register(BuiltInRegistries.ITEM, StellaEnd.id(id), item);
	}

	/** The material's armour, toughness and knockback resistance, and the health a piece adds on top. */
	private static ItemAttributeModifiers armorAttributes(final ArmorMaterial material, final ArmorType type) {
		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
		for (ItemAttributeModifiers.Entry entry : material.createAttributes(type).modifiers()) {
			builder.add(entry.attribute(), entry.modifier(), entry.slot(), entry.display());
		}
		Identifier health = StellaEnd.id("draconic_health_" + type.getName());
		builder.add(Attributes.MAX_HEALTH, new AttributeModifier(health, DraconicGear.ARMOR_HEALTH, AttributeModifier.Operation.ADD_VALUE),
			EquipmentSlotGroup.bySlot(type.getSlot()));
		return builder.build();
	}

	private static ItemAttributeModifiers staffSpeed() {
		return ItemAttributeModifiers.builder()
			.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, SWORD_SPEED_MODIFIER, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
			.build();
	}

	/** The staff compound the equips and rpg mods read by name: a magic attack sixty percent over the strongest staff. */
	private static CustomData staffProfile() {
		CompoundTag staff = new CompoundTag();
		staff.putInt("matk", (int) Math.round(DraconicGear.STAFF_BASE_MATK * (1.0 + DraconicGear.SPELL_POWER_BONUS)));
		staff.putString("effect", "crit");
		staff.putInt("base", 5);
		staff.putInt("step", 4);
		CompoundTag root = new CompoundTag();
		root.put("stella_staff", staff);
		return CustomData.of(root);
	}

	public static Item shard() {
		return shard;
	}

	/** The Sovereign's drop. */
	public static Item darkBlazeCore() {
		return darkBlazeCore;
	}

	/** Each Wither Knight's drop. */
	public static Item witherCore() {
		return witherCore;
	}

	public static List<Item> relics() {
		return List.copyOf(RELICS);
	}

	private static Item.Properties properties(final String name) {
		return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, StellaEnd.id(name)));
	}

	private static ItemAttributeModifiers bonuses(final String relic) {
		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
		switch (relic) {
			case "ender_heart" -> add(builder, relic, Attributes.MAX_HEALTH, 8.0, AttributeModifier.Operation.ADD_VALUE);
			case "shulker_carapace" -> {
				add(builder, relic, Attributes.ARMOR, 4.0, AttributeModifier.Operation.ADD_VALUE);
				add(builder, relic, Attributes.ARMOR_TOUGHNESS, 2.0, AttributeModifier.Operation.ADD_VALUE);
			}
			case "wither_fang" -> add(builder, relic, Attributes.ATTACK_DAMAGE, 4.0, AttributeModifier.Operation.ADD_VALUE);
			case "void_walker_charm" -> {
				add(builder, relic, Attributes.MOVEMENT_SPEED, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
				add(builder, relic, Attributes.SAFE_FALL_DISTANCE, 4.0, AttributeModifier.Operation.ADD_VALUE);
			}
			default -> throw new IllegalArgumentException("unknown relic " + relic);
		}
		return builder.build();
	}

	private static void add(
		final ItemAttributeModifiers.Builder builder, final String relic, final Holder<Attribute> attribute, final double amount,
		final AttributeModifier.Operation operation
	) {
		Identifier id = StellaEnd.id("relic/" + relic + "/" + BuiltInRegistries.ATTRIBUTE.getKey(attribute.value()).getPath());
		builder.add(attribute, new AttributeModifier(id, amount, operation), EquipmentSlotGroup.OFFHAND);
	}
}
