package com.stella.food;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

public final class CookingStation {
	private static final List<Item> COOKED_MEAT = List.of(
		Items.COOKED_BEEF,
		Items.COOKED_PORKCHOP,
		Items.COOKED_CHICKEN,
		Items.COOKED_MUTTON,
		Items.COOKED_RABBIT
	);
	private static final List<Item> MUSHROOM = List.of(Items.BROWN_MUSHROOM, Items.RED_MUSHROOM);
	private static final List<Item> COOKED_FISH = List.of(Items.COOKED_COD, Items.COOKED_SALMON);
	private static final List<DishRecipe> DISH_RECIPES = List.of(
		recipe(FoodItems.HUNTER_STEW, COOKED_MEAT, MUSHROOM, List.of(Items.CARROT)),
		recipe(FoodItems.MINERS_LUNCH, List.of(Items.BAKED_POTATO), COOKED_MEAT, List.of(Items.BEETROOT)),
		recipe(FoodItems.ORCHARD_TART, List.of(Items.APPLE), List.of(Items.WHEAT), List.of(Items.HONEY_BOTTLE)),
		recipe(FoodItems.FISHERS_FEAST, COOKED_FISH, List.of(Items.DRIED_KELP), List.of(Items.POTATO)),
		recipe(FoodItems.NETHER_SKEWER, List.of(Items.COOKED_PORKCHOP), List.of(Items.WARPED_FUNGUS), List.of(Items.BLAZE_POWDER)),
		recipe(FoodItems.GOLDEN_SUPPER, List.of(Items.GOLDEN_CARROT), COOKED_MEAT, List.of(Items.HONEY_BOTTLE))
	);
	private static final Map<Item, Item> VANILLA_COOKING = Map.ofEntries(
		Map.entry(Items.BEEF, Items.COOKED_BEEF),
		Map.entry(Items.PORKCHOP, Items.COOKED_PORKCHOP),
		Map.entry(Items.CHICKEN, Items.COOKED_CHICKEN),
		Map.entry(Items.MUTTON, Items.COOKED_MUTTON),
		Map.entry(Items.RABBIT, Items.COOKED_RABBIT),
		Map.entry(Items.COD, Items.COOKED_COD),
		Map.entry(Items.SALMON, Items.COOKED_SALMON),
		Map.entry(Items.POTATO, Items.BAKED_POTATO),
		Map.entry(Items.KELP, Items.DRIED_KELP)
	);

	private CookingStation() {
	}

	public static List<DishRecipe> dishRecipes() {
		return DISH_RECIPES;
	}

	static Match findMatch(final Container input) {
		List<Integer> occupied = occupiedSlots(input);
		Match infusion = findInfusion(input, occupied);
		if (infusion != null) {
			return infusion;
		}
		Match dish = findDish(input, occupied);
		return dish != null ? dish : findCooking(input, occupied);
	}

	private static Match findInfusion(final Container input, final List<Integer> occupied) {
		if (occupied.size() != 2) {
			return null;
		}
		int dishSlot = -1;
		int potionSlot = -1;
		PotionContents contents = null;
		for (int slot : occupied) {
			ItemStack stack = input.getItem(slot);
			if (FoodItems.isDish(stack.getItem()) && !stack.has(DataComponents.POTION_CONTENTS)) {
				dishSlot = slot;
			} else if (isPotion(stack)) {
				potionSlot = slot;
				contents = stack.get(DataComponents.POTION_CONTENTS);
			}
		}
		if (dishSlot < 0 || potionSlot < 0 || contents == null) {
			return null;
		}
		ItemStack result = input.getItem(dishSlot).copyWithCount(1);
		result.set(DataComponents.POTION_CONTENTS, contents);
		return new Match(result, List.of(dishSlot, potionSlot), Set.of(potionSlot));
	}

	private static Match findDish(final Container input, final List<Integer> occupied) {
		if (occupied.size() != 3) {
			return null;
		}
		for (DishRecipe recipe : DISH_RECIPES) {
			List<Integer> matched = new ArrayList<>();
			for (List<Item> choice : recipe.choices()) {
				int slot = findChoice(input, occupied, matched, choice);
				if (slot < 0) {
					matched.clear();
					break;
				}
				matched.add(slot);
			}
			if (matched.size() == recipe.choices().size()) {
				Set<Integer> bottles = new HashSet<>();
				for (int slot : matched) {
					if (input.getItem(slot).is(Items.HONEY_BOTTLE)) {
						bottles.add(slot);
					}
				}
				return new Match(new ItemStack(recipe.output()), List.copyOf(matched), Set.copyOf(bottles));
			}
		}
		return null;
	}

	private static Match findCooking(final Container input, final List<Integer> occupied) {
		if (occupied.size() != 1) {
			return null;
		}
		int slot = occupied.getFirst();
		ItemStack source = input.getItem(slot);
		Item vanilla = VANILLA_COOKING.get(source.getItem());
		if (vanilla != null) {
			return new Match(new ItemStack(vanilla), List.of(slot), Set.of());
		}
		FoodProperties food = source.get(DataComponents.FOOD);
		if (food == null || !source.has(DataComponents.CONSUMABLE) || FoodItems.isDish(source.getItem())
			|| source.getOrDefault(StellaFood.COOKED_VARIANT, false)) {
			return null;
		}
		ItemStack result = source.copyWithCount(1);
		result.set(
			DataComponents.FOOD,
			new FoodProperties(
				Math.min(20, food.nutrition() + 2),
				Math.min(20.0F, food.saturation() + 1.2F),
				food.canAlwaysEat()
			)
		);
		result.set(StellaFood.COOKED_VARIANT, true);
		result.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return new Match(result, List.of(slot), Set.of());
	}

	private static List<Integer> occupiedSlots(final Container input) {
		List<Integer> occupied = new ArrayList<>();
		for (int slot = 0; slot < input.getContainerSize(); slot++) {
			if (!input.getItem(slot).isEmpty()) {
				occupied.add(slot);
			}
		}
		return occupied;
	}

	private static int findChoice(
		final Container input,
		final List<Integer> occupied,
		final List<Integer> matched,
		final List<Item> choice
	) {
		for (int slot : occupied) {
			if (!matched.contains(slot) && choice.contains(input.getItem(slot).getItem())) {
				return slot;
			}
		}
		return -1;
	}

	private static boolean isPotion(final ItemStack stack) {
		return (stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION))
			&& stack.has(DataComponents.POTION_CONTENTS);
	}

	private static DishRecipe recipe(
		final Item output,
		final List<Item> first,
		final List<Item> second,
		final List<Item> third
	) {
		return new DishRecipe(output, List.of(first, second, third));
	}

	public record DishRecipe(Item output, List<List<Item>> choices) {
	}

	record Match(ItemStack result, List<Integer> inputSlots, Set<Integer> bottleSlots) {
	}
}
