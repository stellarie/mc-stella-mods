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

	@Override
	public void onInitialize() {
		SpellbookLoot.register();
		LOGGER.info("{} initialized", MOD_ID);
	}

	private static List<SpellbookItem> registerCatalog() {
		return SpellbookCatalog.DEFINITIONS.stream().map(Spellbooks::register).toList();
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

	private static Component bookName(final Identifier id) {
		return Component.translatable("item." + id.getNamespace() + "." + id.getPath());
	}

	private static Component line(final MutableComponent text, final ChatFormatting color) {
		return text.withStyle(style -> style.withColor(color).withItalic(false));
	}
}
