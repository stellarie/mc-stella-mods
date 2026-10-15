package com.stella.equips.merchant;

import com.stella.equips.StellaEquips;
import com.stella.equips.tooltip.DescriptionLines;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * The Arcane Shortsword, a netherite-tier sword that only the ghost merchant sells. Every melee hit of it deals one damage;
 * the rule lives in mod-stella-rpg, which finds the sword by the item tag {@code mod-stella-rpg:one_damage_weapons}.
 * Held in the main hand it gives MATK through the {@code stella_staff} profile that the RPG mod reads for staves.
 * It has no spell focus tag, so it never fires a staff bolt.
 */
public final class ArcaneShortsword {
	public static final String ID = "arcane_shortsword";
	/** MATK in K units while held, and what each refine level adds. */
	public static final int MATK = 100;
	public static final int MATK_PER_LEVEL = 5;

	public static final Item ITEM = register();

	private ArcaneShortsword() {
	}

	public static void init() {
		// Touching ITEM runs the static initializer once.
	}

	private static Item register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, ID));
		String lang = "item." + StellaEquips.MOD_ID + "." + ID;
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)
			.sword(ToolMaterial.NETHERITE, 3.0F, -2.4F).rarity(Rarity.EPIC).fireResistant()
			.component(DataComponents.CUSTOM_DATA, profile())
			.component(DataComponents.LORE, new ItemLore(List.of(
				DescriptionLines.requires(Component.translatable(lang + ".requires").withStyle(ChatFormatting.WHITE)),
				DescriptionLines.desc(Component.translatable(lang + ".desc").withStyle(ChatFormatting.GRAY)))))));
	}

	static CustomData profile() {
		CompoundTag staff = new CompoundTag();
		staff.putInt("matk", MATK);
		staff.putInt("matk_step", MATK_PER_LEVEL);
		staff.putString("effect", "none");
		CompoundTag root = new CompoundTag();
		root.put("stella_staff", staff);
		return CustomData.of(root);
	}
}
