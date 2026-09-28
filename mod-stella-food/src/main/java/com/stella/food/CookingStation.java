package com.stella.food;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
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

	public static InteractionResult use(final ServerPlayer player) {
		ItemStack held = player.getMainHandItem();
		if (held.isEmpty()) {
			return InteractionResult.PASS;
		}
		if (FoodItems.isDish(held.getItem()) && isPotion(player.getOffhandItem())) {
			return infuse(player, held, player.getOffhandItem());
		}
		if (player.isShiftKeyDown()) {
			for (DishRecipe recipe : DISH_RECIPES) {
				if (recipe.contains(held.getItem()) && recipe.craft(player)) {
					return InteractionResult.SUCCESS;
				}
			}
			player.sendOverlayMessage(Component.literal("Missing ingredients for a combined dish"));
			return InteractionResult.FAIL;
		}
		if (FoodItems.isDish(held.getItem())) {
			return InteractionResult.PASS;
		}
		Item vanilla = VANILLA_COOKING.get(held.getItem());
		if (vanilla != null) {
			giveResult(player, held, new ItemStack(vanilla));
			return InteractionResult.SUCCESS;
		}
		FoodProperties food = held.get(DataComponents.FOOD);
		if (food == null || held.getOrDefault(StellaFood.COOKED_VARIANT, false)) {
			return InteractionResult.PASS;
		}
		ItemStack cooked = held.copyWithCount(1);
		cooked.set(
			DataComponents.FOOD,
			new FoodProperties(
				Math.min(20, food.nutrition() + 2),
				Math.min(20.0F, food.saturation() + 1.2F),
				food.canAlwaysEat()
			)
		);
		cooked.set(StellaFood.COOKED_VARIANT, true);
		cooked.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		giveResult(player, held, cooked);
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult infuse(
		final ServerPlayer player,
		final ItemStack dish,
		final ItemStack potion
	) {
		if (dish.has(DataComponents.POTION_CONTENTS)) {
			return InteractionResult.FAIL;
		}
		PotionContents contents = potion.get(DataComponents.POTION_CONTENTS);
		if (contents == null) {
			return InteractionResult.PASS;
		}
		ItemStack infused = dish.copyWithCount(1);
		infused.set(DataComponents.POTION_CONTENTS, contents);
		giveResult(player, dish, infused);
		potion.shrink(1);
		giveContainer(player, InteractionHand.OFF_HAND, potion, new ItemStack(Items.GLASS_BOTTLE));
		return InteractionResult.SUCCESS;
	}

	private static boolean isPotion(final ItemStack stack) {
		return (stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION))
			&& stack.has(DataComponents.POTION_CONTENTS);
	}

	private static void giveResult(
		final ServerPlayer player,
		final ItemStack source,
		final ItemStack result
	) {
		source.shrink(1);
		if (source.isEmpty()) {
			player.setItemInHand(InteractionHand.MAIN_HAND, result);
		} else if (!player.getInventory().add(result)) {
			player.drop(result, false, Prediction.PREDICTED);
		}
	}

	private static void giveContainer(
		final ServerPlayer player,
		final InteractionHand hand,
		final ItemStack source,
		final ItemStack container
	) {
		if (source.isEmpty()) {
			player.setItemInHand(hand, container);
		} else if (!player.getInventory().add(container)) {
			player.drop(container, false, Prediction.PREDICTED);
		}
	}

	private static DishRecipe recipe(
		final Item output,
		final List<Item> first,
		final List<Item> second,
		final List<Item> third
	) {
		return new DishRecipe(output, List.of(first, second, third));
	}

	private record DishRecipe(Item output, List<List<Item>> choices) {
		boolean contains(final Item item) {
			return this.choices.stream().anyMatch(choice -> choice.contains(item));
		}

		boolean craft(final ServerPlayer player) {
			Inventory inventory = player.getInventory();
			List<Integer> slots = new ArrayList<>();
			for (List<Item> choice : this.choices) {
				int slot = findSlot(inventory, choice, slots);
				if (slot < 0) {
					return false;
				}
				slots.add(slot);
			}
			for (int slot : slots) {
				ItemStack ingredient = inventory.getItem(slot);
				boolean returnsBottle = ingredient.is(Items.HONEY_BOTTLE);
				ingredient.shrink(1);
				if (returnsBottle) {
					ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
					if (!inventory.add(bottle)) {
						player.drop(bottle, false, Prediction.PREDICTED);
					}
				}
			}
			ItemStack result = new ItemStack(this.output);
			if (player.getMainHandItem().isEmpty()) {
				player.setItemInHand(InteractionHand.MAIN_HAND, result);
			} else if (!inventory.add(result)) {
				player.drop(result, false, Prediction.PREDICTED);
			}
			return true;
		}

		private static int findSlot(
			final Inventory inventory,
			final List<Item> choice,
			final List<Integer> reserved
		) {
			for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
				if (!reserved.contains(slot) && choice.contains(inventory.getItem(slot).getItem())) {
					return slot;
				}
			}
			return -1;
		}
	}
}
