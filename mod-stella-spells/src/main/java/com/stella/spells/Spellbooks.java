package com.stella.spells;

import net.fabricmc.api.ModInitializer;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemLore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Spellbooks implements ModInitializer {
	public static final String MOD_ID = "mod-stella-spells";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final Identifier SPARK_SHOT_ID = Identifier.fromNamespaceAndPath(MOD_ID, "spark_shot");
	/** Spells only a book casts: no scroll copies them. Declared before the scrolls register. */
	public static final java.util.Set<Identifier> BOOK_ONLY = java.util.Set.of(Identifier.fromNamespaceAndPath(MOD_ID, "summon_skeleton"), Identifier.fromNamespaceAndPath(MOD_ID, "elemental_quiver"), Identifier.fromNamespaceAndPath(MOD_ID, "summon_wolf"));
	public static final List<SpellbookItem> CATALOG = registerCatalog();
	public static final SpellbookItem SPARK_SHOT = CATALOG.get(0);
	public static final List<SpellScrollItem> SCROLLS = registerScrolls();
	public static final Identifier SCROLL_OF_RETURN_ID = Identifier.fromNamespaceAndPath(MOD_ID, "return_scroll");
	public static final ReturnScrollItem SCROLL_OF_RETURN = Registry.register(
		BuiltInRegistries.ITEM, SCROLL_OF_RETURN_ID, new ReturnScrollItem(new Item.Properties()
			.setId(ResourceKey.create(Registries.ITEM, SCROLL_OF_RETURN_ID)).stacksTo(16)
			.component(DataComponents.LORE, lore(block("return_scroll", ChatFormatting.AQUA),
				line(Component.translatable("spellbook." + MOD_ID + ".scroll"), ChatFormatting.DARK_GRAY))))
	);
	public static final Identifier SCROLL_BOOK_ID = Identifier.fromNamespaceAndPath(MOD_ID, "scroll_book");
	public static final ScrollBookItem SCROLL_BOOK = Registry.register(
		BuiltInRegistries.ITEM, SCROLL_BOOK_ID, new ScrollBookItem(new Item.Properties()
			.setId(ResourceKey.create(Registries.ITEM, SCROLL_BOOK_ID)).stacksTo(1)
			.component(DataComponents.LORE, new ItemLore(block("scroll_book", ChatFormatting.AQUA))))
	);
	public static final Identifier COMPENDIUM_ID = Identifier.fromNamespaceAndPath(MOD_ID, "compendium");
	public static final CompendiumItem COMPENDIUM = Registry.register(
		BuiltInRegistries.ITEM, COMPENDIUM_ID, new CompendiumItem(new Item.Properties()
			.setId(ResourceKey.create(Registries.ITEM, COMPENDIUM_ID)).stacksTo(1)
			.component(DataComponents.LORE, lore(block("compendium", ChatFormatting.AQUA),
				line(Component.translatable("spellbook." + MOD_ID + ".cooldown", CompendiumItem.COOLDOWN_TICKS / 20), ChatFormatting.DARK_GRAY))))
	);
	/** Scrolls whose paper-and-material pair is already a vanilla recipe take one more of the material. */
	public static final java.util.Set<Identifier> SCROLLS_WITH_DOUBLE_MATERIAL = java.util.Set.of(
		Identifier.fromNamespaceAndPath(MOD_ID, "briar_snare")
	);

	/**
	 * Spells a later round removed, each pointing at the book that took its place, so a stack in a saved world
	 * survives. {@code smite} was one of them; stage 7 gives that id a priest book of its own, so it is no
	 * longer retired: an old stack of the mage Smite now loads as the priest book rather than as Battle Hymn.
	 */
	public static final java.util.Map<String, String> RETIRED = java.util.Map.of(
		"skyhook", "gravity", "healing_spring", "bulwark_hymn"
	);

	@Override
	public void onInitialize() {
		for (java.util.Map.Entry<String, String> retired : RETIRED.entrySet()) {
			Identifier from = Identifier.fromNamespaceAndPath(MOD_ID, retired.getKey());
			Identifier to = Identifier.fromNamespaceAndPath(MOD_ID, retired.getValue());
			((net.fabricmc.fabric.api.event.registry.FabricRegistry) BuiltInRegistries.ITEM).addAlias(from, to);
			((net.fabricmc.fabric.api.event.registry.FabricRegistry) BuiltInRegistries.ITEM).addAlias(scrollId(from), scrollId(to));
		}
		Awakening.register();
		SpellStatus.register();
		QuickSlots.register();
		MobSpells.register();
		MobWeaponImbue.register();
		SummonInventoryStore.register();
		SummonSkeleton.register();
		FighterSkills.register();
		ArcherSkills.register();
		ArcherDraw.register();
		ArrowItems.register();
		SpellEntities.register();
		Falcons.register();
		MobBlitz.register();
		SummonWolf.register();
		Quiver.register();
		ArrowHits.register();
		ConcentrateStats.register();
		PriestSkills.register();
		SpellbookLoot.register();
		SpellStorms.register();
		SpellVisuals.register();
		DragonCharge.register();
		SpellBolt.register();
		SpellImbue.register();
		StaffBolt.register();
		SpellTimers.register();
		BookRefine.register();
		LOGGER.info("{} initialized", MOD_ID);
	}

	private static List<SpellbookItem> registerCatalog() {
		return SpellbookCatalog.DEFINITIONS.stream().map(Spellbooks::register).toList();
	}

	private static List<SpellScrollItem> registerScrolls() {
		return SpellbookCatalog.DEFINITIONS.stream().filter(definition -> !BOOK_ONLY.contains(definition.id())).map(Spellbooks::registerScroll).toList();
	}

	public static Identifier scrollId(final Identifier spell) {
		return Identifier.fromNamespaceAndPath(spell.getNamespace(), spell.getPath() + "_scroll");
	}

	private static SpellScrollItem registerScroll(final SpellDefinition definition) {
		Identifier id = scrollId(definition.id());
		Item.Properties properties = new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).stacksTo(16)
			.component(DataComponents.LORE, scrollDescription(definition));
		return Registry.register(BuiltInRegistries.ITEM, id, new SpellScrollItem(properties, definition));
	}

	private static SpellbookItem register(final SpellDefinition definition) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, definition.id());
		Item.Properties properties = new Item.Properties().setId(key).stacksTo(1).enchantable(1)
			.component(DataComponents.LORE, description(definition));
		return Registry.register(
			BuiltInRegistries.ITEM, definition.id(), new SpellbookItem(properties, definition)
		);
	}

	/**
	 * A Requires line and a Desc line, then the base cooldown; a combo book adds its combo. The Desc line holds the effect,
	 * and says whom or where it hits. The client swaps it for the live line with the viewer's numbers.
	 */
	static ItemLore description(final SpellDefinition definition) {
		List<Component> lines = new java.util.ArrayList<>(block(definition.id().getPath(), ChatFormatting.AQUA));
		lines.add(line(Component.translatable("spellbook." + MOD_ID + ".cooldown", definition.baseCooldownTicks() / 20), ChatFormatting.DARK_GRAY));
		SpellCombos.involving(definition.id()).ifPresent(combo -> lines.add(line(Component.translatable(
			"spellbook." + MOD_ID + ".combo", bookName(combo.first()), bookName(combo.second()), combo.name()
		), ChatFormatting.LIGHT_PURPLE)));
		return new ItemLore(lines);
	}

	// A scroll's tooltip: what it needs, what it does, and that it is used up.
	static ItemLore scrollDescription(final SpellDefinition definition) {
		List<Component> book = description(definition).lines();
		return new ItemLore(List.of(
			book.get(0), book.get(1),
			line(Component.translatable("spellbook." + MOD_ID + ".scroll"), ChatFormatting.DARK_GRAY)
		));
	}

	/** A lore of a Requires and Desc block with the lines that follow it. */
	static ItemLore lore(final List<Component> block, final Component... after) {
		List<Component> lines = new java.util.ArrayList<>(block);
		lines.addAll(List.of(after));
		return new ItemLore(lines);
	}

	/** The Requires line and the Desc line of an item whose lang holds {@code item.<mod>.<name>.requires} and {@code .desc}. */
	static List<Component> block(final String name, final ChatFormatting color) {
		return List.of(
			DescriptionLines.requires(Component.translatable("item." + MOD_ID + "." + name + ".requires").withStyle(ChatFormatting.WHITE)),
			DescriptionLines.desc(Component.translatable("item." + MOD_ID + "." + name + ".desc").withStyle(color))
		);
	}

	private static Component bookName(final Identifier id) {
		return Component.translatable("item." + id.getNamespace() + "." + id.getPath());
	}

	/** A tooltip line in the color, marked so the client splits it at 24 characters. */
	static Component line(final MutableComponent text, final ChatFormatting color) {
		return DescriptionLines.marked(text.withStyle(style -> style.withColor(color).withItalic(false)));
	}
}
