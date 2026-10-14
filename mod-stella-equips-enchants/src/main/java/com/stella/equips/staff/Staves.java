package com.stella.equips.staff;

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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * The craftable staves. A staff has base MATK and no attack damage, and refining raises its MATK. Each staff also
 * has one effect that grows at each refine milestone. The staff carries a {@code stella_staff} compound in its
 * custom data, so mod-stella-rpg reads the MATK, cooldown, crit and penetration, and mod-stella-spells reads the
 * Doublecast chance, with no class of this mod. A mod missing from the pack leaves its half of the staff unused.
 */
public final class Staves {
	/** The key of the staff compound. The RPG mod and the spells mod read the same name. */
	public static final String PROFILE_KEY = "stella_staff";
	public static final int DURABILITY = 320;
	public static final int ENCHANTABILITY = 14;

	/** What one staff does. The effect is a percent that starts at {@code base} and grows by {@code step} per milestone. */
	public record StaffDef(String id, int matk, String effect, int base, int step) {
	}

	public static final List<StaffDef> DEFS = List.of(
		new StaffDef("amethyst_staff", 44, "doublecast", 10, 5),
		new StaffDef("quartz_staff", 46, "cdr", 5, 4),
		new StaffDef("emberheart_staff", 50, "crit", 5, 4),
		new StaffDef("prismarine_staff", 48, "pen", 5, 5)
	);

	private Staves() {
	}

	public static void register() {
		for (StaffDef def : DEFS) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, def.id()));
			Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)
				.durability(DURABILITY).repairable(Items.IRON_INGOT).enchantable(ENCHANTABILITY)
				.component(DataComponents.CUSTOM_DATA, profile(def))
				.component(DataComponents.LORE, lore(def))));
		}
	}

	public static boolean isStaff(final ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && !data.copyTag().getCompoundOrEmpty(PROFILE_KEY).isEmpty();
	}

	private static CustomData profile(final StaffDef def) {
		CompoundTag staff = new CompoundTag();
		staff.putInt("matk", def.matk());
		staff.putString("effect", def.effect());
		staff.putInt("base", def.base());
		staff.putInt("step", def.step());
		CompoundTag root = new CompoundTag();
		root.put(PROFILE_KEY, staff);
		return CustomData.of(root);
	}

	// A Requires line and a Desc line, split at the width when the tooltip builds.
	private static ItemLore lore(final StaffDef def) {
		String key = "item." + StellaEquips.MOD_ID + "." + def.id();
		return new ItemLore(List.of(
			DescriptionLines.requires(Component.translatable(key + ".requires").withStyle(ChatFormatting.WHITE)),
			DescriptionLines.desc(Component.translatable(key + ".desc").withStyle(ChatFormatting.GRAY))
		));
	}
}
