package com.stella.end;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleSmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

/**
 * A smithing recipe whose addition slot must hold several of the material. Vanilla takes one of each input; the menu
 * mixin takes the rest when the result is taken. Only the base's enchantments pass to the result: its attribute
 * modifiers and wear belong to the netherite piece and would overwrite the draconic piece's own stats.
 */
public final class DraconicSmithingRecipe extends SimpleSmithingRecipe {
	public static final MapCodec<DraconicSmithingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
			Recipe.CommonInfo.MAP_CODEC.forGetter(recipe -> recipe.commonInfo),
			Ingredient.CODEC.fieldOf("template").forGetter(recipe -> recipe.template),
			Ingredient.CODEC.fieldOf("base").forGetter(recipe -> recipe.base),
			Ingredient.CODEC.fieldOf("addition").forGetter(recipe -> recipe.addition),
			Codec.intRange(1, 64).fieldOf("addition_count").forGetter(recipe -> recipe.additionCount),
			ItemStackTemplate.CODEC.fieldOf("result").forGetter(recipe -> recipe.result)
		).apply(instance, DraconicSmithingRecipe::new)
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, DraconicSmithingRecipe> STREAM_CODEC = StreamCodec.composite(
		Recipe.CommonInfo.STREAM_CODEC, recipe -> recipe.commonInfo,
		Ingredient.CONTENTS_STREAM_CODEC, recipe -> recipe.template,
		Ingredient.CONTENTS_STREAM_CODEC, recipe -> recipe.base,
		Ingredient.CONTENTS_STREAM_CODEC, recipe -> recipe.addition,
		ByteBufCodecs.VAR_INT, recipe -> recipe.additionCount,
		ItemStackTemplate.STREAM_CODEC, recipe -> recipe.result,
		DraconicSmithingRecipe::new
	);
	public static final RecipeSerializer<DraconicSmithingRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

	private final Ingredient template;
	private final Ingredient base;
	private final Ingredient addition;
	private final int additionCount;
	private final ItemStackTemplate result;

	public DraconicSmithingRecipe(final Recipe.CommonInfo commonInfo, final Ingredient template, final Ingredient base, final Ingredient addition,
		final int additionCount, final ItemStackTemplate result) {
		super(commonInfo);
		this.template = template;
		this.base = base;
		this.addition = addition;
		this.additionCount = additionCount;
		this.result = result;
	}

	/** How many of the addition the recipe takes. */
	public int additionCount() {
		return additionCount;
	}

	@Override
	public boolean matches(final SmithingRecipeInput input, final Level level) {
		return super.matches(input, level) && input.addition().getCount() >= additionCount;
	}

	@Override
	public ItemStack assemble(final SmithingRecipeInput input) {
		ItemEnchantments enchantments = input.base().get(DataComponents.ENCHANTMENTS);
		DataComponentPatch patch = enchantments == null || enchantments.isEmpty()
			? DataComponentPatch.EMPTY
			: DataComponentPatch.builder().set(DataComponents.ENCHANTMENTS, enchantments).build();
		return result.apply(result.count(), patch);
	}

	@Override
	public Optional<Ingredient> templateIngredient() {
		return Optional.of(template);
	}

	@Override
	public Ingredient baseIngredient() {
		return base;
	}

	@Override
	public Optional<Ingredient> additionIngredient() {
		return Optional.of(addition);
	}

	@Override
	public RecipeSerializer<DraconicSmithingRecipe> getSerializer() {
		return SERIALIZER;
	}

	@Override
	protected PlacementInfo createPlacementInfo() {
		return PlacementInfo.createFromOptionals(List.of(Optional.of(template), Optional.of(base), Optional.of(addition)));
	}

	@Override
	public List<RecipeDisplay> display() {
		return List.of(new SmithingRecipeDisplay(
			Ingredient.optionalIngredientToDisplay(Optional.of(template)),
			base.display(),
			Ingredient.optionalIngredientToDisplay(Optional.of(addition)),
			new SlotDisplay.ItemStackSlotDisplay(result),
			new SlotDisplay.ItemSlotDisplay(Items.SMITHING_TABLE)
		));
	}
}
