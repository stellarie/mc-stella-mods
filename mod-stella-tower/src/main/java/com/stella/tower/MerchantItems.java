package com.stella.tower;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * The items only the ghost merchant sells, bar the potion, which is a vanilla potion with effects of its own (see
 * {@link PerfectVeil#potion}). Each does its work anywhere; only the sale is held to the tower.
 */
public final class MerchantItems {
	public static final Identifier SOUL_TONIC_ID = StellaTower.id("soul_tonic");
	public static final SoulTonicItem SOUL_TONIC = Registry.register(BuiltInRegistries.ITEM, SOUL_TONIC_ID, new SoulTonicItem(
		new Item.Properties().setId(ResourceKey.create(Registries.ITEM, SOUL_TONIC_ID)).rarity(Rarity.RARE).stacksTo(16)
			.usingConvertsTo(Items.GLASS_BOTTLE).component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK)
			.component(DataComponents.LORE, lore("soul_tonic"))));

	public static final Identifier PHANTOM_ARROW_ID = StellaTower.id("phantom_arrow");
	public static final PhantomArrowItem PHANTOM_ARROW = Registry.register(BuiltInRegistries.ITEM, PHANTOM_ARROW_ID, new PhantomArrowItem(
		new Item.Properties().setId(ResourceKey.create(Registries.ITEM, PHANTOM_ARROW_ID)).rarity(Rarity.UNCOMMON)
			.component(DataComponents.LORE, lore("phantom_arrow"))));

	public static final Identifier GHOST_LANTERN_ID = StellaTower.id("ghost_lantern");
	public static final GhostLanternBlock GHOST_LANTERN_BLOCK = Registry.register(BuiltInRegistries.BLOCK, GHOST_LANTERN_ID, new GhostLanternBlock(
		BlockBehaviour.Properties.ofFullCopy(Blocks.SOUL_LANTERN).setId(ResourceKey.create(Registries.BLOCK, GHOST_LANTERN_ID)).lightLevel(state -> 15)));
	public static final GhostLanternItem GHOST_LANTERN = Registry.register(BuiltInRegistries.ITEM, GHOST_LANTERN_ID, new GhostLanternItem(GHOST_LANTERN_BLOCK,
		new Item.Properties().setId(ResourceKey.create(Registries.ITEM, GHOST_LANTERN_ID)).useBlockDescriptionPrefix().rarity(Rarity.RARE)
			.component(DataComponents.LORE, lore("ghost_lantern"))));

	public static final Identifier ECTOPLASM_CHARM_ID = StellaTower.id("ectoplasm_charm");
	public static final Item ECTOPLASM_CHARM = Registry.register(BuiltInRegistries.ITEM, ECTOPLASM_CHARM_ID, new Item(
		new Item.Properties().setId(ResourceKey.create(Registries.ITEM, ECTOPLASM_CHARM_ID)).rarity(Rarity.EPIC).stacksTo(1)
			.component(DataComponents.LORE, lore("ectoplasm_charm"))));

	public static final Identifier TOWER_WHETSTONE_ID = StellaTower.id("tower_whetstone");
	public static final Item TOWER_WHETSTONE = Registry.register(BuiltInRegistries.ITEM, TOWER_WHETSTONE_ID, new Item(
		new Item.Properties().setId(ResourceKey.create(Registries.ITEM, TOWER_WHETSTONE_ID)).rarity(Rarity.EPIC).stacksTo(16)
			.component(DataComponents.LORE, lore("tower_whetstone"))));

	private MerchantItems() {
	}

	private static ItemLore lore(final String item) {
		return new ItemLore(List.of(Component.translatable("item.mod-stella-tower." + item + ".desc").withStyle(ChatFormatting.GRAY)));
	}

	/** Loads the class, which registers everything above, and starts what the items do. */
	public static void register() {
		PerfectVeil.register();
		EctoplasmCharm.register();
		RefineWard.register();
		StellaTower.LOGGER.debug("Registered the merchant's goods");
	}
}
