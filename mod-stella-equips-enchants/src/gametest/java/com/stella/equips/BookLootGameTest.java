package com.stella.equips;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** Slice 6. Every catalogue enchantment reaches the world as an enchanted book. */
public class BookLootGameTest {
	private static final String TEST_MOD_ID = "mod-stella-equips-enchants-test-mod-stella-equips-enchants";
	private static final Identifier CATALOGUE_TABLE =
		Identifier.fromNamespaceAndPath(TEST_MOD_ID, "fixture_catalogue");
	private static final Identifier MAXIMA_TABLE =
		Identifier.fromNamespaceAndPath(TEST_MOD_ID, "fixture_maxima");
	private static final Identifier DUNGEON_CHEST = Identifier.withDefaultNamespace("chests/simple_dungeon");
	private static final Identifier RANDOM_LOOT_TAG = Identifier.withDefaultNamespace("on_random_loot");
	private static final int CATALOGUE_ROLLS = 1200;
	private static final int MAXIMA_ROLLS = 1200;
	private static final int CHEST_ROLLS = 600;
	private static final Identifier MAX_ONE = enchantment("armor/second_wind");
	private static final Identifier MAX_TWO = enchantment("bow/marksman");
	private static final Identifier MAX_THREE = enchantment("tool/blockfeast");
	private static final Identifier MAX_FIVE = enchantment("weapon/stunning_blow");
	private static final List<Integer> LEVELS_ONE_TO_FIVE = List.of(1, 2, 3, 4, 5);

	@GameTest(maxTicks = 200)
	public void theLootPathMintsCatalogueBooks(final GameTestHelper helper) {
		ItemStack looted = findCatalogueBook(helper, DUNGEON_CHEST, CHEST_ROLLS);
		helper.assertTrue(
			looted != null,
			"the loot path minted no book carrying a catalogue enchantment in " + CHEST_ROLLS + " chest rolls"
		);
		ItemEnchantments stored = EnchantmentHelper.getEnchantmentsForCrafting(looted);
		helper.assertTrue(
			!stored.isEmpty(),
			"the looted book carries nothing when read with getEnchantmentsForCrafting"
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=lootPathMintsCatalogueBooks RESULT isEnchantedBook={} stored={} rolled={}",
			looted.is(Items.ENCHANTED_BOOK), describe(stored), CHEST_ROLLS
		);
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void everyCatalogueEnchantmentIsInTheLootTag(final GameTestHelper helper) {
		Registry<Enchantment> registry = registry(helper);
		Set<String> catalogue = catalogueIds(registry);
		Set<String> tag = new TreeSet<>();
		registry.getTagOrEmpty(TagKey.create(Registries.ENCHANTMENT, RANDOM_LOOT_TAG)).forEach(
			holder -> holder.unwrapKey().map(key -> key.identifier().toString()).ifPresent(tag::add)
		);
		List<String> missing = new ArrayList<>(catalogue);
		missing.removeAll(tag);
		helper.assertTrue(
			missing.isEmpty() && !catalogue.isEmpty(),
			"catalogue entries with no book form: " + missing + " catalogue=" + catalogue.size()
				+ " tag=" + tag.size()
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=catalogueBookForms RESULT catalogue={} tagged={} missing={}",
			catalogue.size(), tag.size(), missing.size()
		);
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void everyCatalogueEnchantmentCanBeRolled(final GameTestHelper helper) {
		Registry<Enchantment> registry = registry(helper);
		Set<String> observed = new TreeSet<>();
		int books = 0;
		for (int roll = 0; roll < CATALOGUE_ROLLS; roll++) {
			for (ItemStack stack : roll(helper, lootTable(helper, CATALOGUE_TABLE))) {
				if (!stack.is(Items.ENCHANTED_BOOK)) {
					continue;
				}

				books++;
				observed.addAll(ids(EnchantmentHelper.getEnchantmentsForCrafting(stack)));
			}
		}

		List<String> missing = new ArrayList<>(catalogueIds(registry));
		missing.removeAll(observed);
		helper.assertTrue(books == CATALOGUE_ROLLS, "only " + books + " of " + CATALOGUE_ROLLS + " rolls minted a book");
		helper.assertTrue(missing.isEmpty(), "catalogue entries never rolled: " + missing);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=everyCatalogueEnchantmentCanBeRolled RESULT rolls={} books={} observed={} missing={}",
			CATALOGUE_ROLLS, books, observed.size(), missing.size()
		);
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void lootedLevelsStayInsideTheirOwnMaximum(final GameTestHelper helper) {
		Registry<Enchantment> registry = registry(helper);
		Map<String, Map<Integer, Integer>> observed = new TreeMap<>();
		int books = 0;
		for (int roll = 0; roll < MAXIMA_ROLLS; roll++) {
			for (ItemStack stack : roll(helper, lootTable(helper, MAXIMA_TABLE))) {
				if (!stack.is(Items.ENCHANTED_BOOK)) {
					continue;
				}

				books++;
				ItemEnchantments stored = EnchantmentHelper.getEnchantmentsForCrafting(stack);
				for (Holder<Enchantment> holder : stored.keySet()) {
					String id = holder.unwrapKey().map(key -> key.identifier().toString()).orElse("direct");
					observed.computeIfAbsent(id, key -> new TreeMap<>())
						.merge(stored.getLevel(holder), 1, Integer::sum);
				}
			}
		}

		List<String> overDeclared = new ArrayList<>();
		for (Map.Entry<String, Map<Integer, Integer>> entry : observed.entrySet()) {
			Enchantment enchantment = registry.getValue(Identifier.parse(entry.getKey()));
			int declared = enchantment == null ? -1 : enchantment.getMaxLevel();
			for (int level : entry.getValue().keySet()) {
				if (level > declared) {
					overDeclared.add(entry.getKey() + " rolled " + level + " over declared " + declared);
				}
			}
		}

		helper.assertTrue(books == MAXIMA_ROLLS, "only " + books + " of " + MAXIMA_ROLLS + " rolls minted a book");
		helper.assertTrue(overDeclared.isEmpty(), "a roll beat its declared maximum: " + overDeclared);
		helper.assertTrue(
			highest(observed, MAX_ONE) == declaredMax(registry, MAX_ONE)
				&& highest(observed, MAX_TWO) == declaredMax(registry, MAX_TWO)
				&& highest(observed, MAX_THREE) == declaredMax(registry, MAX_THREE)
				&& highest(observed, MAX_FIVE) == declaredMax(registry, MAX_FIVE),
			"declared maxima not reached: maxOne=" + highest(observed, MAX_ONE)
				+ "/" + declaredMax(registry, MAX_ONE) + " marksman=" + highest(observed, MAX_TWO)
				+ "/" + declaredMax(registry, MAX_TWO) + " blockfeast=" + highest(observed, MAX_THREE)
				+ "/" + declaredMax(registry, MAX_THREE) + " stunningBlow=" + highest(observed, MAX_FIVE)
				+ "/" + declaredMax(registry, MAX_FIVE)
		);
		helper.assertTrue(
			levels(observed, MAX_FIVE).equals(LEVELS_ONE_TO_FIVE),
			"stunning_blow rolled " + levels(observed, MAX_FIVE) + ", expected " + LEVELS_ONE_TO_FIVE
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=lootedLevelsStayInsideTheirOwnMaximum RESULT rolls={} books={} observed={} "
				+ "overDeclared={} maxOne={} marksman={} blockfeast={} stunningBlow={}",
			MAXIMA_ROLLS, books, observed.size(), overDeclared.size(),
			levels(observed, MAX_ONE), levels(observed, MAX_TWO),
			levels(observed, MAX_THREE), levels(observed, MAX_FIVE)
		);
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void chestsCarryCatalogueBooks(final GameTestHelper helper) {
		int catalogueBooks = 0;
		for (int roll = 0; roll < CHEST_ROLLS; roll++) {
			for (ItemStack stack : roll(helper, lootTable(helper, DUNGEON_CHEST))) {
				if (!stack.is(Items.ENCHANTED_BOOK)) {
					continue;
				}

				if (ids(EnchantmentHelper.getEnchantmentsForCrafting(stack)).stream()
					.anyMatch(id -> id.startsWith(StellaEquips.MOD_ID + ":"))) {
					catalogueBooks++;
				}
			}
		}

		helper.assertTrue(
			catalogueBooks > 0,
			"a dungeon chest never carried a catalogue book in " + CHEST_ROLLS + " rolls"
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=chestsCarryCatalogueBooks RESULT chestRolls={} catalogueBooks={}",
			CHEST_ROLLS, catalogueBooks
		);
		helper.succeed();
	}

	private static ItemStack findCatalogueBook(
		final GameTestHelper helper,
		final Identifier table,
		final int rolls
	) {
		for (int roll = 0; roll < rolls; roll++) {
			for (ItemStack stack : roll(helper, lootTable(helper, table))) {
				if (!stack.is(Items.ENCHANTED_BOOK)) {
					continue;
				}

				if (ids(EnchantmentHelper.getEnchantmentsForCrafting(stack)).stream()
					.anyMatch(id -> id.startsWith(StellaEquips.MOD_ID + ":"))) {
					return stack;
				}
			}
		}

		return null;
	}

	private static Set<String> ids(final ItemEnchantments enchantments) {
		Set<String> ids = new TreeSet<>();
		for (Holder<Enchantment> holder : enchantments.keySet()) {
			holder.unwrapKey().map(key -> key.identifier().toString()).ifPresent(ids::add);
		}

		return ids;
	}

	private static String describe(final ItemEnchantments enchantments) {
		List<String> described = new ArrayList<>();
		for (Holder<Enchantment> holder : enchantments.keySet()) {
			described.add(holder.unwrapKey().map(key -> key.identifier().toString()).orElse("direct")
				+ " level " + enchantments.getLevel(holder));
		}

		return described.toString();
	}

	private static List<Integer> levels(final Map<String, Map<Integer, Integer>> observed, final Identifier id) {
		return new ArrayList<>(observed.getOrDefault(id.toString(), Map.of()).keySet());
	}

	private static int highest(final Map<String, Map<Integer, Integer>> observed, final Identifier id) {
		return levels(observed, id).stream().mapToInt(Integer::intValue).max().orElse(0);
	}

	private static int declaredMax(final Registry<Enchantment> registry, final Identifier id) {
		Enchantment enchantment = registry.getValue(id);

		return enchantment == null ? -1 : enchantment.getMaxLevel();
	}

	private static Set<String> catalogueIds(final Registry<Enchantment> registry) {
		Set<String> ids = new TreeSet<>();
		for (Holder.Reference<Enchantment> holder : registry.listElements().toList()) {
			Identifier id = holder.key().identifier();
			if (id.getNamespace().equals(StellaEquips.MOD_ID) && !id.getPath().equals("test_marker")) {
				ids.add(id.toString());
			}
		}

		return ids;
	}

	private static List<ItemStack> roll(final GameTestHelper helper, final LootTable table) {
		LootParams params = new LootParams.Builder(helper.getLevel())
			.withParameter(LootContextParams.ORIGIN, helper.absoluteVec(new Vec3(4.5, 3.0, 4.5)))
			.create(LootContextParamSets.CHEST);
		return new ArrayList<>(table.getRandomItems(params));
	}

	private static LootTable lootTable(final GameTestHelper helper, final Identifier id) {
		return helper.getLevel().getServer().reloadableRegistries()
			.getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id));
	}

	private static Registry<Enchantment> registry(final GameTestHelper helper) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
	}

	private static Identifier enchantment(final String path) {
		return Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, path);
	}
}
