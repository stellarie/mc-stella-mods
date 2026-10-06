package com.stella.spells;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public class SpellbookCatalogGameTest {
	@GameTest
	public void everyShippedItemTagHasADisplayName(final GameTestHelper helper) {
		SpellTestLog.track(helper, "everyShippedItemTagHasADisplayName");
		// A recipe viewer names a tag from its lang key, so every item tag this mod ships needs one.
		List<String> keys = BuiltInRegistries.ITEM.getTags()
			.map(named -> named.key().location())
			.filter(id -> id.getNamespace().equals(Spellbooks.MOD_ID))
			.map(id -> "tag.item." + id.getNamespace() + "." + id.getPath().replace('/', '.'))
			.sorted()
			.toList();
		List<String> missing = keys.stream().filter(key -> !Language.getInstance().has(key)).toList();
		Spellbooks.LOGGER.info("[BEHAVIOR] test=everyShippedItemTagHasADisplayName RESULT tags={} missing={}", keys, missing);
		helper.assertTrue(!keys.isEmpty(), "the mod ships no item tag, so nothing was checked");
		helper.assertTrue(missing.isEmpty(), "item tags without a display name: " + missing);
		helper.succeed();
	}

	private static final Identifier TEST_ENCHANTMENT = Identifier.parse(
		"mod-stella-spells-test-mod-stella-spells:anvil_log_test"
	);
	/**
	 * Every book the mod ships, each with the ingredient its own catalogue declares. This is the catalogue and
	 * not a copy of it, so a book added later arrives here on its own and no count needs editing.
	 */
	private static final List<RecipeEntry> EXPECTED_BOOKS = SpellbookCatalog.DEFINITIONS.stream()
		.map(definition -> new RecipeEntry(definition.id(), definition.ingredient()))
		.toList();
	private static final Set<Identifier> EXPECTED_IDS = EXPECTED_BOOKS.stream()
		.map(RecipeEntry::id)
		.collect(Collectors.toUnmodifiableSet());

	@GameTest
	public void catalogRegistersEveryBookAsAUniqueSingleStackWithFixedIdentity(final GameTestHelper helper) {
		SpellTestLog.track(helper, "catalogRegistersEveryBookAsAUniqueSingleStackWithFixedIdentity");
		Set<Identifier> actualIds = BuiltInRegistries.ITEM.stream()
			.filter(item -> item instanceof SpellbookItem)
			.map(BuiltInRegistries.ITEM::getKey)
			.filter(id -> Spellbooks.MOD_ID.equals(id.getNamespace()))
			.collect(Collectors.toSet());
		helper.assertTrue(
			actualIds.equals(EXPECTED_IDS),
			"spellbook item IDs differ: expected=" + EXPECTED_IDS + " actual=" + actualIds
		);
		boolean singleStackBooks = EXPECTED_BOOKS.stream().allMatch(entry -> item(entry.id()).getDefaultMaxStackSize() == 1);
		helper.assertTrue(singleStackBooks, "a spellbook item stacks above one");

		ResourceKey<Enchantment> enchantmentKey = ResourceKey.create(Registries.ENCHANTMENT, TEST_ENCHANTMENT);
		Holder.Reference<Enchantment> enchantment = helper.getLevel().registryAccess()
			.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantmentKey);
		ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		enchantments.set(enchantment, 1);
		boolean fixedIdentity = EXPECTED_BOOKS.stream().allMatch(entry -> {
			ItemStack stack = new ItemStack(item(entry.id()));
			stack.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
			return entry.id().equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
		});
		helper.assertTrue(fixedIdentity, "enchanting changed a spellbook's registered identity");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void anvilAppliesTestEnchantmentToEveryBookWithoutChangingIdentity(final GameTestHelper helper) {
		SpellTestLog.track(helper, "anvilAppliesTestEnchantmentToEveryBookWithoutChangingIdentity");
		ResourceKey<Enchantment> enchantmentKey = ResourceKey.create(Registries.ENCHANTMENT, TEST_ENCHANTMENT);
		Holder.Reference<Enchantment> enchantment = helper.getLevel().registryAccess()
			.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantmentKey);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		List<String> mismatches = new ArrayList<>();
		int menuId = 1;

		for (RecipeEntry entry : EXPECTED_BOOKS) {
			ItemStack input = new ItemStack(item(entry.id()));
			if (!enchantment.value().canEnchant(input) || !enchantment.value().isPrimaryItem(input)) {
				mismatches.add("anvil fixture does not support " + entry.id());
				continue;
			}

			ItemEnchantments.Mutable values = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
			values.set(enchantment, 1);
			ItemStack addition = Items.ENCHANTED_BOOK.getDefaultInstance();
			addition.set(DataComponents.STORED_ENCHANTMENTS, values.toImmutable());
			AnvilMenu menu = new AnvilMenu(menuId++, player.getInventory());
			menu.getSlot(AnvilMenu.INPUT_SLOT).set(input);
			menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(addition);
			menu.createResult();
			ItemStack output = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().copy();
			Identifier outputId = output.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(output.getItem());
			int level = output.isEmpty()
				? 0
				: EnchantmentHelper.getEnchantmentsForCrafting(output).getLevel(enchantment);
			if (!entry.id().equals(outputId) || output.getCount() != 1 || level != 1) {
				mismatches.add(
					"anvil changed " + entry.id() + " to " + outputId + " count=" + output.getCount()
						+ " enchantmentLevel=" + level
				);
			}
		}

		helper.assertTrue(mismatches.isEmpty(), String.join("; ", mismatches));
		helper.succeed();
	}

	@GameTest
	public void catalogHasAMappedFourCornerXRecipeForEveryBook(final GameTestHelper helper) {
		SpellTestLog.track(helper, "catalogHasAMappedFourCornerXRecipeForEveryBook");
		var manager = helper.getLevel().getServer().getRecipeManager();
		Set<Identifier> actualRecipeIds = manager.getRecipes().stream()
			.map(recipe -> recipe.id().identifier())
			.filter(id -> Spellbooks.MOD_ID.equals(id.getNamespace()) && !id.getPath().endsWith("_scroll")
				&& !id.getPath().endsWith("_from_scrolls") && !id.equals(Spellbooks.SCROLL_BOOK_ID))
			.collect(Collectors.toSet());
		List<String> mismatches = new ArrayList<>();
		if (!actualRecipeIds.equals(EXPECTED_IDS)) {
			mismatches.add("recipe IDs expected=" + EXPECTED_IDS + " actual=" + actualRecipeIds);
		}

		for (RecipeEntry entry : EXPECTED_BOOKS) {
			CraftingInput correctX = xInput(entry.ingredient(), ItemStack.EMPTY);
			var match = manager.getRecipeFor(RecipeType.CRAFTING, correctX, helper.getLevel());
			if (match.isEmpty()) {
				mismatches.add("missing X recipe " + entry.id());
				continue;
			}

			var recipe = match.orElseThrow();
			ItemStack result = recipe.value().assemble(correctX);
			if (!entry.id().equals(recipe.id().identifier())
				|| !entry.id().equals(BuiltInRegistries.ITEM.getKey(result.getItem()))
				|| result.getCount() != 1) {
				mismatches.add("wrong recipe result " + entry.id() + " -> " + result);
			}

			CraftingInput extraSide = xInput(entry.ingredient(), new ItemStack(Items.STICK));
			var sideMatch = manager.getRecipeFor(RecipeType.CRAFTING, extraSide, helper.getLevel());
			if (sideMatch.isPresent() && entry.id().equals(sideMatch.orElseThrow().id().identifier())) {
				mismatches.add("X recipe accepted a side ingredient " + entry.id());
			}
		}

		helper.assertTrue(mismatches.isEmpty(), String.join("; ", mismatches));
		helper.succeed();
	}

	private static Item item(final Identifier id) {
		return BuiltInRegistries.ITEM.get(id).map(Holder.Reference::value).orElse(Items.AIR);
	}

	private static CraftingInput xInput(final Item ingredient, final ItemStack side) {
		return CraftingInput.of(
			3, 3, List.of(
				new ItemStack(ingredient), side, new ItemStack(ingredient),
				ItemStack.EMPTY, new ItemStack(Items.BOOK), ItemStack.EMPTY,
				new ItemStack(ingredient), ItemStack.EMPTY, new ItemStack(ingredient)
			)
		);
	}

	private record RecipeEntry(Identifier id, Item ingredient) {
	}
}
