package com.stella.ruins.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The smithing recipe of one affix. The data file names the affix only, as in
 * {@code {"type": "mod-stella-ruins:affix", "affix": "<id>"}}. The affix spec in code gives the template, the
 * base test, and the addition.
 */
public final class AffixRecipe extends SimpleSmithingRecipe {
	private record Raw(Recipe.CommonInfo info, String affix) {
	}

	private static final MapCodec<Raw> RAW_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Recipe.CommonInfo.MAP_CODEC.forGetter(Raw::info),
		Codec.STRING.fieldOf("affix").forGetter(Raw::affix)
	).apply(i, Raw::new));

	public static final MapCodec<AffixRecipe> MAP_CODEC = RAW_CODEC.flatXmap(
		raw -> create(raw.info(), raw.affix()),
		recipe -> DataResult.success(new Raw(recipe.commonInfo, recipe.affixId))
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, AffixRecipe> STREAM_CODEC = StreamCodec.composite(
		Recipe.CommonInfo.STREAM_CODEC, recipe -> recipe.commonInfo,
		ByteBufCodecs.STRING_UTF8, recipe -> recipe.affixId,
		(info, affix) -> create(info, affix).getOrThrow()
	);
	public static final RecipeSerializer<AffixRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

	private final String affixId;
	private final RuinAffixes.Affix affix;
	private final Ingredient template;
	// Item components are not bound while recipes decode, so the base filter waits for its first use.
	private volatile @Nullable Ingredient base;

	private AffixRecipe(final Recipe.CommonInfo info, final String affixId, final RuinAffixes.Affix affix) {
		super(info);
		this.affixId = affixId;
		this.affix = affix;
		this.template = Ingredient.of(affix.template());
	}

	private static DataResult<AffixRecipe> create(final Recipe.CommonInfo info, final String affixId) {
		Optional<RuinAffixes.Affix> found = RuinAffixes.get(affixId);
		if (found.isEmpty()) {
			return DataResult.error(() -> "Unknown ruins affix: " + affixId);
		}
		return DataResult.success(new AffixRecipe(info, affixId, found.get()));
	}

	private static Ingredient baseFilter(final String affixId, final RuinAffixes.Affix affix) {
		List<Item> accepted = BuiltInRegistries.ITEM.stream()
			.filter(item -> item != Items.AIR && affix.spec().accepts().test(new ItemStack(item)))
			.toList();
		if (accepted.isEmpty()) {
			throw new IllegalStateException("No item passes the base test of affix " + affixId);
		}
		return Ingredient.of(accepted.stream());
	}

	@Override
	public boolean matches(final SmithingRecipeInput input, final Level level) {
		return template.test(input.template()) && affix.spec().accepts().test(input.base()) && affix.spec().addition().test(input.addition());
	}

	@Override
	public ItemStack assemble(final SmithingRecipeInput input) {
		return RuinAffixes.apply(input.base(), affix);
	}

	@Override
	public Optional<Ingredient> templateIngredient() {
		return Optional.of(template);
	}

	@Override
	public Ingredient baseIngredient() {
		Ingredient filter = base;
		if (filter == null) {
			filter = baseFilter(affixId, affix);
			base = filter;
		}
		return filter;
	}

	@Override
	public Optional<Ingredient> additionIngredient() {
		return Optional.of(affix.spec().addition());
	}

	@Override
	public RecipeSerializer<AffixRecipe> getSerializer() {
		return SERIALIZER;
	}

	@Override
	protected PlacementInfo createPlacementInfo() {
		return PlacementInfo.create(List.of(template, baseIngredient(), affix.spec().addition()));
	}

	@Override
	public List<RecipeDisplay> display() {
		return List.of(new SmithingRecipeDisplay(
			template.display(), baseIngredient().display(), affix.spec().addition().display(), SlotDisplay.Empty.INSTANCE,
			new SlotDisplay.ItemSlotDisplay(Items.SMITHING_TABLE)
		));
	}
}
