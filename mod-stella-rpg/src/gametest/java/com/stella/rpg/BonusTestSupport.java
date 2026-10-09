package com.stella.rpg;

import com.stella.rpg.roll.WeaponRolls;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Hand-built stacks and direct recipe calls for the craft sum and smelt tests. */
final class BonusTestSupport {
	private BonusTestSupport() {
	}

	static ItemStack rolled(final Item item, final int count, final Map<String, Integer> stats) {
		ItemStack stack = new ItemStack(item, count);
		stack.set(WeaponRolls.STAT_ROLLS, stats);
		return stack;
	}

	static ItemStack withAffixes(final ItemStack stack, final Map<String, Integer> affixes) {
		stack.set(WeaponRolls.AFFIX_ROLLS, affixes);
		return stack;
	}

	static Holder<Enchantment> enchantment(final GameTestHelper helper, final ResourceKey<Enchantment> key) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	static ItemStack enchanted(final ItemStack stack, final Holder<Enchantment> enchantment, final int level) {
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
		mutable.set(enchantment, level);
		stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
		return stack;
	}

	static int level(final ItemStack stack, final Holder<Enchantment> enchantment) {
		return stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(enchantment);
	}

	static Map<String, Integer> stats(final ItemStack stack) {
		return stack.get(WeaponRolls.STAT_ROLLS);
	}

	/** Runs the real crafting recipe that matches a grid of the given size. */
	static ItemStack craft(final GameTestHelper helper, final int width, final int height, final ItemStack... grid) {
		CraftingInput input = CraftingInput.of(width, height, List.of(grid));
		CraftingRecipe recipe = helper.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
			.orElseThrow(() -> new AssertionError("no crafting recipe for " + Arrays.toString(grid))).value();
		return recipe.assemble(input);
	}

	static ItemStack smith(final GameTestHelper helper, final ItemStack template, final ItemStack base, final ItemStack addition) {
		SmithingRecipeInput input = new SmithingRecipeInput(template, base, addition);
		SmithingRecipe recipe = helper.getLevel().recipeAccess().getRecipeFor(RecipeType.SMITHING, input, helper.getLevel())
			.orElseThrow(() -> new AssertionError("no smithing recipe for " + base + " " + addition)).value();
		return recipe.assemble(input);
	}

	static ItemStack smelt(final GameTestHelper helper, final ItemStack input) {
		SingleRecipeInput single = new SingleRecipeInput(input);
		SmeltingRecipe recipe = helper.getLevel().recipeAccess().getRecipeFor(RecipeType.SMELTING, single, helper.getLevel())
			.orElseThrow(() -> new AssertionError("no smelting recipe for " + input)).value();
		return recipe.assemble(single);
	}

	static ItemStack[] repeat(final ItemStack stack, final int copies) {
		ItemStack[] grid = new ItemStack[copies];
		for (int i = 0; i < copies; i++) {
			grid[i] = stack.copy();
		}
		return grid;
	}
}
