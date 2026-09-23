package com.stella.equips;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Slice 6 probe. Asks whether a loot table can roll a catalogue enchantment at
 * a chosen level range, and what level each loot function actually produces.
 */
public class BookLootProbeGameTest {
	private static final String MOD_ID = StellaEquips.MOD_ID;
	private static final String TEST_MOD_ID = "mod-stella-equips-enchants-test-mod-stella-equips-enchants";
	private static final int WIDE_ROLLS = 800;
	private static final int CATALOGUE_ROLLS = 1200;
	private static final int LEVEL5_ROLLS = 2000;

	private static final Identifier RANDOMLY_WIDE = table("fixture_maxima");
	private static final Identifier RANDOMLY_CATALOGUE = table("fixture_catalogue");
	private static final Identifier RANDOMLY_LEVEL5 = table("probe_book_randomly_level5");
	private static final Identifier RANDOMLY_TAG = table("probe_book_randomly_tag");
	private static final Identifier WITH_LEVELS_WIDE = table("probe_book_with_levels_wide");
	private static final Identifier WITH_LEVELS_CATALOGUE = table("probe_book_with_levels_catalogue");

	/** Fixtures chosen to span every declared max_level in the catalogue. */
	private static final Identifier MAX_ONE = enchantment("tool/prospector");
	private static final Identifier MAX_TWO = enchantment("bow/marksman");
	private static final Identifier MAX_THREE = enchantment("tool/blockfeast");
	private static final Identifier MAX_FIVE = enchantment("weapon/stunning_blow");

	@GameTest
	public void probeLootTablesLoad(final GameTestHelper helper) {
		List<Identifier> ids = List.of(
			RANDOMLY_WIDE, RANDOMLY_CATALOGUE, RANDOMLY_LEVEL5, RANDOMLY_TAG, WITH_LEVELS_WIDE, WITH_LEVELS_CATALOGUE
		);
		List<String> missing = new ArrayList<>();
		for (Identifier id : ids) {
			LootTable table = lootTable(helper, id);
			if (table == LootTable.EMPTY || roll(helper, table).isEmpty()) {
				missing.add(id.toString());
			}
		}
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=probeLootTablesLoad RESULT tables={} missing={}",
			ids.size(), missing.size()
		);
		helper.assertTrue(missing.isEmpty(), "probe loot tables did not load or rolled nothing: " + missing);
		helper.succeed();
	}

	/** The decisive answer. Does enchant_randomly honour a 1 to 5 range, or each own range? */
	@GameTest(maxTicks = 200)
	public void randomlyRollsEachEnchantmentsOwnRange(final GameTestHelper helper) {
		Registry<Enchantment> registry = registry(helper);
		Tally tally = new Tally();
		for (int roll = 0; roll < WIDE_ROLLS; roll++) {
			tally.add(roll(helper, lootTable(helper, RANDOMLY_WIDE)));
		}

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=probeRandomlyOwnRange RESULT rolls={} books={} maxEnchantmentsPerBook={} levels={} "
				+ "prospectorMax1={} marksmanMax2={} blockfeastMax3={} stunningBlowMax5={} stunningBlowLevels={}",
			WIDE_ROLLS, tally.books, tally.maxPerBook(), tally.levelCounts(),
			tally.observedLevels(MAX_ONE), tally.observedLevels(MAX_TWO),
			tally.observedLevels(MAX_THREE), tally.observedLevels(MAX_FIVE),
			"1:" + tally.count(MAX_FIVE, 1) + " 2:" + tally.count(MAX_FIVE, 2)
				+ " 3:" + tally.count(MAX_FIVE, 3) + " 4:" + tally.count(MAX_FIVE, 4)
				+ " 5:" + tally.count(MAX_FIVE, 5)
		);

		helper.assertTrue(
			tally.books == WIDE_ROLLS,
			"only " + tally.books + " of " + WIDE_ROLLS + " rolls minted a book"
		);
		helper.assertTrue(tally.badBooks.isEmpty(), "rolls produced non-books: " + tally.badBooks);
		helper.assertTrue(
			reachedDeclaredMax(tally, registry, MAX_ONE) && reachedDeclaredMax(tally, registry, MAX_TWO)
				&& reachedDeclaredMax(tally, registry, MAX_THREE) && reachedDeclaredMax(tally, registry, MAX_FIVE),
			"declared maxima not reached: prospector=" + tally.highestLevel(MAX_ONE)
				+ "/" + declaredMax(registry, MAX_ONE) + " marksman=" + tally.highestLevel(MAX_TWO)
				+ "/" + declaredMax(registry, MAX_TWO) + " blockfeast=" + tally.highestLevel(MAX_THREE)
				+ "/" + declaredMax(registry, MAX_THREE) + " stunningBlow=" + tally.highestLevel(MAX_FIVE)
				+ "/" + declaredMax(registry, MAX_FIVE)
		);
		helper.assertTrue(
			tally.exceedsDeclaredMax(registry).isEmpty(),
			"a roll beat its own max_level: " + tally.exceedsDeclaredMax(registry)
		);
		helper.succeed();
	}

	/**
	 * The rates mio needs: one enchantment that declares max_level 5, rolled
	 * 2000 times, so each of the five outcomes has a countable share.
	 */
	@GameTest(maxTicks = 200)
	public void randomlyRollsTheFullFiveLevelRange(final GameTestHelper helper) {
		Tally tally = new Tally();
		for (int roll = 0; roll < LEVEL5_ROLLS; roll++) {
			tally.add(roll(helper, lootTable(helper, RANDOMLY_LEVEL5)));
		}

		int one = tally.count(MAX_FIVE, 1);
		int two = tally.count(MAX_FIVE, 2);
		int three = tally.count(MAX_FIVE, 3);
		int four = tally.count(MAX_FIVE, 4);
		int five = tally.count(MAX_FIVE, 5);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=probeRandomlyFiveLevels RESULT rolls={} books={} maxEnchantmentsPerBook={} "
				+ "level1={} level2={} level3={} level4={} level5={} distinctLevels={}",
			LEVEL5_ROLLS, tally.books, tally.maxPerBook(), one, two, three, four, five, tally.highestLevel(MAX_FIVE)
		);

		helper.assertTrue(tally.books == LEVEL5_ROLLS, "only " + tally.books + " of " + LEVEL5_ROLLS + " rolls minted a book");
		helper.assertTrue(
			one > 0 && two > 0 && three > 0 && four > 0 && five > 0,
			"a level in 1 to 5 never appeared: " + tally.levelCounts()
		);
		helper.assertTrue(
			tally.maxPerBook() == 1,
			"enchant_randomly put " + tally.maxPerBook() + " enchantments on one book"
		);
		helper.succeed();
	}

	/**
	 * The shipping shape: the catalogue joins the vanilla loot tag by merging
	 * into data/minecraft/tags/enchantment/on_random_loot.json.
	 */
	@GameTest(maxTicks = 200)
	public void theCatalogueJoinsTheVanillaLootTag(final GameTestHelper helper) {
		Registry<Enchantment> registry = registry(helper);
		Tally tally = new Tally();
		for (int roll = 0; roll < LEVEL5_ROLLS; roll++) {
			tally.add(roll(helper, lootTable(helper, RANDOMLY_TAG)));
		}

		Map<Integer, Integer> ours = new TreeMap<>();
		int oursCount = 0;
		int vanillaCount = 0;
		for (Map.Entry<String, Map<Integer, Integer>> entry : tally.observed().entrySet()) {
			int count = entry.getValue().values().stream().mapToInt(Integer::intValue).sum();
			if (entry.getKey().startsWith(MOD_ID + ":")) {
				oursCount += count;
				entry.getValue().forEach((level, hits) -> ours.merge(level, hits, Integer::sum));
			} else {
				vanillaCount += count;
			}
		}

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=probeVanillaLootTag RESULT rolls={} books={} catalogueEnchantments={} "
				+ "vanillaEnchantments={} catalogueLevels={} catalogueLevel5={} overDeclaredMax={}",
			LEVEL5_ROLLS, tally.books, oursCount, vanillaCount, ours,
			ours.getOrDefault(5, 0), tally.exceedsDeclaredMax(registry).size()
		);

		helper.assertTrue(
			oursCount > 0,
			"no catalogue enchantment came out of #minecraft:on_random_loot"
		);
		helper.assertTrue(
			tally.exceedsDeclaredMax(registry).isEmpty(),
			"a roll beat its own max_level: " + tally.exceedsDeclaredMax(registry)
		);
		helper.succeed();
	}

	/** The whole catalogue through enchant_randomly, the shape mio will ship. */
	@GameTest(maxTicks = 200)
	public void randomlyOverTheCatalogueStaysInsideDeclaredLevels(final GameTestHelper helper) {
		Registry<Enchantment> registry = registry(helper);
		Tally tally = new Tally();
		for (int roll = 0; roll < CATALOGUE_ROLLS; roll++) {
			tally.add(roll(helper, lootTable(helper, RANDOMLY_CATALOGUE)));
		}

		Map<Integer, Integer> perLevel = tally.levelCounts();
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=probeRandomlyCatalogue RESULT rolls={} books={} distinctEnchantments={} "
				+ "level1={} level2={} level3={} level4={} level5={} overDeclaredMax={}",
			CATALOGUE_ROLLS, tally.books, tally.observed.size(),
			perLevel.getOrDefault(1, 0), perLevel.getOrDefault(2, 0), perLevel.getOrDefault(3, 0),
			perLevel.getOrDefault(4, 0), perLevel.getOrDefault(5, 0),
			tally.exceedsDeclaredMax(registry).size()
		);

		helper.assertTrue(!tally.observed.isEmpty(), "the catalogue roll produced no enchantment");
		helper.assertTrue(
			tally.exceedsDeclaredMax(registry).isEmpty(),
			"a roll beat its own max_level: " + tally.exceedsDeclaredMax(registry)
		);
		helper.succeed();
	}

	/** What enchant_with_levels does with an explicit 1 to 5 number. */
	@GameTest(maxTicks = 200)
	public void withLevelsRollsCostBandsNotLevels(final GameTestHelper helper) {
		Registry<Enchantment> registry = registry(helper);
		Tally tally = new Tally();
		int unenchanted = 0;
		for (int roll = 0; roll < WIDE_ROLLS; roll++) {
			List<ItemStack> stacks = roll(helper, lootTable(helper, WITH_LEVELS_WIDE));
			tally.add(stacks);
			unenchanted += countUnenchanted(stacks);
		}

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=probeWithLevelsWide RESULT rolls={} books={} unenchanted={} maxEnchantmentsPerBook={} "
				+ "levels={} prospector={} marksman={} blockfeast={} stunningBlow={} stunningBlowLevel5={}",
			WIDE_ROLLS, tally.books, unenchanted, tally.maxPerBook(), tally.levelCounts(),
			tally.observedLevels(MAX_ONE), tally.observedLevels(MAX_TWO),
			tally.observedLevels(MAX_THREE), tally.observedLevels(MAX_FIVE), tally.count(MAX_FIVE, 5)
		);

		helper.assertTrue(
			tally.books + unenchanted == WIDE_ROLLS,
			"accounting lost a roll: books=" + tally.books + " unenchanted=" + unenchanted
		);
		helper.assertTrue(
			tally.exceedsDeclaredMax(registry).isEmpty(),
			"a roll beat its own max_level: " + tally.exceedsDeclaredMax(registry)
		);
		helper.succeed();
	}

	/** The same fallback over the whole catalogue, to see how often it produces nothing. */
	@GameTest(maxTicks = 200)
	public void withLevelsOverTheCatalogue(final GameTestHelper helper) {
		Tally tally = new Tally();
		int unenchanted = 0;
		for (int roll = 0; roll < CATALOGUE_ROLLS; roll++) {
			List<ItemStack> stacks = roll(helper, lootTable(helper, WITH_LEVELS_CATALOGUE));
			tally.add(stacks);
			unenchanted += countUnenchanted(stacks);
		}

		Map<Integer, Integer> perLevel = tally.levelCounts();
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=probeWithLevelsCatalogue RESULT rolls={} books={} unenchanted={} "
				+ "distinctEnchantments={} level1={} level2={} level3={} level4={} level5={}",
			CATALOGUE_ROLLS, tally.books, unenchanted, tally.observed.size(),
			perLevel.getOrDefault(1, 0), perLevel.getOrDefault(2, 0), perLevel.getOrDefault(3, 0),
			perLevel.getOrDefault(4, 0), perLevel.getOrDefault(5, 0)
		);

		helper.assertTrue(
			tally.books + unenchanted == CATALOGUE_ROLLS,
			"accounting lost a roll: books=" + tally.books + " unenchanted=" + unenchanted
		);
		helper.succeed();
	}

	/** The loot book is the real thing: it matches EnchantmentHelper.createBook exactly. */
	@GameTest(maxTicks = 200)
	public void lootBookMatchesTheCanonicalBook(final GameTestHelper helper) {
		ItemStack looted = null;
		for (int roll = 0; roll < WIDE_ROLLS && looted == null; roll++) {
			for (ItemStack stack : roll(helper, lootTable(helper, RANDOMLY_WIDE))) {
				if (stack.is(Items.ENCHANTED_BOOK) && EnchantmentHelper.getEnchantmentsForCrafting(stack).size() == 1) {
					looted = stack;
					break;
				}
			}
		}

		helper.assertTrue(looted != null, "no roll produced a single enchantment book");
		ItemEnchantments stored = EnchantmentHelper.getEnchantmentsForCrafting(looted);
		Holder<Enchantment> holder = stored.keySet().iterator().next();
		int level = stored.getLevel(holder);
		ItemStack canonical = EnchantmentHelper.createBook(new EnchantmentInstance(holder, level));
		boolean matches = EnchantmentHelper.getEnchantmentsForCrafting(canonical).equals(stored);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=probeLootBookIsReal RESULT isEnchantedBook={} storedEnchantments={} "
				+ "plainComponentEmpty={} enchantment={} level={} matchesCreateBook={}",
			looted.is(Items.ENCHANTED_BOOK),
			stored.size(),
			looted.getEnchantments().isEmpty(),
			holder.unwrapKey().map(key -> key.identifier().toString()).orElse("direct"),
			level,
			matches
		);

		helper.assertTrue(looted.is(Items.ENCHANTED_BOOK), "the loot stack is not an enchanted book");
		helper.assertTrue(matches, "the loot book differs from EnchantmentHelper.createBook");
		helper.succeed();
	}

	private static int declaredMax(final Registry<Enchantment> registry, final Identifier id) {
		Enchantment enchantment = registry.getValue(id);

		return enchantment == null ? -1 : enchantment.getMaxLevel();
	}

	private static boolean reachedDeclaredMax(
		final Tally tally,
		final Registry<Enchantment> registry,
		final Identifier id
	) {
		return tally.highestLevel(id) == declaredMax(registry, id);
	}

	private static int countUnenchanted(final List<ItemStack> stacks) {
		int count = 0;
		for (ItemStack stack : stacks) {
			if (!stack.is(Items.ENCHANTED_BOOK)) {
				count++;
			}
		}
		return count;
	}

	private static final class Tally {
		private final Map<String, Map<Integer, Integer>> observed = new TreeMap<>();
		private final List<String> badBooks = new ArrayList<>();
		private int books;
		private int mostEnchantmentsOnOneBook;

		void add(final List<ItemStack> stacks) {
			for (ItemStack stack : stacks) {
				if (!stack.is(Items.ENCHANTED_BOOK)) {
					this.badBooks.add(stack.getItem().toString());
					continue;
				}

				this.books++;
				ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
				this.mostEnchantmentsOnOneBook = Math.max(this.mostEnchantmentsOnOneBook, enchantments.size());
				for (Holder<Enchantment> holder : enchantments.keySet()) {
					String id = holder.unwrapKey().map(key -> key.identifier().toString()).orElse("direct");
					int level = enchantments.getLevel(holder);
					this.observed.computeIfAbsent(id, key -> new TreeMap<>()).merge(level, 1, Integer::sum);
				}
			}
		}

		int maxPerBook() {
			return this.mostEnchantmentsOnOneBook;
		}

		Map<String, Map<Integer, Integer>> observed() {
			return this.observed;
		}

		int count(final Identifier enchantment, final int level) {
			return this.observed.getOrDefault(enchantment.toString(), Map.of()).getOrDefault(level, 0);
		}

		int highestLevel(final Identifier enchantment) {
			return this.observed.getOrDefault(enchantment.toString(), Map.of()).keySet().stream()
				.mapToInt(Integer::intValue)
				.max()
				.orElse(0);
		}

		String observedLevels(final Identifier enchantment) {
			return this.observed.getOrDefault(enchantment.toString(), Map.of()).keySet().toString();
		}

		Map<Integer, Integer> levelCounts() {
			Map<Integer, Integer> counts = new TreeMap<>();
			for (Map<Integer, Integer> perLevel : this.observed.values()) {
				perLevel.forEach((level, count) -> counts.merge(level, count, Integer::sum));
			}
			return counts;
		}

		List<String> exceedsDeclaredMax(final Registry<Enchantment> registry) {
			Map<String, Integer> declared = new HashMap<>();
			registry.listElements().forEach(
				holder -> declared.put(holder.key().identifier().toString(), holder.value().getMaxLevel())
			);
			List<String> offenders = new ArrayList<>();
			this.observed.forEach((id, perLevel) -> {
				int max = declared.getOrDefault(id, -1);
				for (int level : perLevel.keySet()) {
					if (level > max) {
						offenders.add(id + " rolled " + level + " over declared " + max);
					}
				}
			});
			return offenders;
		}
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

	private static Identifier table(final String path) {
		return Identifier.fromNamespaceAndPath(TEST_MOD_ID, path);
	}

	private static Identifier enchantment(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
