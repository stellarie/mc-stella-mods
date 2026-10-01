package com.stella.food;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

/**
 * Container rolls for dishes: a combined dish at 10% and a potion-tipped dish at 5%, each its own draw. A tipped
 * dish carries a random potion and a seasoning roll, the same as one from the cooking station.
 */
public final class FoodLoot {
	public static final double DISH_CHANCE = 0.10;
	public static final double TIPPED_CHANCE = 0.05;
	private static final Set<Holder<Potion>> BASE_POTIONS = Set.of(Potions.WATER, Potions.AWKWARD, Potions.MUNDANE, Potions.THICK);
	private static final Set<Identifier> CONTAINER_TABLES = ConcurrentHashMap.newKeySet();

	private FoodLoot() {
	}

	public static void register() {
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			Identifier table = key.identifier();
			if (!ContainerTables.targets(table)) {
				return;
			}
			if (source.isBuiltin()) {
				CONTAINER_TABLES.add(table);
			} else {
				CONTAINER_TABLES.remove(table);
			}
		});
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> table.unwrapKey()
			.map(key -> key.identifier())
			.filter(CONTAINER_TABLES::contains)
			.ifPresent(id -> drops.addAll(roll(context.getRandom())))
		);
	}

	/** The dishes one container gets: each roll is its own draw. */
	public static List<ItemStack> roll(final RandomSource random) {
		java.util.List<ItemStack> found = new java.util.ArrayList<>();
		if (random.nextDouble() < DISH_CHANCE) {
			found.add(new ItemStack(randomDish(random)));
		}
		if (random.nextDouble() < TIPPED_CHANCE) {
			found.add(tipped(random));
		}
		return found;
	}

	public static ItemStack tipped(final RandomSource random) {
		ItemStack dish = new ItemStack(randomDish(random));
		List<Holder.Reference<Potion>> potions = BuiltInRegistries.POTION.listElements()
			.filter(potion -> !BASE_POTIONS.contains(potion))
			.toList();
		dish.set(DataComponents.POTION_CONTENTS, new PotionContents(potions.get(random.nextInt(potions.size()))));
		dish.set(StellaFood.SEASONING, Seasoning.roll(random));
		return dish;
	}

	private static Item randomDish(final RandomSource random) {
		return FoodItems.DISHES.get(random.nextInt(FoodItems.DISHES.size()));
	}
}
