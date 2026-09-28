package com.stella.food;

import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;

public final class FoodItems {
	public static final Item HUNTER_STEW = register("hunter_stew", 10, 0.8F);
	public static final Item MINERS_LUNCH = register("miners_lunch", 10, 0.8F);
	public static final Item ORCHARD_TART = register("orchard_tart", 8, 0.8F);
	public static final Item FISHERS_FEAST = register("fishers_feast", 10, 0.8F);
	public static final Item NETHER_SKEWER = register("nether_skewer", 10, 0.9F);
	public static final Item GOLDEN_SUPPER = register("golden_supper", 12, 1.0F);
	public static final List<Item> DISHES = List.of(
		HUNTER_STEW,
		MINERS_LUNCH,
		ORCHARD_TART,
		FISHERS_FEAST,
		NETHER_SKEWER,
		GOLDEN_SUPPER
	);
	public static final Map<Item, Holder<MobEffect>> DISH_EFFECTS = Map.of(
		HUNTER_STEW, FoodEffects.SUREFOOTED,
		MINERS_LUNCH, FoodEffects.DEEP_BREATH,
		ORCHARD_TART, FoodEffects.LIGHT_STEP,
		FISHERS_FEAST, FoodEffects.CURRENT_RIDER,
		NETHER_SKEWER, FoodEffects.EMBER_SKIN,
		GOLDEN_SUPPER, FoodEffects.SECOND_WIND
	);

	private FoodItems() {
	}

	public static void register() {
	}

	public static boolean isDish(final Item item) {
		return DISH_EFFECTS.containsKey(item);
	}

	private static Item register(final String path, final int nutrition, final float saturation) {
		Identifier id = StellaFood.id(path);
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
		FoodProperties food = new FoodProperties.Builder()
			.nutrition(nutrition)
			.saturationModifier(saturation)
			.alwaysEdible()
			.build();
		return Registry.register(
			BuiltInRegistries.ITEM,
			id,
			new Item(new Item.Properties().setId(key).stacksTo(16).food(food))
		);
	}
}
