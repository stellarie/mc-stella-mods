package com.stella.equips.refine;

import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Repairable;

/**
 * How strongly a piece's refine specials scale with the material it is made of. Iron is the baseline of 1.
 * Weaker materials fall short of it and better ones pass it. The material comes from the repair tag of the
 * piece, the way {@code mod-stella-rpg} reads it, so a modded piece that uses a vanilla tag needs no list.
 * A shield and a spellbook count as iron, the material they are refined with.
 */
public final class RefineTier {
	public static final double BASELINE = 1.0;
	public static final double VOID = 2.0;
	private static final String VOID_NAMESPACE = "mod-stella-tower";
	private static final String VOID_PREFIX = "void_";

	private static final Map<String, Double> BY_TAG = Map.ofEntries(
		Map.entry("wooden_tool_materials", 0.6),
		Map.entry("repairs_leather_armor", 0.6),
		Map.entry("stone_tool_materials", 0.8),
		Map.entry("repairs_chain_armor", 0.8),
		Map.entry("copper_tool_materials", 0.8),
		Map.entry("repairs_copper_armor", 0.8),
		Map.entry("gold_tool_materials", 0.8),
		Map.entry("repairs_gold_armor", 0.8),
		Map.entry("iron_tool_materials", 1.0),
		Map.entry("repairs_iron_armor", 1.0),
		Map.entry("diamond_tool_materials", 1.35),
		Map.entry("repairs_diamond_armor", 1.35),
		Map.entry("netherite_tool_materials", 1.7),
		Map.entry("repairs_netherite_armor", 1.7)
	);

	private static final Map<Item, Double> BY_ITEM = Map.of(
		Items.LEATHER, 0.6,
		Items.COPPER_INGOT, 0.8,
		Items.GOLD_INGOT, 0.8,
		Items.IRON_INGOT, 1.0,
		Items.DIAMOND, 1.35,
		Items.NETHERITE_INGOT, 1.7
	);

	private RefineTier() {
	}

	/** The scale of the piece's material; {@link #BASELINE} for a shield, a spellbook, or an unknown material. */
	public static double factor(final ItemStack stack) {
		if (stack.isEmpty() || Refine.isSpellbook(stack) || Refine.isShield(stack)) {
			return BASELINE;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (VOID_NAMESPACE.equals(id.getNamespace()) && id.getPath().startsWith(VOID_PREFIX)) {
			return VOID;
		}
		Repairable repairable = stack.get(DataComponents.REPAIRABLE);
		if (repairable == null) {
			return BASELINE;
		}
		HolderSet<Item> items = repairable.items();
		if (items instanceof HolderSet.Named<Item> named) {
			Double byTag = BY_TAG.get(named.key().location().getPath());
			if (byTag != null) {
				return byTag;
			}
		}
		for (Holder<Item> item : items) {
			return BY_ITEM.getOrDefault(item.value(), BASELINE);
		}
		return BASELINE;
	}
}
