package com.stella.spells;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Spellbooks implements ModInitializer {
	public static final String MOD_ID = "mod-stella-spells";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final Identifier SPARK_SHOT_ID = Identifier.fromNamespaceAndPath(MOD_ID, "spark_shot");
	public static final SpellbookItem SPARK_SHOT = registerSparkShot();

	@Override
	public void onInitialize() {
		SpellbookLoot.register();
		LOGGER.info("{} initialized", MOD_ID);
	}

	private static SpellbookItem registerSparkShot() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SPARK_SHOT_ID);
		Item.Properties properties = new Item.Properties().setId(key).stacksTo(1).enchantable(1);
		return Registry.register(BuiltInRegistries.ITEM, SPARK_SHOT_ID, new SpellbookItem(properties));
	}
}
