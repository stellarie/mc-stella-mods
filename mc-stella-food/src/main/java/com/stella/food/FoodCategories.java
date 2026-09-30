package com.stella.food;

import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

final class FoodCategories {
	private static final Set<Item> MEAT = Set.of(
		Items.BEEF, Items.COOKED_BEEF, Items.PORKCHOP, Items.COOKED_PORKCHOP,
		Items.CHICKEN, Items.COOKED_CHICKEN, Items.MUTTON, Items.COOKED_MUTTON,
		Items.RABBIT, Items.COOKED_RABBIT
	);
	private static final Set<Item> FISH = Set.of(
		Items.COD, Items.COOKED_COD, Items.SALMON, Items.COOKED_SALMON,
		Items.TROPICAL_FISH, Items.PUFFERFISH
	);
	private static final Set<Item> FRUIT = Set.of(
		Items.APPLE, Items.MELON_SLICE, Items.SWEET_BERRIES, Items.GLOW_BERRIES,
		Items.CHORUS_FRUIT
	);
	private static final Set<Item> VEGETABLES = Set.of(
		Items.CARROT, Items.POTATO, Items.BAKED_POTATO, Items.BEETROOT,
		Items.DRIED_KELP
	);
	private static final Set<Item> BAKED = Set.of(
		Items.BREAD, Items.PUMPKIN_PIE, Items.RABBIT_STEW
	);
	private static final Set<Item> SWEETS = Set.of(
		Items.COOKIE, Items.CAKE, Items.HONEY_BOTTLE
	);
	private static final Set<Item> SUSPICIOUS = Set.of(
		Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO,
		Items.SUSPICIOUS_STEW, Items.PUFFERFISH
	);

	private FoodCategories() {
	}

	static Holder<MobEffect> effectFor(final Item item) {
		if (MEAT.contains(item)) {
			return FoodEffects.STEADY_HAND;
		}
		if (SUSPICIOUS.contains(item)) {
			return FoodEffects.WILD_TASTE;
		}
		if (FISH.contains(item)) {
			return FoodEffects.RIVER_SENSE;
		}
		if (FRUIT.contains(item)) {
			return FoodEffects.QUICK_STEP;
		}
		if (VEGETABLES.contains(item)) {
			return FoodEffects.STURDY;
		}
		if (BAKED.contains(item)) {
			return FoodEffects.ENDURANCE;
		}
		if (SWEETS.contains(item)) {
			return FoodEffects.SUGAR_RUSH;
		}
		return FoodEffects.NOURISHED;
	}
}
