package com.stella.end;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * The items only the End holds: the void shard, a smithing material for the gear of a later slice, and four relics.
 * A relic gives fixed bonuses while it sits in the off hand.
 */
public final class EndItems {
	public static final String SHARD_ID = "void_shard";
	public static final List<String> RELIC_IDS = List.of("ender_heart", "shulker_carapace", "wither_fang", "void_walker_charm");

	private static Item shard;
	private static final List<Item> RELICS = new ArrayList<>();

	private EndItems() {
	}

	public static void register() {
		shard = Registry.register(BuiltInRegistries.ITEM, StellaEnd.id(SHARD_ID), new Item(properties(SHARD_ID).rarity(Rarity.RARE)));
		for (String name : RELIC_IDS) {
			Item relic = new Item(properties(name).stacksTo(1).rarity(Rarity.EPIC).component(DataComponents.ATTRIBUTE_MODIFIERS, bonuses(name)));
			RELICS.add(Registry.register(BuiltInRegistries.ITEM, StellaEnd.id(name), relic));
		}
	}

	public static Item shard() {
		return shard;
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
