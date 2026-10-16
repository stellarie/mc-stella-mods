package com.stella.tower;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Nine shards of one element make a rune of floor {@value RuneShards#FLOOR_STEP}. A rune and eight shards of its element make
 * that rune ten floors higher. The data file names the type only: the recipe reads item components, which a shaped or
 * shapeless recipe cannot.
 */
public final class RuneShardRecipe extends CustomRecipe {
	public static final RecipeSerializer<RuneShardRecipe> SERIALIZER = new RecipeSerializer<>(
		MapCodec.unit(RuneShardRecipe::new), StreamCodec.<RegistryFriendlyByteBuf, RuneShardRecipe>unit(new RuneShardRecipe()));

	/** What the grid holds: the shared element, and the rune when the grid raises one. */
	private record Mix(TowerElement element, @Nullable RuneData rune) {
	}

	private static @Nullable Mix read(final CraftingInput input) {
		if (input.ingredientCount() != RuneShards.SHARDS_PER_RUNE) {
			return null;
		}
		TowerElement element = null;
		RuneData rune = null;
		int shards = 0;
		for (int slot = 0; slot < input.size(); slot++) {
			ItemStack stack = input.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			TowerElement held = RuneShards.elementOf(stack);
			if (held != null) {
				shards++;
			} else if (rune == null && stack.is(TowerItems.RUNE) && stack.has(TowerItems.RUNE_DATA)) {
				rune = stack.get(TowerItems.RUNE_DATA);
				held = rune.element();
			} else {
				return null;
			}
			if (element != null && element != held) {
				return null;
			}
			element = held;
		}
		boolean made = rune == null && shards == RuneShards.SHARDS_PER_RUNE;
		boolean raised = rune != null && shards == RuneShards.UPGRADE_SHARDS;
		return element != null && (made || raised) ? new Mix(element, rune) : null;
	}

	@Override
	public boolean matches(final CraftingInput input, final Level level) {
		return read(input) != null;
	}

	@Override
	public ItemStack assemble(final CraftingInput input) {
		Mix mix = read(input);
		if (mix == null) {
			return ItemStack.EMPTY;
		}
		long floor = mix.rune() == null ? RuneShards.FLOOR_STEP : (long) mix.rune().floor() + RuneShards.FLOOR_STEP;
		return TowerRunes.rune(mix.element(), (int) Math.min(floor, Integer.MAX_VALUE));
	}

	@Override
	public RecipeSerializer<RuneShardRecipe> getSerializer() {
		return SERIALIZER;
	}
}
