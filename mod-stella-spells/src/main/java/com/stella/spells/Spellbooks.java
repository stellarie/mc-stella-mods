package com.stella.spells;

import net.fabricmc.api.ModInitializer;
import java.util.List;
import java.util.Locale;
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
	public static final List<SpellbookItem> CATALOG = registerCatalog();
	public static final SpellbookItem SPARK_SHOT = CATALOG.get(0);
	public static final List<SpellScrollItem> SCROLLS = registerScrolls();
	public static final Identifier SCROLL_OF_RETURN_ID = Identifier.fromNamespaceAndPath(MOD_ID, "return_scroll");
	public static final ReturnScrollItem SCROLL_OF_RETURN = Registry.register(
		BuiltInRegistries.ITEM, SCROLL_OF_RETURN_ID, new ReturnScrollItem(new Item.Properties()
			.setId(ResourceKey.create(Registries.ITEM, SCROLL_OF_RETURN_ID)).stacksTo(16)
			.component(DataComponents.LORE, new ItemLore(List.of(
				line(Component.translatable("item." + MOD_ID + ".return_scroll.effect"), ChatFormatting.AQUA),
				line(Component.translatable("spellbook." + MOD_ID + ".scroll"), ChatFormatting.DARK_GRAY)
			))))
	);
	/** Scrolls whose paper-and-material pair is already a vanilla recipe take one more of the material. */
	public static final java.util.Set<Identifier> SCROLLS_WITH_DOUBLE_MATERIAL = java.util.Set.of(
		Identifier.fromNamespaceAndPath(MOD_ID, "briar_snare")
	);

	@Override
	public void onInitialize() {
		SpellbookLoot.register();
		SpellStorms.register();
		SpellImbue.register();
		LOGGER.info("{} initialized", MOD_ID);
	}

	private static List<SpellbookItem> registerCatalog() {
		return SpellbookCatalog.DEFINITIONS.stream().map(Spellbooks::register).toList();
	}

	private static List<SpellScrollItem> registerScrolls() {
		return SpellbookCatalog.DEFINITIONS.stream().map(Spellbooks::registerScroll).toList();
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

	// Four tooltip lines: what it targets, how to cast it, what it does, and its base cooldown; a combo book adds its combo.
	static ItemLore description(final SpellDefinition definition) {
		String mode = definition.targetMode().name().toLowerCase(Locale.ROOT);
		String effectKey = "item." + definition.id().getNamespace() + "." + definition.id().getPath() + ".effect";
		List<Component> lines = new java.util.ArrayList<>(List.of(
			line(Component.translatable("spellbook." + MOD_ID + ".target." + mode), ChatFormatting.GRAY),
			line(Component.translatable("spellbook." + MOD_ID + ".use." + mode), ChatFormatting.GRAY),
			line(Component.translatable(effectKey), ChatFormatting.AQUA),
			line(Component.translatable("spellbook." + MOD_ID + ".cooldown", definition.baseCooldownTicks() / 20), ChatFormatting.DARK_GRAY)
		));
		SpellCombos.involving(definition.id()).ifPresent(combo -> lines.add(line(Component.translatable(
			"spellbook." + MOD_ID + ".combo", bookName(combo.first()), bookName(combo.second()), combo.name()
		), ChatFormatting.LIGHT_PURPLE)));
		return new ItemLore(lines);
	}

	// A scroll's tooltip: what it targets, how to cast it, what it does, and that it is used up.
	static ItemLore scrollDescription(final SpellDefinition definition) {
		List<Component> book = description(definition).lines();
		return new ItemLore(List.of(
			book.get(0), book.get(1), book.get(2),
			line(Component.translatable("spellbook." + MOD_ID + ".scroll"), ChatFormatting.DARK_GRAY)
		));
	}

	private static Component bookName(final Identifier id) {
		return Component.translatable("item." + id.getNamespace() + "." + id.getPath());
	}

	private static Component line(final MutableComponent text, final ChatFormatting color) {
		return text.withStyle(style -> style.withColor(color).withItalic(false));
	}
}
