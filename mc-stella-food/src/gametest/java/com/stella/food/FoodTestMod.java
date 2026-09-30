package com.stella.food;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;

public final class FoodTestMod implements ModInitializer {
	public static final String MOD_ID = "mod-stella-food-test-mod-stella-food";
	public static final Identifier PLAIN_FOOD_ID = Identifier.fromNamespaceAndPath(MOD_ID, "plain_modded_food");
	public static final Item PLAIN_FOOD = Registry.register(
		BuiltInRegistries.ITEM,
		PLAIN_FOOD_ID,
		new Item(
			new Item.Properties()
				.setId(ResourceKey.create(Registries.ITEM, PLAIN_FOOD_ID))
				.food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.3F).build())
		)
	);

	@Override
	public void onInitialize() {
	}
}
